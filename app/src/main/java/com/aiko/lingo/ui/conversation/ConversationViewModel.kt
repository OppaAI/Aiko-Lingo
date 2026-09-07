package com.aiko.lingo.ui.conversation

/*
=====================================================================
BUGFIX PASS (this version):
  1. decodeFromString<StreamChunk>(line) was using the default
     top-level kotlinx.serialization `Json` object, which throws on
     unknown JSON keys. The backend's "final" chunk includes a "toast"
     key (and respond_stream also sends "vocabExtracted"), which are
     not fields on StreamChunk. That meant EVERY conversation turn hit
     the catch block and dropped the user into ConversationUiState.Error
     -- including the very first message when starting a session.
     Fixed by decoding with a local `Json { ignoreUnknownKeys = true }`
     instance instead of the default one.
  2. stop() reset UI state and cleared the dialogue, but never
     cancelled the coroutine Job running start()/respond()/handleStream().
     If the user hit stop mid-stream, that orphaned job could keep
     collecting stream lines in the background and mutate _uiState /
     _dialogue after the screen had already reset (e.g. reviving
     Active state or appending a stray dialogue entry). Now the active
     Job is tracked and explicitly cancelled in stop().

BUGFIX PASS (this version, cont.):
  3. On a network/stream failure the user's last message was lost --
     the only way to retry was to retype it from scratch. We now
     remember the last request that was in flight (level for start(),
     text for respond()) and expose retryLast() so the UI's "Retry"
     button can resend the exact same request.
  4. respond() left a stray user bubble + a permanently-stuck
     "Aiko is thinking..." state if the request threw before a stream
     ever started, since isProcessing was only reset in `finally` but
     _uiState was never walked back to Active. Now a failed respond()
     resets to Active (if there's prior dialogue) or SelectingLevel-safe
     Active state isn't assumed; we simply surface Error and let Retry
     replay the same text, matching the intent of fix #3.

BUGFIX PASS (this version, cont. -- audit fixes):
  5. StreamChunk now decodes the backend's "toast" field (it was
     previously deliberately dropped via ignoreUnknownKeys). It's
     surfaced through a new `toastMessage` StateFlow so
     ConversationScreen can actually display the toast the backend
     already generates (e.g. "Perfect! Let's keep talking.") instead of
     silently discarding it.
  6. playAudio() previously failed completely silently on error --
     including on a TTS rate-limit response (HTTP 429), which just
     looked like a dead Play button. It now surfaces a toast so the
     user knows what happened instead of tapping Play repeatedly.
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

class ConversationViewModel(private val apiService: AikoApiService) : ViewModel() {

    // FIX #1: lenient Json instance used for decoding stream chunks.
    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow<ConversationUiState>(ConversationUiState.SelectingLevel)
    val uiState = _uiState.asStateFlow()

    private val _dialogue = MutableStateFlow<List<DialogueEntry>>(emptyList())
    val dialogue = _dialogue.asStateFlow()

    private val _karaokeText = MutableStateFlow("")
    val karaokeText = _karaokeText.asStateFlow()

    // FIX #5: toast surfaced from the backend's "final" stream chunk (or from
    // a playAudio() failure). ConversationScreen collects this to render its
    // Toast composable instead of maintaining its own dead local state.
    private val _toastMessage = MutableStateFlow<Toast?>(null)
    val toastMessage = _toastMessage.asStateFlow()

    fun dismissToast() {
        _toastMessage.value = null
    }

    // ✅ FIX: Prevent race condition from duplicate respond() calls
    private val isProcessing = AtomicBoolean(false)

    // FIX #2: tracks the currently running start()/respond() job so stop()
    // can cancel it instead of letting an orphaned stream mutate state
    // after the screen has already reset.
    private var activeJob: Job? = null

    // FIX #3: remember the last in-flight request so a failed stream can be
    // retried without the user retyping. Only one of these is ever "live"
    // at a time -- retryLast() picks whichever was most recently attempted.
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
                val responseBody = apiService.startConversationStream(ConversationStartRequest(level))
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
                            // FIX #1: use the lenient `json` instance, not the
                            // default kotlinx.serialization Json.decodeFromString.
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
                                    // Return to Main thread to update state
                                    withContext(Dispatchers.Main) {
                                        _karaokeText.value = "" // Clear typewriter text before adding to dialogue list
                                        processResponse(finalResponse, shouldAnimate = false)
                                        // FIX #5: surface the backend's toast (e.g.
                                        // "Perfect! Let's keep talking.") instead of
                                        // dropping it on the floor.
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
                            // ✅ FIX: Notify user of parsing error instead of silently failing
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
        // ✅ FIX: Prevent race condition by blocking duplicate calls
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
                    // FIX #4: surface the error instead of leaving the UI stuck
                    // on ActiveLoading forever with no way forward but Stop.
                    _uiState.value = ConversationUiState.Error(e.message ?: "Failed to respond")
                }
            } finally {
                // ✅ FIX: Always reset processing flag
                isProcessing.set(false)
            }
        }
    }

    // FIX #3: re-send whichever of start()/respond() most recently failed.
    // No-op if nothing has been attempted yet (e.g. Error state reached some
    // other way).
    fun retryLast() {
        when (val action = lastAction) {
            is LastAction.Start -> start(action.level)
            is LastAction.Respond -> {
                // The failed attempt already appended a user bubble to
                // _dialogue before it broke; drop that duplicate before
                // resending so we don't end up with the same line twice.
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
        // FIX #5: previously intentionally omitted -- now decoded and wired
        // into `toastMessage` so the UI can actually show it.
        val toast: Toast? = null
        // Note: respond_stream also sends "vocabExtracted" on the final
        // chunk. It's intentionally not modeled here since the UI doesn't
        // use it yet; `json` is configured with ignoreUnknownKeys = true so
        // it's safely skipped rather than causing a parse failure.
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
                Log.e("Lingo", "Hint request failed", e)
                // Ignore hint errors for now
            }
        }
    }

    fun stop() {
        // FIX #2: cancel any in-flight start()/respond() stream job first,
        // so it can't mutate state after we've already reset the screen.
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
                    setOnCompletionListener { 
                        try {
                            release()
                        } catch (e: Exception) {
                            Log.e("Lingo", "Error releasing media player", e)
                        }
                        mediaPlayer = null
                    }
                    setOnErrorListener { mp, what, extra ->
                        Log.e("Lingo", "MediaPlayer error: what=$what, extra=$extra")
                        // FIX #6: give the user feedback on playback failure
                        // instead of leaving them wondering why nothing played.
                        _toastMessage.value = Toast(type = "error", message = "Audio playback failed.")
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
                // FIX #6: previously fully silent. Now distinguishes a TTS
                // rate-limit response (HTTP 429) from other failures so the
                // user understands why the Play button did nothing.
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
                mediaPlayer = null
            }
        }
    }

    private suspend fun animateKaraoke(text: String) {
        // ✅ FIX: Use StringBuilder instead of string concatenation (O(n) instead of O(n²))
        val sb = StringBuilder()
        _karaokeText.value = ""
        text.forEach { char ->
            sb.append(char)
            _karaokeText.value = sb.toString()
            delay(50) // Typewriter speed
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
