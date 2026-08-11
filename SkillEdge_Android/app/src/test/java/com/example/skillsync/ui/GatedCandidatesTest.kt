package com.example.skillsync.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.skillsync.data.api.AllocationCandidatesResponse
import com.example.skillsync.theme.SkillSyncTheme
import com.example.skillsync.ui.batch.GatedCandidatesSection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The batch-detail eligibility surface. This is where the non-overridable
 * gates are actually applied, so its honesty rules are pinned here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class GatedCandidatesTest {

    @get:Rule
    val compose = createComposeRule()

    private val eligible = mapOf<String, Any>(
        "trainer_name" to "Abhinav Samant",
        "fit" to 87.0,
        "eligible" to true,
        "requires_verification" to true,
        "availability" to mapOf("status" to "available", "reason" to ""),
        "international" to mapOf(
            "visa" to "unknown",
            "visa_detail" to "no visa record held for this trainer",
            "timezone_fit" to "workable",
        ),
        "factors" to listOf(
            mapOf("name" to "Course experience", "contribution" to 20.0,
                  "evidence" to "27 prior deliveries of this course"),
            mapOf("name" to "Load headroom", "contribution" to -3.0,
                  "evidence" to "88% utilised"),
        ),
    )

    private val blockedByDnc = mapOf<String, Any>(
        "trainer_name" to "Priya Sharma",
        "eligible" to false,
        "fit" to 0.0,
        "blockers" to listOf(
            mapOf("gate" to "dnc", "detail" to "Cisco has marked this trainer do-not-call")
        ),
    )

    private fun render(
        response: AllocationCandidatesResponse? = null,
        loading: Boolean = false,
        unverified: String? = null,
    ) {
        compose.setContent {
            SkillSyncTheme { GatedCandidatesSection(response, loading, unverified) }
        }
    }

    @Test
    fun anEligibleCandidateShowsItsFitAndAvailability() {
        render(AllocationCandidatesResponse(
            ready = true, candidates = listOf(eligible),
            counts = mapOf("eligible" to 1.0, "blocked" to 0.0),
        ))
        compose.onNodeWithText("Abhinav Samant").assertExists()
        compose.onNodeWithText("87").assertExists()
        compose.onNodeWithText("Available").assertExists()
    }

    @Test
    fun theScoreCanBeAudited() {
        // A manager who cannot see the reasoning cannot overrule it.
        render(AllocationCandidatesResponse(ready = true, candidates = listOf(eligible)))
        compose.onNodeWithText("Why this score").performClick()
        compose.onNodeWithText("Course experience").assertExists()
        compose.onNodeWithText("27 prior deliveries of this course").assertExists()
        compose.onNodeWithText("+20").assertExists()
        compose.onNodeWithText("-3").assertExists()
    }

    @Test
    fun anUnknownVisaIsFlaggedForVerificationNotHidden() {
        render(AllocationCandidatesResponse(ready = true, candidates = listOf(eligible)))
        compose.onNodeWithText(
            "Visa: no visa record held for this trainer. Verify before assigning."
        ).assertExists()
    }

    @Test
    fun aBlockedCandidateStaysVisibleWithItsReason() {
        // Silently filtering an excluded trainer makes the absence look like a
        // bug. DNC is absolute, and the manager must see that it was applied.
        render(AllocationCandidatesResponse(
            ready = true, candidates = emptyList(), blocked = listOf(blockedByDnc),
            counts = mapOf("eligible" to 0.0, "blocked" to 1.0),
        ))
        compose.onNodeWithText("1 not eligible").assertExists()
        compose.onNodeWithText("Show why").performClick()
        compose.onNodeWithText("Priya Sharma").assertExists()
        compose.onNodeWithText(
            "Client exclusion: Cisco has marked this trainer do-not-call"
        ).assertExists()
    }

    @Test
    fun anEmptyEligibleListSaysSoWithoutImplyingAFailure() {
        render(AllocationCandidatesResponse(
            ready = true, candidates = emptyList(), blocked = listOf(blockedByDnc),
        ))
        compose.onNodeWithText(
            "Nobody on your team clears every requirement for these dates."
        ).assertExists()
    }

    @Test
    fun anUnverifiableCourseIsNeverShownAsNobodyAvailable() {
        render(unverified = "This course could not be matched in the RMS catalogue.")
        compose.onNodeWithText("Availability could not be verified").assertExists()
        compose.onNodeWithText("This is not the same as nobody being available.").assertExists()
        compose.onAllNodesWithText(
            "Nobody on your team clears every requirement for these dates."
        ).assertCountEquals(0)
    }

    @Test
    fun theLoadingStateNamesWhatIsBeingChecked() {
        render(loading = true)
        compose.onNodeWithText("Running the full eligibility check").assertExists()
    }

    @Test
    fun theHeaderReportsBothCounts() {
        render(AllocationCandidatesResponse(
            ready = true, candidates = listOf(eligible), blocked = listOf(blockedByDnc),
            counts = mapOf("eligible" to 1.0, "blocked" to 1.0),
        ))
        compose.onNodeWithText("1 eligible, 1 blocked, checked against real dates").assertExists()
    }
}
