package com.aiko.lingo.data.model

import kotlinx.serialization.Serializable

@Serializable
data class TranslateRequest(
    val text: String
)

@Serializable
data class TranslationResult(
    val register: String,
    val text: String,
    val audioUrl: String? = null
)

@Serializable
data class TranslateResponse(
    val translations: List<TranslationResult>
)

@Serializable
data class ConversationStartRequest(
    val level: String
)

@Serializable
data class ConversationResponse(
    val japaneseText: String,
    val englishTranslation: String,
    val audioUrl: String? = null,
    val isFinished: Boolean = false
)

@Serializable
data class ConversationRespondRequest(
    val text: String
)

@Serializable
data class ConversationHintResponse(
    val japaneseText: String,
    val englishTranslation: String,
    val audioUrl: String? = null
)
