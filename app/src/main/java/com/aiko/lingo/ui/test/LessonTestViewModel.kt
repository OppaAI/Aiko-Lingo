package com.aiko.lingo.ui.test

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiko.lingo.data.StudyTracker
import com.aiko.lingo.data.remote.AikoApiService
import com.aiko.lingo.data.remote.TestAnswerDto
import com.aiko.lingo.data.remote.TestQuestionDto
import com.aiko.lingo.data.remote.TestSubmitRequest
import com.aiko.lingo.data.remote.TestSubmitResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Typing-test flow shared by the Vocab and Grammar tracks.
 *
 * Each question shows the English meaning; the learner types the Japanese.
 * Questions arrive pre-shuffled from the server and cover the WHOLE lesson
 * (or up to 100 sampled cards for a level final). 100% correct passes.
 */
class LessonTestViewModel(
    private val api: AikoApiService,
    val track: String // "vocab" | "grammar"
) : ViewModel() {

    private val _uiState = MutableStateFlow<LessonTestUiState>(LessonTestUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _submitError = MutableStateFlow<String?>(null)
    val submitError = _submitError.asStateFlow()

    fun dismissSubmitError() {
        _submitError.value = null
    }

    private var deckId: String? = null
    private var isFinal: Boolean = false

    fun loadLessonTest(id: String) {
        deckId = id
        isFinal = false
        viewModelScope.launch {
            _uiState.value = LessonTestUiState.Loading
            try {
                val res = if (track == "grammar") api.getGrammarTest(id) else api.getCourseTest(id)
                if (res.questions.isEmpty()) {
                    _uiState.value = LessonTestUiState.Error("This lesson has no testable cards yet.")
                } else {
                    _uiState.value = LessonTestUiState.Testing(
                        questions = res.questions,
                        title = res.title.ifBlank { "Lesson Test" },
                        isFinal = false
                    )
                }
            } catch (e: Exception) {
                Log.e("LessonTest", "Failed to load test", e)
                _uiState.value = LessonTestUiState.Error(e.message ?: "Failed to load test")
            }
        }
    }

    fun loadFinalTest() {
        deckId = null
        isFinal = true
        viewModelScope.launch {
            _uiState.value = LessonTestUiState.Loading
            try {
                val res = if (track == "grammar") api.getGrammarFinalTest() else api.getCourseFinalTest()
                if (res.questions.isEmpty()) {
                    _uiState.value = LessonTestUiState.Error("No cards available for the final yet.")
                } else {
                    _uiState.value = LessonTestUiState.Testing(
                        questions = res.questions,
                        title = "Final Test",
                        isFinal = true
                    )
                }
            } catch (e: Exception) {
                Log.e("LessonTest", "Failed to load final", e)
                _uiState.value = LessonTestUiState.Error(e.message ?: "Failed to load final")
            }
        }
    }

    fun submit(answers: Map<String, String>) {
        val current = _uiState.value as? LessonTestUiState.Testing ?: return
        viewModelScope.launch {
            _uiState.value = current.copy(submitting = true)
            try {
                val body = TestSubmitRequest(
                    answers = current.questions.map {
                        TestAnswerDto(qid = it.qid, answer = answers[it.qid].orEmpty())
                    }
                )
                val res = when {
                    isFinal && track == "grammar" -> api.submitGrammarFinal(body)
                    isFinal -> api.submitCourseFinal(body)
                    track == "grammar" -> api.submitGrammarTest(deckId.orEmpty(), body)
                    else -> api.submitCourseTest(deckId.orEmpty(), body)
                }
                _uiState.value = LessonTestUiState.Result(res, isFinal)
                StudyTracker.markStudied()
            } catch (e: Exception) {
                Log.e("LessonTest", "Submit failed", e)
                // Keep the questions on screen so answers aren't lost.
                _uiState.value = current.copy(submitting = false)
                _submitError.value = e.message ?: "Submit failed"
            }
        }
    }

    fun retry() {
        if (isFinal) loadFinalTest() else deckId?.let { loadLessonTest(it) }
    }
}

sealed class LessonTestUiState {
    object Loading : LessonTestUiState()
    data class Testing(
        val questions: List<TestQuestionDto>,
        val title: String,
        val isFinal: Boolean,
        val submitting: Boolean = false
    ) : LessonTestUiState()
    data class Result(val response: TestSubmitResponse, val isFinal: Boolean) : LessonTestUiState()
    data class Error(val message: String) : LessonTestUiState()
}
