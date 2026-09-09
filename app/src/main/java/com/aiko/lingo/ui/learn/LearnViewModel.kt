package com.aiko.lingo.ui.learn

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiko.lingo.data.model.LessonDeck
import com.aiko.lingo.data.model.LessonDeckMeta
import com.aiko.lingo.data.remote.AikoApiService
import com.aiko.lingo.data.remote.LearnItemDto
import com.aiko.lingo.data.remote.LearnSessionResponse
import com.aiko.lingo.data.remote.LearnStatusResponse
import com.aiko.lingo.data.remote.MarkLearnedRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LearnViewModel(private val apiService: AikoApiService) : ViewModel() {

    companion object {
        val JLPT_ORDER = listOf("N5", "N4", "N3", "N2", "N1")
    }

    private val _decks = MutableStateFlow<List<LessonDeckMeta>>(emptyList())
    val decks = _decks.asStateFlow()

    private val _detail = MutableStateFlow<LessonDeck?>(null)
    val detail = _detail.asStateFlow()

    private val _listLoading = MutableStateFlow(true)
    val listLoading = _listLoading.asStateFlow()

    private val _detailLoading = MutableStateFlow(false)
    val detailLoading = _detailLoading.asStateFlow()

    private val _listError = MutableStateFlow<String?>(null)
    val listError = _listError.asStateFlow()

    private val _detailError = MutableStateFlow<String?>(null)
    val detailError = _detailError.asStateFlow()

    private var pendingDeckId: String? = null

    // --- JLPT level progression (N5 -> N1) ---
    private val _currentLevel = MutableStateFlow("N5")
    val currentLevel = _currentLevel.asStateFlow()

    private val _levels = MutableStateFlow(JLPT_ORDER)
    val levels = _levels.asStateFlow()

    private val _learnStatus = MutableStateFlow<LearnStatusResponse?>(null)
    val learnStatus = _learnStatus.asStateFlow()

    private val _levelLoading = MutableStateFlow(false)
    val levelLoading = _levelLoading.asStateFlow()

    private val _levelError = MutableStateFlow<String?>(null)
    val levelError = _levelError.asStateFlow()

    // --- Shared learn pool (learn/new + mark) ---
    private val _learnPool = MutableStateFlow<LearnSessionResponse?>(null)
    val learnPool = _learnPool.asStateFlow()

    private val _poolLoading = MutableStateFlow(false)
    val poolLoading = _poolLoading.asStateFlow()

    private val _poolError = MutableStateFlow<String?>(null)
    val poolError = _poolError.asStateFlow()

    private val _lastXpEarned = MutableStateFlow<Int?>(null)
    val lastXpEarned = _lastXpEarned.asStateFlow()

    init {
        loadDecks()
        refreshProgress()
    }

    /** Reload level + status + pool together (e.g. on entry / retry). */
    fun refreshProgress() {
        loadLevel()
        loadLearnStatus()
        loadLearnPool()
    }

    fun loadLevel() {
        viewModelScope.launch {
            _levelLoading.value = true
            _levelError.value = null
            try {
                val res = apiService.getLevel()
                if (res.level.isNotBlank()) _currentLevel.value = res.level
                if (res.levels.isNotEmpty()) _levels.value = res.levels.sortedBy { JLPT_ORDER.indexOf(it).takeIf { i -> i >= 0 } ?: 99 }
            } catch (e: Exception) {
                Log.e("Learn", "Failed to fetch level", e)
                _levelError.value = e.message ?: "Failed to load level"
            }
            _levelLoading.value = false
        }
    }

    fun loadLearnStatus() {
        viewModelScope.launch {
            try {
                val status = apiService.getLearnStatus()
                _learnStatus.value = status
                if (status.level.isNotBlank()) _currentLevel.value = status.level
                if (status.levels.isNotEmpty()) _levels.value = status.levels.sortedBy { JLPT_ORDER.indexOf(it).takeIf { i -> i >= 0 } ?: 99 }
            } catch (e: Exception) {
                Log.e("Learn", "Failed to fetch learn status", e)
                // Non-fatal: decks still work without status.
            }
        }
    }

    fun loadLearnPool() {
        viewModelScope.launch {
            _poolLoading.value = true
            _poolError.value = null
            try {
                _learnPool.value = apiService.getLearnNew()
            } catch (e: Exception) {
                Log.e("Learn", "Failed to fetch learn pool", e)
                _poolError.value = e.message ?: "Failed to load new vocab"
            }
            _poolLoading.value = false
        }
    }

    /** Switch JLPT track, e.g. N5 -> N4. Backend filters learn/new + courses. */
    fun setLevel(level: String) {
        if (!isLevelUnlocked(level)) {
            _levelError.value = "Complete $currentLevel to unlock $level 🔒"
            return
        }
        viewModelScope.launch {
            _levelLoading.value = true
            _levelError.value = null
            try {
                val res = apiService.setLevel(com.aiko.lingo.data.remote.SetLevelRequest(level))
                if (res.level.isNotBlank()) _currentLevel.value = res.level
                if (res.levels.isNotEmpty()) _levels.value = res.levels.sortedBy { JLPT_ORDER.indexOf(it).takeIf { i -> i >= 0 } ?: 99 }
                // Level changed -> pool + status are stale.
                loadLearnStatus()
                loadLearnPool()
            } catch (e: Exception) {
                Log.e("Learn", "Failed to set level", e)
                _levelError.value = e.message ?: "Failed to set level"
            }
            _levelLoading.value = false
        }
    }

    /** N5 is lowest/start. True when current pool fully learned. */
    fun isCurrentComplete(): Boolean {
        val pool = _learnPool.value
        if (pool != null) {
            return pool.items.isEmpty() && pool.pending_in_pool == 0
        }
        val status = _learnStatus.value ?: return false
        return status.pool_size_at_level == 0
    }

    /** Earned progression: can always go back, forward only one step when complete. */
    fun isLevelUnlocked(level: String): Boolean {
        val order = _levels.value.ifEmpty { JLPT_ORDER }
        val curIdx = order.indexOf(_currentLevel.value).takeIf { it >= 0 } ?: 0
        val idx = order.indexOf(level)
        if (idx < 0) return false
        if (idx <= curIdx) return true // review easier levels
        if (idx == curIdx + 1) return isCurrentComplete()
        return false // can't skip N5 -> N1 directly
    }

    fun nextLevel(): String? {
        val order = _levels.value.ifEmpty { JLPT_ORDER }
        val idx = order.indexOf(_currentLevel.value)
        return if (idx >= 0 && idx + 1 < order.size) order[idx + 1] else null
    }

    /** Mark pool items learned -> earns XP, advances progress toward next JLPT. */
    fun markLearned(items: List<LearnItemDto>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            _poolLoading.value = true
            try {
                val res = apiService.markLearned(MarkLearnedRequest(items))
                _lastXpEarned.value = res.xp
                // Refresh pool + status so progress bar / pending count advance.
                loadLearnStatus()
                loadLearnPool()
            } catch (e: Exception) {
                Log.e("Learn", "Failed to mark learned", e)
                _poolError.value = e.message ?: "Failed to save progress"
            }
            _poolLoading.value = false
        }
    }

    fun clearXpToast() {
        _lastXpEarned.value = null
    }

    fun loadDecks() {
        viewModelScope.launch {
            _listLoading.value = true
            _listError.value = null
            try {
                val decks = apiService.getLessons()
                _decks.value = decks
            } catch (e: Exception) {
                Log.e("Learn", "Failed to fetch lesson decks", e)
                _listError.value = e.message ?: "Failed to load lessons"
            }
            _listLoading.value = false
        }
    }

    fun openDeck(deckId: String) {
        pendingDeckId = deckId
        viewModelScope.launch {
            _detailLoading.value = true
            _detailError.value = null
            try {
                val deck = apiService.getLesson(deckId)
                _detail.value = deck
            } catch (e: Exception) {
                Log.e("Learn", "Failed to fetch lesson deck", e)
                _detailError.value = e.message ?: "Failed to load lesson"
            }
            _detailLoading.value = false
        }
    }

    fun retryDetail() {
        pendingDeckId?.let { openDeck(it) }
    }

    fun backToDecks() {
        _detail.value = null
        _detailError.value = null
        _detailLoading.value = false
        pendingDeckId = null
        if (_decks.value.isEmpty() && !_listLoading.value) {
            loadDecks()
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    // Generation counter: a tap while another card is still preparing must
    // not let the stale player start over the new one (the "mixed audio"
    // bug). Only the latest generation is allowed to start.
    private var playToken = 0

    // Speak the current flashcard (reading, falling back to the front text).
    fun playCard(text: String) {
        if (text.isBlank()) return
        stopAudio()
        val token = ++playToken
        viewModelScope.launch {
            var currentPlayer: MediaPlayer? = null
            try {
                val url = apiService.getTts(text).audioUrl
                currentPlayer = MediaPlayer().apply {
                    setDataSource(url)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    prepareAsync()
                    setOnPreparedListener { player ->
                        if (token != playToken) {
                            try { player?.release() } catch (e: Exception) {
                                Log.e("Learn", "Error releasing stale player", e) }
                            return@setOnPreparedListener
                        }
                        try { player?.start() } catch (e: Exception) {
                        Log.e("Learn", "Failed to start card audio", e) } }
                    setOnCompletionListener { try { release() } catch (e: Exception) {
                        Log.e("Learn", "Error releasing card player", e) }
                        mediaPlayer = null }
                    setOnErrorListener { mp, what, extra ->
                        Log.e("Learn", "Card audio error: what=$what, extra=$extra")
                        try { mp?.release() } catch (e: Exception) {
                            Log.e("Learn", "Error releasing card player", e) }
                        mediaPlayer = null
                        true
                    }
                }
                mediaPlayer = currentPlayer
            } catch (e: Exception) {
                Log.e("Learn", "Card audio playback error", e)
                try { currentPlayer?.release() } catch (re: Exception) {
                    Log.e("Learn", "Error releasing card player", re) }
                mediaPlayer = null
            }
        }
    }

    fun stopAudio() {
        playToken++
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.e("Learn", "Error stopping card audio", e)
        } finally {
            mediaPlayer = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAudio()
    }
}

sealed class LearnUiState {
    object Loading : LearnUiState()
    data class Decks(val decks: List<LessonDeckMeta>) : LearnUiState()
    data class Detail(val deck: LessonDeck) : LearnUiState()
    data class Error(val message: String) : LearnUiState()
}
