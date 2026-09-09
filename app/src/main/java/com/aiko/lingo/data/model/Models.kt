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
    val isFinished: Boolean = false,
    val isCorrect: Boolean = true,
    val feedback: String? = null,
    val suggestion: String? = null,
    val explanation: String? = null,
    val vocabExtracted: Int? = null,
    val toast: Toast? = null
)

@Serializable
data class Toast(
    val type: String, // "success", "feedback", "info"
    val message: String
)

@Serializable
data class ConversationRespondRequest(
    val text: String,
    val history: List<DialogueHistoryEntry> = emptyList()
)

@Serializable
data class DialogueHistoryEntry(
    val speaker: String, // "aiko" or "student"
    val text: String
)

@Serializable
data class ConversationHintResponse(
    val japaneseText: String,
    val englishTranslation: String,
    val audioUrl: String? = null,
    val explanation: String? = null
)

// ============================================================================
// SRS & Review Models
// ============================================================================
@Serializable
data class ReviewCard(
    val card_id: Int = 0,
    val hiragana: String = "",
    val meaning: String = "",
    val context: String = "",
    val kanji: String? = null // NEW: optional Kanji field for better readability
)

@Serializable
data class ReviewSessionResponse(
    val cards_due: Int = 0,
    val first_card: ReviewCard? = null
)

@Serializable
data class ReviewResponseRequest(
    val card_id: Int,
    val response: String,
    val grade: Int = 0
)

@Serializable
data class ReviewResponseData(
    val updated_card: UpdatedCard = UpdatedCard(),
    val next_card: ReviewCard? = null,
    val cards_remaining: Int = 0,
    // NEW: optional toast from review_respond (e.g. "🌟 word = meaning" on Easy grade)
    val toast: Toast? = null
)

@Serializable
data class UpdatedCard(
    val id: Int = 0,
    val interval: Int = 0,
    val ease: Double = 2.5
)

// ============================================================================
// Stats & XP Models
// ============================================================================
@Serializable
data class StatsResponse(
    val total_cards: Int,
    val learned_today: Int,
    val reviews_today: Int,
    val avg_ease: Double,
    val cards_due: Int,
    val streak: Streak,
    val xp: Int,
    val level: Int,
    val last_level: String,
    // NEW: cards the user is struggling with (>50% failure rate), for the
    // "Practice These" widget on the dashboard. Defaults to empty so older
    // backend responses without this field still deserialize fine.
    val weak_vocab: List<ReviewCard> = emptyList()
)

@Serializable
data class WordOfDayResponse(
    val card_id: Int = 0,
    val hiragana: String = "",
    val meaning: String = "",
    val context: String = "",
    val audioUrl: String? = null,
    val date: String = "",
    val kanji: String? = null // NEW: support Kanji for Word of the Day
)

@Serializable
data class LessonDeckMeta(
    val id: String = "",
    val title: String = "",
    val subtitle: String = "",
    val kind: String = "",
    val card_count: Int = 0
)

@Serializable
data class LessonCard(
    val front: String = "",
    val back: String = "",
    val reading: String = ""
)

@Serializable
data class LessonDeck(
    val id: String = "",
    val title: String = "",
    val subtitle: String = "",
    val kind: String = "",
    val cards: List<LessonCard> = emptyList()
)

@Serializable
data class Streak(
    val days: Int,
    val total_sessions: Int,
    val next_reward: String
)

@Serializable
data class XPResponse(
    val xp: Int,
    val level: Int,
    val next_level_xp: Int
)

@Serializable
data class XPAddRequest(
    val amount: Int
)

@Serializable
data class LeaderboardResponse(
    val rank: Int,
    val username: String,
    val streak: Int,
    val xp: Int,
    val level: Int,
    val top_users: List<LeaderboardUser>
)

@Serializable
data class LeaderboardUser(
    val username: String,
    val streak: Int,
    val xp: Int,
    val level: Int? = null
)
