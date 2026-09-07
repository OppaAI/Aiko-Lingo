package com.aiko.lingo.ui.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiko.lingo.data.model.StatsResponse
import com.aiko.lingo.data.remote.AikoApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DashboardViewModel(private val apiService: AikoApiService) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        refreshStats()
    }

    fun refreshStats() {
        viewModelScope.launch {
            _uiState.value = DashboardUiState.Loading
            try {
                val stats = apiService.getStats()
                _uiState.value = DashboardUiState.Success(stats)
            } catch (e: Exception) {
                Log.e("Dashboard", "Failed to fetch stats", e)
                _uiState.value = DashboardUiState.Error(e.message ?: "Failed to load stats")
            }
        }
    }
}

sealed class DashboardUiState {
    object Loading : DashboardUiState()
    data class Success(val stats: StatsResponse) : DashboardUiState()
    data class Error(val message: String) : DashboardUiState()
}
