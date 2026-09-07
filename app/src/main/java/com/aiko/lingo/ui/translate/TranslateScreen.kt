package com.aiko.lingo.ui.translate

/*
=====================================================================
BUGFIX PASS (this version):
  1. The "Retry" button in the Error state had an empty onClick:
         Button(onClick = { }) { Text("Retry") }
     It didn't do anything. Fixed by remembering the last submitted
     query text and calling viewModel.translate(lastQuery) from Retry.

BUGFIX PASS (this version, cont.):
  2. TranslateViewModel already exposed `audioLoading: StateFlow<Boolean>`
     but TranslateScreen never collected it, so tapping the play button
     gave zero feedback while TTS was being generated (which can take a
     couple of seconds) -- it looked like the button just did nothing.
     TranslationCard now takes the ViewModel, collects audioLoading, and
     swaps the icon for a small spinner while a fetch is in flight,
     disabling the button so repeated taps can't pile up requests.

BUGFIX PASS (this version, cont. -- audit fix #3):
  3. Audio playback failures (notably TTS rate limiting, HTTP 429) were
     completely silent from the user's perspective. Wrapped the screen
     in a Box and added a Toast overlay driven by the ViewModel's new
     `audioError` StateFlow (reusing the Toast composable from the
     conversation package).
=====================================================================
*/

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiko.lingo.data.model.TranslationResult
import com.aiko.lingo.ui.conversation.Toast
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslateScreen(
    viewModel: TranslateViewModel,
    onBack: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    // FIX #1: remember the last text that was actually submitted, so
    // Retry can resend it even if the user has since edited the field.
    var lastQuery by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()
    // FIX #3: audio playback error (e.g. TTS rate limit) to surface as a toast.
    val audioError by viewModel.audioError.collectAsState()

    fun submit(text: String) {
        if (text.isBlank()) return
        lastQuery = text
        viewModel.translate(text)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← Back") }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Translate", style = MaterialTheme.typography.headlineMedium)
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding(),
                placeholder = { Text("Enter English to translate...") },
                // ✅ FIX: Use IconButton instead of Button for trailing icon
                trailingIcon = {
                    IconButton(
                        onClick = { submit(inputText) },
                        enabled = inputText.isNotBlank()
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Translate")
                    }
                },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            when (val state = uiState) {
                TranslateUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is TranslateUiState.Success -> {
                    Column {
                        if (state.isRefreshing) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                            )
                        }
                        // ✅ FIX: Add keys for proper recomposition tracking
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(
                                state.translations,
                                key = { translation -> "${translation.text}-${translation.register}" }
                            ) { translation ->
                                TranslationCard(
                                    translation = translation,
                                    viewModel = viewModel,
                                    onPlayAudio = { viewModel.playAudio(translation.text, translation.audioUrl) }
                                )
                            }
                        }
                    }
                }
                is TranslateUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Error: ${state.message}",
                                color = Color.Red
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            // FIX #1: Retry now re-submits the last query instead
                            // of being a no-op.
                            Button(
                                onClick = { submit(lastQuery) },
                                enabled = lastQuery.isNotBlank()
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
                TranslateUiState.Idle -> {
                    Text(
                        "Enter something to translate ♡",
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }

        // FIX #3: surface audio playback failures (e.g. TTS rate limit)
        // instead of a silently dead Play button.
        audioError?.let { message ->
            Toast(
                message = message,
                onDismiss = { viewModel.dismissAudioError() },
                isError = true,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )

            LaunchedEffect(message) {
                delay(3000)
                viewModel.dismissAudioError()
            }
        }
    }
}

@Composable
fun TranslationCard(
    translation: TranslationResult,
    viewModel: TranslateViewModel,
    onPlayAudio: () -> Unit
) {
    // FIX #2: this was previously ignored entirely -- the ViewModel tracked
    // loading state but nothing in the UI ever read it.
    val isAudioLoading by viewModel.audioLoading.collectAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = translation.register.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = translation.text,
                    style
