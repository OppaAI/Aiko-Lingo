package com.aiko.lingo.ui.translate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiko.lingo.data.model.TranslateRequest
import com.aiko.lingo.data.model.TranslationResult
import com.aiko.lingo.data.remote.AikoApiService
import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
                _uiState.value = TranslateUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    private var isAudioLoading = MutableStateFlow(false)
    val audioLoading = isAudioLoading.asStateFlow()

    fun playAudio(text: String, existingUrl: String? = null) {
        if (text.isBlank() && existingUrl.isNullOrBlank()) return

        stopAudio()
        
        viewModelScope.launch {
            try {
                isAudioLoading.value = true
                val url = existingUrl ?: apiService.getTts(text).audioUrl
                
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(url)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    prepareAsync()
                    setOnPreparedListener { 
                        isAudioLoading.value = false
                        start() 
                    }
                    setOnCompletionListener { 
                        release()
                        mediaPlayer = null
                    }
                }
            } catch (e: Exception) {
                isAudioLoading.value = false
                // Log error
            }
        }
    }

    fun stopAudio() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            // Ignore stop errors
        } finally {
            mediaPlayer = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAudio()
    }
}

sealed class TranslateUiState {
    object Idle : TranslateUiState()
    object Loading : TranslateUiState() // Initial loading
    data class Success(
        val translations: List<TranslationResult>,
        val isRefreshing: Boolean = false
    ) : TranslateUiState()
    data class Error(val message: String) : TranslateUiState()
}
