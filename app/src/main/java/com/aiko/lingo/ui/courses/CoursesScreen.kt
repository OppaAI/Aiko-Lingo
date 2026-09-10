package com.aiko.lingo.ui.courses

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiko.lingo.data.remote.AikoApiService
import com.aiko.lingo.data.remote.CourseDetail
import com.aiko.lingo.data.remote.CourseMeta
import com.aiko.lingo.data.remote.CurrentLessonResponse
import com.aiko.lingo.data.remote.LessonProgressDto
import com.aiko.lingo.data.remote.LingoCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CoursesViewModel(private val api: AikoApiService, private val mode: String) : ViewModel() {
    private val cacheKey get() = "courses_$mode"
    private val _list = MutableStateFlow<List<CourseMeta>>(emptyList())
    val list = _list.asStateFlow()
    private val _detail = MutableStateFlow<CourseDetail?>(null)
    val detail = _detail.asStateFlow()
    private val _listLoading = MutableStateFlow(true)
    val listLoading = _listLoading.asStateFlow()
    private val _detailLoading = MutableStateFlow(false)
    val detailLoading = _detailLoading.asStateFlow()
    private val _listError = MutableStateFlow<String?>(null)
    val listError = _listError.asStateFlow()
    private val _detailError = MutableStateFlow<String?>(null)
    val detailError = _detailError.asStateFlow()

    private var pendingId: String? = null

    // --- Current lesson progression (one at a time + tests) ---
    private val track get() = if (mode == "grammar") "grammar" else "vocab"
    private val _current = MutableStateFlow<CourseDetail?>(null)
    val current = _current.asStateFlow()
    private val _progress = MutableStateFlow<LessonProgressDto?>(null)
    val progress = _progress.asStateFlow()
    private val _currentLoading = MutableStateFlow(false)
    val currentLoading = _currentLoading.asStateFlow()
    private val _currentError = MutableStateFlow<String?>(null)
    val currentError = _currentError.asStateFlow()

    init {
        // Cache-first: instant list on revisit, refresh in background.
        LingoCache.get<List<CourseMeta>>(cacheKey, 600_000)?.let {
            _list.value = it
            _listLoading.value = false
        }
        LingoCache.get<CurrentLessonResponse>("current_$mode", 60_000)?.let {
            _current.value = it.lesson
            _progress.value = it.progress
        }
        reload()
        loadCurrent()
    }

    fun reload() {
        viewModelScope.launch {
            if (_list.value.isEmpty()) _listLoading.value = true
            _listError.value = null
            try {
                val decks = if (mode == "grammar") api.getGrammarDecks() else api.getCourses()
                _list.value = decks
                LingoCache.put(cacheKey, decks)
            } catch (e: Exception) {
                if (_list.value.isEmpty()) {
                    _listError.value = e.message ?: "Failed to load"
                }
            }
            _listLoading.value = false
        }
    }

    fun open(id: String) {
        pendingId = id
        viewModelScope.launch {
            _detailLoading.value = true
            _detailError.value = null
            try {
                _detail.value = if (mode == "grammar") api.getGrammarDeck(id) else api.getCourse(id)
            } catch (e: Exception) {
                _detailError.value = e.message ?: "Failed to load detail"
            }
            _detailLoading.value = false
        }
    }

    fun retryDetail() {
        pendingId?.let { open(it) }
    }

    fun backToList() {
        _detail.value = null
        _detailError.value = null
        _detailLoading.value = false
        pendingId = null
    }

    /** One lesson at a time: the server tracks which deck is current. */
    fun loadCurrent() {
        viewModelScope.launch {
            if (_current.value == null && _progress.value == null) {
                _currentLoading.value = true
            }
            _currentError.value = null
            try {
                val res = if (track == "grammar") api.getCurrentGrammar() else api.getCurrentCourse()
                _current.value = res.lesson
                _progress.value = res.progress
                LingoCache.put("current_$mode", res)
            } catch (e: Exception) {
                Log.e("Courses", "Failed to fetch current lesson", e)
                if (_current.value == null && _progress.value == null) {
                    _currentError.value = e.message ?: "Failed to load lesson"
                }
            }
            _currentLoading.value = false
        }
    }

    /** Called after a lesson/final test passes: progression may move. */
    fun onTestPassed() {
        LingoCache.invalidate("current_$mode", cacheKey)
        loadCurrent()
        reload()
    }

    // --- Pronunciation: TTS per card, same guarded pattern as Learn ---
    private val _speakingKey = MutableStateFlow<String?>(null)
    val speakingKey = _speakingKey.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var playToken = 0

    fun playCard(text: String, key: String = text) {
        if (text.isBlank()) return
        stopAudio()
        _speakingKey.value = key
        val token = ++playToken
        viewModelScope.launch {
            var currentPlayer: MediaPlayer? = null
            try {
                val url = api.getTts(text).audioUrl
                currentPlayer = MediaPlayer().apply {
                    setDataSource(url)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    prepareAsync()
                    setOnPreparedListener { player ->
                        if (token != playToken) {
                            try { player?.release() } catch (e: Exception) {
                                Log.e("Courses", "Error releasing stale player", e) }
                            return@setOnPreparedListener
                        }
                        try { player?.start() } catch (e: Exception) {
                            Log.e("Courses", "Failed to start card audio", e)
                            _speakingKey.value = null
                        }
                    }
                    setOnCompletionListener { mp ->
                        try { mp?.release() } catch (e: Exception) {
                            Log.e("Courses", "Error releasing card player", e) }
                        if (mediaPlayer === mp) {
                            mediaPlayer = null
                        }
                        if (token == playToken) _speakingKey.value = null
                    }
                    setOnErrorListener { mp, what, extra ->
                        Log.e("Courses", "Card audio error: what=$what, extra=$extra")
                        try { mp?.release() } catch (e: Exception) {
                            Log.e("Courses", "Error releasing card player", e) }
                        if (mediaPlayer === mp) {
                            mediaPlayer = null
                        }
                        if (token == playToken) _speakingKey.value = null
                        true
                    }
                }
                if (token != playToken) {
                    try { currentPlayer?.release() } catch (e: Exception) {
                        Log.e("Courses", "Error releasing superseded player", e) }
                    return@launch
                }
                mediaPlayer = currentPlayer
            } catch (e: Exception) {
                Log.e("Courses", "Card audio playback error", e)
                try { currentPlayer?.release() } catch (re: Exception) {
                    Log.e("Courses", "Error releasing card player", re) }
                mediaPlayer = null
                if (token == playToken) _speakingKey.value = null
            }
        }
    }

    fun stopAudio() {
        playToken++
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.e("Courses", "Error stopping card audio", e)
        } finally {
            mediaPlayer = null
            _speakingKey.value = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAudio()
    }
}

@Composable
fun CoursesScreen(
    vm: CoursesViewModel,
    title: String,
    onBack: () -> Unit,
    onNavigateToTest: (String) -> Unit = {},
    onNavigateToFinal: () -> Unit = {}
) {
    val current by vm.current.collectAsState()
    val progress by vm.progress.collectAsState()
    val currentLoading by vm.currentLoading.collectAsState()
    val currentError by vm.currentError.collectAsState()
    val speakingKey by vm.speakingKey.collectAsState()

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(Modifier.height(12.dp))
        when {
            currentLoading && current == null && progress == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            currentError != null && current == null && progress == null -> {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Error: $currentError", textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { vm.loadCurrent() }) { Text("Retry") }
                }
            }
            progress?.final_unlocked == true -> {
                val p = progress!!
                Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("All ${p.lessons_total} decks cleared! ✨", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            "Final Grammar Test: random questions from every deck · 100% to level up 🎯",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = onNavigateToFinal,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) { Text("Take ${p.level} Final Grammar Test 🏆") }
                    }
                }
            }
            current != null -> {
                val d = current!!
                val num = progress?.current_lesson ?: 1
                val total = progress?.lessons_total ?: 0
                if (currentLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    if (total > 0) "Deck $num of $total · ${d.title}" else d.title,
                    fontWeight = FontWeight.Bold, fontSize = 16.sp
                )
                Text(
                    "${d.level} · ${d.cards.size} patterns — study, then test ✍️",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    items(d.cards, key = { "${it.front}||${it.back}" }) { c ->
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
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    IconButton(onClick = {
                                        vm.playCard(c.reading.ifBlank { c.front }, key = c.front)
                                    }) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Hear pronunciation")
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onNavigateToTest(d.id) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("Take Test $num ✍️ (100% to advance)", fontWeight = FontWeight.ExtraBold) }
            }
            else -> {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("📚", fontSize = 40.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("No decks yet — check back soon!", textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { vm.loadCurrent() }) { Text("Reload") }
                }
            }
        }
    }
}
