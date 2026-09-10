package com.aiko.lingo.ui.dashboard

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiko.lingo.data.model.StatsResponse
import com.aiko.lingo.data.model.WordOfDayResponse
import com.aiko.lingo.data.local.OfflineCache
import com.aiko.lingo.data.remote.AikoApiService
import com.aiko.lingo.data.remote.LingoCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DashboardViewModel(private val apiService: AikoApiService) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        refreshStats()
    }

    // Monotonic request id: a slow word-of-day from refresh N must never
    // overwrite fresher stats emitted by refresh N+1.
    private var refreshSeq = 0

    fun refreshStats() {
        // Cache-first: memory, then disk (offline), then network.
        val cachedStats: StatsResponse? = LingoCache.get("stats", 30_000)
            ?: OfflineCache.get(OfflineCache.STATS)
        val cachedWord: WordOfDayResponse? = LingoCache.get("word", 600_000)
            ?: OfflineCache.get(OfflineCache.WORD)
        val cachedJlpt: String? = LingoCache.get("stats_jlpt", 60_000)
        if (cachedStats != null) {
            _uiState.value = DashboardUiState.Success(cachedStats, cachedWord, cachedJlpt ?: "")
        } else {
            _uiState.value = DashboardUiState.Loading
        }
        val seq = ++refreshSeq
        viewModelScope.launch {
            try {
                val stats = apiService.getStats()
                LingoCache.put("stats", stats)
                OfflineCache.put(OfflineCache.STATS, stats)
                // JLPT track is best-effort -- never block stats on it.
                val jlptLevel = try {
                    apiService.getLearnStatus().level.ifBlank { stats.last_level }
                } catch (e: Exception) {
                    Log.w("Dashboard", "JLPT level unavailable", e)
                    stats.last_level
                }
                if (seq != refreshSeq) return@launch
                // Emit stats immediately so the spinner stops; word-of-day
                // (LLM-backed for brand-new users) loads after.
                _uiState.value = DashboardUiState.Success(stats, cachedWord, jlptLevel)
                LingoCache.put("stats_jlpt", jlptLevel)
                viewModelScope.launch {
                    try {
                        val wordOfDay = apiService.getWordOfDay()
                        if (seq != refreshSeq) return@launch
                        LingoCache.put("word", wordOfDay)
                        OfflineCache.put(OfflineCache.WORD, wordOfDay)
                        _uiState.value = DashboardUiState.Success(stats, wordOfDay, jlptLevel)
                    } catch (e: Exception) {
                        Log.w("Dashboard", "Word of the day unavailable", e)
                    }
                }
            } catch (e: Exception) {
                if (seq != refreshSeq) return@launch
                // Keep cached content on refresh failure instead of error screen.
                if (cachedStats == null) {
                    Log.e("Dashboard", "Failed to fetch stats", e)
                    _uiState.value = DashboardUiState.Error(e.message ?: "Failed to load stats")
                } else {
                    Log.w("Dashboard", "Stats refresh failed, keeping cache", e)
                }
            }
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    private var playToken = 0

    // Compact speak button for the Word of the Day card -- same pattern as
    // ConversationViewModel.playAudio: prefer the server-provided audioUrl,
    // otherwise synthesize via /tts, and stream it with MediaPlayer.
    fun playWord(text: String, existingUrl: String? = null) {
        if (text.isBlank() && existingUrl.isNullOrBlank()) return
        stopAudio()
        val token = ++playToken
        viewModelScope.launch {
            var currentPlayer: MediaPlayer? = null
            try {
                val url = existingUrl ?: apiService.getTts(text).audioUrl
                currentPlayer = MediaPlayer().apply {
                    setDataSource(url)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    prepareAsync()
                    setOnPreparedListener { try { start() } catch (e: Exception) {
                        Log.e("Dashboard", "Failed to start word audio", e) } }
                    setOnCompletionListener { mp ->
                        try { mp?.release() } catch (e: Exception) {
                        Log.e("Dashboard", "Error releasing word player", e) }
                        if (mediaPlayer === mp) {
                            mediaPlayer = null
                        }
                    }
                    setOnErrorListener { mp, what, extra ->
                        Log.e("Dashboard", "Word audio error: what=$what, extra=$extra")
                        try { mp?.release() } catch (e: Exception) {
                            Log.e("Dashboard", "Error releasing word player", e) }
                        if (mediaPlayer === mp) {
                            mediaPlayer = null
                        }
                        true
                    }
                }
                if (token != playToken) {
                    try { currentPlayer?.release() } catch (e: Exception) {
                        Log.e("Dashboard", "Error releasing superseded player", e) }
                    return@launch
                }
                mediaPlayer = currentPlayer
            } catch (e: Exception) {
                Log.e("Dashboard", "Word audio playback error", e)
                try { currentPlayer?.release() } catch (re: Exception) {
                    Log.e("Dashboard", "Error releasing word player", re) }
                if (mediaPlayer === currentPlayer) {
                    mediaPlayer = null
                }
            }
        }
    }

    fun stopAudio() {
        playToken++
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.e("Dashboard", "Error stopping word audio", e)
        } finally {
            mediaPlayer = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAudio()
    }
}

sealed class DashboardUiState {
    object Loading : DashboardUiState()
    data class Success(val stats: StatsResponse, val wordOfDay: WordOfDayResponse? = null, val jlptLevel: String = "") : DashboardUiState()
    data class Error(val message: String) : DashboardUiState()
}
