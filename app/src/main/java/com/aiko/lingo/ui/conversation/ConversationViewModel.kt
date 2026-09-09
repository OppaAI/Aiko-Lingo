package com.aiko.lingo.ui.conversation

/*
=====================================================================
BUGFIX PASS (this version):
  1. decodeFromString with ignoreUnknownKeys for stream chunks.
  2. activeJob cancelled in stop() so orphaned streams cannot revive UI.
  3. retryLast() resends last start()/respond() after Error.
  4. failed respond() surfaces Error instead of stuck ActiveLoading.
  5. StreamChunk.toast wired to toastMessage StateFlow.
  6. playAudio() surfaces TTS/rate-limit errors via toast.
  7. getHint() respects isProcessing so it cannot interleave with respond().
  8. MediaPlayer completion/error only clears mediaPlayer if it is still
     the same instance (avoids nulling a newer player after rapid replays).
=====================================================================
*/

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiko.lingo.data.model.ConversationRespondRequest
import com.aiko.lingo.data.model.ConversationResponse
import com.aiko.lingo.data.model.ConversationStartRequest
import com.aiko.lingo.data.model.DialogueHistoryEntry
import com.aiko.lingo.data.model.Toast
import com.aiko.lingo.data.remote.AikoApiService
import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import retrofit2.HttpException
import java.util.concurrent.atomic.AtomicBoolean

class ConversationViewModel(
    private val apiService: AikoApiService,
    private val streamingApi: AikoApiService = apiService,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow<ConversationUiState>(ConversationUiState.SelectingLevel)
    val uiState = _uiState.asStateFlow()

    private val _dialogue = MutableStateFlow<List<DialogueEntry>>(emptyList())
    val dialogue = _dialogue.asStateFlow()

    private val _karaokeText = MutableStateFlow("")
    val karaokeText = _karaokeText.asStateFlow()

    private val _toastMessage = MutableStateFlow<Toast?>(null)
    val toastMessage = _toastMessage.asStateFlow()

    fun dismissToast() {
        _toastMessage.value = null
    }

    private val isProcessing = AtomicBoolean(false)
    private var activeJob: Job? = null

    private sealed class LastAction {
        data class Start(val level: String) : LastAction()
        data class Respond(val text: String) : LastAction()
    }
    private var lastAction: LastAction? = null

    fun start(level: String) {
        lastAction = LastAction.Start(level)
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = ConversationUiState.Loading
            _karaokeText.value = ""
            try {
                val responseBody = streamingApi.startConversationStream(ConversationStartRequest(level))
                handleStream(responseBody)
            } catch (e: Exception) {
                Log.e("Lingo", "Start failed", e)
                _uiState.value = ConversationUiState.Error(e.message ?: "Failed to start")
            }
        }
    }

    private suspend fun handleStream(responseBody: ResponseBody) {
        withContext(Dispatchers.IO) {
            try {
                responseBody.byteStream().bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (line.isBlank()) return@forEach
                        try {
                            val chunk = json.decodeFromString<StreamChunk>(line)
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
                                    withContext(Dispatchers.Main) {
                                        _karaokeText.value = ""
                                        processResponse(finalResponse, shouldAnimate = false)
                                        chunk.toast?.let { _toastMessage.value = it }
                                    }
                                }
                                "error" -> {
                                    withContext(Dispatchers.Main) {
                                        _uiState.value = ConversationUiState.Error(chunk.message ?: "Stream error")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("Lingo", "Stream parse error: ${e.message} for line: $line", e)
                            withContext(Dispatchers.Main) {
                                _uiState.value = ConversationUiState.Error("Failed to parse response: ${e.message}")
                            }
                            return@useLines
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("Lingo", "Stream reading failed", e)
                withContext(Dispatchers.Main) {
                    _uiState.value = ConversationUiState.Error("Connection error: ${e.message}")
                }
            }
        }
    }

    fun respond(text: String) {
        if (!isProcessing.compareAndSet(false, true)) {
            Log.w("Lingo", "Already processing a response, ignoring duplicate call")
            return
        }

        lastAction = LastAction.Respond(text)
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            try {
                val currentDialogue = _dialogue.value
                val history = currentDialogue.map {
                    DialogueHistoryEntry(
                        speaker = if (it.isUser) "student" else "aiko",
                        text = it.japanese
                    )
                }

                _dialogue.value += DialogueEntry(text, "", isUser = true)
                _uiState.value = ConversationUiState.ActiveLoading
                _karaokeText.value = ""

                try {
                    val responseBody = streamingApi.respondToConversationStream(
                        ConversationRespondRequest(text = text, history = history)
                    )
                    handleStream(responseBody)
                } catch (e: Exception) {
                    _uiState.value = ConversationUiState.Error(e.message ?: "Failed to respond")
                }
            } finally {
                isProcessing.set(false)
            }
        }
    }

    fun retryLast() {
        when (val action = lastAction) {
            is LastAction.Start -> start(action.level)
            is LastAction.Respond -> {
                if (_dialogue.value.lastOrNull()?.let { it.isUser && it.japanese == action.text } == true) {
                    _dialogue.value = _dialogue.value.dropLast(1)
                }
                respond(action.text)
            }
            null -> Log.w("Lingo", "retryLast() called with no prior action")
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
        val message: String? = null,
        val toast: Toast? = null
    )

    fun getHint() {
        // FIX #7: do not interleave hint dialogue while respond() is mid-stream.
        if (isProcessing.get()) {
            Log.w("Lingo", "Hint ignored — respond() still in progress")
            return
        }
        if (!isProcessing.compareAndSet(false, true)) {
            return
        }
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
                Log.e("Lingo", "Hint request failed", e)
            } finally {
                isProcessing.set(false)
            }
        }
    }

    fun stop() {
        activeJob?.cancel()
        activeJob = null
        lastAction = null
        viewModelScope.launch {
            try {
                apiService.stopConversation()
            } finally {
                _uiState.value = ConversationUiState.SelectingLevel
                _dialogue.value = emptyList()
                isProcessing.set(false)
            }
        }
    }

    private suspend fun processResponse(
        response: ConversationResponse,
        shouldAnimate: Boolean = true,
        isHint: Boolean = false,
    ) {
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
            var currentPlayer: MediaPlayer? = null
            try {
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
                            start()
                        } catch (e: Exception) {
                            Log.e("Lingo", "Failed to start audio playback", e)
                        }
                    }
                    // FIX #8: only clear the field if this instance is still current.
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
                        _toastMessage.value = Toast(type = "error", message = "Audio playback failed.")
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
                _toastMessage.value = if (e is HttpException && e.code() == 429) {
                    Toast(type = "error", message = "Audio rate limit reached. Please wait a moment.")
                } else {
                    Toast(type = "error", message = "Couldn't play audio right now.")
                }
                try {
                    currentPlayer?.release()
                } catch (releaseError: Exception) {
                    Log.e("Lingo", "Error releasing media player after exception", releaseError)
                }
                if (mediaPlayer === currentPlayer) {
                    mediaPlayer = null
                }
            }
        }
    }

    private suspend fun animateKaraoke(text: String) {
        val sb = StringBuilder()
        _karaokeText.value = ""
        text.forEach { char ->
            sb.append(char)
            _karaokeText.value = sb.toString()
            delay(50)
        }
        delay(300)
        _karaokeText.value = ""
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

    override fun onCleared() {
        super.onCleared()
        activeJob?.cancel()
        stopAudio()
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
    object ActiveLoading : ConversationUiState()
    object Finished : ConversationUiState()
    data class Error(val message: String) : ConversationUiState()
}
