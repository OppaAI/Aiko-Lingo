package com.aiko.lingo.ui.leaderboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiko.lingo.data.model.LeaderboardResponse
import com.aiko.lingo.data.remote.AikoApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LeaderboardViewModel(private val apiService: AikoApiService) : ViewModel() {

    private val _uiState = MutableStateFlow<LeaderboardUiState>(LeaderboardUiState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        fetchLeaderboard()
    }

    fun fetchLeaderboard() {
        viewModelScope.launch {
            _uiState.value = LeaderboardUiState.Loading
            try {
                val leaderboard = apiService.getLeaderboard()
                _uiState.value = LeaderboardUiState.Success(leaderboard)
            } catch (e: Exception) {
                Log.e("Leaderboard", "Failed to fetch leaderboard", e)
                _uiState.value = LeaderboardUiState.Error(e.message ?: "Failed to load leaderboard")
            }
        }
    }
}

sealed class LeaderboardUiState {
    object Loading : LeaderboardUiState()
    data class Success(val leaderboard: LeaderboardResponse) : LeaderboardUiState()
    data class Error(val message: String) : LeaderboardUiState()
}
