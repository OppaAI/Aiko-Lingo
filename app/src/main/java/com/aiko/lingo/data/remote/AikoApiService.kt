package com.aiko.lingo.data.remote

import com.aiko.lingo.data.model.*
import retrofit2.http.Body
import retrofit2.http.POST

interface AikoApiService {
    @POST("api/english/translate")
    suspend fun translate(@Body request: TranslateRequest): TranslateResponse

    @POST("api/english/conversation/start")
    suspend fun startConversation(@Body request: ConversationStartRequest): ConversationResponse

    @POST("api/english/conversation/respond")
    suspend fun respondToConversation(@Body request: ConversationRespondRequest): ConversationResponse

    @POST("api/english/conversation/hint")
    suspend fun getHint(): ConversationHintResponse

    @POST("api/english/conversation/stop")
    suspend fun stopConversation(): Unit
}
