package com.aiko.lingo.ui.test

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiko.lingo.data.remote.TestQuestionDto
import com.aiko.lingo.data.remote.TestSubmitResponse
import com.aiko.lingo.ui.conversation.Toast
import com.aiko.lingo.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun LessonTestScreen(
    viewModel: LessonTestViewModel,
    onBack: () -> Unit,
    onPassed: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val submitError by viewModel.submitError.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (viewModel.track == "grammar") "Grammar Test ✏️" else "Lesson Test 📝",
                style = MaterialTheme.typography.headlineMedium
            )
        }
        Spacer(Modifier.height(12.dp))

        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            when (val state = uiState) {
                LessonTestUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ShoujoAccent)
                    }
                }
                is LessonTestUiState.Testing -> {
                    TestForm(
                        questions = state.questions,
                        title = state.title,
                        isFinal = state.isFinal,
                        submitting = state.submitting,
                        onSubmit = { viewModel.submit(it) }
                    )
                }
                is LessonTestUiState.Result -> {
                    TestResultView(
                        response = state.response,
                        isFinal = state.isFinal,
                        onBack = onBack,
                        onPassed = onPassed,
                        onRetry = { viewModel.retry() }
                    )
                }
                is LessonTestUiState.Error -> {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Couldn't load the test 😭", style = MaterialTheme.typography.titleLarge)
                        Text(state.message, color = Color.Gray, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.retry() }) { Text("Retry") }
                            OutlinedButton(onClick = onBack) { Text("Back") }
                        }
                    }
                }
            }

            submitError?.let { message ->
                Toast(
                    message = message,
                    onDismiss = { viewModel.dismissSubmitError() },
                    isError = true,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                )
                LaunchedEffect(message) {
                    delay(3000)
                    viewModel.dismissSubmitError()
                }
            }
        }
    }
}

@Composable
private fun TestForm(
    questions: List<TestQuestionDto>,
    title: String,
    isFinal: Boolean,
    submitting: Boolean,
    onSubmit: (Map<String, String>) -> Unit
) {
    // One input per question, keyed by qid so scroll position/answers survive.
    val answers = remember(questions) { mutableStateMapOf<String, String>() }
    val answered = answers.values.count { it.isNotBlank() }

    Column(Modifier.fillMaxSize()) {
        Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = ShoujoText)
        Text(
            if (isFinal) "Final: ${questions.size} random questions · need 100% to level up 🎯"
            else "${questions.size} questions · type every answer · need 100% to advance ✨",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { if (questions.isNotEmpty()) answered.toFloat() / questions.size else 0f },
            modifier = Modifier.fillMaxWidth().height(10.dp),
            color = PastelGreenDark,
            trackColor = Color(0xFFE0E0E0)
        )
        Text(
            "$answered / ${questions.size} answered",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            items(questions, key = { it.qid }) { q ->
                Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(q.prompt, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = answers[q.qid].orEmpty(),
                            onValueChange = { if (!submitting) answers[q.qid] = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Type in Japanese…") },
                            singleLine = true,
                            enabled = !submitting,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
        }

        Button(
            onClick = { onSubmit(answers.toMap()) },
            enabled = !submitting && answered == questions.size && questions.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PastelGreenDark)
        ) {
            if (submitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White
                )
                Spacer(Modifier.width(8.dp))
                Text("Grading…", fontWeight = FontWeight.ExtraBold)
            } else {
                Text("Submit test ✅", fontWeight = FontWeight.ExtraBold)
            }
        }
        if (answered < questions.size) {
            Text(
                "Answer every question to submit.",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun TestResultView(
    response: TestSubmitResponse,
    isFinal: Boolean,
    onBack: () -> Unit,
    onPassed: () -> Unit,
    onRetry: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (response.passed) "🎉" else "💪", fontSize = 52.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "${response.correct} / ${response.total} correct",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = ShoujoText
                )
                if (response.passed) {
                    Text(
                        when {
                            response.new_level != null -> "Perfect! Welcome to ${response.new_level}! 🏆"
                            isFinal -> "Perfect! Final cleared! 🏆"
                            else -> "Perfect! Lesson cleared! ✨"
                        },
                        fontWeight = FontWeight.Bold,
                        color = PastelGreenDark,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        "Need 100% to ${if (isFinal) "level up" else "advance"} — review the misses below 👇",
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
                if (response.xp > 0) {
                    Text(
                        "+${response.xp} XP",
                        fontWeight = FontWeight.Bold,
                        color = PastelOrangeDark
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            items(response.results.filter { !it.correct }) { r ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(r.prompt, fontWeight = FontWeight.Bold)
                        Text("You: ${r.given.ifBlank { "(blank)" }}", color = Color(0xFFB71C1C))
                        Text(
                            "Answer: ${r.expected.firstOrNull().orEmpty()}",
                            color = PastelGreenDark,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            if (response.results.any { !it.correct }) {
                item {
                    Text(
                        "Showing misses only (${response.results.count { !it.correct }}).",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        if (response.passed) {
            Button(
                onClick = onPassed,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PastelGreenDark)
            ) {
                Text(
                    if (response.new_level != null) "Continue at ${response.new_level} →"
                    else "Next lesson →",
                    fontWeight = FontWeight.ExtraBold
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(20.dp)
                ) { Text("Try again ✍️", fontWeight = FontWeight.ExtraBold) }
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(20.dp)
                ) { Text("Study more 📖") }
            }
        }
    }
}
