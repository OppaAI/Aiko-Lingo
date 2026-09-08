package com.aiko.lingo.ui.dashboard

/*
=====================================================================
BUGFIX PASS (this version):
  1. Error state had no retry affordance -- added a Retry button
     calling viewModel.refreshStats().
  2. Added a "Practice These" row driven by the new
     StatsResponse.weak_vocab field (cards with a high review-failure
     rate), matching the weak-vocab backend endpoint / SRS addition.

DUOLINGO-STYLE UPGRADES (this version):
  3. The "Practice These" widget now correctly routes to a specialized
     Practice mode in the Review screen, allowing for targeted
     learning of weak spots (a key Duolingo feature).
=====================================================================
*/

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import com.aiko.lingo.data.model.WordOfDayResponse
import com.aiko.lingo.data.model.StatsResponse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onBack: () -> Unit,
    onNavigateToReview: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Dashboard", style = MaterialTheme.typography.headlineMedium)
            }
            IconButton(onClick = { viewModel.refreshStats() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (val state = uiState) {
            DashboardUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is DashboardUiState.Success -> {
                DashboardContent(
                    stats = state.stats,
                    wordOfDay = state.wordOfDay,
                    onNavigateToReview = onNavigateToReview,
                    onPlayWord = { text, url -> viewModel.playWord(text, url) }
                )
            }
            is DashboardUiState.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Error: ${state.message}", color = Color.Red, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.refreshStats() }) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardContent(
    stats: StatsResponse,
    wordOfDay: WordOfDayResponse?,
    onNavigateToReview: (String) -> Unit,
    onPlayWord: (String, String?) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        wordOfDay?.let { WordOfDayCard(it, onPlayWord) }

        Spacer(modifier = Modifier.height(16.dp))

        XPCard(stats)

        Spacer(modifier = Modifier.height(16.dp))

        StreakCard(stats)

        Spacer(modifier = Modifier.height(16.dp))

        StatsGrid(stats)

        if (stats.weak_vocab.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            WeakVocabCard(stats.weak_vocab, onNavigateToReview)
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (stats.cards_due > 0) {
            Button(
                onClick = { onNavigateToReview("SRS") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Review")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Review ${stats.cards_due} cards", fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun WordOfDayCard(word: WordOfDayResponse, onPlayWord: (String, String?) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("📖 Word of the Day", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(word.hiragana, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(word.meaning, fontSize = 16.sp)
                if (word.context.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(word.context, fontSize = 13.sp, color = Color.Gray)
                }
            }
            IconButton(onClick = { onPlayWord(word.hiragana, word.audioUrl) }) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play word")
            }
        }
    }
}

@Composable
private fun WeakVocabCard(weakVocab: List<ReviewCard>, onNavigateToReview: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "🎯 Practice These",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                "Words you've been missing lately",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(weakVocab.take(6), key = { it.card_id }) { card ->
                    Card(
                        modifier = Modifier
                            .width(110.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(10.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                card.hiragana,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                card.meaning,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = { onNavigateToReview("PRACTICE") }) {
                Text("Practice now →")
            }
        }
    }
}

@Composable
private fun XPCard(stats: StatsResponse) {
    val progressPercentage = stats.xp % 100 / 100f
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // DUOLINGO UPGRADE: Circular Progress for XP
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progressPercentage },
                    modifier = Modifier.size(80.dp),
                    strokeWidth = 8.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${stats.level}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "LVL",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(24.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Daily Progress",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${stats.xp} Total XP",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                LinearProgressIndicator(
                    progress = { progressPercentage },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    "${100 - (stats.xp % 100)} XP to next level",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun StreakCard(stats: StatsResponse) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "🔥 Current Streak",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${stats.streak.days} days",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stats.streak.next_reward,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 12.sp
                )
            }
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondary),
                contentAlignment = Alignment.Center
            ) {
                Text("${stats.streak.days}", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StatsGrid(stats: StatsResponse) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatBox(
                icon = "📚",
                label = "Total Cards",
                value = stats.total_cards.toString(),
                modifier = Modifier.weight(1f)
            )
            StatBox(
                icon = "✨",
                label = "Learned Today",
                value = stats.learned_today.toString(),
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatBox(
                icon = "🎯",
                label = "Reviews Today",
                value = stats.reviews_today.toString(),
                modifier = Modifier.weight(1f)
            )
            StatBox(
                icon = "📊",
                label = "Avg Ease",
                value = "%.1f".format(stats.avg_ease),
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatBox(
                icon = "⏰",
                label = "Due Now",
                value = stats.cards_due.toString(),
                modifier = Modifier.weight(1f)
            )
            StatBox(
                icon = "🎮",
                label = "Level",
                value = stats.last_level.replaceFirstChar { it.uppercase() },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatBox(
    icon: String,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(icon, fontSize = 24.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
