package com.aiko.lingo.ui.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiko.lingo.data.model.ConversationRespondRequest
import com.aiko.lingo.data.model.ConversationResponse
import com.aiko.lingo.data.model.ConversationStartRequest
import com.aiko.lingo.data.model.DialogueHistoryEntry
import com.aiko.lingo.data.remote.AikoApiService
import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ConversationViewModel(private val apiService: AikoApiService) : ViewModel() {

    private val _uiState = MutableStateFlow<ConversationUiState>(ConversationUiState.SelectingLevel)
    val uiState = _uiState.asStateFlow()

    private val _dialogue = MutableStateFlow<List<DialogueEntry>>(emptyList())
    val dialogue = _dialogue.asStateFlow()

    private val _karaokeText = MutableStateFlow("")
    val karaokeText = _karaokeText.asStateFlow()

    fun start(level: String) {
        viewModelScope.launch {
            _uiState.value = ConversationUiState.Loading
            try {
                val response = apiService.startConversation(ConversationStartRequest(level))
                processResponse(response)
                _uiState.value = ConversationUiState.Active
            } catch (e: Exception) {
                _uiState.value = ConversationUiState.Error(e.message ?: "Failed to start")
            }
        }
    }

    fun respond(text: String) {
        viewModelScope.launch {
            val currentDialogue = _dialogue.value
            val history = currentDialogue.map { 
                DialogueHistoryEntry(
                    speaker = if (it.isUser) "student" else "aiko",
                    text = it.japanese
                )
            }

            // Add user entry first
            _dialogue.value += DialogueEntry(text, "", isUser = true)
            
            // Set to loading but keep the conversation on screen
            _uiState.value = ConversationUiState.ActiveLoading 
            
            try {
                val response = apiService.respondToConversation(
                    ConversationRespondRequest(text = text, history = history)
                )
                processResponse(response)
            } catch (e: Exception) {
                _uiState.value = ConversationUiState.Error(e.message ?: "Failed to respond")
            }
        }
    }

    fun getHint() {
        viewModelScope.launch {
            try {
                val response = apiService.getHint()
                // Process as Aiko response but maybe flag it as hint
                animateKaraoke(response.japaneseText)
                _dialogue.value += DialogueEntry(
                    japanese = response.japaneseText,
                    english = response.englishTranslation,
                    isUser = false,
                    isHint = true,
                    audioUrl = response.audioUrl
                )
                playAudio(response.audioUrl)
            } catch (e: Exception) {
                // Ignore hint errors for now
            }
        }
    }

    fun stop() {
        viewModelScope.launch {
            try {
                apiService.stopConversation()
            } finally {
                _uiState.value = ConversationUiState.SelectingLevel
                _dialogue.value = emptyList()
            }
        }
    }

    private suspend fun processResponse(response: ConversationResponse) {
        animateKaraoke(response.japaneseText)
        _dialogue.value += DialogueEntry(
            japanese = response.japaneseText,
            english = response.englishTranslation,
            isUser = false,
            audioUrl = response.audioUrl,
            isCorrect = response.isCorrect,
            feedback = response.feedback,
            suggestion = response.suggestion
        )
        if (response.isFinished) {
            _uiState.value = ConversationUiState.Finished
        } else {
            _uiState.value = ConversationUiState.Active
        }
        playAudio(response.audioUrl)
    }

    private var mediaPlayer: MediaPlayer? = null

    fun playAudio(url: String?) {
        if (url.isNullOrBlank()) return

        viewModelScope.launch {
            try {
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(url)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    prepareAsync()
                    setOnPreparedListener { start() }
                }
            } catch (e: Exception) {
                // Log error
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private suspend fun animateKaraoke(text: String) {
        _karaokeText.value = ""
        text.forEach { char ->
            _karaokeText.value += char
            delay(50) // Typewriter speed
        }
        delay(300) // Brief pause after finishing
        _karaokeText.value = "" // Clear after done to avoid double bubbles
    }
}

data class DialogueEntry(
    val japanese: String,
    val english: String,
    val isUser: Boolean,
    val isHint: Boolean = false,
    val audioUrl: String? = null,
    val isCorrect: Boolean = true,
    val feedback: String? = null,
    val suggestion: String? = null
)

sealed class ConversationUiState {
    object SelectingLevel : ConversationUiState()
    object Loading : ConversationUiState()
    object Active : ConversationUiState()
    object ActiveLoading : ConversationUiState() // Added for non-blocking loading
    object Finished : ConversationUiState()
    data class Error(val message: String) : ConversationUiState()
}
