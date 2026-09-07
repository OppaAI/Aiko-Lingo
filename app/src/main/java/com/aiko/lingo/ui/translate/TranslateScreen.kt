package com.aiko.lingo.ui.translate

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslateScreen(
    viewModel: TranslateViewModel,
    onBack: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()

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
                    onClick = { viewModel.translate(inputText) },
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
                                translation,
                                onPlayAudio = { viewModel.playAudio(translation.text) }
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
                        Button(onClick = { }) {
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
}

@Composable
fun TranslationCard(translation: TranslationResult, onPlayAudio: () -> Unit) {
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
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                    fontWeight = FontWeight.Medium
                )
            }
            // ✅ NEW: Show audio URL availability
            IconButton(onClick = onPlayAudio) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Play TTS",
                    tint = if (translation.audioUrl != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Gray
                    }
                )
            }
        }
    }
}
