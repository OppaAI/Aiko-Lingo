package com.aiko.lingo.ui.dashboard

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiko.lingo.data.model.StatsResponse
import com.aiko.lingo.data.model.WordOfDayResponse
import com.aiko.lingo.data.remote.AikoApiService
import retrofit2.HttpException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DashboardViewModel(private val apiService: AikoApiService) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        refreshStats()
    }

    fun refreshStats() {
        viewModelScope.launch {
            _uiState.value = DashboardUiState.Loading
            try {
                val stats = apiService.getStats()
                // JLPT track is best-effort -- never block stats on it.
                val jlptLevel = try {
                    apiService.getLearnStatus().level.ifBlank { stats.last_level }
                } catch (e: Exception) {
                    Log.w("Dashboard", "JLPT level unavailable", e)
                    stats.last_level
                }
                // Emit stats immediately so the spinner stops; word-of-day
                // (LLM-backed, can take ~60s for new users) loads after.
                _uiState.value = DashboardUiState.Success(stats, null, jlptLevel)
                viewModelScope.launch {
                    try {
                        val wordOfDay = apiService.getWordOfDay()
                        _uiState.value = DashboardUiState.Success(stats, wordOfDay, jlptLevel)
                    } catch (e: Exception) {
                        Log.w("Dashboard", "Word of the day unavailable", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("Dashboard", "Failed to fetch stats", e)
                _uiState.value = DashboardUiState.Error(e.message ?: "Failed to load stats")
            }
        }
    }

    private var mediaPlayer: MediaPlayer? = null

    // Compact speak button for the Word of the Day card -- same pattern as
    // ConversationViewModel.playAudio: prefer the server-provided audioUrl,
    // otherwise synthesize via /tts, and stream it with MediaPlayer.
    fun playWord(text: String, existingUrl: String? = null) {
        if (text.isBlank() && existingUrl.isNullOrBlank()) return
        stopAudio()
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
                    setOnCompletionListener { try { release() } catch (e: Exception) {
                        Log.e("Dashboard", "Error releasing word player", e) }
                        mediaPlayer = null }
                    setOnErrorListener { mp, what, extra ->
                        Log.e("Dashboard", "Word audio error: what=$what, extra=$extra")
                        try { mp?.release() } catch (e: Exception) {
                            Log.e("Dashboard", "Error releasing word player", e) }
                        mediaPlayer = null
                        true
                    }
                }
                mediaPlayer = currentPlayer
            } catch (e: Exception) {
                Log.e("Dashboard", "Word audio playback error", e)
                try { currentPlayer?.release() } catch (re: Exception) {
                    Log.e("Dashboard", "Error releasing word player", re) }
                mediaPlayer = null
            }
        }
    }

    fun stopAudio() {
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
