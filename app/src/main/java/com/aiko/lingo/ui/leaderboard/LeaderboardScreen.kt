package com.aiko.lingo.ui.leaderboard

/*
=====================================================================
CUTE UI OVERHAUL (this version):
  1. Rank badges as colorful, rounded squares (16dp).
  2. League badge with "premium" gold aesthetic and soft shadow.
  3. Your Rank highlighted with a large, pastel pink card.
  4. Top Players list with clean, rounded rows and bold emojis.
=====================================================================
*/

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiko.lingo.data.model.LeaderboardResponse
import com.aiko.lingo.data.model.LeaderboardUser
import com.aiko.lingo.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    viewModel: LeaderboardViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Leaderboard 🏆", fontWeight = FontWeight.ExtraBold, color = ShoujoText) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ShoujoText)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.fetchLeaderboard() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = ShoujoText)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            when (val state = uiState) {
                LeaderboardUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PastelYellowDark)
                    }
                }
                is LeaderboardUiState.Success -> {
                    LeaderboardContent(state.leaderboard)
                }
                is LeaderboardUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Oops! Leaderboard is missing.",
                            color = Color.Red,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderboardContent(leaderboard: LeaderboardResponse) {
    Column(modifier = Modifier.fillMaxSize()) {
        YourRankCard(leaderboard)

        Spacer(modifier = Modifier.height(20.dp))

        Surface(
            modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(24.dp)),
            color = PastelYellow,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(2.dp, PastelYellowDark)
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🏆", fontSize = 36.sp)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        "Gold League",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF827717)
                    )
                    Text(
                        "Top 10 users qualify for Emerald!",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF827717).copy(alpha = 0.6f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Top Players ✨",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = ShoujoText,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            itemsIndexed(leaderboard.top_users) { index, user ->
                LeaderboardRow(rank = index + 1, user = user, isCurrentUser = user.username == leaderboard.username)
            }
        }
    }
}

@Composable
private fun YourRankCard(leaderboard: LeaderboardResponse) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(32.dp)),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = ShoujoPink
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "YOUR RANK",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Text(
                    "#${leaderboard.rank}",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
            Surface(
                color = Color.White.copy(alpha = 0.25f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "${leaderboard.xp}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        "XP",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun LeaderboardRow(
    rank: Int,
    user: LeaderboardUser,
    isCurrentUser: Boolean
) {
    val backgroundColor = if (isCurrentUser) ShoujoSoftPink else Color.White
    val borderColor = if (isCurrentUser) ShoujoPink else Color(0xFFF5F5F5)

    Surface(
        modifier = Modifier.fillMaxWidth().shadow(1.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = backgroundColor,
        border = androidx.compose.foundation.BorderStroke(2.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            when (rank) {
                                1 -> Color(0xFFFFD700)
                                2 -> Color(0xFFE0E0E0)
                                3 -> Color(0xFFFFCCBC)
                                else -> Color(0xFFF5F5F5)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        when (rank) {
                            1 -> "🥇"
                            2 -> "🥈"
                            3 -> "🥉"
                            else -> "#$rank"
                        },
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (rank > 3) Color.Gray else Color.Unspecified
                    )
                }

                Column {
                    Text(
                        user.username,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = ShoujoText
                    )
                    Text(
                        "🔥 ${user.streak} day streak",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${user.xp}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = ShoujoAccent
                )
                Text(
                    "XP",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.LightGray
                )
            }
        }
    }
}
