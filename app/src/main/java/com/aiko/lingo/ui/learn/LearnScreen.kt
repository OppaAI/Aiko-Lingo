package com.aiko.lingo.ui.learn

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiko.lingo.data.model.LessonDeck

@Composable
fun LearnScreen(
    viewModel: LearnViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDetail = uiState is LearnUiState.Detail

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (isDetail) viewModel.backToDecks() else onBack() }) {
                Text("← Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Learn 🎓", style = MaterialTheme.typography.headlineMedium)
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (val state = uiState) {
            LearnUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is LearnUiState.Decks -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.decks, key = { it.id }) { deck ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.openDeck(deck.id) },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(deck.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    Text(deck.subtitle, fontSize = 14.sp)
                                    Text(
                                        "${deck.card_count} cards",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Icon(Icons.Default.ArrowForward, contentDescription = "Open deck")
                            }
                        }
                    }
                }
            }
            is LearnUiState.Detail -> {
                FlashcardViewer(deck = state.deck, onSpeak = { viewModel.playCard(it) })
            }
            is LearnUiState.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Error: ${state.message}", textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.loadDecks() }) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

@Composable
private fun FlashcardViewer(deck: LessonDeck, onSpeak: (String) -> Unit) {
    var index by remember(deck.id) { mutableIntStateOf(0) }
    var flipped by remember(deck.id, index) { mutableStateOf(false) }
    val card = deck.cards.getOrNull(index)

    if (card == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("This deck is empty.")
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "${index + 1} / ${deck.cards.size}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clickable { flipped = !flipped },
            shape = RoundedCornerShape(24.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // Shrink long text so multi-line cards fit instead of
                // overflowing over each other.
                val frontSize = when {
                    card.front.length <= 3 -> 64.sp
                    card.front.length <= 6 -> 48.sp
                    card.front.length <= 10 -> 36.sp
                    else -> 26.sp
                }
                val backSize = if (card.back.length <= 30) 26.sp else 20.sp
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    if (!flipped) {
                        Text(card.front, fontSize = frontSize, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Tap to reveal", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    } else {
                        Text(card.back, fontSize = backSize, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        if (card.reading.isNotBlank() && card.reading != card.front) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(card.reading, fontSize = 18.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { if (index > 0) index-- },
                enabled = index > 0
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Previous")
            }
            IconButton(onClick = {
                onSpeak(card.reading.ifBlank { card.front })
            }) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Speak")
            }
            Button(
                onClick = { if (index < deck.cards.size - 1) index++ },
                enabled = index < deck.cards.size - 1
            ) {
                Text("Next")
            }
        }
    }
}
