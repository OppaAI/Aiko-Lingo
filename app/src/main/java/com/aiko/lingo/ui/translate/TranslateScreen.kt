package com.aiko.lingo.ui.translate

/*
=====================================================================
CUTE UI OVERHAUL (this version):
  1. Pastel input field with super-rounded corners (28dp).
  2. Large, shadow-heavy cards (32dp) for each translation.
  3. Play buttons as prominent pastel-colored circular icons.
  4. Explicit "Translate" branding with a sparkly header.
=====================================================================
*/

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiko.lingo.data.model.TranslationResult
import com.aiko.lingo.ui.conversation.Toast
import com.aiko.lingo.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslateScreen(
    viewModel: TranslateViewModel,
    onBack: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var lastQuery by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()
    val audioError by viewModel.audioError.collectAsState()

    fun submit(text: String) {
        if (text.isBlank()) return
        lastQuery = text
        viewModel.translate(text)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quick Translate ✨", fontWeight = FontWeight.ExtraBold, color = ShoujoText) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ShoujoText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .shadow(2.dp, RoundedCornerShape(28.dp)),
                    placeholder = { Text("What do you want to say?", color = Color.Gray) },
                    shape = RoundedCornerShape(28.dp),
                    trailingIcon = {
                        IconButton(
                            onClick = { submit(inputText) },
                            enabled = inputText.isNotBlank(),
                            modifier = Modifier
                                .padding(8.dp)
                                .background(
                                    if (inputText.isNotBlank()) PastelOrangeDark else Color.LightGray,
                                    RoundedCornerShape(16.dp)
                                )
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Translate", tint = Color.White)
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PastelOrangeDark,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(32.dp))

                when (val state = uiState) {
                    TranslateUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = PastelOrangeDark)
                        }
                    }
                    is TranslateUiState.Success -> {
                        Column {
                            if (state.isRefreshing) {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    color = PastelOrangeDark
                                )
                            }
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(20.dp),
                                contentPadding = PaddingValues(bottom = 32.dp)
                            ) {
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
                            modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Error: ${state.message}",
                                    color = Color.Red,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { submit(lastQuery) },
                                    enabled = lastQuery.isNotBlank(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PastelBlueDark)
                                ) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                    TranslateUiState.Idle -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Type something above to see magic ♡", color = Color.Gray, fontSize = 18.sp)
                            Text("🌸 ✦ ✨ ✦ 🌸", color = Color.LightGray, modifier = Modifier.padding(top = 16.dp), fontSize = 24.sp)
                        }
                    }
                }
            }

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
}

@Composable
fun TranslationCard(
    translation: TranslationResult,
    viewModel: TranslateViewModel,
    onPlayAudio: () -> Unit
) {
    val isAudioLoading by viewModel.audioLoading.collectAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(32.dp)),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        )
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    color = PastelPurple,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = translation.register.uppercase(),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = PastelPurpleDark
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = translation.text,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = ShoujoText
                )
            }
            IconButton(
                onClick = onPlayAudio,
                enabled = !isAudioLoading,
                modifier = Modifier
                    .size(64.dp)
                    .background(PastelPurple.copy(alpha = 0.5f), RoundedCornerShape(32.dp))
                    .shadow(1.dp, RoundedCornerShape(32.dp))
            ) {
                if (isAudioLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp, color = PastelPurpleDark)
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play Audio", tint = PastelPurpleDark, modifier = Modifier.size(36.dp))
                }
            }
        }
    }
}
