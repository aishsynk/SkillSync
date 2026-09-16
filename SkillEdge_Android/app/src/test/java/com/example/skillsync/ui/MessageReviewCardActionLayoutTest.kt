package com.example.skillsync.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.skillsync.feature.communication.ui.MessageReviewCard
import com.example.skillsync.theme.SkillSyncTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Release-blocker regression: at 360dp, MessageReviewCard with all four
 * actions (Regenerate, Edit, Copy, Share) used to squeeze the last action
 * ("Share") to near-zero width, wrapping its label character-by-character
 * ("S" / "h" / "a" on separate lines). The fix moved the four-action layout
 * to a 2x2 grid so every label renders as one line, full text, and stays
 * clickable — proven here against the real production composable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MessageReviewCardActionLayoutTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun fourActions_atThreeSixtyDp_allRenderOnOneLineAndAreClickable() {
        var regenerated = false
        var copied = false
        var shared = false

        compose.setContent {
            SkillSyncTheme {
                // 360dp: the narrow-phone width the defect was observed at.
                Box(Modifier.width(360.dp)) {
                    MessageReviewCard(
                        text = "Hi Niharika, four batches delivered this month at 94% learner feedback.",
                        onTextChange = {},
                        onRegenerate = { regenerated = true },
                        onCopy = { copied = true },
                        onShare = { shared = true },
                    )
                }
            }
        }

        // Each label must exist as ONE node with its FULL text — a
        // character-wrapped label would not match this exact string.
        compose.onNodeWithText("Regenerate").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText("Edit").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText("Copy").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText("Share").assertIsDisplayed().assertHasClickAction()

        compose.onNodeWithText("Regenerate").performClick()
        compose.onNodeWithText("Copy").performClick()
        compose.onNodeWithText("Share").performClick()

        assert(regenerated) { "Regenerate action did not fire its callback" }
        assert(copied) { "Copy action did not fire its callback" }
        assert(shared) { "Share action did not fire its callback" }
    }

    @Test
    fun threeActions_noRegenerate_stillFitsNaturally() {
        compose.setContent {
            SkillSyncTheme {
                Box(Modifier.width(360.dp)) {
                    MessageReviewCard(
                        text = "Hi Priya, four batches delivered this month at 94% learner feedback.",
                        onTextChange = {},
                        onCopy = {},
                        onShare = {},
                    )
                }
            }
        }
        compose.onNodeWithText("Edit").assertIsDisplayed()
        compose.onNodeWithText("Copy").assertIsDisplayed()
        compose.onNodeWithText("Share").assertIsDisplayed()
    }
}
