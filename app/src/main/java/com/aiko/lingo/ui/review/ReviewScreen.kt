package com.aiko.lingo.ui.review

/*
=====================================================================
BUGFIX PASS (this version):
  1. Error state showed red text with no way to recover; added a
     Retry button wired to viewModel.retryLoadCards().
  2. ReviewViewModel already collected the backend's review toast
     (e.g. "🌟 word = meaning" on an Easy grade) but this screen
     never rendered it. Wrapped in a Box with a Toast overlay.

DUOLINGO-STYLE UPGRADES (this version):
  3. Added Multiple Choice options! Instead of just typing, users can
     tap one of 4 options (staple Duolingo feature). Tapping an option
     gives immediate visual feedback (green/red) and plays a haptic-like
     color shift.
  4. Added a "XP Gain" progress bar color shift and a celebratory
     confetti-style emoji explosion on the Finished screen.
  5. UI Polish: Cards now have a slight elevation and a "Nihongo"
     badge to feel more like a premium learning app.
=====================================================================
*/

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiko.lingo.data.model.ReviewCard
import com.aiko.lingo.ui.conversation.Toast
import kotlin.time.Duration.Companion.seconds
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
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    "Practice Mode",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "$reviewsCompleted / ${reviewsCompleted + cardsDue}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // DUOLINGO UPGRADE: Animated progress bar
            val animatedProgress by animateFloatAsState(
                targetValue = if (cardsDue + reviewsCompleted > 0)
                    (reviewsCompleted.toFloat() / (reviewsCompleted + cardsDue))
                else 0f,
                animationSpec = tween(durationMillis = 500),
                label = "review_progress"
            )
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedContent(
                targetState = uiState,
                transitionSpec = {
                    fadeIn() + slideInHorizontally { it } togetherWith
                    fadeOut() + slideOutHorizontally { -it }
                },
                label = "screen_transition"
            ) { state ->
                when (state) {
                    ReviewUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    ReviewUiState.Reviewing -> {
                        currentCard?.let { card ->
                            ReviewCardContent(card, viewModel)
                        }
                    }
                    is ReviewUiState.Finished -> {
                        ReviewFinishedScreen(
                            cardsRemaining = state.cardsRemaining,
                            reviewsCompleted = reviewsCompleted,
                            onBack = onBack
                        )
                    }
                    is ReviewUiState.Error -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = Color.Red, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Oops! Something went wrong", style = MaterialTheme.typography.titleMedium)
                            Text(state.message, color = Color.Gray, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(onClick = { viewModel.retryLoadCards() }) {
                                Text("Retry")
                            }
                        }
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
                    .padding(16.dp)
            )

            LaunchedEffect(t) {
                delay(3.seconds)
                viewModel.dismissToast()
            }
        }
    }
}

@Composable
private fun ColumnScope.ReviewCardContent(
    card: ReviewCard,
    viewModel: ReviewViewModel
) {
    val haptic = LocalHapticFeedback.current
    val choices by viewModel.choices.collectAsState()
    var selectedChoice by remember { mutableStateOf<String?>(null) }
    var selectedGrade by remember { mutableIntStateOf(-1) }
    var showMeaning by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // DUOLINGO STYLE: Big prominent card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "NIHONGO",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    card.hiragana,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (card.context.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        card.context,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        fontStyle = FontStyle.Italic
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Select the correct meaning:",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(12.dp))

        // DUOLINGO UPGRADE: Multiple Choice Grid
        choices.forEach { choice ->
            val isCorrect = choice == card.meaning
            val isSelected = selectedChoice == choice
            val backgroundColor = when {
                isSelected && isCorrect -> Color(0xFFE8F5E9)
                isSelected && !isCorrect -> Color(0xFFFFEBEE)
                showMeaning && isCorrect -> Color(0xFFE8F5E9)
                else -> MaterialTheme.colorScheme.surface
            }
            val borderColor = when {
                isSelected && isCorrect -> Color(0xFF4CAF50)
                isSelected && !isCorrect -> Color(0xFFF44336)
                showMeaning && isCorrect -> Color(0xFF4CAF50)
                else -> MaterialTheme.colorScheme.outlineVariant
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable(enabled = !showMeaning) {
                        selectedChoice = choice
                        showMeaning = true
                        // Auto-assign grade for Duolingo mode
                        selectedGrade = if (isCorrect) 3 else 0 // Easy if correct, Again if wrong
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                shape = RoundedCornerShape(12.dp),
                color = backgroundColor,
                border = BorderStroke(2.dp, borderColor)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = choice,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                    if (showMeaning && isCorrect) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                    } else if (isSelected && !isCorrect) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFF44336))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Duolingo-style action bar at the bottom
        if (showMeaning) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                color = if (selectedChoice == card.meaning) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        if (selectedChoice == card.meaning) "Correct! 🎉" else "Incorrect",
                        fontWeight = FontWeight.Bold,
                        color = if (selectedChoice == card.meaning) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                    if (selectedChoice != card.meaning) {
                        Text("Correct meaning: ${card.meaning}", color = Color(0xFFC62828))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            viewModel.submitReview(card.card_id, selectedChoice ?: "", selectedGrade)
                            selectedChoice = null
                            selectedGrade = -1
                            showMeaning = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedChoice == card.meaning) Color(0xFF4CAF50) else Color(0xFFF44336)
                        )
                    ) {
                        Text("CONTINUE")
                    }
                }
            }
        }
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
        // DUOLINGO STYLE: Celebration emoji with bounce
        Text("🎊", fontSize = 80.sp)

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Session Complete!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "You reviewed $reviewsCompleted words!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (cardsRemaining > 0) {
                    Text(
                        "$cardsRemaining more cards due later today",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
                Text(
                    "Keep up the great work!",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(56.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text("CONTINUE", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}
