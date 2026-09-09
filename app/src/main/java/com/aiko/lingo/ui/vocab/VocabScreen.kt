package com.aiko.lingo.ui.vocab

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import com.aiko.lingo.data.remote.CourseDetail
import com.aiko.lingo.data.remote.LearnSessionResponse
import com.aiko.lingo.data.remote.LearnStatusResponse

@Composable
fun VocabScreen(
    viewModel: VocabViewModel,
    onBack: () -> Unit
) {
    val decks by viewModel.decks.collectAsState()
    val detail by viewModel.detail.collectAsState()
    val listLoading by viewModel.listLoading.collectAsState()
    val detailLoading by viewModel.detailLoading.collectAsState()
    val listError by viewModel.listError.collectAsState()
    val detailError by viewModel.detailError.collectAsState()
    val courses by viewModel.courses.collectAsState()
    val courseDetail by viewModel.courseDetail.collectAsState()
    val courseDetailLoading by viewModel.courseDetailLoading.collectAsState()
    val courseDetailError by viewModel.courseDetailError.collectAsState()
    val coursesError by viewModel.coursesError.collectAsState()
    val speakingKey by viewModel.speakingKey.collectAsState()
    val currentLevel by viewModel.currentLevel.collectAsState()
    val levels by viewModel.levels.collectAsState()
    val learnStatus by viewModel.learnStatus.collectAsState()
    val levelLoading by viewModel.levelLoading.collectAsState()
    val levelError by viewModel.levelError.collectAsState()
    val learnPool by viewModel.learnPool.collectAsState()
    val poolLoading by viewModel.poolLoading.collectAsState()
    val poolError by viewModel.poolError.collectAsState()
    val lastXpEarned by viewModel.lastXpEarned.collectAsState()
    val isDetail = detail != null || detailLoading || detailError != null ||
        courseDetail != null || courseDetailLoading || courseDetailError != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (isDetail) viewModel.backToList() else onBack() }) {
                Text("← Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Vocab 🌱", style = MaterialTheme.typography.headlineMedium)
        }

        Spacer(modifier = Modifier.height(16.dp))

        when {
            listLoading && decks.isEmpty() && !isDetail -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            listError != null && decks.isEmpty() && !isDetail -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Error: $listError", textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.loadDecks() }) {
                        Text("Retry")
                    }
                }
            }
            detail != null -> {
                if (detailLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                }
                if (detailError != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Couldn't refresh deck: $detailError")
                            TextButton(onClick = { viewModel.retryDetail() }) { Text("Retry detail") }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                FlashcardViewer(deck = detail!!, onSpeak = { viewModel.playCard(it) })
            }
            detailLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            detailError != null -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Error: $detailError", textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.retryDetail() }) { Text("Retry") }
                        OutlinedButton(onClick = { viewModel.backToList() }) { Text("Back") }
                    }
                }
            }
            courseDetail != null -> {
                CourseDetailView(
                    detail = courseDetail!!,
                    loading = courseDetailLoading,
                    error = courseDetailError,
                    speakingKey = speakingKey,
                    onRetry = { viewModel.retryCourseDetail() },
                    onSpeak = { text, key -> viewModel.playCard(text, key) }
                )
            }
            courseDetailLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            courseDetailError != null -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Error: $courseDetailError", textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.retryCourseDetail() }) { Text("Retry") }
                        OutlinedButton(onClick = { viewModel.backToList() }) { Text("Back") }
                    }
                }
            }
            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        LevelProgressionCard(
                            currentLevel = currentLevel,
                            levels = levels,
                            status = learnStatus,
                            loading = levelLoading,
                            error = levelError,
                            isUnlocked = { viewModel.isLevelUnlocked(it) },
                            onSelect = { viewModel.setLevel(it) }
                        )
                    }
                    item {
                        LearnPoolCard(
                            pool = learnPool,
                            loading = poolLoading,
                            error = poolError,
                            xpEarned = lastXpEarned,
                            currentLevel = currentLevel,
                            nextLevel = viewModel.nextLevel(),
                            hasProgress = viewModel.hasProgress(),
                            isComplete = viewModel.isCurrentComplete(),
                            onReload = { viewModel.loadLearnPool() },
                            onMarkLearned = { viewModel.markLearned(it) },
                            onLevelUp = { viewModel.setLevel(it) },
                            onDismissXp = { viewModel.clearXpToast() }
                        )
                    }
                    item {
                        Text(
                            "Kana & lessons",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    items(decks, key = { it.id }) { deck ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.openDeck(deck.id) },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(deck.title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text(deck.subtitle, fontSize = 14.sp)
                                    Text(
                                        "${deck.card_count} cards",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Open deck")
                            }
                        }
                    }
                    item {
                        Text(
                            "Courses 📖",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    if (coursesError != null && courses.isEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("Couldn't load courses: $coursesError")
                                    TextButton(onClick = { viewModel.loadCourses() }) { Text("Retry") }
                                }
                            }
                        }
                    }
                    if (courses.isEmpty() && coursesError == null) {
                        item {
                            Text(
                                "No course decks yet — check back soon!",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    items(courses, key = { it.id }) { course ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.openCourse(course.id) },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(course.title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${course.level} · ${course.card_count} cards",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Open course")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseDetailView(
    detail: CourseDetail,
    loading: Boolean,
    error: String?,
    speakingKey: String?,
    onRetry: () -> Unit,
    onSpeak: (String, String) -> Unit
) {
    if (loading) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
    }
    if (error != null) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("Couldn't refresh course: $error")
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    Text(detail.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    Text("${detail.level} · ${detail.cards.size} cards", color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(12.dp))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(detail.cards) { c ->
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(c.front, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        if (c.reading.isNotBlank()) Text(c.reading, fontSize = 14.sp)
                        Text(c.back, fontSize = 16.sp)
                        if (c.note.isNotBlank()) Text(c.note, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                    if (speakingKey == c.front) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { onSpeak(c.reading.ifBlank { c.front }, c.front) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Hear pronunciation")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelProgressionCard(
    currentLevel: String,
    levels: List<String>,
    status: LearnStatusResponse?,
    loading: Boolean,
    error: String?,
    isUnlocked: (String) -> Boolean,
    onSelect: (String) -> Unit
) {
    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("JLPT Track · start N5 🌱", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val order = levels.ifEmpty { listOf("N5", "N4", "N3", "N2", "N1") }
                order.forEach { lvl ->
                    val selected = lvl == currentLevel
                    val unlocked = selected || isUnlocked(lvl)
                    FilterChip(
                        selected = selected,
                        enabled = unlocked,
                        onClick = { if (!selected) onSelect(lvl) },
                        label = { Text(if (unlocked) lvl else "$lvl 🔒", fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }
            Text("Complete $currentLevel pool to unlock next 🔒", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
            if (error != null) {
                Text("Level sync failed: $error", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
            if (status != null) {
                Spacer(Modifier.height(8.dp))
                val total = status.user_cards + status.pool_size_at_level
                val progress = if (total > 0) status.user_cards.toFloat() / total else 0f
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(
                    "$currentLevel · ${status.user_cards}/$total learned · +${status.learned_today} today · ${status.pool_size_at_level} left",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text("Current: $currentLevel", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun LearnPoolCard(
    pool: LearnSessionResponse?,
    loading: Boolean,
    error: String?,
    xpEarned: Int?,
    currentLevel: String,
    nextLevel: String?,
    hasProgress: Boolean,
    isComplete: Boolean,
    onReload: () -> Unit,
    onMarkLearned: (List<com.aiko.lingo.data.remote.LearnItemDto>) -> Unit,
    onLevelUp: (String) -> Unit,
    onDismissXp: () -> Unit
) {
    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("New vocab · $currentLevel", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            if (xpEarned != null) {
                Spacer(Modifier.height(8.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("+$xpEarned XP earned! 🎉", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        TextButton(onClick = onDismissXp) { Text("OK") }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            when {
                loading && pool == null -> Text("Loading new vocab…", fontSize = 13.sp)
                error != null && pool == null -> {
                    Text("Error: $error", fontSize = 13.sp)
                    Button(onClick = onReload, modifier = Modifier.padding(top = 8.dp)) { Text("Retry") }
                }
                pool == null || pool.items.isEmpty() -> {
                    if ((pool?.pending_in_pool ?: 0) == 0) {
                        if (isComplete && hasProgress) {
                            Text("All caught up at $currentLevel! ✨", fontWeight = FontWeight.Bold)
                            if (nextLevel != null) {
                                Text("Earn $nextLevel by leveling up:", fontSize = 13.sp)
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = { onLevelUp(nextLevel) }) { Text("Level up → $nextLevel") }
                            } else {
                                Text("You reached N1 — top rank! 🏆", fontSize = 13.sp)
                            }
                        } else {
                            Text("Preparing $currentLevel words… ⏳", fontWeight = FontWeight.Bold)
                            Text("No waiting on LLM — pool refills in background. Tap reload.", fontSize = 13.sp)
                            Button(onClick = onReload, modifier = Modifier.padding(top = 8.dp), enabled = !loading) { Text("Reload") }
                        }
                    } else {
                        Text("${pool?.pending_in_pool ?: 0} cards pending — tap reload.", fontSize = 13.sp)
                        Button(onClick = onReload, modifier = Modifier.padding(top = 8.dp)) { Text("Load more") }
                    }
                }
                else -> {
                    Text("${pool.items.size} new · ${pool.pending_in_pool} pending in pool", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    pool.items.take(5).forEach { item ->
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text(item.front, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            if (item.reading.isNotBlank() && item.reading != item.front) {
                                Text(item.reading, fontSize = 13.sp)
                            }
                            Text(item.back, fontSize = 15.sp)
                        }
                        HorizontalDivider()
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onMarkLearned(pool.items) },
                            enabled = !loading,
                            modifier = Modifier.weight(1f)
                        ) { Text("Got it +XP") }
                        OutlinedButton(onClick = onReload, enabled = !loading) { Text("↻") }
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous")
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
