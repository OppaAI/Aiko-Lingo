package com.aiko.lingo.ui.learn

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiko.lingo.data.model.LessonDeck
import com.aiko.lingo.data.model.LessonDeckMeta
import com.aiko.lingo.data.remote.AikoApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LearnViewModel(private val apiService: AikoApiService) : ViewModel() {

    private val _uiState = MutableStateFlow<LearnUiState>(LearnUiState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        loadDecks()
    }

    fun loadDecks() {
        viewModelScope.launch {
            _uiState.value = LearnUiState.Loading
            try {
                val decks = apiService.getLessons()
                _uiState.value = LearnUiState.Decks(decks)
            } catch (e: Exception) {
                Log.e("Learn", "Failed to fetch lesson decks", e)
                _uiState.value = LearnUiState.Error(e.message ?: "Failed to load lessons")
            }
        }
    }

    fun openDeck(deckId: String) {
        viewModelScope.launch {
            _uiState.value = LearnUiState.Loading
            try {
                val deck = apiService.getLesson(deckId)
                _uiState.value = LearnUiState.Detail(deck)
            } catch (e: Exception) {
                Log.e("Learn", "Failed to fetch lesson deck", e)
                _uiState.value = LearnUiState.Error(e.message ?: "Failed to load lesson")
            }
        }
    }

    fun backToDecks() {
        loadDecks()
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
