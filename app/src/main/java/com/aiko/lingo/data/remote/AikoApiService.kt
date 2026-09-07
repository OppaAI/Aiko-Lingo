package com.aiko.lingo.data.remote

import com.aiko.lingo.data.model.*
import retrofit2.http.*
import kotlinx.serialization.Serializable
import okhttp3.ResponseBody

interface AikoApiService {
    // ========== Translation ==========
    @POST("api/english/translate")
    suspend fun translate(@Body request: TranslateRequest): TranslateResponse

    // ========== Conversation ==========
    @Streaming
    @POST("api/english/conversation/start")
    suspend fun startConversationStream(@Body request: ConversationStartRequest): ResponseBody

    @POST("api/english/conversation/respond")
    suspend fun respondToConversation(@Body request: ConversationRespondRequest): ConversationResponse

    @Streaming
    @POST("api/english/conversation/respond_stream")
    suspend fun respondToConversationStream(@Body request: ConversationRespondRequest): ResponseBody

    @POST("api/english/conversation/hint")
    suspend fun getHint(): ConversationHintResponse

    @POST("api/english/conversation/stop")
    suspend fun stopConversation(): Unit

    // ========== TTS ==========
    @GET("api/english/tts")
    suspend fun getTts(@Query("text") text: String): TtsResponse

    // ========== Stats & XP ==========
    @GET("api/english/stats")
    suspend fun getStats(): StatsResponse

    @GET("api/english/xp")
    suspend fun getXpData(): XPResponse

    @POST("api/english/xp/add")
    suspend fun addXp(@Body request: XPAddRequest): XPResponse

    // ========== Leaderboard ==========
    @GET("api/english/leaderboard")
    suspend fun getLeaderboard(): LeaderboardResponse

    // ========== SRS Review ==========
    @POST("api/english/conversation/review/start")
    suspend fun startReviewSession(): ReviewSessionResponse

    @POST("api/english/conversation/review/respond")
    suspend fun respondToReview(@Body request: ReviewResponseRequest): ReviewResponseData
}

@Serializable
data class TtsResponse(val audioUrl: String)
