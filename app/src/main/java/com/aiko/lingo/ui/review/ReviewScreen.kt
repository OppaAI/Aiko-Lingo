package com.aiko.lingo.ui.review

/*
=====================================================================
FIX (this version):
  1. Wrapped `ReviewCardContent` in a Column inside `AnimatedContent`.
     Since `ReviewCardContent` is a `ColumnScope` extension using
     `.weight(1f)`, it must be called within a Column.
  2. Fixed a potential crash where filling max size inside
     AnimatedContent might conflict with parent constraints.

CUTE UI OVERHAUL (maintained):
  - Multiple Choice Grid, Action Bar, and bouncy animations.
=====================================================================
*/

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.shadow
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
import com.aiko.lingo.ui.theme.*
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

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(8.dp))
                
                val animatedProgress by animateFloatAsState(
                    targetValue = if (cardsDue + reviewsCompleted > 0)
                        (reviewsCompleted.toFloat() / (reviewsCompleted + cardsDue))
                    else 0f,
                    animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                    label = "review_progress"
                )
                
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .weight(1f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    color = PastelGreenDark,
                    trackColor = Color(0xFFE0E0E0)
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Text(
                    text = "🔥 $reviewsCompleted",
                    fontWeight = FontWeight.ExtraBold,
                    color = PastelOrangeDark,
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedContent(
                targetState = uiState,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) + slideInHorizontally { it } togetherWith
                    fadeOut(animationSpec = tween(300)) + slideOutHorizontally { -it }
                },
                label = "screen_transition",
                modifier = Modifier.fillMaxSize().weight(1f)
            ) { state ->
                when (state) {
                    ReviewUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ShoujoAccent)
                        }
                    }
                    ReviewUiState.Reviewing -> {
                        currentCard?.let { card ->
                            // ✅ FIX: Provide ColumnScope for ReviewCardContent
                            Column(modifier = Modifier.fillMaxSize()) {
                                ReviewCardContent(card, viewModel)
                            }
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
                            Text("Oops! Something went wrong 😭", style = MaterialTheme.typography.titleLarge)
                            Text(state.message, color = Color.Gray, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { viewModel.retryLoadCards() },
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PastelBlueDark)
                            ) {
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
            .weight(1f)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .shadow(6.dp, RoundedCornerShape(32.dp)),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp, horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = PastelPurple,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "NIHONGO ✨",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = PastelPurpleDark
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = card.kanji ?: card.hiragana,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = ShoujoText,
                    textAlign = TextAlign.Center
                )
                if (card.kanji != null) {
                    Text(
                        text = card.hiragana,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Gray
                    )
                }
                if (card.context.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        card.context,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray,
                        fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            "Select the correct meaning:",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.align(Alignment.Start),
            color = ShoujoText.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        choices.forEach { choice ->
            val isCorrect = choice == card.meaning
            val isSelected = selectedChoice == choice
            
            val backgroundColor = when {
                showMeaning && isCorrect -> PastelGreen.copy(alpha = 0.4f)
                isSelected && !isCorrect -> Color(0xFFFFEBEE)
                isSelected -> PastelBlue.copy(alpha = 0.3f)
                else -> Color.White
            }
            val borderColor = when {
                showMeaning && isCorrect -> PastelGreenDark
                isSelected && !isCorrect -> Color(0xFFEF5350)
                isSelected -> PastelBlueDark
                else -> Color(0xFFE0E0E0)
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clickable(enabled = !showMeaning) {
                        selectedChoice = choice
                        showMeaning = true
                        selectedGrade = if (isCorrect) 3 else 0
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                shape = RoundedCornerShape(24.dp),
                color = backgroundColor,
                border = BorderStroke(2.dp, borderColor),
                shadowElevation = if (isSelected) 0.dp else 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = choice,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isSelected || (showMeaning && isCorrect)) ShoujoText else Color.DarkGray
                    )
                    if (showMeaning && isCorrect) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PastelGreenDark)
                    } else if (isSelected && !isCorrect) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFEF5350))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (showMeaning) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                color = if (selectedChoice == card.meaning) PastelGreen.copy(alpha = 0.8f) else Color(0xFFFFEBEE),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (selectedChoice == card.meaning) "🎉 AMAZING!" else "KEEP TRYING!",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (selectedChoice == card.meaning) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                        )
                    }
                    if (selectedChoice != card.meaning) {
                        Text(
                            "The correct meaning is: ${card.meaning}",
                            color = Color(0xFFB71C1C),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            viewModel.submitReview(card.card_id, selectedChoice ?: "", selectedGrade)
                            selectedChoice = null
                            selectedGrade = -1
                            showMeaning = false
                        },
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedChoice == card.meaning) PastelGreenDark else Color(0xFFEF5350)
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        Text("CONTINUE", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
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
        Text("🥳", fontSize = 52.sp)

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            if (reviewsCompleted > 0) "Goal Reached!" else "All Caught Up!",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            color = PastelGreenDark
        )

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            color = PastelGreen.copy(alpha = 0.3f),
            shape = RoundedCornerShape(32.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (reviewsCompleted > 0) {
                    Text(
                        "You mastered $reviewsCompleted words!",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        color = ShoujoText
                    )
                } else {
                    Text(
                        "No new cards to learn today ♡",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        color = ShoujoText
                    )
                }
                if (cardsRemaining > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "$cardsRemaining cards left for later",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    if (reviewsCompleted > 0) "You're becoming a Nihongo pro! ♡" else "Chat with Aiko to discover new words! ✨",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = PastelGreenDark,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(56.dp)
                .shadow(8.dp, RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PastelGreenDark)
        ) {
            Text("CONTINUE", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
    }
}
