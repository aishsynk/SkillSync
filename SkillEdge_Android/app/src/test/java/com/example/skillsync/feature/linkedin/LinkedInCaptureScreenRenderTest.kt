package com.example.skillsync.feature.linkedin

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.skillsync.feature.home.DashboardTab
import com.example.skillsync.feature.linkedin.engine.LinkedInAnalysis
import com.example.skillsync.feature.linkedin.engine.LinkedInCapture
import com.example.skillsync.feature.linkedin.ui.LinkedInCaptureEditingContent
import com.example.skillsync.feature.linkedin.ui.LinkedInCaptureResultContent
import com.example.skillsync.feature.linkedin.ui.LinkedInCaptureUiState
import com.example.skillsync.theme.SkillSyncTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * JVM composition smoke tests for the LinkedIn capture screen. These catch
 * composition crashes and wrong data-binding the same way ScreenRenderTest does
 * for the manager screens — there is no emulator on this project.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h3600dp")
class LinkedInCaptureScreenRenderTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun editing_rendersGuideHintAndAnalyseButton() {
        compose.setContent {
            SkillSyncTheme {
                LinkedInCaptureEditingContent(
                    ui = LinkedInCaptureUiState(rawText = "A real post text."),
                    onTextChange = {},
                    onAuthorChange = {},
                    onUrlChange = {},
                    onSubmit = {},
                )
            }
        }
        compose.onNodeWithText("Paste the visible post text").assertIsDisplayed()
        compose.onNodeWithText("Analyse post").assertIsDisplayed()
    }

    @Test
    fun editing_urlOnlyCaptureShowsWarningBanner() {
        compose.setContent {
            SkillSyncTheme {
                LinkedInCaptureEditingContent(
                    ui = LinkedInCaptureUiState(
                        rawText = "https://lnkd.in/gXy",
                        urlOnlyHint = "LinkedIn shared only a post link. Paste or capture the visible post text before analysis.",
                    ),
                    onTextChange = {},
                    onAuthorChange = {},
                    onUrlChange = {},
                    onSubmit = {},
                )
            }
        }
        compose.onNodeWithText("Link-only capture").assertIsDisplayed()
        compose.onAllNodesWithText("Analyse post").assertCountEquals(1)
    }

    @Test
    fun result_rendersActionReactionCommentAndActions() {
        compose.setContent {
            SkillSyncTheme {
                LinkedInCaptureResultContent(
                    analysis = LinkedInAnalysis(
                        captureId = "capture_1",
                        action = LinkedInCapture.ACTION_REACTION_AND_COMMENT,
                        actionReason = "A milestone worth acknowledging.",
                        selectedReaction = "CELEBRATE",
                        reactionReason = "Celebrate this certification win.",
                        shouldComment = true,
                        comment = "Congratulations on the Azure certification!",
                        commentValidation = LinkedInCapture.COMMENT_VALIDATION_PASS,
                        textLength = 342,
                        postUrl = "https://www.linkedin.com/posts/abc-123",
                    ),
                    copied = false,
                    onCopy = {},
                    onOpenOriginal = {},
                    onAnalyseAnother = {},
                    onDone = {},
                )
            }
        }
        compose.onNodeWithText("React and comment").assertIsDisplayed()
        compose.onNodeWithText("Give a Celebrate reaction").assertIsDisplayed()
        compose.onNodeWithText("Congratulations on the Azure certification!").assertIsDisplayed()
        compose.onNodeWithText("Copy comment").assertIsDisplayed()
        compose.onNodeWithText("Open post").assertIsDisplayed()
    }

    @Test
    fun result_manualReviewShowsReviewBanner() {
        compose.setContent {
            SkillSyncTheme {
                LinkedInCaptureResultContent(
                    analysis = LinkedInAnalysis(
                        action = LinkedInCapture.ACTION_MANUAL_REVIEW,
                        requiresManualReview = true,
                        actionReason = "Post touches a sensitive topic.",
                    ),
                    copied = false,
                    onCopy = {},
                    onOpenOriginal = {},
                    onAnalyseAnother = {},
                    onDone = {},
                )
            }
        }
        compose.onNodeWithText("Requires your review").assertIsDisplayed()
        compose.onNodeWithText("Manual review").assertIsDisplayed()
    }

    @Test
    fun dashboardEntryCard_existsOnTodayBriefAndOpensTheFlow() {
        var opened = false
        compose.setContent {
            SkillSyncTheme {
                DashboardTab(
                    data = emptyMap(),
                    profile = null,
                    capability = null,
                    capabilityLoading = false,
                    email = "aishwar.c@koenig-solutions.com",
                    onTrainerClick = { _, _ -> },
                    onOpenProfile = {},
                    onDrill = {},
                    onOpenLinkedInCapture = { opened = true },
                )
            }
        }
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Analyse a LinkedIn post"))
        compose.onNodeWithText("Analyse a LinkedIn post").performClick()
        assertTrue(opened)
    }
}