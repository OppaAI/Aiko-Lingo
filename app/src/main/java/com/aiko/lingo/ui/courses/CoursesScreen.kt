package com.aiko.lingo.ui.courses

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiko.lingo.data.remote.AikoApiService
import com.aiko.lingo.data.remote.CourseDetail
import com.aiko.lingo.data.remote.CourseMeta
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CoursesViewModel(private val api: AikoApiService, private val mode: String) : ViewModel() {
    private val _list = MutableStateFlow<List<CourseMeta>>(emptyList())
    val list = _list.asStateFlow()
    private val _detail = MutableStateFlow<CourseDetail?>(null)
    val detail = _detail.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                _list.value = if (mode == "grammar") api.getGrammarDecks() else api.getCourses()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load"
            }
            _loading.value = false
        }
    }

    fun open(id: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                _detail.value = if (mode == "grammar") api.getGrammarDeck(id) else api.getCourse(id)
            } catch (e: Exception) {
                _error.value = e.message
            }
            _loading.value = false
        }
    }

    fun backToList() { _detail.value = null }
}

@Composable
fun CoursesScreen(vm: CoursesViewModel, title: String, onBack: () -> Unit) {
    val list by vm.list.collectAsState()
    val detail by vm.detail.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()

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
        when {
            loading && detail == null && list.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null && detail == null -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("Error: $error", textAlign = TextAlign.Center)
                Button(onClick = { vm.reload() }) { Text("Retry") }
            }
            detail != null -> {
                val d = detail!!
                Text(d.title, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("${d.level} · ${d.cards.size} cards", color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(d.cards) { c ->
                        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(c.front, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                if (c.reading.isNotBlank()) Text(c.reading, fontSize = 14.sp)
                                Text(c.back, fontSize = 16.sp)
                                if (c.note.isNotBlank()) Text(c.note, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(list, key = { it.id }) { deck ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { vm.open(deck.id) },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Text(deck.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("${deck.level} · ${deck.card_count} cards", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
