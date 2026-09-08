package com.aiko.lingo.ui.review

/*
=====================================================================
BUGFIX PASS (this version):
  1. startReviewSession() was called from init{} but was private, so
     if it failed there was no way to retry short of navigating away
     and back (which recreates the ViewModel). Added a public
     retryLoadCards() the screen's Error state can call directly.

BUGFIX PASS (this version, cont. -- audit fix #5):
  2. ReviewResponseData.toast (e.g. "🌟 word = meaning" on an Easy
     grade) was already modeled but never read. It's now surfaced
     through a toastMessage StateFlow so ReviewScreen can display it.

BUGFIX PASS (this version, cont. -- audit fix):
  3. The backend returns HTTP 400 "No cards due for review" when the
     queue is empty -- a normal, expected state, not a failure. It was
     previously caught by the generic `catch (e: Exception)` and shown
     as a red Error screen with a Retry button that just re-hit the
     same empty queue and errored again, forever. An HTTP 400 from
     startReviewSession() is now treated as ReviewUiState.Finished(0),
     the same "you're all caught up" state a user reaches after
     clearing their last due card.
=====================================================================
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

    private val _uiState = MutableStateFlow<ReviewUiState>(ReviewUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _currentCard = MutableStateFlow<ReviewCard?>(null)
    val currentCard = _currentCard.asStateFlow()

    private val _cardsDue = MutableStateFlow(0)
    val cardsDue = _cardsDue.asStateFlow()

    private val _reviewsCompleted = MutableStateFlow(0)
    val reviewsCompleted = _reviewsCompleted.asStateFlow()

    // FIX #2: surfaces the toast the backend already sends on review
    // responses (e.g. an Easy-grade "mastered" celebration).
    private val _toastMessage = MutableStateFlow<Toast?>(null)
    val toastMessage = _toastMessage.asStateFlow()

    fun dismissToast() {
        _toastMessage.value = null
    }

    init {
        startReviewSession()
    }

    private fun startReviewSession() {
        viewModelScope.launch {
            _uiState.value = ReviewUiState.Loading
            try {
                val response = apiService.startReviewSession()
                _currentCard.value = response.first_card
                _cardsDue.value = response.cards_due
                _uiState.value = ReviewUiState.Reviewing
            } catch (e: HttpException) {
                // FIX #3: an empty due queue (HTTP 400 "No cards due for
                // review") is a normal "you're caught up" state, not an
                // error -- route it to Finished instead of a dead-end
                // Error screen whose Retry button just re-errors forever.
                if (e.code() == 400) {
                    _reviewsCompleted.value = 0
                    _cardsDue.value = 0
                    _uiState.value = ReviewUiState.Finished(0)
                } else {
                    Log.e("Review", "Failed to start review session", e)
                    _uiState.value = ReviewUiState.Error(e.message() ?: "Failed to start review")
                }
            } catch (e: Exception) {
                Log.e("Review", "Failed to start review session", e)
                _uiState.value = ReviewUiState.Error(e.message ?: "Failed to start review")
            }
        }
    }

    // FIX: public retry hook so the screen can recover from a failed
    // initial load without recreating the whole ViewModel.
    fun retryLoadCards() {
        startReviewSession()
    }

    fun submitReview(cardId: Int, response: String, grade: Int) {
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

                // FIX #2: wire the toast through instead of ignoring it.
                result.toast?.let { _toastMessage.value = it }

                if (result.next_card != null) {
                    _currentCard.value = result.next_card
                    _cardsDue.value = result.cards_remaining
                } else {
                    _uiState.value = ReviewUiState.Finished(result.cards_remaining)
                }
            } catch (e: Exception) {
                Log.e("Review", "Failed to submit review", e)
                _uiState.value = ReviewUiState.Error(e.message ?: "Failed to submit review")
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
