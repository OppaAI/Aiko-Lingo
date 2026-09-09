package com.aiko.lingo.ui.leaderboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiko.lingo.data.model.LeaderboardResponse
import com.aiko.lingo.data.remote.AikoApiService
import com.aiko.lingo.data.remote.LingoCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LeaderboardViewModel(private val apiService: AikoApiService) : ViewModel() {

    private val _uiState = MutableStateFlow<LeaderboardUiState>(LeaderboardUiState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        LingoCache.get<LeaderboardResponse>("leaderboard", 60_000)?.let {
            _uiState.value = LeaderboardUiState.Success(it)
        }
        fetchLeaderboard()
    }

    fun fetchLeaderboard() {
        viewModelScope.launch {
            if (_uiState.value !is LeaderboardUiState.Success) {
                _uiState.value = LeaderboardUiState.Loading
            }
            try {
                val leaderboard = apiService.getLeaderboard()
                LingoCache.put("leaderboard", leaderboard)
                _uiState.value = LeaderboardUiState.Success(leaderboard)
            } catch (e: Exception) {
                Log.e("Leaderboard", "Failed to fetch leaderboard", e)
                if (_uiState.value !is LeaderboardUiState.Success) {
                    _uiState.value = LeaderboardUiState.Error(e.message ?: "Failed to load leaderboard")
                }
            }
        }
    }
}

sealed class LeaderboardUiState {
    object Loading : LeaderboardUiState()
    data class Success(val leaderboard: LeaderboardResponse) : LeaderboardUiState()
    data class Error(val message: String) : LeaderboardUiState()
}
