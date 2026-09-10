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
    suspend fun stopConversation(): StopResponse

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

    // Lesson-at-a-time progression: one current lesson, per-lesson typing
    // test, and a level final. Same shape for vocab (courses) and grammar.
    @GET("api/nihongo/courses/current")
    suspend fun getCurrentCourse(): CurrentLessonResponse

    @GET("api/nihongo/courses/{courseId}/test")
    suspend fun getCourseTest(@Path("courseId") courseId: String): TestQuestionsResponse

    @POST("api/nihongo/courses/{courseId}/test/submit")
    suspend fun submitCourseTest(
        @Path("courseId") courseId: String,
        @Body body: TestSubmitRequest
    ): TestSubmitResponse

    @GET("api/nihongo/courses/final/test")
    suspend fun getCourseFinalTest(@Query("n") n: Int = 100): TestQuestionsResponse

    @POST("api/nihongo/courses/final/submit")
    suspend fun submitCourseFinal(@Body body: TestSubmitRequest): TestSubmitResponse

    @GET("api/nihongo/grammar/current")
    suspend fun getCurrentGrammar(): CurrentLessonResponse

    @GET("api/nihongo/grammar/{grammarId}/test")
    suspend fun getGrammarTest(@Path("grammarId") grammarId: String): TestQuestionsResponse

    @POST("api/nihongo/grammar/{grammarId}/test/submit")
    suspend fun submitGrammarTest(
        @Path("grammarId") grammarId: String,
        @Body body: TestSubmitRequest
    ): TestSubmitResponse

    @GET("api/nihongo/grammar/final/test")
    suspend fun getGrammarFinalTest(@Query("n") n: Int = 100): TestQuestionsResponse

    @POST("api/nihongo/grammar/final/submit")
    suspend fun submitGrammarFinal(@Body body: TestSubmitRequest): TestSubmitResponse
}

@Serializable
data class StopResponse(val success: Boolean = false)

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

// --- Lesson / test progression ------------------------------------------------
// Backend `lesson` payloads reuse CourseDetail (extra lesson/lessons_total keys
// are ignored); `lesson` is null once the level final is unlocked.
@Serializable
data class LessonProgressDto(
    val level: String = "N5",
    val track: String = "vocab",
    val current_lesson: Int = 1,
    val lessons_total: Int = 0,
    val final_unlocked: Boolean = false
)

@Serializable
data class CurrentLessonResponse(
    val progress: LessonProgressDto = LessonProgressDto(),
    val lesson: CourseDetail? = null
)

@Serializable
data class TestQuestionDto(
    val qid: String = "",
    val prompt: String = "",
    val hint: String = ""
)

@Serializable
data class TestQuestionsResponse(
    val deck_id: String = "",
    val title: String = "",
    val level: String = "",
    val questions: List<TestQuestionDto> = emptyList(),
    val total_at_level: Int = 0
)

@Serializable
data class TestAnswerDto(val qid: String, val answer: String = "")

@Serializable
data class TestSubmitRequest(val answers: List<TestAnswerDto> = emptyList())

@Serializable
data class TestItemResultDto(
    val qid: String = "",
    val prompt: String = "",
    val expected: List<String> = emptyList(),
    val given: String = "",
    val correct: Boolean = false
)

@Serializable
data class TestSubmitResponse(
    val correct: Int = 0,
    val total: Int = 0,
    val passed: Boolean = false,
    val xp: Int = 0,
    val results: List<TestItemResultDto> = emptyList(),
    val progress: LessonProgressDto? = null,
    val new_level: String? = null
)
