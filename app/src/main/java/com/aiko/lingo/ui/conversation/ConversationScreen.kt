package com.aiko.lingo.ui.conversation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ConversationScreen(
    viewModel: ConversationViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val dialogue by viewModel.dialogue.collectAsState()
    val karaokeText by viewModel.karaokeText.collectAsState()
    val listState = rememberLazyListState()

    // Auto scroll to bottom
    LaunchedEffect(dialogue.size, karaokeText) {
        if (dialogue.isNotEmpty()) {
            listState.animateScrollToItem(dialogue.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Conversation", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.weight(1f))
            if (uiState is ConversationUiState.Active) {
                IconButton(onClick = { viewModel.stop() }, colors = IconButtonDefaults.iconButtonColors(contentColor = Color.Red)) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (val state = uiState) {
            ConversationUiState.SelectingLevel -> LevelSelection { viewModel.start(it) }
            ConversationUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is ConversationUiState.Error -> Text("Error: ${state.message}", color = Color.Red)
            ConversationUiState.Active, ConversationUiState.ActiveLoading, ConversationUiState.Finished -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .imePadding()
                ) {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(dialogue) { entry ->
                            DialogueBubble(entry, onPlayAudio = { viewModel.playAudio(entry.audioUrl) })
                        }
                        
                        if (state is ConversationUiState.ActiveLoading) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.CenterStart) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }

                        if (karaokeText.isNotEmpty() && (dialogue.isEmpty() || !dialogue.last().isUser)) {
                            item {
                                KaraokeBubble(karaokeText)
                            }
                        }
                    }

                    if (state is ConversationUiState.Active || state is ConversationUiState.ActiveLoading) {
                        ResponseInput(
                            onSend = { viewModel.respond(it) },
                            onHint = { viewModel.getHint() },
                            enabled = state is ConversationUiState.Active
                        )
                    } else {
                        Button(onClick = { viewModel.stop() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Return to Menu")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LevelSelection(onLevelSelected: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Select Your Level", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(24.dp))
        LevelButton("Beginner", "beginner", onLevelSelected)
        LevelButton("Intermediate", "intermediate", onLevelSelected)
        LevelButton("Advanced", "advanced", onLevelSelected)
    }
}

@Composable
fun LevelButton(label: String, level: String, onClick: (String) -> Unit) {
    Button(
        onClick = { onClick(level) },
        modifier = Modifier.fillMaxWidth(0.8f).padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(label)
    }
}

@Composable
fun DialogueBubble(entry: DialogueEntry, onPlayAudio: () -> Unit) {
    val alignment = if (entry.isUser) Alignment.End else Alignment.Start
    val color = if (entry.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    val textColor = if (entry.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondary

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Surface(
            color = color,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.padding(12.dp).weight(1f, fill = false)) {
                    Text(entry.japanese, color = textColor, fontSize = 18.sp)
                    if (entry.english.isNotEmpty()) {
                        Text(entry.english, color = textColor.copy(alpha = 0.7f), fontSize = 14.sp)
                    }
                }
                if (!entry.isUser && entry.audioUrl != null) {
                    IconButton(onClick = onPlayAudio) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = textColor)
                    }
                }
            }
        }
    }
}

@Composable
fun KaraokeBubble(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        Text(text, modifier = Modifier.padding(12.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ResponseInput(onSend: (String) -> Unit, onHint: () -> Unit, enabled: Boolean) {
    var text by remember { mutableStateOf("") }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onHint, enabled = enabled) {
            Icon(Icons.Default.Lightbulb, contentDescription = "Hint", tint = if (enabled) Color.Yellow else Color.Gray)
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Reply to Aiko...") },
            shape = RoundedCornerShape(24.dp),
            enabled = enabled
        )
        IconButton(onClick = { if (text.isNotBlank()) { onSend(text); text = "" } }, enabled = enabled) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
        }
    }
}
