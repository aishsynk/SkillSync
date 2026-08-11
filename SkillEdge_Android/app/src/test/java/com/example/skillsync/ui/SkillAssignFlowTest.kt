package com.example.skillsync.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.skillsync.theme.SkillSyncTheme
import com.example.skillsync.ui.main.SkillAssignFlow
import com.example.skillsync.ui.main.SkillCandidate
import com.example.skillsync.ui.main.SkillWriteResult
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Skill to Select Members to Assign (§7.6).
 *
 * The rules pinned here are the ones that make it a manager's workflow rather
 * than a wrapper over a single-record API.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class SkillAssignFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private val people = listOf(
        SkillCandidate("Abhinav Samant", "a@k.com"),
        SkillCandidate("Beena Rao", "b@k.com"),
        SkillCandidate("Chetan Iyer", "c@k.com", alreadyHas = true, currentLevel = 7),
    )

    private var assigned: Pair<List<SkillCandidate>, Int>? = null

    private fun render(
        results: List<SkillWriteResult>? = null,
        working: Boolean = false,
        onAssigned: (List<SkillCandidate>, Int) -> Unit = { s, l -> assigned = s to l },
    ) {
        compose.setContent {
            SkillSyncTheme {
                SkillAssignFlow(
                    courseName = "AZ-305T00: Designing Azure Infrastructure",
                    candidates = people,
                    working = working,
                    results = results,
                    onAssign = { sel, lvl -> onAssigned(sel, lvl) },
                    onDismiss = {},
                )
            }
        }
    }

    @Test
    fun theSkillIsAlreadyChosenSoTheManagerPicksPeople() {
        // The old flow made a manager search for the course again per person.
        render()
        compose.onNodeWithText("AZ-305T00: Designing Azure Infrastructure").assertExists()
        compose.onNodeWithText("Abhinav Samant").assertExists()
        compose.onNodeWithText("Beena Rao").assertExists()
    }

    @Test
    fun peopleWhoAlreadyHoldTheSkillAreHiddenByDefault() {
        // The common case is "everyone who is missing this".
        render()
        compose.onAllNodesWithText("Chetan Iyer").assertCountEquals(0)
        compose.onNodeWithText("1 already have it").assertExists()
    }

    @Test
    fun showingEveryoneRevealsWhatTheyAlreadyHold() {
        render()
        compose.onNodeWithText("1 already have it").performClick()
        compose.onNodeWithText("Chetan Iyer").assertExists()
        compose.onNodeWithText("Already holds it at level 7").assertExists()
    }

    @Test
    fun nothingCanBeAssignedUntilSomeoneIsSelected() {
        render()
        compose.onNodeWithText("Select who needs this").assertIsNotEnabled()
    }

    @Test
    fun selectAllPicksEveryVisiblePerson() {
        render()
        compose.onNodeWithText("Select all").performClick()
        compose.onNodeWithText("Review 2 assignments").assertExists()
    }

    @Test
    fun thePreviewStatesThatTheWriteCannotBeUndone() {
        // RMS has no remove or update endpoint, so this must be said before
        // the write, not discovered afterwards.
        render()
        compose.onNodeWithText("Select all").performClick()
        compose.onNode(hasText("Review 2 assignments") and hasClickAction()).performClick()
        compose.onNodeWithText("This writes to production RMS and cannot be undone.").assertExists()
        compose.onNodeWithText(
            "RMS has no remove or edit endpoint, so a wrong entry has to be " +
                "corrected by the RMS team rather than in this app."
        ).assertExists()
    }

    @Test
    fun thePreviewNamesEveryPersonAndTheLevel() {
        render()
        compose.onNodeWithText("Select all").performClick()
        compose.onNode(hasText("Review 2 assignments") and hasClickAction()).performClick()
        compose.onNodeWithText("WILL BE ASSIGNED").assertExists()
        compose.onAllNodesWithText("level 5").assertCountEquals(2)
    }

    @Test
    fun confirmingSendsTheSelection() {
        var got: Pair<List<SkillCandidate>, Int>? = null
        render(onAssigned = { s, l -> got = s to l })
        compose.onNodeWithText("Select all").performClick()
        compose.onNode(hasText("Review 2 assignments") and hasClickAction()).performClick()
        compose.onNodeWithText("WILL BE ASSIGNED").assertExists()
        // Target the clickable node: performClick on a Text inside a Button
        // hits the Text, which carries no click action.
        // Invoke the click action directly. Synthesised taps do not reach a
        // button nested inside the bottom sheet's footer column under
        // Robolectric, which is a harness limitation, not a UI defect.
        compose.onNodeWithTag("assign-confirm")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(2, got?.first?.size)
        assertEquals(5, got?.second)
    }

    @Test
    fun theChosenLevelIsWhatGetsSent() {
        var got: Pair<List<SkillCandidate>, Int>? = null
        render(onAssigned = { s, l -> got = s to l })
        compose.onNodeWithText("Select all").performClick()
        compose.onNode(hasText("Review 2 assignments") and hasClickAction()).performClick()
        compose.onAllNodesWithText("8").onFirst().performClick()
        // Invoke the click action directly. Synthesised taps do not reach a
        // button nested inside the bottom sheet's footer column under
        // Robolectric, which is a harness limitation, not a UI defect.
        compose.onNodeWithTag("assign-confirm")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(8, got?.second)
    }

    @Test
    fun partialFailureIsReportedPerRow() {
        // A bulk write against production RMS partially fails as a matter of
        // course; one aggregate "done" would hide which rows still need work.
        render(results = listOf(
            SkillWriteResult("a@k.com", ok = true, message = "Recorded"),
            SkillWriteResult("b@k.com", ok = false, message = "Refused by RMS"),
        ))
        compose.onNodeWithText("1 recorded, 1 refused.").assertExists()
        compose.onNodeWithText("Refused by RMS").assertExists()
        compose.onNodeWithText("Beena Rao").assertExists()
    }

    @Test
    fun aCleanRunSaysSoPlainly() {
        render(results = listOf(SkillWriteResult("a@k.com", ok = true, message = "Recorded")))
        compose.onNodeWithText("All 1 recorded in RMS.").assertExists()
    }
}
