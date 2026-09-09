package com.aiko.lingo.ui.translate

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiko.lingo.data.model.TranslateRequest
import com.aiko.lingo.data.model.TranslationResult
import com.aiko.lingo.data.remote.AikoApiService
import com.aiko.lingo.data.remote.LingoCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

class TranslateViewModel(private val apiService: AikoApiService) : ViewModel() {

    private val _uiState = MutableStateFlow<TranslateUiState>(TranslateUiState.Idle)
    val uiState = _uiState.asStateFlow()

    fun translate(text: String) {
        if (text.isBlank()) return
        val key = text.trim()

        viewModelScope.launch {
            LingoCache.get<List<TranslationResult>>("tr_$key", 600_000)?.let {
                _uiState.value = TranslateUiState.Success(it)
                return@launch
            }
            val currentState = _uiState.value
            if (currentState is TranslateUiState.Success) {
                _uiState.value = currentState.copy(isRefreshing = true)
            } else {
                _uiState.value = TranslateUiState.Loading
            }

            stopAudio()
            try {
                val response = apiService.translate(TranslateRequest(key))
                LingoCache.put("tr_$key", response.translations)
                _uiState.value = TranslateUiState.Success(response.translations)
            } catch (e: Exception) {
                Log.e("Lingo", "Translation error", e)
                _uiState.value = TranslateUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    private val isAudioLoading = MutableStateFlow<String?>(null)
    val audioLoading = isAudioLoading.asStateFlow()

    private val _audioError = MutableStateFlow<String?>(null)
    val audioError = _audioError.asStateFlow()

    fun dismissAudioError() {
        _audioError.value = null
    }

    fun stopAudio() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.e("Lingo", "Error stopping audio", e)
        } finally {
            mediaPlayer = null
        }
    }

    fun playAudio(text: String, existingUrl: String? = null, key: String = text) {
        if (text.isBlank() && existingUrl.isNullOrBlank()) return

        stopAudio()

        viewModelScope.launch {
            var currentPlayer: MediaPlayer? = null
            try {
                isAudioLoading.value = key
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
                            isAudioLoading.value = null
                            start()
                        } catch (e: Exception) {
                            Log.e("Lingo", "Failed to start audio playback", e)
                            isAudioLoading.value = null
                        }
                    }
                    // Only clear mediaPlayer if this callback belongs to the current instance.
                    setOnCompletionListener { mp ->
                        try {
                            mp.release()
                        } catch (e: Exception) {
                            Log.e("Lingo", "Error releasing media player", e)
                        }
                        if (mediaPlayer === mp) {
                            mediaPlayer = null
                        }
                    }
                    setOnErrorListener { mp, what, extra ->
                        Log.e("Lingo", "MediaPlayer error: what=$what, extra=$extra")
                        isAudioLoading.value = null
                        _audioError.value = "Audio playback failed."
                        try {
                            mp?.release()
                        } catch (e: Exception) {
                            Log.e("Lingo", "Error releasing media player on error", e)
                        }
                        if (mediaPlayer === mp) {
                            mediaPlayer = null
                        }
                        true
                    }
                }
                mediaPlayer = currentPlayer
            } catch (e: Exception) {
                Log.e("Lingo", "Audio playback error", e)
                isAudioLoading.value = null
                _audioError.value = if (e is HttpException && e.code() == 429) {
                    "Audio rate limit reached. Please wait a moment."
                } else {
                    "Couldn't play audio right now."
                }
                try {
                    currentPlayer?.release()
                } catch (releaseError: Exception) {
                    Log.e("Lingo", "Error releasing media player on catch", releaseError)
                }
                if (mediaPlayer === currentPlayer) {
                    mediaPlayer = null
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAudio()
    }
}

sealed class TranslateUiState {
    object Idle : TranslateUiState()
    object Loading : TranslateUiState()
    data class Success(
        val translations: List<TranslationResult>,
        val isRefreshing: Boolean = false
    ) : TranslateUiState()
    data class Error(val message: String) : TranslateUiState()
}
