package com.example.skillsync.feature.linkedin

import com.example.skillsync.core.network.CaptureAnalyzeResponseDto
import com.example.skillsync.core.network.CaptureMetadataDto
import com.example.skillsync.core.network.CapturedPostDto
import com.example.skillsync.core.network.CommentDecisionDto
import com.example.skillsync.core.network.EngagementDecisionDto
import com.example.skillsync.core.network.PostAnalysisDto
import com.example.skillsync.core.network.ReactionOutcomeDto
import com.example.skillsync.feature.linkedin.engine.LinkedInAnalysisMapper
import com.example.skillsync.feature.linkedin.engine.LinkedInCapture
import com.example.skillsync.feature.linkedin.engine.LinkedInCaptureParser
import com.example.skillsync.feature.linkedin.engine.LinkedInLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkedInCaptureParserTest {

    @Test
    fun normalizeText_trimsAndCollapsesExcessBlankLines() {
        val raw = "\n  Hello  \n\n\n\nWorld  \n   "
        assertEquals("Hello  \n\nWorld", LinkedInCaptureParser.normalizeText(raw))
    }

    @Test
    fun extractFirstUrl_returnsFirstHttpUrl() {
        val raw = "Text before\nlink:\nhttps://www.linkedin.com/posts/abc-123?utm=a\n and then more"
        assertEquals("https://www.linkedin.com/posts/abc-123?utm=a", LinkedInCaptureParser.extractFirstUrl(raw))
    }

    @Test
    fun extractFirstUrl_returnsNullWhenNoUrl() {
        assertNull(LinkedInCaptureParser.extractFirstUrl("just words, no link here"))
    }

    @Test
    fun isUrlOnly_trueForALinkAlone() {
        assertTrue(LinkedInCaptureParser.isUrlOnly("https://www.linkedin.com/posts/abc-123"))
        assertTrue(LinkedInCaptureParser.isUrlOnly("  https://lnkd.in/gXy  \n"))
    }

    @Test
    fun isUrlOnly_falseWhenTextAccompaniesLink() {
        assertFalse(LinkedInCaptureParser.isUrlOnly("This is a big announcement!\nhttps://www.linkedin.com/posts/abc-123"))
    }

    @Test
    fun detectSourceApp_linkedinDomainsMapToLinkedin() {
        assertEquals(
            LinkedInCapture.SOURCE_LINKEDIN,
            LinkedInCaptureParser.detectSourceApp("https://lnkd.in/gXy"),
        )
        assertEquals(
            LinkedInCapture.SOURCE_LINKEDIN,
            LinkedInCaptureParser.detectSourceApp("https://www.linkedin.com/posts/abc-123"),
        )
    }

    @Test
    fun detectSourceApp_otherUrlMapsToOther_noUrlUnknown() {
        assertEquals(
            LinkedInCapture.SOURCE_OTHER,
            LinkedInCaptureParser.detectSourceApp("Check https://example.com/a"),
        )
        assertEquals(
            LinkedInCapture.SOURCE_UNKNOWN,
            LinkedInCaptureParser.detectSourceApp("no url anywhere"),
        )
    }

    @Test
    fun detectSourceApp_shortLinkWithinTextMapsToLinkedin() {
        assertEquals(
            LinkedInCapture.SOURCE_LINKEDIN,
            LinkedInCaptureParser.detectSourceApp("Such a proud moment!\nhttps://lnkd.in/gXy"),
        )
    }

    @Test
    fun buildRequest_fillsWireFieldsAndEmptyOptionalsToNull() {
        val post = LinkedInCaptureParser.buildRequest(
            captureMethod = LinkedInCapture.METHOD_SHARE_INTENT,
            rawText = "Announcement text",
            sourceApp = LinkedInCapture.SOURCE_LINKEDIN,
            authorName = "   ",
            postUrl = "",
            nowIso = "2026-09-14T10:00:00Z",
        )
        assertEquals(LinkedInCapture.METHOD_SHARE_INTENT, post.capture_method)
        assertEquals(LinkedInCapture.SOURCE_LINKEDIN, post.source_app)
        assertEquals("Announcement text", post.raw_text)
        assertNull(post.author_name)
        assertNull(post.post_url)
        assertEquals("2026-09-14T10:00:00Z", post.captured_at)
        assertEquals("android_share_intent", post.metadata?.get("trigger"))
    }

    @Test
    fun buildRequest_pasteTriggerForInAppFlow() {
        val post = LinkedInCaptureParser.buildRequest(
            captureMethod = LinkedInCapture.METHOD_PASTE,
            rawText = "text",
            sourceApp = LinkedInCapture.SOURCE_LINKEDIN,
            authorName = "Alex Chen",
            postUrl = "https://www.linkedin.com/posts/abc-123",
            nowIso = "2026-09-14T10:00:00Z",
        )
        assertEquals("android_paste", post.metadata?.get("trigger"))
        assertEquals("Alex Chen", post.author_name)
        assertEquals("https://www.linkedin.com/posts/abc-123", post.post_url)
    }

    @Test
    fun mapper_bringsTheFullResponseIntoTheDomainModel() {
        val response = CaptureAnalyzeResponseDto(
            capture = CaptureMetadataDto(
                capture_id = "capture_42",
                capture_method = "PASTE",
                source_app = "LINKEDIN",
                text_length = 342,
                content_hash = "abc123",
            ),
            post_analysis = PostAnalysisDto(
                post_type = "ACHIEVEMENT",
                topics = listOf("azure", "certification"),
                intent = "CELEBRATE",
                tone = "PROUD",
                sensitivity = "MEDIUM",
                is_achievement = true,
                is_question = false,
                language = "en",
                confidence = 0.92,
                reasoning_summary = "Clearly a professional milestone.",
            ),
            decision = EngagementDecisionDto(
                action = "REACTION_AND_COMMENT",
                selected_reaction = "CELEBRATE",
                reason = "Milestone worth acknowledging",
                should_comment = true,
                should_select_reaction = true,
                should_generate_comment = true,
                requires_manual_review = false,
                reaction_reason = "Celebrate a certification win",
                comment_strategy = "SPECIFIC_PRAISE",
                comment_depth = "SHORT",
                comment_reason = "Short warm praise fits the milestone.",
            ),
            comment_decision = CommentDecisionDto(should_comment = true, strategy = "SPECIFIC_PRAISE", depth = "SHORT"),
            comment = "Congratulations on the Azure certification!",
            comment_validation = "PASS",
            reaction = ReactionOutcomeDto(reaction = "CELEBRATE", reaction_reason = "Celebrate a certification win"),
        )

        val a = LinkedInAnalysisMapper.map(response)

        assertEquals("capture_42", a.captureId)
        assertEquals(342, a.textLength)
        assertEquals("abc123", a.contentHash)
        assertEquals("ACHIEVEMENT", a.postType)
        assertTrue(a.isAchievement)
        assertEquals(listOf("azure", "certification"), a.topics)
        assertEquals("REACTION_AND_COMMENT", a.action)
        assertEquals("CELEBRATE", a.selectedReaction)
        assertEquals("Congratulations on the Azure certification!", a.comment)
        assertEquals("PASS", a.commentValidation)
        assertTrue(a.requiresReaction)
        assertTrue(a.shouldComment == true)
    }

    @Test
    fun labels_speakHumanLanguageForKnownEnums() {
        assertEquals("React and comment", LinkedInLabels.action("REACTION_AND_COMMENT"))
        assertEquals("Manual review", LinkedInLabels.action("MANUAL_REVIEW"))
        assertEquals("Celebrate", LinkedInLabels.reaction("CELEBRATE"))
        assertEquals("Insightful", LinkedInLabels.reaction("INSIGHTFUL"))
        assertEquals("No reaction", LinkedInLabels.reaction("NONE"))
        // Unknown enum values fall back to the raw value rather than blanking.
        assertEquals("MYSTERY_ACTION", LinkedInLabels.action("MYSTERY_ACTION"))
    }
}