package com.example.skillsync.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage
import com.example.skillsync.core.storage.LocalCache
import com.example.skillsync.core.storage.ViberConfig
import com.example.skillsync.core.storage.ViberOutboxItem
import com.example.skillsync.feature.ai.TeamFact
import com.example.skillsync.feature.ai.TrainerFact
import com.example.skillsync.feature.ai.ui.CopilotScreen
import com.example.skillsync.feature.report.ui.AccountsScreen
import com.example.skillsync.feature.report.ui.AccountsViewModel
import com.example.skillsync.feature.report.ui.HrMonthlyReportScreen
import com.example.skillsync.feature.report.ui.HrMonthlyReportViewModel
import com.example.skillsync.feature.report.ui.SkillRequestsScreen
import com.example.skillsync.feature.report.ui.SkillRequestsViewModel
import com.example.skillsync.feature.viber.ui.ViberAutomationScreen
import com.example.skillsync.feature.viber.ui.ViberAutomationViewModel
import com.example.skillsync.theme.AuroraBackground
import com.example.skillsync.theme.SkillSyncTheme
import com.example.skillsync.theme.skill
import org.junit.Rule
import org.junit.Test

/**
 * Real-emulator captures for inner pages 5-9 (Phase 2): HR Monthly Review
 * (team and reportee), Skill Requests (empty and loaded), Delivery Agent,
 * Accounts and the Viber Dispatch Centre (rules and outbox).
 *
 * Same harness as [InnerPagesScreenshotTest]: production composables, fetch
 * lambdas or snapshot seams instead of the network, and nothing in a fixture
 * that the backend does not actually send.
 */
class InnerPagesPhase2ScreenshotTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val storage = TestStorage()
    private val manager = "aishwar.c@koenig-solutions.com"

    private fun save(name: String) {
        settle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        storage.openOutputFile("$name.png").use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** The clock is manual (aurora/shimmer animations never idle), so pump it. */
    private fun settle() {
        repeat(6) { compose.mainClock.advanceTimeBy(250); Thread.sleep(150) }
    }

    private fun start(content: @androidx.compose.runtime.Composable () -> Unit) {
        LocalCache.init(InstrumentationRegistry.getInstrumentation().targetContext)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SkillSyncTheme {
                Box(Modifier.fillMaxSize()) {
                    AuroraBackground()
                    content()
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
    }

    // ── 5. HR Monthly Review ─────────────────────────────────────────────────

    private fun reportee(
        name: String,
        email: String,
        util: Int,
        hrScore: Int,
        trajectory: String,
        negFeedback: Int = 0,
        certGaps: Int = 0,
    ) = mapOf<String, Any>(
        "name" to name, "email" to email,
        "hr_score" to hrScore, "utilisation_pct" to util, "batch_count" to 4,
        "avg_qubits" to 78, "negative_feedback_count" to negFeedback,
        "hr_positive_count" to if (hrScore >= 80) 1 else 0, "hr_negative_count" to 0,
        "certs_missing" to certGaps, "certs_held" to 6,
        "top_courses" to listOf("AZ-104T00", "AZ-500T00"),
        "trajectory" to trajectory,
        "structured_feedback" to mapOf(
            "strength" to "Held a 94% learner score across four batches this month.",
            "area_of_improvement" to
                if (certGaps > 0) "Certification for AZ-104 has lapsed while still delivering it."
                else "Session recordings are uploaded late in most batches.",
            "other_feedback" to "Picked up two short-notice batches without escalation.",
            "trajectory" to trajectory,
            "formatted_text" to "Monthly evaluation for $name.",
        ),
        "message_monthly" to
            "Hi ${name.substringBefore(" ")}, four batches delivered this month at 94% learner " +
            "feedback. One thing to tighten: session recordings are going up late.",
        "message_monthend" to
            "Hi ${name.substringBefore(" ")}, closing the month: four batches, 94% learner " +
            "feedback, and recordings now the one open item to fix before next month.",
        "trainer_index" to mapOf(
            "total_score" to 74, "tier" to "Tier 2: Platinum", "tier_badge" to "Platinum",
            "tier_level" to 2, "utilization_pts" to 22, "quality_pts" to 18,
            "certifications_pts" to 14, "instructor_pts" to 8, "knowledge_sharing_pts" to 6,
            "beast_ai_pts" to 6,
        ),
    )

    private fun hrPayload() = mapOf<String, Any>(
        "month" to "2026-09",
        "team_summary" to mapOf(
            "headcount" to 3, "avg_utilisation" to 71, "avg_hr_score" to 78,
            "total_batches" to 12, "total_negative_feedback" to 1,
            "total_positive_hr" to 2, "total_negative_hr" to 0, "cert_gap_count" to 1,
        ),
        "reportees" to listOf(
            reportee("Niharika Rao", "niharika@koenig-solutions.com", 82, 86, "High Performer"),
            reportee("Priya Sharma", "priya@koenig-solutions.com", 68, 74, "Needs Coaching", 1, 1),
            reportee("Rahul Verma", "rahul@koenig-solutions.com", 44, 71, "Bench Upskilling"),
        ),
        "team_digest_monthly" to
            "Team, twelve batches delivered this month at an average 71% utilisation. " +
            "One lapsed certification and one negative feedback are the open items.",
        "team_digest_monthend" to
            "Team, closing September: twelve batches, 71% average utilisation. " +
            "The AZ-104 certification gap carries into October as the one thing to clear.",
        "loading" to false,
    )

    @Test
    fun hr_monthly_team_screenshot() {
        val vm = HrMonthlyReportViewModel()
        vm.renderSnapshot(hrPayload())
        start {
            HrMonthlyReportScreen(
                // Blank manager so the screen keeps the injected snapshot rather
                // than starting a live month fetch.
                managerEmail = "",
                onBack = {},
                vm = vm,
            )
        }
        save("07_hr_monthly_team")
    }

    @Test
    fun hr_monthly_reportee_screenshot() {
        val vm = HrMonthlyReportViewModel()
        vm.renderSnapshot(hrPayload())
        start {
            HrMonthlyReportScreen(managerEmail = "", onBack = {}, vm = vm)
        }
        // Scroll the reportee list to the first card by index, not by a touch
        // gesture: a real swipe hung instrumentation on this device (touch
        // injection deadlocking against the manually-paused Compose clock).
        // performScrollToIndex is a semantics action, not a gesture, and is
        // the deterministic way Compose tests scroll a LazyColumn.
        // Item order: 0=team summary, 1=team message, 2=filter chips,
        // 3="Reportees" header, 4=first reportee card (Niharika Rao).
        settle()
        compose.onNodeWithTag("hrMonthlyReporteeList").performScrollToIndex(4)
        settle()
        save("08_hr_monthly_reportee")
    }

    // ── 6. Skill Requests ────────────────────────────────────────────────────

    private fun skillRequestsPayload(vararg rows: Map<String, Any>) =
        mapOf<String, Any>("requests" to rows.toList())

    private fun skillRequest(id: String, email: String, course: String, level: Int) =
        mapOf<String, Any>(
            "id" to id, "reportee_email" to email, "manager_email" to manager,
            "course_id" to "C-$level", "course_name" to course,
            "requested_level" to level, "from_date" to "2026-10-01",
            "status" to "pending", "created_at" to "2026-09-14T09:12:00",
        )

    @Test
    fun skill_requests_empty_screenshot() {
        val vm = SkillRequestsViewModel(fetchPending = { skillRequestsPayload() })
        start { SkillRequestsScreen(managerEmail = manager, onBack = {}, vm = vm) }
        save("09_skill_requests_empty")
    }

    @Test
    fun skill_requests_loaded_screenshot() {
        val vm = SkillRequestsViewModel(
            fetchPending = {
                skillRequestsPayload(
                    skillRequest("skreq_1", "niharika@koenig-solutions.com", "AZ-500T00 Azure Security", 5),
                    skillRequest("skreq_2", "priya@koenig-solutions.com", "DP-700T00 Fabric Data Engineer", 5),
                    skillRequest("skreq_3", "rahul@koenig-solutions.com", "SC-200T00 Security Operations", 6),
                )
            },
        )
        start { SkillRequestsScreen(managerEmail = manager, onBack = {}, vm = vm) }
        save("10_skill_requests_loaded")
    }

    // ── 7. Delivery Agent ────────────────────────────────────────────────────

    /** The agent takes facts directly, so no view model or seam is involved. */
    private fun teamFact() = TeamFact(
        trainers = listOf(
            TrainerFact(
                email = "niharika@koenig-solutions.com", name = "Niharika Rao",
                utilisation = 82, capacityBucket = "Optimal", readiness = 88,
                currentCourse = "AZ-104T00",
            ),
            TrainerFact(
                email = "priya@koenig-solutions.com", name = "Priya Sharma",
                utilisation = 68, capacityBucket = "Optimal", readiness = 72,
                feedbackRisk = "High", certGaps = listOf("AZ-104"),
            ),
            TrainerFact(
                email = "rahul@koenig-solutions.com", name = "Rahul Verma",
                utilisation = 44, capacityBucket = "Low", readiness = 61,
            ),
        ),
        utilisationHistory = listOf(64, 68, 71, 69, 71),
        avgUtilisation = 71,
        readinessScore = 74,
        activeDeliveries = 3,
        upcomingDeliveries = 5,
    )

    @Test
    fun delivery_agent_screenshot() {
        start {
            CopilotScreen(team = teamFact(), onTrainerClick = { _, _ -> }, onBack = {})
        }
        save("11_delivery_agent")
    }

    // ── 8. Accounts ──────────────────────────────────────────────────────────

    private fun accountsPayload() = mapOf<String, Any>(
        "manager" to manager,
        "window" to mapOf("past_days" to 90, "forward_days" to 60),
        "accounts" to listOf(
            mapOf(
                "name" to "Emirates NBD", "batches_delivered" to 7,
                "participants_delivered" to 84, "batches_upcoming" to 3,
                "open_demand_batches" to 2,
                "trainers" to listOf("Niharika Rao", "Priya Sharma"),
                "courses" to listOf("AZ-104T00", "AZ-500T00", "SC-200T00"),
                "last_delivery_date" to "2026-09-08", "next_start_date" to "2026-09-22",
                "avg_learner_rating" to 4.6,
            ),
            mapOf(
                "name" to "Etihad Airways", "batches_delivered" to 3,
                "participants_delivered" to 36, "batches_upcoming" to 1,
                "open_demand_batches" to 1,
                "trainers" to listOf("Rahul Verma"),
                "courses" to listOf("DP-700T00"),
                "last_delivery_date" to "2026-08-29", "next_start_date" to "2026-10-06",
            ),
            mapOf(
                "name" to "Majid Al Futtaim", "batches_delivered" to 2,
                "participants_delivered" to 22, "batches_upcoming" to 0,
                "open_demand_batches" to 0,
                "trainers" to listOf("Niharika Rao"),
                "courses" to listOf("AZ-900T00"),
                "last_delivery_date" to "2026-07-30", "next_start_date" to "",
            ),
        ),
        // 7 of 12 delivered batches: over half, so the concentration warning
        // must fire and must state 7 of 12 over the 90-day window.
        "concentration" to mapOf(
            "account" to "Emirates NBD", "batches_delivered" to 7,
            "team_batches_delivered" to 12, "share_pct" to 58.3,
        ),
        "summary" to mapOf(
            "account_count" to 3, "top_account" to "Emirates NBD",
            "top_account_share" to 58.3, "unspecified_batches" to 1,
        ),
        "generated_at" to "2026-09-16T06:00:00",
        "loading" to false,
    )

    @Test
    fun accounts_screenshot() {
        val vm = AccountsViewModel()
        vm.renderSnapshot(accountsPayload())
        start {
            AccountsScreen(
                // Blank manager keeps the injected snapshot; init() is guarded on it.
                managerEmail = "",
                onOpenTrainer = { _, _ -> }, onBack = {},
                vm = vm,
            )
        }
        save("12_accounts")
    }

    // ── 9. Viber Dispatch Centre ─────────────────────────────────────────────

    private fun outboxItem(id: String, category: String, name: String, status: String) =
        ViberOutboxItem(
            id = id, category = category, recipientName = name,
            recipientEmail = "${name.substringBefore(" ").lowercase()}@koenig-solutions.com",
            courseName = "AZ-104T00 Azure Administrator",
            messageText = "Hi ${name.substringBefore(" ")}, AZ-104T00 starts 22 Sep with 12 learners " +
                "and you are the certified match. Can you take it?",
            status = status,
        )

    /** No bot token: the screen must offer sharing, never sending. */
    private fun shareOnlyConfig() = ViberConfig(
        dispatchMode = ViberConfig.MODE_INTENT_NOTIFICATION,
        viberBotToken = "",
    )

    @Test
    fun viber_rules_screenshot() {
        val vm = ViberAutomationViewModel()
        vm.renderSnapshot(
            items = listOf(
                outboxItem("v1", ViberOutboxItem.CAT_DEMAND, "Niharika Rao", ViberOutboxItem.STATUS_QUEUED),
            ),
            config = shareOnlyConfig(),
        )
        start { ViberAutomationScreen(managerEmail = "", onBack = {}, viewModel = vm) }
        save("13_viber_rules")
    }

    @Test
    fun viber_outbox_screenshot() {
        val vm = ViberAutomationViewModel()
        vm.renderSnapshot(
            items = listOf(
                outboxItem("v1", ViberOutboxItem.CAT_DEMAND, "Niharika Rao", ViberOutboxItem.STATUS_QUEUED),
                outboxItem("v2", ViberOutboxItem.CAT_WEEKLY, "Priya Sharma", ViberOutboxItem.STATUS_QUEUED),
                outboxItem("v3", ViberOutboxItem.CAT_DELIVERY, "Rahul Verma", ViberOutboxItem.STATUS_FAILED),
                // Handed to Viber but never confirmed — must read as shared.
                outboxItem("v4", ViberOutboxItem.CAT_WEEKLY, "Niharika Rao", ViberOutboxItem.STATUS_SHARED_EXTERNALLY),
            ),
            config = shareOnlyConfig(),
        )
        start { ViberAutomationScreen(managerEmail = "", onBack = {}, viewModel = vm) }
        // Scroll to the outbox and history sections.
        compose.onRoot().performTouchInput { swipeUp() }
        save("14_viber_outbox")
    }

    // ── Release blocker verification: MessageReviewCard 4-action layout ────
    //
    // Regenerate + Edit + Copy + Share used to squeeze "Share" to near-zero
    // width and wrap it character-by-character. Fixed with a 2x2 action grid
    // in the shared MessageReviewCard. Captured here in both real contexts
    // that use it with all four actions: HR Monthly's team card (the real
    // production screen) and the Weekly team card's message surface (the
    // same shared MessageReviewCard call the real WeeklyReportScreen team
    // card makes, at the same 360dp width).

    @Test
    fun hr_monthly_team_final_screenshot() {
        val vm = HrMonthlyReportViewModel()
        vm.renderSnapshot(hrPayload())
        start {
            HrMonthlyReportScreen(managerEmail = "", onBack = {}, vm = vm)
        }
        save("hr_monthly_team_final")
    }

    @Test
    fun weekly_team_message_final_screenshot() {
        start {
            com.example.skillsync.theme.SkillCard(modifier = androidx.compose.ui.Modifier.fillMaxWidth()) {
                androidx.compose.foundation.layout.Column(
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                ) {
                    androidx.compose.material3.Text(
                        "Message to the team",
                        style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = androidx.compose.material3.MaterialTheme.skill.bodyText,
                        // ^ resolves via the imported `skill` extension above
                    )
                    // The exact production MessageReviewCard call the real
                    // WeeklyReportScreen team card makes (feature/report/ui/
                    // WeeklyReportScreen.kt), with Weekly-team-shaped verified
                    // text and all four actions wired.
                    com.example.skillsync.feature.communication.ui.MessageReviewCard(
                        text = "Team, this week the team delivered 4 batches to 40 participants. " +
                            "2 batches on the board are still unstaffed, 1 of which this team can " +
                            "already teach. 1 certification gap remains open across the team.",
                        onTextChange = {},
                        onRegenerate = {},
                        onCopy = {},
                        onShare = {},
                    )
                }
            }
        }
        save("weekly_team_message_final")
    }
}
