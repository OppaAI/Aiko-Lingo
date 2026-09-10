package com.aiko.lingo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aiko.lingo.data.remote.AikoApiService
import com.aiko.lingo.ui.conversation.ConversationViewModel
import com.aiko.lingo.ui.translate.TranslateViewModel
import com.aiko.lingo.ui.dashboard.DashboardViewModel
import com.aiko.lingo.ui.review.ReviewViewModel
import com.aiko.lingo.ui.leaderboard.LeaderboardViewModel
import com.aiko.lingo.ui.vocab.VocabViewModel
import com.aiko.lingo.ui.practice.PracticeViewModel
import com.aiko.lingo.ui.courses.CoursesViewModel
import com.aiko.lingo.ui.test.LessonTestViewModel

class ViewModelFactory(
    private val apiService: AikoApiService,
    private val streamingApi: AikoApiService = apiService,
    private val coursesMode: String = "courses",
    private val testTrack: String = "vocab",
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(TranslateViewModel::class.java) ->
                TranslateViewModel(apiService) as T
            modelClass.isAssignableFrom(ConversationViewModel::class.java) ->
                ConversationViewModel(apiService, streamingApi) as T
            modelClass.isAssignableFrom(DashboardViewModel::class.java) ->
                DashboardViewModel(apiService) as T
            modelClass.isAssignableFrom(ReviewViewModel::class.java) ->
                ReviewViewModel(apiService) as T
            modelClass.isAssignableFrom(LeaderboardViewModel::class.java) ->
                LeaderboardViewModel(apiService) as T
            modelClass.isAssignableFrom(VocabViewModel::class.java) ->
                VocabViewModel(apiService) as T
            modelClass.isAssignableFrom(PracticeViewModel::class.java) ->
                PracticeViewModel(apiService) as T
            modelClass.isAssignableFrom(CoursesViewModel::class.java) ->
                CoursesViewModel(apiService, coursesMode) as T
            modelClass.isAssignableFrom(LessonTestViewModel::class.java) ->
                LessonTestViewModel(apiService, testTrack) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
