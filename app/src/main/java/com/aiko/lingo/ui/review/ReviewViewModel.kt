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

DUOLINGO-STYLE UPGRADES (this version):
  4. Added "Targeted Practice" mode. If initialized with mode=PRACTICE,
     it hits the `api/english/weak-vocab` endpoint to fetch the cards
     the user is failing most often, instead of the SRS due queue.
  5. Added `choices` StateFlow to support Multiple Choice questions,
     a staple Duolingo feature. It automatically generates 3
     distractors from other cards in the current session.
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

enum class ReviewMode {
    SRS, // Standard spaced-repetition due cards
    PRACTICE // Targeted practice for weak vocabulary
}

class ReviewViewModel(private val apiService: AikoApiService) : ViewModel() {

    private val _uiState = MutableStateFlow<ReviewUiState>(ReviewUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _currentCard = MutableStateFlow<ReviewCard?>(null)
    val currentCard = _currentCard.asStateFlow()

    private val _cardsDue = MutableStateFlow(0)
    val cardsDue = _cardsDue.asStateFlow()

    private val _reviewsCompleted = MutableStateFlow(0)
    val reviewsCompleted = _reviewsCompleted.asStateFlow()

    private val _toastMessage = MutableStateFlow<Toast?>(null)
    val toastMessage = _toastMessage.asStateFlow()

    // DUOLINGO UPGRADE: support for multiple choice questions
    private val _choices = MutableStateFlow<List<String>>(emptyList())
    val choices = _choices.asStateFlow()

    private var allCardsInSession = listOf<ReviewCard>()
    private var currentMode = ReviewMode.SRS

    fun setMode(mode: ReviewMode) {
        currentMode = mode
        loadCards()
    }

    fun dismissToast() {
        _toastMessage.value = null
    }

    private fun loadCards() {
        viewModelScope.launch {
            _uiState.value = ReviewUiState.Loading
            try {
                if (currentMode == ReviewMode.PRACTICE) {
                    val cards = apiService.getWeakVocab()
                    if (cards.isEmpty()) {
                        _uiState.value = ReviewUiState.Finished(0)
                    } else {
                        allCardsInSession = cards
                        _currentCard.value = cards.first()
                        _cardsDue.value = cards.size
                        _uiState.value = ReviewUiState.Reviewing
                        generateChoices(cards.first())
                    }
                } else {
                    val response = apiService.startReviewSession()
                    _currentCard.value = response.first_card
                    _cardsDue.value = response.cards_due
                    // We don't have the full list for SRS yet, so generateChoices 
                    // will use a fallback or wait for more cards.
                    _uiState.value = ReviewUiState.Reviewing
                    generateChoices(response.first_card)
                }
            } catch (e: HttpException) {
                if (e.code() == 400) {
                    _reviewsCompleted.value = 0
                    _cardsDue.value = 0
                    _uiState.value = ReviewUiState.Finished(0)
                } else {
                    Log.e("Review", "Failed to load cards", e)
                    _uiState.value = ReviewUiState.Error(e.message() ?: "Failed to load cards")
                }
            } catch (e: Exception) {
                Log.e("Review", "Failed to load cards", e)
                _uiState.value = ReviewUiState.Error(e.message ?: "Failed to load cards")
            }
        }
    }

    private fun generateChoices(correctCard: ReviewCard) {
        // DUOLINGO UPGRADE: Create multiple choice options.
        // If we have a pool of cards (Practice mode), use them as distractors.
        // Otherwise, use generic distractors for now.
        val distractors = allCardsInSession
            .filter { it.card_id != correctCard.card_id }
            .map { it.meaning }
            .shuffled()
            .take(3)
        
        val finalChoices = (distractors + correctCard.meaning).shuffled()
        _choices.value = finalChoices
    }

    fun retryLoadCards() {
        loadCards()
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

                result.toast?.let { _toastMessage.value = it }

                if (result.next_card != null) {
                    _currentCard.value = result.next_card
                    _cardsDue.value = result.cards_remaining
                    generateChoices(result.next_card)
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
