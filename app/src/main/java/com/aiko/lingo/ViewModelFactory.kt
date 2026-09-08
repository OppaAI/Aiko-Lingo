package com.aiko.lingo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aiko.lingo.data.remote.AikoApiService
import com.aiko.lingo.ui.conversation.ConversationViewModel
import com.aiko.lingo.ui.translate.TranslateViewModel
import com.aiko.lingo.ui.dashboard.DashboardViewModel
import com.aiko.lingo.ui.review.ReviewViewModel
import com.aiko.lingo.ui.leaderboard.LeaderboardViewModel
import com.aiko.lingo.ui.learn.LearnViewModel

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
            modelClass.isAssignableFrom(DashboardViewModel::class.java) -> {
                DashboardViewModel(apiService) as T
            }
            modelClass.isAssignableFrom(ReviewViewModel::class.java) -> {
                ReviewViewModel(apiService) as T
            }
            modelClass.isAssignableFrom(LeaderboardViewModel::class.java) -> {
                LeaderboardViewModel(apiService) as T
            }
            modelClass.isAssignableFrom(LearnViewModel::class.java) -> {
                LearnViewModel(apiService) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
