package com.aiko.lingo.ui.vocab

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiko.lingo.data.model.LessonDeck
import com.aiko.lingo.data.model.LessonDeckMeta
import com.aiko.lingo.data.remote.AikoApiService
import com.aiko.lingo.data.remote.CourseDetail
import com.aiko.lingo.data.remote.CourseMeta
import com.aiko.lingo.data.remote.LearnItemDto
import com.aiko.lingo.data.remote.LearnSessionResponse
import com.aiko.lingo.data.remote.LearnStatusResponse
import com.aiko.lingo.data.remote.LingoCache
import com.aiko.lingo.data.remote.MarkLearnedRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VocabViewModel(private val apiService: AikoApiService) : ViewModel() {

    companion object {
        val JLPT_ORDER = listOf("N5", "N4", "N3", "N2", "N1")
    }

    // --- Lesson decks (kana + words & phrases) ---
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

    // --- Courses (full JLPT course decks) ---
    private val _courses = MutableStateFlow<List<CourseMeta>>(emptyList())
    val courses = _courses.asStateFlow()

    private val _courseDetail = MutableStateFlow<CourseDetail?>(null)
    val courseDetail = _courseDetail.asStateFlow()

    private val _coursesLoading = MutableStateFlow(false)
    val coursesLoading = _coursesLoading.asStateFlow()

    private val _courseDetailLoading = MutableStateFlow(false)
    val courseDetailLoading = _courseDetailLoading.asStateFlow()

    private val _coursesError = MutableStateFlow<String?>(null)
    val coursesError = _coursesError.asStateFlow()

    private val _courseDetailError = MutableStateFlow<String?>(null)
    val courseDetailError = _courseDetailError.asStateFlow()

    private var pendingCourseId: String? = null

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
        // Cache-first: instant content on revisit, refresh in background.
        LingoCache.get<List<LessonDeckMeta>>("vocab_decks", 600_000)?.let { _decks.value = it }
        LingoCache.get<List<CourseMeta>>("vocab_courses", 600_000)?.let { _courses.value = it }
        LingoCache.get<String>("vocab_level", 60_000)?.let { _currentLevel.value = it }
        LingoCache.get<LearnStatusResponse>("vocab_status", 30_000)?.let {
            _learnStatus.value = it
            if (it.level.isNotBlank()) _currentLevel.value = it.level
        }
        LingoCache.get<LearnSessionResponse>("vocab_pool", 30_000)?.let { _learnPool.value = it }
        if (_decks.value.isNotEmpty() || _learnPool.value != null) {
            _listLoading.value = false
        }
        loadDecks()
        loadCourses()
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
                if (res.level.isNotBlank()) {
                    _currentLevel.value = res.level
                    LingoCache.put("vocab_level", res.level)
                }
                if (res.levels.isNotEmpty()) _levels.value = res.levels.sortedBy { JLPT_ORDER.indexOf(it).takeIf { i -> i >= 0 } ?: 99 }
            } catch (e: Exception) {
                Log.e("Vocab", "Failed to fetch level", e)
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
                LingoCache.put("vocab_status", status)
                if (status.level.isNotBlank()) _currentLevel.value = status.level
                if (status.levels.isNotEmpty()) _levels.value = status.levels.sortedBy { JLPT_ORDER.indexOf(it).takeIf { i -> i >= 0 } ?: 99 }
            } catch (e: Exception) {
                Log.e("Vocab", "Failed to fetch learn status", e)
            }
        }
    }

    fun loadLearnPool() {
        viewModelScope.launch {
            _poolLoading.value = true
            _poolError.value = null
            try {
                val pool = apiService.getLearnNew()
                _learnPool.value = pool
                LingoCache.put("vocab_pool", pool)
            } catch (e: Exception) {
                Log.e("Vocab", "Failed to fetch learn pool", e)
                if (_learnPool.value == null) {
                    _poolError.value = e.message ?: "Failed to load new vocab"
                }
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
                LingoCache.invalidate("vocab_pool", "vocab_status")
                loadLearnStatus()
                loadLearnPool()
            } catch (e: Exception) {
                Log.e("Vocab", "Failed to set level", e)
                _levelError.value = e.message ?: "Failed to set level"
            }
            _levelLoading.value = false
        }
    }

    /** N5 is lowest/start. True only with real progress + empty pool. */
    fun hasProgress(): Boolean {
        val s = _learnStatus.value ?: return false
        return s.user_cards > 0 || s.learned_today > 0 || s.reviews_today > 0
    }

    fun isCurrentComplete(): Boolean {
        if (!hasProgress()) return false
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
        if (idx <= curIdx) return true
        if (idx == curIdx + 1) return isCurrentComplete()
        return false
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
                LingoCache.invalidate("vocab_pool", "vocab_status")
                loadLearnStatus()
                loadLearnPool()
            } catch (e: Exception) {
                Log.e("Vocab", "Failed to mark learned", e)
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
            if (_decks.value.isEmpty()) _listLoading.value = true
            _listError.value = null
            try {
                val decks = apiService.getLessons()
                _decks.value = decks
                LingoCache.put("vocab_decks", decks)
            } catch (e: Exception) {
                Log.e("Vocab", "Failed to fetch lesson decks", e)
                if (_decks.value.isEmpty()) {
                    _listError.value = e.message ?: "Failed to load lessons"
                }
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
                Log.e("Vocab", "Failed to fetch lesson deck", e)
                _detailError.value = e.message ?: "Failed to load lesson"
            }
            _detailLoading.value = false
        }
    }

    fun retryDetail() {
        pendingDeckId?.let { openDeck(it) }
    }

    fun loadCourses() {
        viewModelScope.launch {
            _coursesError.value = null
            try {
                val decks = apiService.getCourses()
                _courses.value = decks
                LingoCache.put("vocab_courses", decks)
            } catch (e: Exception) {
                Log.e("Vocab", "Failed to fetch courses", e)
                if (_courses.value.isEmpty()) {
                    _coursesError.value = e.message ?: "Failed to load courses"
                }
            }
        }
    }

    fun openCourse(id: String) {
        pendingCourseId = id
        viewModelScope.launch {
            _courseDetailLoading.value = true
            _courseDetailError.value = null
            try {
                _courseDetail.value = apiService.getCourse(id)
            } catch (e: Exception) {
                Log.e("Vocab", "Failed to fetch course", e)
                _courseDetailError.value = e.message ?: "Failed to load course"
            }
            _courseDetailLoading.value = false
        }
    }

    fun retryCourseDetail() {
        pendingCourseId?.let { openCourse(it) }
    }

    fun backToList() {
        _detail.value = null
        _detailError.value = null
        _detailLoading.value = false
        pendingDeckId = null
        _courseDetail.value = null
        _courseDetailError.value = null
        _courseDetailLoading.value = false
        pendingCourseId = null
        if (_decks.value.isEmpty() && !_listLoading.value) {
            loadDecks()
        }
    }

    // --- Pronunciation (TTS + guarded MediaPlayer, stale-tap safe) ---
    private val _speakingKey = MutableStateFlow<String?>(null)
    val speakingKey = _speakingKey.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var playToken = 0

    fun playCard(text: String, key: String = text) {
        if (text.isBlank()) return
        stopAudio()
        _speakingKey.value = key
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
                                Log.e("Vocab", "Error releasing stale player", e) }
                            return@setOnPreparedListener
                        }
                        try { player?.start() } catch (e: Exception) {
                            Log.e("Vocab", "Failed to start card audio", e)
                            if (token == playToken) _speakingKey.value = null
                        }
                    }
                    setOnCompletionListener {
                        try { release() } catch (e: Exception) {
                            Log.e("Vocab", "Error releasing card player", e) }
                        mediaPlayer = null
                        if (token == playToken) _speakingKey.value = null
                    }
                    setOnErrorListener { mp, what, extra ->
                        Log.e("Vocab", "Card audio error: what=$what, extra=$extra")
                        try { mp?.release() } catch (e: Exception) {
                            Log.e("Vocab", "Error releasing card player", e) }
                        mediaPlayer = null
                        if (token == playToken) _speakingKey.value = null
                        true
                    }
                }
                mediaPlayer = currentPlayer
            } catch (e: Exception) {
                Log.e("Vocab", "Card audio playback error", e)
                try { currentPlayer?.release() } catch (re: Exception) {
                    Log.e("Vocab", "Error releasing card player", re) }
                mediaPlayer = null
                if (token == playToken) _speakingKey.value = null
            }
        }
    }

    fun stopAudio() {
        playToken++
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.e("Vocab", "Error stopping card audio", e)
        } finally {
            mediaPlayer = null
            _speakingKey.value = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAudio()
    }
}
