package com.aiko.lingo.data.remote

import com.aiko.lingo.data.model.*
import retrofit2.http.*
import kotlinx.serialization.Serializable
import okhttp3.ResponseBody

interface AikoApiService {
    @POST("api/english/translate")
    suspend fun translate(@Body request: TranslateRequest): TranslateResponse

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

    @GET("api/english/tts")
    suspend fun getTts(@Query("text") text: String): TtsResponse
}

@Serializable
data class TtsResponse(val audioUrl: String)
