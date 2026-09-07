package com.aiko.lingo.ui.translate

/*
=====================================================================
BUGFIX PASS (this version -- audit fix #3):
  1. playAudio() previously failed completely silently -- including
     on a TTS rate-limit response (HTTP 429), which just looked like
     the Play button did nothing. Added an `audioError` StateFlow so
     TranslateScreen can show a toast explaining what happened
     (rate-limited vs. a generic playback failure), instead of the
     user tapping Play repeatedly with no feedback.
=====================================================================
*/

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiko.lingo.data.model.TranslateRequest
import com.aiko.lingo.data.model.TranslationResult
import com.aiko.lingo.data.remote.AikoApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

class TranslateViewModel(private val apiService: AikoApiService) : ViewModel() {

    private val _uiState = MutableStateFlow<TranslateUiState>(TranslateUiState.Idle)
    val uiState = _uiState.asStateFlow()

    fun translate(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is TranslateUiState.Success) {
                _uiState.value = currentState.copy(isRefreshing = true)
            } else {
                _uiState.value = TranslateUiState.Loading
            }
            
            stopAudio() // Stop any current playback when a new translation starts
            try {
                val response = apiService.translate(TranslateRequest(text))
                _uiState.value = TranslateUiState.Success(response.translations)
            } catch (e: Exception) {
                Log.e("Lingo", "Translation error", e)
                _uiState.value = TranslateUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    private var isAudioLoading = MutableStateFlow(false)
    val audioLoading = isAudioLoading.asStateFlow()

    // FIX: surfaces playAudio() failures (notably TTS rate limiting) so the
    // UI can show the user something instead of a dead Play button.
    private val _audioError = MutableStateFlow<String?>(null)
    val audioError = _audioError.asStateFlow()

    fun dismissAudioError() {
        _audioError.value = null
    }

    fun playAudio(text: String, existingUrl: String? = null) {
        if (text.isBlank() && existingUrl.isNullOrBlank()) return

        stopAudio()
        
        viewModelScope.launch {
            var currentPlayer: MediaPlayer? = null
            try {
                isAudioLoading.value = true
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
                    setOnPreparedListener { 
                        try {
                            isAudioLoading.value = false
                            start()
                        } catch (e: Exception) {
                            Log.e("Lingo", "Failed to start audio playback", e)
                            isAudioLoading.value = false
                        }
                    }
                    setOnCompletionListener { 
                        try {
                            release()
                        } catch (e: Exception) {
                            Log.e("Lingo", "Error releasing media player", e)
                        }
                        mediaPlayer = null
                    }
                    // ✅ FIX: Add error listener to handle playback errors
                    setOnErrorListener { mp, what, extra ->
                        Log.e("Lingo", "MediaPlayer error: what=$what, extra=$extra")
                        isAudioLoading.value = false
                        _audioError.value = "Audio playback failed."
                        try {
                            mp?.release()
                        } catch (e: Exception) {
                            Log.e("Lingo", "Error releasing media player on error", e)
                        }
                        mediaPlayer = null
                        true
                    }
                }
                mediaPlayer = currentPlayer
            } catch (e: Exception) {
                Log.e("Lingo", "Audio playback error", e)
                isAudioLoading.value = false
                // FIX: distinguish a TTS rate-limit response (HTTP 429) from
                // other failures so the user knows why nothing played.
                _audioError.value = if (e is HttpException && e.code() == 429) {
                    "Audio rate limit reached. Please wait a moment."
                } else {
                    "Couldn't play audio right now."
                }
                // ✅ FIX: Properly cleanup on error
                try {
                    currentPlayer?.release()
                } catch (releaseError:
