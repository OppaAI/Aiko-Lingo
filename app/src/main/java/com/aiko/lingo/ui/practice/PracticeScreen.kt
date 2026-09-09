package com.aiko.lingo.ui.practice

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiko.lingo.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun PracticeScreen(
    viewModel: PracticeViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Spacer(Modifier.width(8.dp))
            Text("Practice ✍️", style = MaterialTheme.typography.headlineMedium)
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (val state = uiState) {
            PracticeUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ShoujoAccent)
                }
            }
            PracticeUiState.Question -> {
                PracticeQuestion(viewModel)
            }
            is PracticeUiState.Finished -> {
                PracticeFinished(
                    correct = state.correct,
                    total = state.total,
                    xp = state.xp,
                    onRetry = { viewModel.loadSession() },
                    onBack = onBack
                )
            }
            is PracticeUiState.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Oops! Practice failed to load.", textAlign = TextAlign.Center)
                    Text(state.message, color = Color.Gray, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.loadSession() }) { Text("Retry") }
                }
            }
        }
    }
}

@Composable
private fun PracticeQuestion(viewModel: PracticeViewModel) {
    val index by viewModel.index.collectAsState()
    val session by viewModel.session.collectAsState()
    val score by viewModel.score.collectAsState()
    val speaking by viewModel.speaking.collectAsState()
    val card = session.getOrNull(index) ?: return

    // Reset per-card answer UI whenever the card changes.
    var input by remember(index) { mutableStateOf("") }
    var verdict by remember(index) { mutableStateOf<Boolean?>(null) }

    if (verdict == true) {
        LaunchedEffect(index) {
            delay(700)
            viewModel.answerCorrect()
            viewModel.next()
        }
    }

    fun submit() {
        if (input.isBlank() || verdict != null) return
        verdict = viewModel.checkAnswer(card, input)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = { (index.toFloat()) / session.size },
                modifier = Modifier
                    .weight(1f)
                    .height(12.dp),
                color = PastelOrangeDark,
                trackColor = Color(0xFFE0E0E0)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "${index + 1} / ${session.size} · ⭐ $score",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                color = ShoujoText
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = PastelYellow)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "What is this in Japanese?",
                    fontSize = 13.sp,
                    color = ShoujoText.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    card.meaning,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ShoujoText,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.playCurrent() },
                    enabled = !speaking,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PastelOrangeDark)
                ) {
                    if (speaking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(Icons.Default.VolumeUp, contentDescription = "Hear it")
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Hear it 🔊")
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = input,
            onValueChange = { if (verdict == null) input = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Type in ひらがな… (⌨️ ✍️ 🎤 via keyboard)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { submit() }),
            shape = RoundedCornerShape(20.dp),
            isError = verdict == false
        )

        Spacer(modifier = Modifier.height(12.dp))

        when (verdict) {
            true -> {
                Text(
                    "✓ Correct!",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = PastelGreenDark
                )
            }
            false -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Answer: ${card.hiragana}",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB71C1C)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.next() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))
                        ) { Text("Next →") }
                    }
                }
            }
            null -> {
                Button(
                    onClick = { submit() },
                    enabled = input.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PastelOrangeDark)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Send", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun PracticeFinished(
    correct: Int,
    total: Int,
    xp: Int,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🎉", fontSize = 52.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "$correct / $total correct!",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            color = ShoujoText
        )
        if (xp > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("+$xp XP earned! ✨", fontWeight = FontWeight.Bold, color = PastelOrangeDark)
        }
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(0.85f).height(52.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PastelOrangeDark)
        ) { Text("Practice again ✍️", fontWeight = FontWeight.ExtraBold) }
        TextButton(onClick = onBack, modifier = Modifier.padding(top = 4.dp)) {
            Text("Back to menu", fontWeight = FontWeight.Bold)
        }
    }
}
