package com.aiko.lingo.ui.review

/*
=====================================================================
BUGFIX PASS (this version):
  1. startReviewSession() was called from init{} but was private, so
     if it failed there was no way to retry short of navigating away
     and back (which recreates the ViewModel). Added a public
     retryLoadCards() the screen's Error state can call directly.
=====================================================================
*/

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiko.lingo.data.model.ReviewCard
import com.aiko.lingo.data.model.ReviewResponseRequest
import com.aiko.lingo.data.remote.AikoApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReviewViewModel(private val apiService: AikoApiService) : ViewModel() {

    private val _uiState = MutableStateFlow<ReviewUiState>(ReviewUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _currentCard = MutableStateFlow<ReviewCard?>(null)
    val currentCard = _currentCard.asStateFlow()

    private val _cardsDue = MutableStateFlow(0)
    val cardsDue = _cardsDue.asStateFlow()

    private val _reviewsCompleted = MutableStateFlow(0)
    val reviewsCompleted = _reviewsCompleted.asStateFlow()

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
                        grade = grade
                    )
                )
                _reviewsCompleted.value += 1

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

    fun finishReview() {
        _uiState.value = ReviewUiState.Finished(_cardsDue.value)
    }
}

sealed class ReviewUiState {
    object Loading : ReviewUiState()
    object Reviewing : ReviewUiState()
    data class Finished(val cardsRemaining: Int) : ReviewUiState()
    data class Error(val message: String) : ReviewUiState()
}
