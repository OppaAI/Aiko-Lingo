package com.aiko.lingo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aiko.lingo.data.remote.AikoApiService
import com.aiko.lingo.ui.conversation.ConversationViewModel
import com.aiko.lingo.ui.translate.TranslateViewModel

class ViewModelFactory(private val apiService: AikoApiService) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(TranslateViewModel::class.java) -> {
                TranslateViewModel(apiService) as T
            }
            modelClass.isAssignableFrom(ConversationViewModel::class.java) -> {
                ConversationViewModel(apiService) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
