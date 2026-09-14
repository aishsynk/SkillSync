package com.example.skillsync.core.network

import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Wire contract for the LinkedIn Capture backend (`POST /api/v1/captures/analyse`).
 *
 * Field names are snake_case to match the JSON the FastAPI backend emits and
 * reads; Gson maps them directly. Mutable-ish null safety is handled in the
 * mapper (`toAnalysis`), never here.
 */
data class CapturedPostDto(
    val capture_method: String,
    val source_app: String,
    val raw_text: String? = null,
    val selected_text: String? = null,
    val author_name: String? = null,
    val author_id: String? = null,
    val post_url: String? = null,
    val captured_at: String? = null,
    val language_hint: String? = null,
    val metadata: Map<String, String>? = null,
)

data class RelationshipContextDto(
    val type: String? = null,
    val strength: Double? = null,
)

data class InteractionContextDto(
    val days_since_last_interaction: Int? = null,
    val recent_interaction_count: Int? = null,
)

data class CaptureAnalysisRequestDto(
    val capture: CapturedPostDto,
    val relationship: RelationshipContextDto? = null,
    val interaction: InteractionContextDto? = null,
)

data class CaptureMetadataDto(
    val capture_id: String? = null,
    val capture_method: String? = null,
    val source_app: String? = null,
    val author_name: String? = null,
    val post_url: String? = null,
    val text_length: Int? = null,
    val content_hash: String? = null,
)

data class PostAnalysisDto(
    val post_type: String? = null,
    val topics: List<String>? = null,
    val intent: String? = null,
    val tone: String? = null,
    val sensitivity: String? = null,
    val is_achievement: Boolean? = null,
    val is_question: Boolean? = null,
    val is_promotional: Boolean? = null,
    val is_engagement_bait: Boolean? = null,
    val is_spam: Boolean? = null,
    val is_technical: Boolean? = null,
    val is_personal: Boolean? = null,
    val is_controversial: Boolean? = null,
    val language: String? = null,
    val confidence: Double? = null,
    val reasoning_summary: String? = null,
    val evidence: List<String>? = null,
    val relevance_score: Double? = null,
)

data class EngagementDecisionDto(
    val action: String? = null,
    val selected_reaction: String? = null,
    val engagement_score: Double? = null,
    val relationship_score: Double? = null,
    val relevance_score: Double? = null,
    val quality_score: Double? = null,
    val novelty_score: Double? = null,
    val risk_score: Double? = null,
    val confidence: Double? = null,
    val reason: String? = null,
    val reaction_reason: String? = null,
    val comment_strategy: String? = null,
    val comment_depth: String? = null,
    val should_comment: Boolean? = null,
    val comment_reason: String? = null,
    val decision_factors: List<String>? = null,
    val requires_manual_review: Boolean? = null,
    val should_generate_comment: Boolean? = null,
    val should_select_reaction: Boolean? = null,
    val policy_overrides: List<String>? = null,
)

data class CommentDecisionDto(
    val should_comment: Boolean? = null,
    val strategy: String? = null,
    val depth: String? = null,
    val reason: String? = null,
)

data class ReactionOutcomeDto(
    val reaction: String? = null,
    val reaction_reason: String? = null,
)

data class CaptureAnalyzeResponseDto(
    val capture: CaptureMetadataDto? = null,
    val post_analysis: PostAnalysisDto? = null,
    val decision: EngagementDecisionDto? = null,
    val comment_decision: CommentDecisionDto? = null,
    val comment: String? = null,
    val comment_validation: String? = null,
    val reaction: ReactionOutcomeDto? = null,
)

interface LinkedInApi {
    @POST("api/v1/captures/analyse")
    suspend fun analyseCapture(@Body body: CaptureAnalysisRequestDto): CaptureAnalyzeResponseDto
}