package com.aiko.lingo.data.remote

import com.aiko.lingo.data.model.*
import retrofit2.http.*
import kotlinx.serialization.Serializable
import okhttp3.ResponseBody

interface AikoApiService {
    @POST("api/nihongo/translate")
    suspend fun translate(@Body request: TranslateRequest): TranslateResponse

    @Streaming
    @POST("api/nihongo/conversation/start")
    suspend fun startConversationStream(@Body request: ConversationStartRequest): ResponseBody

    @Streaming
    @POST("api/nihongo/conversation/respond_stream")
    suspend fun respondToConversationStream(@Body request: ConversationRespondRequest): ResponseBody

    @POST("api/nihongo/conversation/respond")
    suspend fun respondToConversation(@Body request: ConversationRespondRequest): ConversationResponse

    @POST("api/nihongo/conversation/hint")
    suspend fun getHint(): ConversationHintResponse

    @POST("api/nihongo/conversation/stop")
    suspend fun stopConversation(): Unit

    @GET("api/nihongo/tts")
    suspend fun getTts(@Query("text") text: String): TtsResponse

    @GET("api/nihongo/stats")
    suspend fun getStats(): StatsResponse

    @GET("api/nihongo/xp")
    suspend fun getXpData(): XPResponse

    @POST("api/nihongo/xp/add")
    suspend fun addXp(@Body request: XPAddRequest): XPResponse

    @GET("api/nihongo/weak-vocab")
    suspend fun getWeakVocab(): List<ReviewCard>

    @GET("api/nihongo/lessons")
    suspend fun getLessons(): List<LessonDeckMeta>

    @GET("api/nihongo/lessons/{deckId}")
    suspend fun getLesson(@Path("deckId") deckId: String): LessonDeck

    // JLPT Learn (shared pool, level-filtered)
    @GET("api/nihongo/learn/new")
    suspend fun getLearnNew(): LearnSessionResponse

    @POST("api/nihongo/learn/mark")
    suspend fun markLearned(@Body body: MarkLearnedRequest): MarkLearnedResponse

    @GET("api/nihongo/learn/status")
    suspend fun getLearnStatus(): LearnStatusResponse

    @GET("api/nihongo/level")
    suspend fun getLevel(): LevelResponse

    @POST("api/nihongo/level")
    suspend fun setLevel(@Body body: SetLevelRequest): LevelResponse

    // Full courses e.g. N1 Lesson 1
    @GET("api/nihongo/courses")
    suspend fun getCourses(): List<CourseMeta>

    @GET("api/nihongo/courses/{courseId}")
    suspend fun getCourse(@Path("courseId") courseId: String): CourseDetail

    // Grammar review decks
    @GET("api/nihongo/grammar")
    suspend fun getGrammarDecks(): List<CourseMeta>

    @GET("api/nihongo/grammar/{grammarId}")
    suspend fun getGrammarDeck(@Path("grammarId") grammarId: String): CourseDetail

    @GET("api/nihongo/word-of-day")
    suspend fun getWordOfDay(): WordOfDayResponse

    @GET("api/nihongo/leaderboard")
    suspend fun getLeaderboard(): LeaderboardResponse

    @POST("api/nihongo/conversation/review/start")
    suspend fun startReviewSession(): ReviewSessionResponse

    @POST("api/nihongo/conversation/review/respond")
    suspend fun respondToReview(@Body request: ReviewResponseRequest): ReviewResponseData

    // Random 10-card learnt sessions (equal-or-lower JLPT, repeats by chance)
    @GET("api/nihongo/review/session")
    suspend fun getReviewSession(@Query("n") n: Int = 10): List<ReviewCard>

    @GET("api/nihongo/practice/session")
    suspend fun getPracticeSession(@Query("n") n: Int = 10): List<ReviewCard>

    @POST("api/nihongo/practice/mark")
    suspend fun markPractice(@Body body: PracticeMarkRequest): PracticeMarkResponse
}

@Serializable
data class TtsResponse(val audioUrl: String)

@Serializable
data class LearnItemDto(
    val pool_id: Int? = null,
    val front: String,
    val back: String,
    val reading: String = "",
    val kind: String = "kanji",
    val level: String = "N5",
)

@Serializable
data class LearnSessionResponse(
    val items: List<LearnItemDto> = emptyList(),
    val level: String = "N5",
    val pending_in_pool: Int = 0,
)

@Serializable
data class MarkLearnedRequest(val items: List<LearnItemDto> = emptyList())

@Serializable
data class MarkLearnedResponse(val learned: Int = 0, val xp: Int = 0)

@Serializable
data class LearnStatusResponse(
    val level: String = "N5",
    val levels: List<String> = emptyList(),
    val pool_size_at_level: Int = 0,
    val pool_size_total: Int = 0,
    val user_cards: Int = 0,
    val reviews_today: Int = 0,
    val learned_today: Int = 0,
)

@Serializable
data class LevelResponse(val level: String = "N5", val levels: List<String> = emptyList())

@Serializable
data class SetLevelRequest(val level: String)

@Serializable
data class CourseMeta(
    val id: String = "",
    val title: String = "",
    val level: String = "N5",
    val kind: String = "",
    val card_count: Int = 0,
    val lesson: Int? = null,
)

@Serializable
data class CourseCardDto(
    val front: String = "",
    val back: String = "",
    val reading: String = "",
    val note: String = "",
)

@Serializable
data class CourseDetail(
    val id: String = "",
    val title: String = "",
    val level: String = "N5",
    val kind: String = "",
    val cards: List<CourseCardDto> = emptyList(),
)

@Serializable
data class PracticeMarkRequest(val correct: Int, val total: Int)

@Serializable
data class PracticeMarkResponse(val xp: Int = 0, val correct: Int = 0)
