package com.example.skillsync.feature.linkedin.engine

/**
 * Shared contract values for the LinkedIn Capture workflow.
 *
 * Strings mirror the backend enums in `domain/enums.py`; keeping them as plain
 * constants (not a parsed enum) makes the parser and mapper pure and trivially
 * testable.
 */
object LinkedInCapture {

    const val METHOD_SHARE_INTENT = "SHARE_INTENT"
    const val METHOD_PASTE = "PASTE"

    /** Screen-entry route, distinct from capture method: share intent vs in-app card. */
    const val ENTRY_SHARE_INTENT = "SHARE_INTENT"
    const val ENTRY_IN_APP = "IN_APP"

    const val SOURCE_LINKEDIN = "LINKEDIN"
    const val SOURCE_OTHER = "OTHER"
    const val SOURCE_UNKNOWN = "UNKNOWN"

    const val ACTION_IGNORE = "IGNORE"
    const val ACTION_REACTION_ONLY = "REACTION_ONLY"
    const val ACTION_COMMENT_ONLY = "COMMENT_ONLY"
    const val ACTION_REACTION_AND_COMMENT = "REACTION_AND_COMMENT"
    const val ACTION_SAVE = "SAVE"
    const val ACTION_MANUAL_REVIEW = "MANUAL_REVIEW"

    const val COMMENT_VALIDATION_PASS = "PASS"
    const val COMMENT_VALIDATION_FAILED = "FAILED"

    /** Shown when the capture holds only a link; LinkedIn shares must contain text. */
    const val URL_ONLY_MESSAGE = "LinkedIn shared only a post link. Paste or capture the visible post text before analysis."
}

/**
 * Sealed result of an analysis attempt. [Success] carries the fully-mapped
 * decision; [UrlOnly] is the backend's HTTP 422 contract response (no raw text
 * was provided); [Failure] is any transport / non-422 problem.
 */
sealed interface LinkedInAnalysisResult {
    data class Success(val analysis: LinkedInAnalysis) : LinkedInAnalysisResult
    data class UrlOnly(val message: String) : LinkedInAnalysisResult
    data class Failure(val message: String) : LinkedInAnalysisResult
}

/** Domain model of the backend `CaptureAnalyzeResponse`. Nullable where the backend may omit a field. */
data class LinkedInAnalysis(
    val captureId: String? = null,
    val sourceApp: String? = null,
    val authorName: String? = null,
    val postUrl: String? = null,
    val textLength: Int? = null,
    val contentHash: String? = null,
    val postType: String? = null,
    val topics: List<String> = emptyList(),
    val intent: String? = null,
    val tone: String? = null,
    val sensitivity: String? = null,
    val isAchievement: Boolean = false,
    val isQuestion: Boolean = false,
    val isPromotional: Boolean = false,
    val isEngagementBait: Boolean = false,
    val isSpam: Boolean = false,
    val isTechnical: Boolean = false,
    val isPersonal: Boolean = false,
    val isControversial: Boolean = false,
    val language: String? = null,
    val confidence: Double? = null,
    val reasoningSummary: String? = null,
    val evidence: List<String> = emptyList(),
    val relevanceScore: Double? = null,
    val action: String = LinkedInCapture.ACTION_IGNORE,
    val selectedReaction: String? = null,
    val actionReason: String? = null,
    val reactionReason: String? = null,
    val commentStrategy: String? = null,
    val commentDepth: String? = null,
    val shouldComment: Boolean? = null,
    val commentReason: String? = null,
    val requiresManualReview: Boolean = false,
    val shouldGenerateComment: Boolean = false,
    val shouldSelectReaction: Boolean = false,
    val comment: String? = null,
    val commentValidation: String? = null,
    val reaction: String? = null,
) {
    val requiresReaction: Boolean
        get() = shouldSelectReaction || !selectedReaction.isNullOrBlank()
}