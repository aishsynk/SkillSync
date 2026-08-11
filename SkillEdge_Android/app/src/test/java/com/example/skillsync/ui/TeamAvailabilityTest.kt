package com.example.skillsync.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.example.skillsync.theme.SkillSyncTheme
import com.example.skillsync.ui.main.TeamMemberCard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The team card's availability line.
 *
 * The card showed utilisation as the only availability signal — the same
 * fallacy corrected on Demand. These pin the replacement.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class TeamAvailabilityTest {

    @get:Rule
    val compose = createComposeRule()

    private val trainer = mapOf<String, Any>(
        "trainer_name" to "Abhinav Samant",
        "official_email" to "a@k.com",
        "designation" to "Corporate Trainer",
        "current_utilization" to 40.0,
        "upcoming_count" to 2.0,
    )

    // Doubles, as Gson decodes them from the wire.
    private fun availability(
        leave: Int = 0, confirmed: Int = 0, exclusions: Int = 0,
        verified: Boolean = true, nextLeave: List<String> = emptyList(),
    ) = mapOf<String, Any>(
        "leave_days" to leave.toDouble(),
        "confirmed_days" to confirmed.toDouble(),
        "client_exclusions" to exclusions.toDouble(),
        "verified" to verified,
        "next_leave" to nextLeave,
    )

    private fun render(a: Map<String, Any>?) {
        compose.setContent {
            SkillSyncTheme {
                TeamMemberCard(trainer, state = null, calendarAvailability = a) {}
            }
        }
    }

    @Test
    fun aLightlyUtilisedTrainerOnLeaveIsNotShownAsFree() {
        // 40% utilised would previously have read as 60% "available capacity".
        render(availability(leave = 3, nextLeave = listOf("2026-09-02")))
        compose.onNodeWithText("Leave from 2026-09-02").assertExists()
    }

    @Test
    fun aClearTrainerSaysSo() {
        render(availability())
        compose.onNodeWithText("No leave booked").assertExists()
    }

    @Test
    fun committedDaysAreReported() {
        render(availability(confirmed = 6))
        compose.onNodeWithText("6 committed days").assertExists()
    }

    @Test
    fun clientExclusionsAreSurfaced() {
        render(availability(exclusions = 2))
        compose.onNodeWithText("2 client exclusions").assertExists()
    }

    @Test
    fun anUnverifiedScheduleIsNotShownAsClear() {
        render(availability(verified = false))
        compose.onNodeWithText("Availability unverified").assertExists()
        compose.onAllNodesWithText("No leave booked").assertCountEquals(0)
    }

    @Test
    fun theCardStillRendersWithoutAvailability() {
        render(null)
        compose.onNodeWithText("Abhinav Samant").assertExists()
    }
}
