package com.aiko.lingo.ui.conversation

import android.util.Log
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody

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
            _karaokeText.value = ""
            try {
                val responseBody = apiService.startConversationStream(ConversationStartRequest(level))
                handleStream(responseBody)
            } catch (e: Exception) {
                _uiState.value = ConversationUiState.Error(e.message ?: "Failed to start")
            }
        }
    }

    private suspend fun handleStream(responseBody: ResponseBody) {
        responseBody.byteStream().bufferedReader().useLines { lines ->
            lines.forEach { line ->
                if (line.isBlank()) return@forEach
                try {
                    val chunk = Json.decodeFromString<StreamChunk>(line)
                    when (chunk.type) {
                        "delta" -> {
                            _karaokeText.value += chunk.text ?: ""
                        }
                        "final" -> {
                            val finalResponse = ConversationResponse(
                                japaneseText = chunk.japanese ?: "",
                                englishTranslation = chunk.english ?: "",
                                audioUrl = chunk.audioUrl,
                                isFinished = chunk.isFinished ?: false,
                                isCorrect = chunk.isCorrect ?: true,
                                feedback = chunk.feedback,
                                suggestion = chunk.suggestion
                            )
                            processResponse(finalResponse, shouldAnimate = false)
                        }
                        "error" -> {
                            _uiState.value = ConversationUiState.Error(chunk.message ?: "Stream error")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Lingo", "Stream parse error: ${e.message} for line: $line")
                }
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
            
            // Set to loading
            _uiState.value = ConversationUiState.ActiveLoading 
            _karaokeText.value = "" 
            
            try {
                val responseBody = apiService.respondToConversationStream(
                    ConversationRespondRequest(text = text, history = history)
                )
                handleStream(responseBody)
            } catch (e: Exception) {
                _uiState.value = ConversationUiState.Error(e.message ?: "Failed to respond")
            }
        }
    }

    @Serializable
    data class StreamChunk(
        val type: String,
        val text: String? = null,
        val japanese: String? = null,
        val english: String? = null,
        val isCorrect: Boolean? = null,
        val feedback: String? = null,
        val suggestion: String? = null,
        val isFinished: Boolean? = null,
        val audioUrl: String? = null,
        val message: String? = null
    )

    fun getHint() {
        viewModelScope.launch {
            try {
                val response = apiService.getHint()
                val mappedResponse = ConversationResponse(
                    japaneseText = response.japaneseText,
                    englishTranslation = response.englishTranslation,
                    audioUrl = response.audioUrl,
                    isCorrect = true
                )
                processResponse(mappedResponse, shouldAnimate = true, isHint = true)
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

    private suspend fun processResponse(response: ConversationResponse, shouldAnimate: Boolean = true, isHint: Boolean = false) {
        if (shouldAnimate) {
            animateKaraoke(response.japaneseText)
        }
        
        _dialogue.value += DialogueEntry(
            japanese = response.japaneseText,
            english = response.englishTranslation,
            isUser = false,
            isHint = isHint,
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
        playAudio(response.japaneseText, response.audioUrl)
    }

    private var mediaPlayer: MediaPlayer? = null

    fun playAudio(text: String, existingUrl: String? = null) {
        if (text.isBlank() && existingUrl.isNullOrBlank()) return

        stopAudio()
        
        viewModelScope.launch {
            try {
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
                    setOnPreparedListener { start() }
                    setOnCompletionListener { 
                        release()
                        mediaPlayer = null
                    }
                }
            } catch (e: Exception) {
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
