package com.aiko.lingo.data.remote

import com.aiko.lingo.data.model.*
import retrofit2.http.*
import kotlinx.serialization.Serializable
import okhttp3.ResponseBody

/*
=====================================================================
BUGFIX PASS (this version):
  1. Removed respondToConversation() (POST api/nihongo/conversation/respond).
     Per audit: the backend only ever implemented the streaming variant
     (respond_stream), so this was a dead interface method pointing at a
     404. The frontend never called it -- ConversationViewModel always
     uses respondToConversationStream().
     NOTE: the backend HAS since implemented POST
     api/nihongo/conversation/respond (non-streaming, same
     post-processing as the stream: SRS insert, audio, XP, toast), so the
     method is re-exposed below for fast-retry / low-bandwidth callers.
=====================================================================
*/

interface AikoApiService {
    // ========== Translation ==========
    @POST("api/nihongo/translate")
    suspend fun translate(@Body request: TranslateRequest): TranslateResponse

    // ========== Conversation ==========
    @Streaming
    @POST("api/nihongo/conversation/start")
    suspend fun startConversationStream(@Body request: ConversationStartRequest): ResponseBody

    @Streaming
    @POST("api/nihongo/conversation/respond_stream")
    suspend fun respondToConversationStream(@Body request: ConversationRespondRequest): ResponseBody

    // Non-streaming counterpart (backend shares post-processing with the
    // stream: vocab/SRS insert, audioUrl, streak+XP, toast). Used for
    // fast retry when the stream fails mid-turn.
    @POST("api/nihongo/conversation/respond")
    suspend fun respondToConversation(@Body request: ConversationRespondRequest): ConversationResponse

    @POST("api/nihongo/conversation/hint")
    suspend fun getHint(): ConversationHintResponse

    @POST("api/nihongo/conversation/stop")
    suspend fun stopConversation(): Unit

    // ========== TTS ==========
    @GET("api/nihongo/tts")
    suspend fun getTts(@Query("text") text: String): TtsResponse

    // ========== Stats & XP ==========
    @GET("api/nihongo/stats")
    suspend fun getStats(): StatsResponse

    @GET("api/nihongo/xp")
    suspend fun getXpData(): XPResponse

    @POST("api/nihongo/xp/add")
    suspend fun addXp(@Body request: XPAddRequest): XPResponse

    // NEW: dedicated weak-vocab endpoint (also embedded in StatsResponse.weak_vocab,
    // but exposed standalone so screens that don't need full stats can fetch just this).
    @GET("api/nihongo/weak-vocab")
    suspend fun getWeakVocab(): List<ReviewCard>

    // ========== Learn (lesson decks) ==========
    @GET("api/nihongo/lessons")
    suspend fun getLessons(): List<LessonDeckMeta>

    @GET("api/nihongo/lessons/{deckId}")
    suspend fun getLesson(@Path("deckId") deckId: String): LessonDeck

    // ========== Word of the Day ==========
    @GET("api/nihongo/word-of-day")
    suspend fun getWordOfDay(): WordOfDayResponse

    // ========== Leaderboard ==========
    @GET("api/nihongo/leaderboard")
    suspend fun getLeaderboard(): LeaderboardResponse

    // ========== SRS Review ==========
    @POST("api/nihongo/conversation/review/start")
    suspend fun startReviewSession(): ReviewSessionResponse

    @POST("api/nihongo/conversation/review/respond")
    suspend fun respondToReview(@Body request: ReviewResponseRequest): ReviewResponseData
}

@Serializable
data class TtsResponse(val audioUrl: String)
