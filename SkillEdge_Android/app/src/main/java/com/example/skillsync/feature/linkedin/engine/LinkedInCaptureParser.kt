package com.example.skillsync.feature.linkedin.engine

import com.example.skillsync.core.network.CaptureAnalyzeResponseDto
import com.example.skillsync.core.network.CapturedPostDto

/**
 * Pure, framework-free helpers for turning raw LinkedIn share/paste content into
 * a clean capture and mapping the backend wire response into [LinkedInAnalysis].
 *
 * Everything here is a pure function of its inputs so it can be unit tested
 * without Robolectric or a network.
 */
object LinkedInCaptureParser {

    private val URL_REGEX =
        Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

    /** Trim, normalise line endings and collapse 3+ blank lines to keep captures tidy. */
    fun normalizeText(text: String?): String = text
        .orEmpty()
        .trim()
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace(Regex("""\n{3,}"""), "\n\n")

    /** First `http(s)://` URL in the text, or null. */
    fun extractFirstUrl(text: String?): String? =
        URL_REGEX.find(text.orEmpty())?.value?.trimEnd('.', ',', ';', ')', ']', '}')

    /** True when the capture is only a link with no real post text. */
    fun isUrlOnly(text: String?): Boolean {
        val cleaned = text.orEmpty().replace(URL_REGEX, " ").trim()
        return cleaned.isEmpty()
    }

    /**
     * Best-effort app the post came from. Linkedin URLs (including `lnkd.in`
     * short links) mean LINKEDIN; any other URL means OTHER; otherwise UNKNOWN.
     */
    fun detectSourceApp(text: String?): String {
        val url = extractFirstUrl(text) ?: return LinkedInCapture.SOURCE_UNKNOWN
        return if (url.contains("linkedin.com", ignoreCase = true) ||
            url.contains("lnkd.in", ignoreCase = true)
        ) {
            LinkedInCapture.SOURCE_LINKEDIN
        } else {
            LinkedInCapture.SOURCE_OTHER
        }
    }

    /** Rebuilds [CapturedPostDto] for a submission. */
    fun buildRequest(
        captureMethod: String,
        rawText: String,
        sourceApp: String,
        authorName: String?,
        postUrl: String?,
        nowIso: String,
    ): CapturedPostDto = CapturedPostDto(
        capture_method = captureMethod,
        source_app = sourceApp,
        raw_text = rawText,
        author_name = authorName?.trim()?.takeIf { it.isNotBlank() },
        post_url = postUrl?.trim()?.takeIf { it.isNotBlank() },
        captured_at = nowIso,
        metadata = mapOf(
            "trigger" to if (captureMethod == LinkedInCapture.METHOD_SHARE_INTENT) {
                "android_share_intent"
            } else {
                "android_paste"
            }
        ),
    )
}

object LinkedInAnalysisMapper {

    fun map(response: CaptureAnalyzeResponseDto): LinkedInAnalysis {
        val capture = response.capture
        val post = response.post_analysis
        val decision = response.decision
        val commentDecision = response.comment_decision
        val reaction = response.reaction

        return LinkedInAnalysis(
            captureId = capture?.capture_id,
            sourceApp = capture?.source_app,
            authorName = capture?.author_name,
            postUrl = capture?.post_url ?: post?.let { null },
            textLength = capture?.text_length,
            contentHash = capture?.content_hash,
            postType = post?.post_type,
            topics = post?.topics.orEmpty(),
            intent = post?.intent,
            tone = post?.tone,
            sensitivity = post?.sensitivity,
            isAchievement = post?.is_achievement ?: false,
            isQuestion = post?.is_question ?: false,
            isPromotional = post?.is_promotional ?: false,
            isEngagementBait = post?.is_engagement_bait ?: false,
            isSpam = post?.is_spam ?: false,
            isTechnical = post?.is_technical ?: false,
            isPersonal = post?.is_personal ?: false,
            isControversial = post?.is_controversial ?: false,
            language = post?.language,
            confidence = post?.confidence,
            reasoningSummary = post?.reasoning_summary,
            evidence = post?.evidence.orEmpty(),
            relevanceScore = decision?.relevance_score ?: post?.relevance_score,
            action = decision?.action ?: LinkedInCapture.ACTION_IGNORE,
            selectedReaction = decision?.selected_reaction,
            actionReason = decision?.reason,
            reactionReason = decision?.reaction_reason ?: reaction?.reaction_reason,
            commentStrategy = commentDecision?.strategy ?: decision?.comment_strategy,
            commentDepth = commentDecision?.depth ?: decision?.comment_depth,
            shouldComment = commentDecision?.should_comment ?: decision?.should_comment,
            commentReason = commentDecision?.reason ?: decision?.comment_reason,
            requiresManualReview = decision?.requires_manual_review ?: false,
            shouldGenerateComment = decision?.should_generate_comment ?: false,
            shouldSelectReaction = decision?.should_select_reaction ?: false,
            comment = response.comment,
            commentValidation = response.comment_validation,
            reaction = reaction?.reaction,
        )
    }
}

/** Human-friendly labels for backend enum values (fall back to the raw value when unknown). */
object LinkedInLabels {

    private val ACTION_LABELS = mapOf(
        LinkedInCapture.ACTION_IGNORE to "Skip — no engagement",
        LinkedInCapture.ACTION_REACTION_ONLY to "React only",
        LinkedInCapture.ACTION_COMMENT_ONLY to "Comment only",
        LinkedInCapture.ACTION_REACTION_AND_COMMENT to "React and comment",
        LinkedInCapture.ACTION_SAVE to "Save for later",
        LinkedInCapture.ACTION_MANUAL_REVIEW to "Manual review",
    )

    private val REACTION_LABELS = mapOf(
        "NONE" to "No reaction",
        "LIKE" to "Like",
        "CELEBRATE" to "Celebrate",
        "SUPPORT" to "Support",
        "LOVE" to "Love",
        "INSIGHTFUL" to "Insightful",
        "FUNNY" to "Funny",
    )

    private val DECISION_FACTOR_HINTS = mapOf(
        "content_relevance" to "Relevant to my space",
        "relationship_goal" to "Relationship goal",
        "recency" to "Recent activity",
        "engagement_policy" to "Engagement policy",
        "risk_threshold" to "Risk threshold",
        "sensitivity" to "Sensitivity",
    )

    fun action(action: String?): String =
        ACTION_LABELS[action] ?: action ?: LinkedInCapture.ACTION_IGNORE

    fun reaction(reaction: String?): String =
        REACTION_LABELS[reaction] ?: reaction ?: "No reaction"

    fun topicHint(label: String?): String =
        DECISION_FACTOR_HINTS[label] ?: label ?: ""
}