package com.aiko.lingo.ui.review

/*
=====================================================================
BUGFIX PASS (this version):
  1. Error state showed red text with no way to recover; added a
     Retry button wired to viewModel.retryLoadCards().

BUGFIX PASS (this version, cont. -- audit fix):
  2. ReviewViewModel already collected the backend's review toast
     (e.g. "🌟 word = meaning" on an Easy grade, from
     ReviewResponseData.toast) into a toastMessage StateFlow, but this
     screen never collected or rendered it -- the toast was silently
     dropped. Wrapped the screen in a Box and added the same Toast
     overlay pattern already used on the Conversation and Translate
     screens (reusing their Toast composable).
=====================================================================
*/

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiko.lingo.data.model.ReviewCard
import com.aiko.lingo.ui.conversation.Toast
import kotlinx.coroutines.delay

@Composable
fun ReviewScreen(
    viewModel: ReviewViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentCard by viewModel.currentCard.collectAsState()
    val cardsDue by viewModel.cardsDue.collectAsState()
    val reviewsCompleted by viewModel.reviewsCompleted.collectAsState()
    // FIX #2: was previously collected nowhere -- the backend's review
    // toast (e.g. an Easy-grade "🌟 word = meaning" celebration) was
    // dropped on the floor even though the ViewModel already exposed it.
    val toast by viewModel.toastMessage.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onBack) { Text("← Back") }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Vocabulary Review", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "$reviewsCompleted / $cardsDue",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { if (cardsDue > 0) (reviewsCompleted.toFloat() / cardsDue) else 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )

            Spacer(modifier = Modifier.height(24.dp))

            when (uiState) {
                ReviewUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                ReviewUiState.Reviewing -> {
                    currentCard?.let { card ->
                        ReviewCardContent(card, viewModel, reviewsCompleted, cardsDue)
                    }
                }
                is ReviewUiState.Finished -> {
                    ReviewFinishedScreen(
                        cardsRemaining = (uiState as ReviewUiState.Finished).cardsRemaining,
                        reviewsCompleted = reviewsCompleted,
                        onBack = onBack
                    )
                }
                is ReviewUiState.Error -> {
                    // FIX: add Retry so a failed load/submit isn't a dead end.
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "Error: ${(uiState as ReviewUiState.Error).message}",
                            color = Color.Red,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { viewModel.retryLoadCards() }) {
                                Text("Retry")
                            }
                            OutlinedButton(onClick = onBack) {
                                Text("Back")
                            }
                        }
                    }
                }
            }
        }

        // FIX #2: backend-driven review toast (e.g. an Easy-grade
        // "🌟 word = meaning" celebration), same pattern as the
        // Conversation and Translate screens.
        toast?.let { t ->
            Toast(
                message = t.message,
                onDismiss = { viewModel.dismissToast() },
                isError = t.type == "error",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )

            LaunchedEffect(t) {
                delay(3000)
                viewModel.dismissToast()
            }
        }
    }
}

@Composable
private fun ReviewCardContent(
    card: ReviewCard,
    viewModel: ReviewViewModel,
    reviewsCompleted: Int,
    cardsDue: Int
) {
    var userResponse by remember { mutableStateOf("") }
    var selectedGrade by remember { mutableStateOf(-1) }
    var showMeaning by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .weight(1f)
    ) {
        // Card Display
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "What does this mean?",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    card.hiragana,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 48.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (showMeaning) {
                    Text(
                        "→ ${card.meaning}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (!showMeaning) {
                    TextButton(onClick = { showMeaning = true }) {
                        Text("Show meaning")
                    }
                }
            }
        }

        // Context
        if (card.context.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Context:", style = MaterialTheme.typography.labelSmall)
                    Text(card.context, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // User Response Input
        OutlinedTextField(
            value = userResponse,
            onValueChange = { userResponse = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Your response...") },
            enabled = showMeaning
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Grade Buttons
        if (showMeaning) {
            Text("How did you remember it?", style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GradeButton(
                    label = "❌ Again",
                    grade = 0,
                    isSelected = selectedGrade == 0,
                    onClick = { selectedGrade = 0 },
                    modifier = Modifier.weight(1f)
                )
                GradeButton(
                    label = "🤔 Hard",
                    grade = 1,
                    isSelected = selectedGrade == 1,
                    onClick = { selectedGrade = 1 },
                    modifier = Modifier.weight(1f)
                )
                GradeButton(
                    label = "✓ Good",
                    grade = 2,
                    isSelected = selectedGrade == 2,
                    onClick = { selectedGrade = 2 },
                    modifier = Modifier.weight(1f)
                )
                GradeButton(
                    label = "⭐ Easy",
                    grade = 3,
                    isSelected = selectedGrade == 3,
                    onClick = { selectedGrade = 3 },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Submit Button
        Button(
            onClick = {
                viewModel.submitReview(card.card_id, userResponse, selectedGrade)
                userResponse = ""
                selectedGrade = -1
                showMeaning = false
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            enabled = showMeaning && selectedGrade >= 0
        ) {
            Text("Next", fontSize = 16.sp)
        }
    }
}

@Composable
private fun GradeButton(
    label: String,
    grade: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ReviewFinishedScreen(
    cardsRemaining: Int,
    reviewsCompleted: Int,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text("🎉", fontSize = 56.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Great job!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            "You completed $reviewsCompleted reviews",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        Text(
            "$cardsRemaining cards due later",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(48.dp)
        ) {
            Text("Back to Dashboard")
        }
    }
}
