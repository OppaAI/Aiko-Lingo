package com.aiko.lingo.ui.translate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiko.lingo.data.model.TranslateRequest
import com.aiko.lingo.data.model.TranslationResult
import com.aiko.lingo.data.remote.AikoApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TranslateViewModel(private val apiService: AikoApiService) : ViewModel() {

    private val _uiState = MutableStateFlow<TranslateUiState>(TranslateUiState.Idle)
    val uiState = _uiState.asStateFlow()

    fun translate(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            _uiState.value = TranslateUiState.Loading
            try {
                val response = apiService.translate(TranslateRequest(text))
                _uiState.value = TranslateUiState.Success(response.translations)
            } catch (e: Exception) {
                _uiState.value = TranslateUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

sealed class TranslateUiState {
    object Idle : TranslateUiState()
    object Loading : TranslateUiState()
    data class Success(val translations: List<TranslationResult>) : TranslateUiState()
    data class Error(val message: String) : TranslateUiState()
}
