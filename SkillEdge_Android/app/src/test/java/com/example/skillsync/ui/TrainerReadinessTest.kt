package com.example.skillsync.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.skillsync.theme.SkillSyncTheme
import com.example.skillsync.ui.trainer.Trainer360Content
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Trainer 360's readiness section, fed the real wire shape. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class TrainerReadinessTest {

    @get:Rule
    val compose = createComposeRule()

    private fun readiness(
        leave: Int = 2, confirmed: Int = 4, tentative: Int = 3,
        exclusions: Int = 1, unknown: Int = 5,
    ) = mapOf<String, Any>(
        "schedule" to mapOf(
            "leave_days" to leave.toDouble(),
            "confirmed_days" to confirmed.toDouble(),
            "tentative_days" to tentative.toDouble(),
            "next_leave" to listOf("2026-09-02", "2026-09-03"),
            "delivery_modes" to listOf("ILO"),
            "client_exclusions" to exclusions.toDouble(),
        ),
        "certification" to mapOf(
            "courses_reviewed" to 12.0,
            "unknown_requirement" to unknown.toDouble(),
            "gaps" to listOf(mapOf(
                "course" to "AZ-305T00: Designing Azure Infrastructure",
                "exam_name" to "Microsoft Certified: Azure Solutions Architect Expert",
            )),
        ),
    )

    private fun render(r: Map<String, Any>?) {
        compose.setContent {
            SkillSyncTheme { Trainer360Content(emptyMap(), readiness = r) }
        }
    }

    @Test
    fun leaveAndCommitmentsComeFromTheRealCalendar() {
        render(readiness())
        compose.onNodeWithText("ON LEAVE").assertExists()
        compose.onNodeWithText("COMMITTED").assertExists()
        compose.onNodeWithText("Next leave: 2026-09-02, 2026-09-03").assertExists()
    }

    @Test
    fun provisionalWorkIsCountedSeparately() {
        // Counting it as committed overstates the load; ignoring it understates it.
        render(readiness())
        compose.onNodeWithText("PROVISIONAL").assertExists()
    }

    @Test
    fun clientExclusionsAreCalledOutAsAbsolute() {
        render(readiness(exclusions = 2))
        compose.onNodeWithText(
            "2 client exclusions on record. These block allocation regardless of fit."
        ).assertExists()
    }

    @Test
    fun anInferredExamNameSaysSo() {
        render(readiness())
        compose.onNodeWithText(
            "Likely exam: Microsoft Certified: Azure Solutions Architect Expert (inferred from delivery history)"
        ).assertExists()
    }

    @Test
    fun coursesWithNoPolicyAreUnknownNotClean() {
        render(readiness(unknown = 5))
        compose.onNodeWithText(
            "5 courses have no exam policy in RMS, so their requirement is unknown rather than clear."
        ).assertExists()
    }

    @Test
    fun aTrainerWithNoLeaveReadsAsClear() {
        render(readiness(leave = 0, confirmed = 0, exclusions = 0))
        compose.onNodeWithText("No leave or confirmed commitments in the next 90 days.").assertExists()
    }

    @Test
    fun theScreenStillRendersWithoutReadiness() {
        render(null)
        compose.onNodeWithText("Now").assertExists()
    }
}
