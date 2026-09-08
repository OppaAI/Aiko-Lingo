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
     it hits the `api/nihongo/weak-vocab` endpoint to fetch the cards
     the user is failing most often, instead of the SRS due queue.
  5. Added `choices` StateFlow to support Multiple Choice questions,
     a staple Duolingo feature. It automatically generates 3
     distractors from other cards in the current session.

SRS CHOICE FIX (this version):
  6. In SRS mode, we often only have one card at a time. Fixed
     generateChoices to fetch a fallback pool of weak vocabulary
     to use as distractors if the current session pool is empty.
  7. Added a hardcoded fallback list of common Japanese meanings to
     ensure 4 choices are ALWAYS present even for brand-new users.

LEARN / REVIEW SPLIT:
  8. Removed ReviewMode.LEARN — learning new vocab is the Learn screen
     (lesson decks + pregen pool). Review is only SRS + weak practice.
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
    SRS, // Spaced-repetition of words you have already learnt
    PRACTICE, // Targeted practice for weak vocabulary
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
    private var distractorPool = listOf<String>()
    private var currentMode = ReviewMode.SRS

    private val FALLBACK_MEANINGS = listOf(
        "to go", "to come", "to see", "to drink", "to eat",
        "friend", "family", "school", "water", "apple",
        "good", "bad", "happy", "sad", "big", "small",
        "How are you?", "Good morning", "Thank you", "Excuse me",
        "Nice to meet you", "See you later", "I hope so", "That's right"
    )

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
            
            // Prefetch a pool of potential distractors in the background
            launch {
                try {
                    val weak = apiService.getWeakVocab()
                    distractorPool = weak.map { it.meaning }
                } catch (e: Exception) {
                    Log.w("Review", "Failed to fetch distractor pool", e)
                }
            }

            try {
                when (currentMode) {
                    ReviewMode.PRACTICE -> {
                        Log.d("Review", "Loading cards for mode: $currentMode")
                        val cards = apiService.getWeakVocab()

                        Log.d("Review", "Final card count for $currentMode: ${cards.size}")

                        if (cards.isEmpty()) {
                            _uiState.value = ReviewUiState.Finished(0)
                        } else {
                            allCardsInSession = cards
                            _currentCard.value = cards.first()
                            _cardsDue.value = cards.size
                            _uiState.value = ReviewUiState.Reviewing
                            generateChoices(cards.first())
                        }
                    }
                    ReviewMode.SRS -> {
                        Log.d("Review", "Loading SRS cards")
                        val response = apiService.startReviewSession()
                        Log.d("Review", "SRS session started: ${response.cards_due} cards, first: ${response.first_card.hiragana}")
                        _currentCard.value = response.first_card
                        _cardsDue.value = response.cards_due
                        _uiState.value = ReviewUiState.Reviewing
                        generateChoices(response.first_card)
                    }
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
        val sessionDistractors = allCardsInSession
            .filter { it.card_id != correctCard.card_id }
            .map { it.meaning }
            
        val poolDistractors = distractorPool
            .filter { it != correctCard.meaning && it !in sessionDistractors }
            
        var combinedDistractors = (sessionDistractors + poolDistractors)
            .distinct()
            .shuffled()
            .take(3)
        
        // Final fallback distractors if we still don't have 3
        if (combinedDistractors.size < 3) {
            val needed = 3 - combinedDistractors.size
            val extraDistractors = FALLBACK_MEANINGS
                .filter { it != correctCard.meaning && it !in combinedDistractors }
                .shuffled()
                .take(needed)
            combinedDistractors = combinedDistractors + extraDistractors
        }

        val finalChoices = (combinedDistractors + correctCard.meaning).shuffled()
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
