package com.aiko.lingo.ui.practice

/*
Practice = type-what-you-hear session: 10 random learnt cards
(equal-or-lower JLPT, repeats across sessions allowed).
Each card shows its meaning + a play button; the user types the
Japanese (keyboard, handwriting/draw, or voice via system IME).
Matching (hiragana or kanji) advances to the next card.
*/

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiko.lingo.data.model.ReviewCard
import com.aiko.lingo.data.StudyTracker
import com.aiko.lingo.data.remote.AikoApiService
import com.aiko.lingo.data.remote.PracticeMarkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

class PracticeViewModel(private val apiService: AikoApiService) : ViewModel() {

    companion object {
        const val SESSION_SIZE = 10
    }

    private val _uiState = MutableStateFlow<PracticeUiState>(PracticeUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _session = MutableStateFlow<List<ReviewCard>>(emptyList())
    val session = _session.asStateFlow()

    private val _index = MutableStateFlow(0)
    val index = _index.asStateFlow()

    private val _score = MutableStateFlow(0)
    val score = _score.asStateFlow()

    private val _xpEarned = MutableStateFlow<Int?>(null)
    val xpEarned = _xpEarned.asStateFlow()

    private val _speaking = MutableStateFlow(false)
    val speaking = _speaking.asStateFlow()

    init {
        loadSession()
    }

    fun loadSession() {
        viewModelScope.launch {
            _uiState.value = PracticeUiState.Loading
            _score.value = 0
            _index.value = 0
            _xpEarned.value = null
            try {
                val cards = apiService.getPracticeSession(SESSION_SIZE)
                if (cards.isEmpty()) {
                    // total == 0 marks the "nothing to study yet" empty state.
                    _uiState.value = PracticeUiState.Finished(0, 0, 0)
                } else {
                    _session.value = cards
                    _uiState.value = PracticeUiState.Question
                }
            } catch (e: HttpException) {
                // Backend 400s when the pool is empty -- same empty state as
                // Review, not an error screen.
                if (e.code() == 400) {
                    _uiState.value = PracticeUiState.Finished(0, 0, 0)
                } else {
                    Log.e("Practice", "Failed to load session", e)
                    _uiState.value = PracticeUiState.Error(e.message() ?: "Failed to load cards")
                }
            } catch (e: Exception) {
                Log.e("Practice", "Failed to load session", e)
                _uiState.value = PracticeUiState.Error(e.message ?: "Failed to load cards")
            }
        }
    }

    fun currentCard(): ReviewCard? = _session.value.getOrNull(_index.value)

    /** Accepted answers: hiragana reading and/or kanji form. */
    fun acceptedAnswers(card: ReviewCard): List<String> {
        val out = mutableListOf(normalize(card.hiragana))
        card.kanji?.let {
            val n = normalize(it)
            if (n.isNotBlank() && n !in out) out.add(n)
        }
        return out.filter { it.isNotBlank() }
    }

    fun checkAnswer(card: ReviewCard, input: String): Boolean {
        return normalize(input) in acceptedAnswers(card)
    }

    fun answerCorrect() {
        _score.value += 1
    }

    fun next() {
        stopAudio()
        val nextIndex = _index.value + 1
        if (nextIndex < _session.value.size) {
            _index.value = nextIndex
        } else {
            finish()
        }
    }

    private fun finish() {
        StudyTracker.markStudied()
        viewModelScope.launch {
            val total = _session.value.size
            val correct = _score.value
            try {
                val res = apiService.markPractice(PracticeMarkRequest(correct, total))
                _xpEarned.value = res.xp
            } catch (e: Exception) {
                Log.e("Practice", "Failed to record practice", e)
            }
            _uiState.value = PracticeUiState.Finished(correct, total, _xpEarned.value ?: 0)
        }
    }

    private fun normalize(s: String): String {
        return s.trim().lowercase()
            .replace("\\s+".toRegex(), "")
            // Grammar patterns carry a leading 〜 which phone keyboards make
            // painful to type; accept the bare form too.
            .trimStart('〜', '～', '~', '-', '·')
    }

    // --- Pronunciation for the current card ---
    private var mediaPlayer: MediaPlayer? = null
    private var playToken = 0

    fun playCurrent() {
        val card = currentCard() ?: return
        val text = card.hiragana.ifBlank { card.kanji ?: "" }
        if (text.isBlank()) return
        stopAudio()
        _speaking.value = true
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
                                Log.e("Practice", "Error releasing stale player", e) }
                            return@setOnPreparedListener
                        }
                        try { player?.start() } catch (e: Exception) {
                            Log.e("Practice", "Failed to start audio", e)
                            if (token == playToken) _speaking.value = false
                        }
                    }
                    setOnCompletionListener { mp ->
                        try { mp?.release() } catch (e: Exception) {
                            Log.e("Practice", "Error releasing player", e) }
                        if (mediaPlayer === mp) {
                            mediaPlayer = null
                        }
                        if (token == playToken) _speaking.value = false
                    }
                    setOnErrorListener { mp, what, extra ->
                        Log.e("Practice", "Audio error: what=$what, extra=$extra")
                        try { mp?.release() } catch (e: Exception) {
                            Log.e("Practice", "Error releasing player", e) }
                        if (mediaPlayer === mp) {
                            mediaPlayer = null
                        }
                        if (token == playToken) _speaking.value = false
                        true
                    }
                }
                if (token != playToken) {
                    try { currentPlayer?.release() } catch (e: Exception) {
                        Log.e("Practice", "Error releasing superseded player", e) }
                    return@launch
                }
                mediaPlayer = currentPlayer
            } catch (e: Exception) {
                Log.e("Practice", "Audio playback error", e)
                try { currentPlayer?.release() } catch (re: Exception) {
                    Log.e("Practice", "Error releasing player", re) }
                mediaPlayer = null
                if (token == playToken) _speaking.value = false
            }
        }
    }

    fun stopAudio() {
        playToken++
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.e("Practice", "Error stopping audio", e)
        } finally {
            mediaPlayer = null
            _speaking.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAudio()
    }
}

sealed class PracticeUiState {
    object Loading : PracticeUiState()
    object Question : PracticeUiState()
    data class Finished(val correct: Int, val total: Int, val xp: Int) : PracticeUiState()
    data class Error(val message: String) : PracticeUiState()
}
