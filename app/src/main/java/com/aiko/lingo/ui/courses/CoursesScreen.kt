package com.aiko.lingo.ui.courses

import androidx.compose.foundation.clickable
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

    init {
        // Cache-first: instant list on revisit, refresh in background.
        LingoCache.get<List<CourseMeta>>(cacheKey, 600_000)?.let {
            _list.value = it
            _listLoading.value = false
        }
        reload()
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
                    setOnCompletionListener {
                        try { release() } catch (e: Exception) {
                            Log.e("Courses", "Error releasing card player", e) }
                        mediaPlayer = null
                        if (token == playToken) _speakingKey.value = null
                    }
                    setOnErrorListener { mp, what, extra ->
                        Log.e("Courses", "Card audio error: what=$what, extra=$extra")
                        try { mp?.release() } catch (e: Exception) {
                            Log.e("Courses", "Error releasing card player", e) }
                        mediaPlayer = null
                        if (token == playToken) _speakingKey.value = null
                        true
                    }
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
fun CoursesScreen(vm: CoursesViewModel, title: String, onBack: () -> Unit) {
    val list by vm.list.collectAsState()
    val detail by vm.detail.collectAsState()
    val listLoading by vm.listLoading.collectAsState()
    val detailLoading by vm.detailLoading.collectAsState()
    val listError by vm.listError.collectAsState()
    val detailError by vm.detailError.collectAsState()
    val speakingKey by vm.speakingKey.collectAsState()

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                if (detail != null) vm.backToList() else onBack()
            }) { Text("← Back") }
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(Modifier.height(12.dp))
        if (detailLoading && detail == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
        }
        when {
            listLoading && list.isEmpty() && detail == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            listError != null && list.isEmpty() && detail == null -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("Error: $listError", textAlign = TextAlign.Center)
                Button(onClick = { vm.reload() }) { Text("Retry") }
            }
            detail != null -> {
                val d = detail!!
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
                            Text("Couldn't refresh detail: $detailError")
                            TextButton(onClick = { vm.retryDetail() }) { Text("Retry detail") }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Text(d.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("${d.level} · ${d.cards.size} cards", color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(d.cards) { c ->
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
            }
            detail == null && detailLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            detail == null && detailError != null -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("Error: $detailError", textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.retryDetail() }) { Text("Retry") }
                    OutlinedButton(onClick = { vm.backToList() }) { Text("Back to list") }
                }
            }
            !listLoading && list.isEmpty() && detail == null -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("📚", fontSize = 40.sp)
                Spacer(Modifier.height(8.dp))
                Text("No decks yet — check back soon!", textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { vm.reload() }) { Text("Reload") }
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(list, key = { it.id }) { deck ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { vm.open(deck.id) },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(deck.title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("${deck.level} · ${deck.card_count} cards", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
