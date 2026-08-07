package com.example.skillsync.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.skillsync.HomeTab
import com.example.skillsync.theme.SkillSyncTheme
import com.example.skillsync.ui.main.CoursesTab
import com.example.skillsync.ui.main.DashboardTab
import com.example.skillsync.ui.main.SkillSyncNavBar
import com.example.skillsync.ui.main.TeamTab
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
 *
 * These cover composition and data-binding, not visual layout — a screenshot
 * harness or a real device is still needed for that.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h3600dp")
class ScreenRenderTest {

    @get:Rule val compose = createComposeRule()

    // ── Fixtures shaped like the real payloads ──────────────────────────────

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
        "feedback_risk" to "Low",
        "negative_count" to 0.0,
        "upcoming_count" to 2.0,
        "recommended_action" to "Monitor performance",
        "utilization_series" to listOf(
            mapOf("month" to "Jun 2026", "utilization" to 43.0),
            mapOf("month" to "Jul 2026", "utilization" to 52.0),
            mapOf("month" to "Aug 2026", "utilization" to 22.0),
        ),
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
        "manager_kpis" to mapOf(
            "total_team_members" to 2.0,
            "active_trainers" to 1.0,
            "unallocated_trainers" to 1.0,
            "active_batches" to 1.0,
            "upcoming_batches" to 2.0,
            "training_days_delivered" to 18.0,
            "training_days_window_label" to "last 30 days",
            "avg_team_utilization" to 32.0,
            "utilization_sample" to 2.0,
            "high_risk_trainers" to 0.0,
            "deployable_pct" to 100.0,
            "unknown_status" to 0.0,
            "open_actions" to 1.0,
            "open_demand" to 12.0,
        ),
        "trainer_operations_df" to listOf(trainerOps()),
        "trainer_current_state_df" to listOf(stateDelivering()),
        "batch_engagement_df" to listOf(
            mapOf(
                "course_name" to "AI-102T00: Develop AI Solutions in Azure",
                "trainer_name" to "Abhinav Samant",
                "engagement_state" to "current",
                "delivery_mode" to "ILO",
                "start_at" to "2026-08-03",
            )
        ),
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

    private fun managerProfile() = mapOf<String, Any>(
        "email" to "aishwar.c@koenig-solutions.com",
        "name" to "Aishwar Nigam",
        "first_name" to "Aishwar",
        "photo_url" to "",
        "initials" to "AN",
        "designation" to "Senior Corporate Trainer (Global)",
        "role" to "Delivery Manager",
        "tenure_years" to 3.0,
        "team" to mapOf("size" to 2.0, "direct" to 2.0, "indirect" to 0.0, "reachable" to true),
    )

    private fun capabilityPayload() = mapOf<String, Any>(
        "team_size" to 1.0,
        "kpis" to mapOf(
            "certified_trainers" to 1.0,
            "certification_gap_count" to 3.0,
            "team_skill_coverage_pct" to 67.0,
            "avg_trainer_coverage_pct" to 40.0,
            "distinct_courses" to 30.0,
            "single_owner_courses" to 24.0,
            "certification_tracks" to 5.0,
            "team_readiness_score" to 54.0,
            "ready_trainers" to 0.0,
        ),
        "trainers" to listOf(
            mapOf(
                "trainer_name" to "Abhinav Samant",
                "trainer_email" to "abhinav.samant@koenig-solutions.com",
                "photo_url" to "",
                "utilization" to 39.0,
                "readiness_score" to 46.0,
                "readiness_bucket" to "Developing",
                "course_count" to 30.0,
                "avg_qubits" to 42.0,
                "courses" to listOf(
                    mapOf(
                        "course" to "AI-102T00: Develop AI Solutions in Azure",
                        "vendor" to "Microsoft", "qubits_score" to 98.0,
                        "skill_level" to "5", "approved" to false,
                        "future_skill" to false, "delivered" to 1.0,
                    )
                ),
                "certification" to mapOf(
                    "held" to listOf(
                        mapOf("name" to "Microsoft Certified: Power BI Data Analyst Associate",
                              "code" to "PL-300", "logo" to ""),
                        mapOf("name" to "Microsoft Azure Data Fundamentals",
                              "code" to "DP-900", "logo" to ""),
                    ),
                    "accreditations" to listOf("MCT"),
                    "missing" to listOf(
                        mapOf("code" to "AI-102", "name" to "Azure AI Engineer Associate",
                              "because" to "AI-102T00: Develop AI Solutions in Azure",
                              "priority" to "high", "delivered" to 1.0, "qubits_score" to 98.0),
                    ),
                    "recommended" to listOf(
                        mapOf("code" to "DP-203", "name" to "Azure Data Engineer Associate",
                              "because" to "DP-900"),
                    ),
                    "coverage_pct" to 40.0,
                    "gap_count" to 3.0,
                    "held_codes" to listOf("DP-900", "MCT", "PL-300"),
                    "taught_codes" to listOf("AI-102", "DP-900", "PL-300"),
                ),
            )
        ),
        "courses" to listOf(
            mapOf(
                "course" to "PL-300T00: Design and Manage Analytics Solutions Using Power BI",
                "vendor" to "Microsoft",
                "exam_code" to "PL-300",
                "certification" to "Power BI Data Analyst Associate",
                "future_skill" to false,
                "owner_count" to 2.0,
                "certified_count" to 2.0,
                "approved_count" to 0.0,
                "delivered_total" to 4.0,
                "best_qubits" to 100.0,
                "coverage" to "shared",
                "owners" to listOf(
                    mapOf("trainer_name" to "Abhinav Samant",
                          "trainer_email" to "abhinav.samant@koenig-solutions.com",
                          "photo_url" to "", "qubits_score" to 100.0, "skill_level" to "5",
                          "approved" to false, "delivered" to 3.0, "certified" to true),
                    mapOf("trainer_name" to "Niharika Niharika",
                          "trainer_email" to "niharika.n@koenig-solutions.com",
                          "photo_url" to "", "qubits_score" to 98.0, "skill_level" to "4",
                          "approved" to false, "delivered" to 1.0, "certified" to true),
                ),
            ),
            mapOf(
                "course" to "Cisco NSO Administration and DevOps (NSO303)",
                "vendor" to "Cisco", "exam_code" to "", "certification" to "",
                "future_skill" to true, "owner_count" to 1.0, "certified_count" to 0.0,
                "approved_count" to 0.0, "delivered_total" to 0.0, "best_qubits" to 40.0,
                "coverage" to "single",
                "owners" to listOf(
                    mapOf("trainer_name" to "Abhinav Samant",
                          "trainer_email" to "abhinav.samant@koenig-solutions.com",
                          "photo_url" to "", "qubits_score" to 40.0, "skill_level" to "2",
                          "approved" to false, "delivered" to 0.0, "certified" to false),
                ),
            ),
        ),
    )

    // ── Dashboard ───────────────────────────────────────────────────────────

    @Composable
    private fun Dashboard(
        capability: Map<String, Any>? = capabilityPayload(),
        onTrainerClick: (String, String) -> Unit = { _, _ -> },
    ) {
        DashboardTab(
            data = dashboardPayload(),
            profile = managerProfile(),
            capability = capability,
            capabilityLoading = false,
            email = "aishwar.c@koenig-solutions.com",
            onTrainerClick = onTrainerClick,
            onOpenProfile = {},
            onDrill = {},
        )
    }

    @Test
    fun dashboard_greetsTheSignedInManagerByName() {
        compose.setContent { SkillSyncTheme { Dashboard() } }
        compose.onNodeWithText("Aishwar Nigam").assertExists()
        compose.onNodeWithText("DELIVERY MANAGER").assertExists()
        compose.onNodeWithText("Senior Corporate Trainer (Global)").assertExists()
    }

    @Test
    fun dashboard_showsTrainerAndTheBatchTheyAreIn() {
        compose.setContent { SkillSyncTheme { Dashboard() } }
        // Several of these legitimately appear in more than one place (KPI tile,
        // Top performers row, roster card), so assert presence not global counts.
        compose.onAllNodesWithText("Abhinav Samant").onFirst().assertExists()
        compose.onAllNodesWithText("Delivering").onFirst().assertExists()
        compose.onAllNodesWithText("39%").onFirst().assertExists()
        compose.onAllNodesWithText("Light").onFirst().assertExists()
        compose.onNodeWithText("Top performing").assertExists()
        compose.onNodeWithText("AI-102T00: Develop AI Solutions in Azure").assertExists()
        compose.onNodeWithText("ILO · Microsoft · 1 pax · ends in 2 d").assertExists()
        compose.onNodeWithText("LIVE").assertExists()
    }

    /** The twelve manager KPIs the dashboard is required to surface. */
    @Test
    fun dashboard_rendersEveryManagerKpi() {
        compose.setContent { SkillSyncTheme { Dashboard() } }
        listOf(
            "TEAM MEMBERS", "ACTIVE TRAINERS", "UNALLOCATED", "ACTIVE BATCHES",
            "UPCOMING BATCHES", "TRAINING DAYS", "AVG UTILISATION", "CERTIFIED",
            "CERT GAPS", "HIGH RISK", "READINESS", "SKILL COVERAGE",
        ).forEach { compose.onNodeWithText(it).assertExists() }
    }

    /**
     * Certification KPIs come from a slower second call. Until it lands they must
     * show a placeholder — rendering 0 would state, falsely, that the team holds
     * no certifications.
     */
    @Test
    fun dashboard_certKpisAreNotZeroBeforeCapabilityLoads() {
        compose.setContent {
            SkillSyncTheme {
                DashboardTab(
                    data = dashboardPayload(),
                    profile = managerProfile(),
                    capability = null,
                    capabilityLoading = false,
                    email = "aishwar.c@koenig-solutions.com",
                    onTrainerClick = { _, _ -> },
                    onOpenProfile = {},
                    onDrill = {},
                )
            }
        }
        compose.onNodeWithText("SKILL COVERAGE").assertExists()
        // Unloaded capability KPIs offer to fetch; they never render a false 0.
        compose.onAllNodesWithText("Tap to load").onFirst().assertExists()
    }

    @Test
    fun dashboard_trainerCardIsClickable() {
        var clickedEmail = ""
        compose.setContent {
            SkillSyncTheme { Dashboard(onTrainerClick = { e, _ -> clickedEmail = e }) }
        }
        // The last occurrence is the roster card; earlier ones are chart labels
        // and the Top performers row, which are not all navigation targets.
        compose.onAllNodesWithText("Abhinav Samant").onLast().performClick()
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
                    capability = null,
                    onClick = {},
                )
            }
        }
        compose.onNodeWithText("—").assertExists()
        compose.onAllNodesWithText("Unknown").onFirst().assertExists()
        compose.onNodeWithText("Assignment data unavailable from RMS").assertExists()
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

    @Test
    fun navBar_exposesAllFiveDestinations() {
        compose.setContent {
            SkillSyncTheme { SkillSyncNavBar(HomeTab.DASHBOARD) {} }
        }
        listOf("Home", "Team", "Courses", "Demand", "Actions").forEach {
            compose.onNodeWithText(it).assertExists()
        }
    }

    // ── Team ────────────────────────────────────────────────────────────────

    @Test
    fun teamTab_rendersRosterAndFilterControls() {
        compose.setContent {
            SkillSyncTheme {
                TeamTab(dashboardPayload(), capabilityPayload()) { _, _ -> }
            }
        }
        compose.onNodeWithText("Filters").assertExists()
        compose.onNodeWithText("Sort: Utilisation").assertExists()
        compose.onNodeWithText("1 of 1 trainers").assertExists()
        compose.onAllNodesWithText("Abhinav Samant").onFirst().assertExists()
        // Certification signal from the capability payload reaches the roster card.
        compose.onNodeWithText("Developing").assertExists()
        compose.onNodeWithText("2 cert").assertExists()
        compose.onNodeWithText("3 gap").assertExists()
    }

    @Test
    fun teamTab_statusFilterNarrowsTheRoster() {
        compose.setContent {
            SkillSyncTheme {
                TeamTab(dashboardPayload(), capabilityPayload()) { _, _ -> }
            }
        }
        // The only trainer is Delivering, so filtering to Preparing empties it.
        compose.onNodeWithText("Preparing").performClick()
        compose.onNodeWithText("0 of 1 trainers").assertExists()
        compose.onNodeWithText("No trainer matches these filters.").assertExists()
    }

    // ── Courses ─────────────────────────────────────────────────────────────

    @Test
    fun coursesTab_showsOwnershipAndCertificationMapping() {
        compose.setContent {
            SkillSyncTheme { CoursesTab(capabilityPayload(), false) { _, _ -> } }
        }
        compose.onNodeWithText("Course catalogue").assertExists()
        compose.onNodeWithText(
            "PL-300T00: Design and Manage Analytics Solutions Using Power BI"
        ).assertExists()
        compose.onNodeWithText("Power BI Data Analyst Associate").assertExists()
        compose.onNodeWithText("2/2 certified").assertExists()
        compose.onNodeWithText("2 trainers can deliver").assertExists()
        // A course only one person can teach is flagged as delivery risk.
        compose.onNodeWithText("1 trainer can deliver").assertExists()
        compose.onAllNodesWithText("Single owner").onFirst().assertExists()
    }

    @Test
    fun coursesTab_filtersToSingleOwnerCourses() {
        compose.setContent {
            SkillSyncTheme { CoursesTab(capabilityPayload(), false) { _, _ -> } }
        }
        compose.onNodeWithText("2 of 2 courses").assertExists()
        compose.onNodeWithText("Single owner only").performClick()
        compose.onNodeWithText("1 of 2 courses").assertExists()
    }

    // ── Trainer 360 ─────────────────────────────────────────────────────────

    private fun trainer360Payload() = mapOf<String, Any>(
        "identity" to mapOf(
            "name" to "Abhinav Samant",
            "email" to "Abhinav.Samant@koenig-solutions.com",
            "emp_code" to "3815",
            "designation" to "Corporate Trainer",
            "reports_to" to "aishwar.c@koenig-solutions.com",
            "direct_report" to true,
            "date_of_joining" to "2025-11-19",
            "tenure_years" to 0.7,
            "photo_url" to "",
            "languages" to listOf(mapOf("language" to "English", "level" to "Good")),
            "clients" to listOf("Mercedes-Benz", "Deloitte"),
            "summary" to "",
            "experience" to "",
            "has_resume" to true,
        ),
        "metrics" to mapOf(
            "readiness_score" to 46.0,
            "readiness_bucket" to "Developing",
            "risk_score" to 0.0,
            "risk_level" to "Low",
            "skill_match_pct" to 40.0,
            "team_rank" to 1.0,
            "team_size" to 2.0,
            "avg_qubits" to 42.0,
        ),
        "utilization" to mapOf(
            "current" to 39.0,
            "available" to true,
            "peak" to 52.2,
            "upcoming_load" to 0.0,
            "bench_months" to 0.0,
            "series" to listOf(
                mapOf("month" to "Jun 2026", "load" to 75.8, "utilization" to 43.1),
                mapOf("month" to "Jul 2026", "load" to 96.0, "utilization" to 52.2),
                mapOf("month" to "Aug 2026", "load" to 38.0, "utilization" to 22.6),
            ),
        ),
        "capability" to mapOf(
            "total_courses" to 30.0,
            "approved_courses" to 0.0,
            "future_skills" to 2.0,
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
        "certifications" to mapOf(
            "count" to 2.0,
            "accreditation_count" to 1.0,
            "held" to listOf(
                mapOf("name" to "Microsoft Certified: Power BI Data Analyst Associate",
                      "code" to "PL-300", "logo" to ""),
                mapOf("name" to "Microsoft Azure Data Fundamentals",
                      "code" to "DP-900", "logo" to ""),
            ),
            "accreditations" to listOf("MCT"),
            "missing" to listOf(
                mapOf("code" to "AI-102", "name" to "Azure AI Engineer Associate",
                      "because" to "AI-102T00: Develop AI Solutions in Azure",
                      "priority" to "high", "delivered" to 1.0),
            ),
            "recommended" to listOf(
                mapOf("code" to "DP-203", "name" to "Azure Data Engineer Associate",
                      "because" to "DP-900"),
            ),
            "coverage_pct" to 40.0,
            "gap_count" to 1.0,
        ),
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
        listOf(
            "Personal details", "Utilisation", "Capability metrics",
            "Certifications", "Capability", "Delivery",
            "Feedback & incidents", "Availability",
        ).forEach { compose.onAllNodesWithText(it).onFirst().assertExists() }
        compose.onAllNodesWithText("Abhinav Samant").onFirst().assertExists()
        compose.onNodeWithText("Corporate Trainer").assertExists()
    }

    /** Held / missing / recommended — the certification gap analysis in full. */
    @Test
    fun trainer360_showsCertificationGapAnalysis() {
        compose.setContent { SkillSyncTheme { Trainer360Content(trainer360Payload()) } }
        compose.onNodeWithText("TEACHING ACCREDITATION").assertExists()
        compose.onNodeWithText("MCT").assertExists()
        compose.onNodeWithText("CERTIFICATIONS HELD").assertExists()
        compose.onNodeWithText("Microsoft Certified: Power BI Data Analyst Associate").assertExists()
        compose.onAllNodesWithText("PL-300").onFirst().assertExists()
        compose.onNodeWithText(
            "MISSING — TEACHES THE COURSE, HOLDS NO CERTIFICATION"
        ).assertExists()
        compose.onNodeWithText("Azure AI Engineer Associate").assertExists()
        compose.onNodeWithText("HIGH").assertExists()
        compose.onNodeWithText("RECOMMENDED NEXT").assertExists()
        compose.onNodeWithText("Azure Data Engineer Associate").assertExists()
    }

    @Test
    fun trainer360_showsCapabilityMetrics() {
        compose.setContent { SkillSyncTheme { Trainer360Content(trainer360Payload()) } }
        compose.onAllNodesWithText("Developing").onFirst().assertExists()
        compose.onAllNodesWithText("Low").onFirst().assertExists()
        compose.onNodeWithText("skill match").assertExists()
        compose.onNodeWithText("#1").assertExists()
        compose.onNodeWithText("of 2 in team").assertExists()
    }

    /** Empty feedback must be stated as absence of data, not implied as a clean record. */
    @Test
    fun trainer360_distinguishesNoDataFromCleanRecord() {
        compose.setContent { SkillSyncTheme { Trainer360Content(trainer360Payload()) } }
        compose.onNodeWithText(
            "RMS returned no feedback records", substring = true,
        ).assertExists()
    }

    @Test
    fun trainer360_survivesEmptyPayload() {
        // The endpoint can legitimately return sparse data; this must not crash.
        compose.setContent { SkillSyncTheme { Trainer360Content(emptyMap()) } }
        compose.onNodeWithText("Trainer").assertExists()
    }
}
