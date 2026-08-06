package com.example.skillsync.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.skillsync.HomeTab
import com.example.skillsync.theme.SkillSyncTheme
import com.example.skillsync.ui.main.DashboardTab
import com.example.skillsync.ui.main.DemandTab
import com.example.skillsync.ui.main.SkillSyncNavBar
import com.example.skillsync.ui.main.TrainerCard
import com.example.skillsync.ui.trainer.Trainer360Content
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Renders the real screens on the JVM. There is no emulator on this project, so
 * without these a composition crash or a wrong-data path would only surface once
 * the APK was installed. Payloads mirror verified live RMS responses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class ScreenRenderTest {

    @get:Rule val compose = createComposeRule()

    // ── Fixtures shaped like the real /unified-manager-intelligence payload ──

    private fun trainerOps(
        name: String = "Abhinav Samant",
        email: String = "abhinav.samant@koenig-solutions.com",
        util: Any? = 39.0,
        capacity: String = "Light",
    ) = mapOf(
        "trainer_name" to name,
        "official_email" to email,
        "emp_id" to "3815",
        "designation" to "Corporate Trainer",
        "direct_or_indirect" to "direct",
        "current_utilization" to util,
        "capacity_bucket" to capacity,
        "negative_count" to 0.0,
        "upcoming_count" to 2.0,
        "recommended_action" to "Monitor performance",
    )

    private fun stateDelivering(email: String = "abhinav.samant@koenig-solutions.com") = mapOf(
        "trainer_email" to email,
        "current_status" to "teaching_now",
        "status_label" to "Delivering",
        "confidence" to 90.0,
        "reason" to "Currently on assignment",
        "current_batch" to mapOf(
            "course_name" to "AI-102T00: Develop AI Solutions in Azure",
            "delivery_mode" to "ILO",
            "vendor" to "Microsoft",
            "participants" to 1.0,
            "days_left" to 2.0,
            "start_at" to "2026-08-03",
            "end_at" to "2026-08-06",
        ),
        "next_batch" to emptyMap<String, Any>(),
    )

    private fun dashboardPayload() = mapOf<String, Any>(
        "trainer_operations_df" to listOf(trainerOps()),
        "trainer_current_state_df" to listOf(stateDelivering()),
        "unallocated_demand_df" to listOf(
            mapOf(
                "demand_id" to "264455",
                "course_name" to "Cisco NSO Administration and DevOps (NSO303)",
                "start_date" to "2026-08-24",
                "delivery_mode" to "ILO",
                "customer" to "Cisco",
                "participants" to "1",
            )
        ),
        "manager_action_objects" to emptyList<Map<String, Any>>(),
        "trainer_decision_objects" to emptyList<Map<String, Any>>(),
    )

    // ── Dashboard ───────────────────────────────────────────────────────────

    @Test
    fun dashboard_showsTrainerAndTheBatchTheyAreIn() {
        compose.setContent {
            SkillSyncTheme { DashboardTab(dashboardPayload(), { _, _ -> }, {}) }
        }
        // Several of these legitimately appear in more than one place (KPI tile,
        // Top performers row, roster card). Asserting exact global counts made the
        // test break every time a section was added, so assert presence instead.
        compose.onAllNodesWithText("Abhinav Samant").onFirst().assertExists()
        compose.onAllNodesWithText("Delivering").onFirst().assertExists()
        compose.onAllNodesWithText("39%").onFirst().assertExists()
        compose.onAllNodesWithText("Light").onFirst().assertExists()
        // These are unique, and are the point of the card.
        compose.onNodeWithText("Top performing").assertExists()
        compose.onNodeWithText("AI-102T00: Develop AI Solutions in Azure").assertExists()
        compose.onNodeWithText("ILO · Microsoft · 1 pax · ends in 2 d").assertExists()
        compose.onNodeWithText("LIVE").assertExists()
    }

    @Test
    fun dashboard_trainerCardIsClickable() {
        var clickedEmail = ""
        compose.setContent {
            SkillSyncTheme {
                DashboardTab(dashboardPayload(), { e, _ -> clickedEmail = e }, {})
            }
        }
        // Both the Top performers row and the roster card open the profile.
        compose.onAllNodesWithText("Abhinav Samant")[0].performClick()
        assertEquals("abhinav.samant@koenig-solutions.com", clickedEmail)
    }

    /** Regression: a missing utilisation must read "—", never a confident 0%. */
    @Test
    fun trainerCard_showsDashWhenUtilisationMissing() {
        compose.setContent {
            SkillSyncTheme {
                TrainerCard(
                    trainer = trainerOps(util = null, capacity = "Unknown"),
                    state = mapOf(
                        "current_status" to "unknown",
                        "status_label" to "Unknown",
                        "confidence" to 0.0,
                        "reason" to "Assignment data unavailable from RMS",
                        "current_batch" to emptyMap<String, Any>(),
                        "next_batch" to emptyMap<String, Any>(),
                    ),
                    onClick = {},
                )
            }
        }
        compose.onNodeWithText("—").assertExists()
        compose.onNodeWithText("Unknown").assertExists()
        compose.onNodeWithText("Assignment data unavailable from RMS").assertExists()
    }

    @Test
    fun demandTab_rendersRealCourseNames() {
        compose.setContent { SkillSyncTheme { DemandTab(dashboardPayload()) } }
        compose.onNodeWithText("Cisco NSO Administration and DevOps (NSO303)").assertExists()
    }

    @Test
    fun navBar_reportsTabSelection() {
        var selected = HomeTab.DASHBOARD
        compose.setContent {
            SkillSyncTheme { SkillSyncNavBar(HomeTab.DASHBOARD) { selected = it } }
        }
        compose.onNodeWithText("Demand").performClick()
        assertEquals(HomeTab.DEMAND, selected)
    }

    // ── Trainer 360 ─────────────────────────────────────────────────────────

    private fun trainer360Payload() = mapOf<String, Any>(
        "identity" to mapOf(
            "name" to "Abhinav Samant",
            "email" to "Abhinav.Samant@koenig-solutions.com",
            "emp_code" to "3815",
            "date_of_joining" to "2025-11-19",
        ),
        "utilization" to mapOf(
            "current" to 39.0,
            "peak" to 52.2,
            "series" to listOf(
                mapOf("month" to "Jun 2026", "load" to 75.8, "utilization" to 43.1),
                mapOf("month" to "Jul 2026", "load" to 96.0, "utilization" to 52.2),
                mapOf("month" to "Aug 2026", "load" to 38.0, "utilization" to 22.6),
            ),
        ),
        "capability" to mapOf(
            "total_courses" to 30.0,
            "approved_courses" to 0.0,
            "avg_qubits" to 42.0,
            "courses" to listOf(
                mapOf(
                    "course" to "AI-102T00: Develop AI Solutions in Azure",
                    "vendor" to "Microsoft", "qubits_score" to 98.0,
                    "skill_level" to "5", "approved" to false,
                    "future_skill" to false, "delivered" to 1.0,
                )
            ),
        ),
        "certifications" to mapOf("count" to 1.0, "held" to listOf("MCT")),
        "delivery" to mapOf(
            "total" to 13.0, "upcoming" to 0.0, "current" to 1.0,
            "assignments" to listOf(
                mapOf(
                    "course" to "AI-102T00: Develop AI Solutions in Azure",
                    "mode" to "ILO", "participants" to 1.0,
                    "start_at" to "2026-08-03", "state" to "current",
                    "location" to "", "vendor" to "Microsoft",
                )
            ),
        ),
        "feedback" to mapOf(
            "negative_total" to 0.0, "hr_positive" to 0.0,
            "hr_negative" to 0.0, "negative_details" to emptyList<Map<String, Any>>(),
        ),
        "availability" to mapOf(
            "off_dates" to emptyMap<String, Any>(),
            "leave_data_available" to false,
        ),
    )

    @Test
    fun trainer360_rendersEverySection() {
        compose.setContent { SkillSyncTheme { Trainer360Content(trainer360Payload()) } }
        compose.onNodeWithText("Abhinav Samant").assertExists()
        compose.onNodeWithText("Utilisation trend").assertExists()
        compose.onNodeWithText("Capability").assertExists()
        compose.onNodeWithText("MCT").assertExists()
    }

    /** Empty feedback must be stated as absence of data, not implied as a clean record. */
    @Test
    fun trainer360_distinguishesNoDataFromCleanRecord() {
        compose.setContent { SkillSyncTheme { Trainer360Content(trainer360Payload()) } }
        val note = compose.onNodeWithText(
            "RMS returned no feedback records", substring = true,
        )
        note.assertExists()
    }

    @Test
    fun trainer360_survivesEmptyPayload() {
        // The endpoint can legitimately return sparse data; this must not crash.
        compose.setContent { SkillSyncTheme { Trainer360Content(emptyMap()) } }
        compose.onNodeWithText("Trainer").assertExists()
    }
}
