package com.aiko.lingo.ui.review

/*
Review = one session of 10 random learnt cards (equal-or-lower JLPT).
Cards may repeat across sessions by chance — correct answers can show
again later. Each answer is still graded through SM-2 (respondToReview)
so scheduling + XP keep working; the session simply ends after 10.
*/

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiko.lingo.data.model.ReviewCard
import com.aiko.lingo.data.model.ReviewResponseRequest
import com.aiko.lingo.data.model.Toast
import com.aiko.lingo.data.remote.AikoApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

class ReviewViewModel(private val apiService: AikoApiService) : ViewModel() {

    companion object {
        const val SESSION_SIZE = 10
    }

    private val _uiState = MutableStateFlow<ReviewUiState>(ReviewUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _session = MutableStateFlow<List<ReviewCard>>(emptyList())
    val session = _session.asStateFlow()

    private val _index = MutableStateFlow(0)
    val index = _index.asStateFlow()

    private val _currentCard = MutableStateFlow<ReviewCard?>(null)
    val currentCard = _currentCard.asStateFlow()

    private val _cardsDue = MutableStateFlow(SESSION_SIZE)
    val cardsDue = _cardsDue.asStateFlow()

    private val _reviewsCompleted = MutableStateFlow(0)
    val reviewsCompleted = _reviewsCompleted.asStateFlow()

    private val _toastMessage = MutableStateFlow<Toast?>(null)
    val toastMessage = _toastMessage.asStateFlow()

    private val _choices = MutableStateFlow<List<String>>(emptyList())
    val choices = _choices.asStateFlow()

    private val _submitting = MutableStateFlow(false)
    val submitting = _submitting.asStateFlow()

    private val FALLBACK_MEANINGS = listOf(
        "to go", "to come", "to see", "to drink", "to eat",
        "friend", "family", "school", "water", "apple",
        "good", "bad", "happy", "sad", "big", "small",
        "How are you?", "Good morning", "Thank you", "Excuse me",
        "Nice to meet you", "See you later", "I hope so", "That's right"
    )

    init {
        loadSession()
    }

    fun dismissToast() {
        _toastMessage.value = null
    }

    fun loadSession() {
        viewModelScope.launch {
            _uiState.value = ReviewUiState.Loading
            _reviewsCompleted.value = 0
            _index.value = 0
            try {
                val cards = apiService.getReviewSession(SESSION_SIZE)
                if (cards.isEmpty()) {
                    _cardsDue.value = 0
                    _uiState.value = ReviewUiState.Finished(0)
                } else {
                    _session.value = cards
                    _currentCard.value = cards.first()
                    _cardsDue.value = cards.size
                    _uiState.value = ReviewUiState.Reviewing
                    generateChoices(cards.first())
                }
            } catch (e: HttpException) {
                if (e.code() == 400) {
                    _reviewsCompleted.value = 0
                    _cardsDue.value = 0
                    _uiState.value = ReviewUiState.Finished(0)
                } else {
                    Log.e("Review", "Failed to load session", e)
                    _uiState.value = ReviewUiState.Error(e.message() ?: "Failed to load cards")
                }
            } catch (e: Exception) {
                Log.e("Review", "Failed to load session", e)
                _uiState.value = ReviewUiState.Error(e.message ?: "Failed to load cards")
            }
        }
    }

    private fun generateChoices(correctCard: ReviewCard) {
        val sessionDistractors = _session.value
            .filter { it.card_id != correctCard.card_id }
            .map { it.meaning }

        var combinedDistractors = sessionDistractors
            .distinct()
            .shuffled()
            .take(3)

        if (combinedDistractors.size < 3) {
            val needed = 3 - combinedDistractors.size
            val extraDistractors = FALLBACK_MEANINGS
                .filter { it != correctCard.meaning && it !in combinedDistractors }
                .shuffled()
                .take(needed)
            combinedDistractors = combinedDistractors + extraDistractors
        }

        _choices.value = (combinedDistractors + correctCard.meaning).shuffled()
    }

    fun retryLoadCards() {
        loadSession()
    }

    fun submitReview(cardId: Int, response: String, grade: Int) {
        if (_submitting.value) return
        _submitting.value = true
        viewModelScope.launch {
            try {
                val result = apiService.respondToReview(
                    ReviewResponseRequest(
                        card_id = cardId,
                        response = response,
                        grade = grade,
                    )
                )
                _reviewsCompleted.value += 1

                result.toast?.let { _toastMessage.value = it }

                val nextIndex = _index.value + 1
                _index.value = nextIndex
                _cardsDue.value = _session.value.size - nextIndex
                if (nextIndex < _session.value.size) {
                    val next = _session.value[nextIndex]
                    _currentCard.value = next
                    generateChoices(next)
                } else {
                    _uiState.value = ReviewUiState.Finished(result.cards_remaining)
                }
            } catch (e: Exception) {
                Log.e("Review", "Failed to submit review", e)
                // Stay on the card so no progress is lost; the screen shows
                // a toast with a retry instead of a dead-end error screen.
                _toastMessage.value = Toast(
                    type = "error",
                    message = "Couldn't save that answer — tap CONTINUE to retry."
                )
            } finally {
                _submitting.value = false
            }
        }
    }
}

sealed class ReviewUiState {
    object Loading : ReviewUiState()
    object Reviewing : ReviewUiState()
    data class Finished(val cardsRemaining: Int) : ReviewUiState()
    data class Error(val message: String) : ReviewUiState()
}
