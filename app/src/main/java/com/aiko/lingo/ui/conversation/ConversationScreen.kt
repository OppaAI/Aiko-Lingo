package com.aiko.lingo.ui.conversation

/*
=====================================================================
CUTE UI OVERHAUL (this version):
  1. Chat bubbles with extra rounded corners (32dp).
  2. Pastel color coding for speakers (Pink for Aiko, Blue for Student).
  3. Redesigned level selection with large, friendly buttons.
  4. Glowing typing indicators and playful icons.
=====================================================================
*/

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiko.lingo.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    viewModel: ConversationViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val dialogue by viewModel.dialogue.collectAsState()
    val karaokeText by viewModel.karaokeText.collectAsState()
    val toast by viewModel.toastMessage.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(dialogue.size) {
        if (dialogue.isNotEmpty()) {
            listState.animateScrollToItem(dialogue.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Chat with Aiko", fontWeight = FontWeight.ExtraBold, color = ShoujoText)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("🌸", fontSize = 24.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ShoujoText)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.stop() }) {
                        Icon(Icons.Default.StopCircle, contentDescription = "End Chat", tint = Color(0xFFEF5350), modifier = Modifier.size(28.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(Color(0xFFFAFAFA))) {
            Column(modifier = Modifier.fillMaxSize()) {
                when (val state = uiState) {
                    ConversationUiState.SelectingLevel -> {
                        StartConversationView(onStart = { viewModel.start(it) })
                    }
                    ConversationUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ShoujoAccent)
                        }
                    }
                    is ConversationUiState.Error -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("Aiko is taking a nap... 😴", style = MaterialTheme.typography.titleLarge)
                            Text(state.message, color = Color.Gray, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { viewModel.retryLast() },
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PastelBlueDark)
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                    else -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                contentPadding = PaddingValues(vertical = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(dialogue) { entry ->
                                    ChatBubble(entry)
                                }
                                
                                if (state is ConversationUiState.ActiveLoading) {
                                    item {
                                        AikoTypingIndicator()
                                    }
                                }
                            }

                            if (karaokeText.isNotEmpty()) {
                                KaraokeView(karaokeText)
                            }

                            MessageInput(
                                viewModel = viewModel,
                                isEnabled = state is ConversationUiState.Active || state is ConversationUiState.Finished
                            )
                        }
                    }
                }
            }

            toast?.let { t ->
                Toast(
                    message = t.message,
                    onDismiss = { viewModel.dismissToast() },
                    isError = t.type == "error",
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 100.dp, start = 16.dp, end = 16.dp)
                )
                LaunchedEffect(t) {
                    delay(4000)
                    viewModel.dismissToast()
                }
            }
        }
    }
}

@Composable
fun StartConversationView(onStart: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Ready to practice? 🌸", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = ShoujoText)
        Text("Choose a level to start chatting!", modifier = Modifier.padding(bottom = 40.dp), color = Color.Gray, fontSize = 18.sp)
        
        LevelButton("Beginner", "Easy words and basic sentences", PastelGreen, onClick = { onStart("beginner") })
        LevelButton("Intermediate", "Natural phrases and daily topics", PastelBlue, onClick = { onStart("intermediate") })
        LevelButton("Advanced", "Complex topics and honorifics", ShoujoPink, onClick = { onStart("advanced") })
    }
}

@Composable
fun LevelButton(title: String, desc: String, color: Color, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).height(90.dp).shadow(2.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = ShoujoText)
            Text(desc, fontSize = 14.sp, color = ShoujoText.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun ChatBubble(entry: DialogueEntry) {
    val isAiko = !entry.isUser
    val bubbleColor = if (isAiko) ShoujoSoftPink else ShoujoPink
    val alignment = if (isAiko) Alignment.Start else Alignment.End
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(
                topStart = 32.dp,
                topEnd = 32.dp,
                bottomStart = if (isAiko) 8.dp else 32.dp,
                bottomEnd = if (isAiko) 32.dp else 8.dp
            ),
            modifier = Modifier.widthIn(max = 320.dp).shadow(2.dp, RoundedCornerShape(32.dp)),
            border = if (isAiko) BorderStroke(1.dp, ShoujoPink) else null
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = entry.japanese,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = ShoujoText
                )
                if (entry.english.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = ShoujoText.copy(alpha = 0.1f))
                    Text(
                        text = entry.english,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        fontStyle = FontStyle.Italic
                    )
                }
            }
        }
    }
}

@Composable
fun MessageInput(viewModel: ConversationViewModel, isEnabled: Boolean) {
    var text by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 16.dp,
        color = Color.White,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp).safeDrawingPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.getHint() },
                enabled = isEnabled,
                modifier = Modifier.size(52.dp).background(PastelYellow, RoundedCornerShape(16.dp))
            ) {
                Icon(Icons.Default.Lightbulb, contentDescription = "Hint", tint = PastelYellowDark)
            }

            Spacer(modifier = Modifier.width(8.dp))

            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f).shadow(1.dp, RoundedCornerShape(24.dp)),
                placeholder = { Text("Reply in Japanese...", color = Color.LightGray) },
                enabled = isEnabled,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFF5F5F5),
                    unfocusedContainerColor = Color(0xFFF5F5F5),
                    disabledContainerColor = Color(0xFFEEEEEE),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            IconButton(
                onClick = { 
                    viewModel.respond(text)
                    text = ""
                },
                enabled = isEnabled && text.isNotBlank(),
                modifier = Modifier.size(52.dp).background(
                    if (isEnabled && text.isNotBlank()) ShoujoPink else Color.LightGray,
                    RoundedCornerShape(16.dp)
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
            }

        }
    }
}

@Composable
fun AikoTypingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.padding(start = 8.dp).shadow(1.dp, RoundedCornerShape(24.dp))
        ) {
            Text("Aiko is thinking... 🌸", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = ShoujoPink)
        }
    }
}

@Composable
fun KaraokeView(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        color = ShoujoText.copy(alpha = 0.9f),
        shape = RoundedCornerShape(24.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            modifier = Modifier.padding(20.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
fun Toast(
    message: String,
    onDismiss: () -> Unit,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(0.9f).shadow(8.dp, RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        color = if (isError) Color(0xFFFFEBEE) else Color(0xFFE8F5E9),
        border = androidx.compose.foundation.BorderStroke(2.dp, if (isError) Color(0xFFEF5350) else PastelGreenDark)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isError) Icons.Default.Info else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (isError) Color(0xFFEF5350) else PastelGreenDark,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.ExtraBold,
                color = ShoujoText
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(20.dp), tint = Color.Gray)
            }
        }
    }
}
