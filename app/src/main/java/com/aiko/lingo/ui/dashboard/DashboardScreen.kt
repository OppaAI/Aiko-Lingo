package com.aiko.lingo.ui.dashboard

/*
=====================================================================
CRASH FIX (this version):
  1. Moved the `when(uiState)` block so that the scrollable Column is
     ONLY used in the Success state.
  2. Fixed a classic Compose crash where `Box(Modifier.fillMaxSize())`
     (the Loading indicator) was measured inside a scrollable Column,
     leading to infinite height constraints and an IllegalStateException.

CUTE UI OVERHAUL (maintained):
  - Circular XP Progress, 32dp rounded cards, and pastel palette.
=====================================================================
*/

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiko.lingo.data.model.ReviewCard
import com.aiko.lingo.data.model.WordOfDayResponse
import com.aiko.lingo.data.model.StatsResponse
import com.aiko.lingo.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onBack: () -> Unit,
    onNavigateToReview: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your Stats ✨", fontWeight = FontWeight.ExtraBold, color = ShoujoText) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ShoujoText)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshStats() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = ShoujoText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        // ✅ FIX: Move the scrollable Column inside the Success state
        // or ensure it doesn't wrap the Loading/Error states which use fillMaxSize.
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = uiState) {
                DashboardUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ShoujoAccent)
                    }
                }
                is DashboardUiState.Success -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        DashboardContent(
                            stats = state.stats,
                            wordOfDay = state.wordOfDay,
                            onNavigateToReview = onNavigateToReview,
                            onPlayWord = { text, url -> viewModel.playWord(text, url) }
                        )
                        Spacer(modifier = Modifier.height(48.dp))
                    }
                }
                is DashboardUiState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Oops! Stats failed to load.", color = Color.Red, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.refreshStats() },
                            colors = ButtonDefaults.buttonColors(containerColor = PastelBlueDark),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Retry")
                        }
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

        Spacer(modifier = Modifier.height(24.dp))

        XPCard(stats)

        Spacer(modifier = Modifier.height(24.dp))

        StreakCard(stats)

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            "Learning Progress 🌸",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = ShoujoText,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        StatsGrid(stats)

        if (stats.weak_vocab.isNotEmpty()) {
            Spacer(modifier = Modifier.height(32.dp))
            WeakVocabCard(stats.weak_vocab, onNavigateToReview)
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (stats.cards_due > 0) {
            Button(
                onClick = { onNavigateToReview("SRS") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .shadow(8.dp, RoundedCornerShape(32.dp)),
                shape = RoundedCornerShape(32.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PastelGreenDark,
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Review")
                Spacer(modifier = Modifier.width(12.dp))
                Text("Start Practice (${stats.cards_due})", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun WordOfDayCard(word: WordOfDayResponse, onPlayWord: (String, String?) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(32.dp)),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = PastelYellow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    color = PastelYellowDark.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        "WORD OF THE DAY 📖",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF6D4C41)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = word.kanji ?: word.hiragana,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ShoujoText
                )
                if (word.kanji != null) {
                    Text(
                        text = word.hiragana,
                        fontSize = 16.sp,
                        color = ShoujoText.copy(alpha = 0.6f)
                    )
                }
                Text(word.meaning, fontSize = 20.sp, color = ShoujoText.copy(alpha = 0.8f))
            }
            IconButton(
                onClick = { onPlayWord(word.hiragana, word.audioUrl) },
                modifier = Modifier
                    .size(64.dp)
                    .background(Color.White, RoundedCornerShape(32.dp))
                    .shadow(2.dp, RoundedCornerShape(32.dp))
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = PastelYellowDark, modifier = Modifier.size(36.dp))
            }
        }
    }
}

@Composable
private fun WeakVocabCard(weakVocab: List<ReviewCard>, onNavigateToReview: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(32.dp)),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎯", fontSize = 28.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Tricky Words",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFB71C1C)
                )
            }
            Text(
                "Focus on words you've missed",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(20.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(weakVocab.take(5), key = { it.card_id }) { card ->
                    Surface(
                        modifier = Modifier.width(130.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White,
                        shadowElevation = 2.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(card.hiragana, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = ShoujoText)
                            Text(card.meaning, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 1, color = Color.Gray)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { onNavigateToReview("PRACTICE") },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))
            ) {
                Text("Practice Weak Spots", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun XPCard(stats: StatsResponse) {
    val progressPercentage = stats.xp % 100 / 100f
    
    Card(
        modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(32.dp)),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = PastelBlue)
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progressPercentage },
                    modifier = Modifier.size(90.dp),
                    strokeWidth = 12.dp,
                    color = PastelBlueDark,
                    trackColor = Color.White.copy(alpha = 0.5f)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${stats.level}",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = PastelBlueDark
                    )
                }
            }

            Spacer(modifier = Modifier.width(24.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Daily Progress",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = ShoujoText
                )
                Text(
                    "${stats.xp} Total XP ✨",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ShoujoText.copy(alpha = 0.6f)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                LinearProgressIndicator(
                    progress = { progressPercentage },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp)),
                    color = PastelBlueDark,
                    trackColor = Color.White
                )
                
                Text(
                    "${100 - (stats.xp % 100)} XP to next level",
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = PastelBlueDark
                )
            }
        }
    }
}

@Composable
private fun StreakCard(stats: StatsResponse) {
    Card(
        modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(32.dp)),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = PastelOrange)
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🔥", fontSize = 56.sp)
            Spacer(modifier = Modifier.width(20.dp))
            Column {
                Text(
                    "${stats.streak.days} Day Streak!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFD84315)
                )
                Text(
                    stats.streak.next_reward,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE64A19).copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun StatsGrid(stats: StatsResponse) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatBox("📚", "Total Words", stats.total_cards.toString(), PastelPurple, Modifier.weight(1f))
            StatBox("✨", "Learned", stats.learned_today.toString(), PastelGreen, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatBox("🎯", "Practice", stats.reviews_today.toString(), PastelOrange, Modifier.weight(1f))
            StatBox("📊", "Memory Ease", "%.1f".format(stats.avg_ease), PastelBlue, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatBox(icon: String, label: String, value: String, color: Color, modifier: Modifier) {
    Surface(
        modifier = modifier.shadow(2.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = color
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(icon, fontSize = 32.sp)
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = ShoujoText.copy(alpha = 0.5f))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = ShoujoText)
        }
    }
}
