package com.aiko.lingo.ui.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiko.lingo.data.model.ConversationRespondRequest
import com.aiko.lingo.data.model.ConversationResponse
import com.aiko.lingo.data.model.ConversationStartRequest
import com.aiko.lingo.data.remote.AikoApiService
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
            _uiState.value = ConversationUiState.Loading
            try {
                // Add user entry first
                _dialogue.value += DialogueEntry(text, "", isUser = true)
                val response = apiService.respondToConversation(ConversationRespondRequest(text))
                processResponse(response)
                _uiState.value = ConversationUiState.Active
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
                _dialogue.value += DialogueEntry(response.japaneseText, response.englishTranslation, isUser = false, isHint = true)
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
        _dialogue.value += DialogueEntry(response.japaneseText, response.englishTranslation, isUser = false)
        if (response.isFinished) {
            _uiState.value = ConversationUiState.Finished
        }
    }

    private suspend fun animateKaraoke(text: String) {
        _karaokeText.value = ""
        text.forEach { char ->
            _karaokeText.value += char
            delay(50) // Typewriter speed
        }
    }
}

data class DialogueEntry(
    val japanese: String,
    val english: String,
    val isUser: Boolean,
    val isHint: Boolean = false
)

sealed class ConversationUiState {
    object SelectingLevel : ConversationUiState()
    object Loading : ConversationUiState()
    object Active : ConversationUiState()
    object Finished : ConversationUiState()
    data class Error(val message: String) : ConversationUiState()
}
