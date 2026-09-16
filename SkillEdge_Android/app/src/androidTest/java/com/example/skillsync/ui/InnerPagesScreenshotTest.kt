package com.example.skillsync.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage
import com.example.skillsync.core.data.DataSource
import com.example.skillsync.core.data.RepositoryResult
import com.example.skillsync.core.storage.LocalCache
import com.example.skillsync.feature.report.ui.CapacityRunwayScreen
import com.example.skillsync.feature.report.ui.CapacityRunwayViewModel
import com.example.skillsync.feature.report.ui.DeliveryComplianceScreen
import com.example.skillsync.feature.report.ui.DeliveryComplianceViewModel
import com.example.skillsync.feature.report.ui.PipelineRadarScreen
import com.example.skillsync.feature.report.ui.PipelineRadarViewModel
import com.example.skillsync.feature.report.ui.PrioritiesScreen
import com.example.skillsync.feature.report.ui.PrioritiesViewModel
import com.example.skillsync.theme.AuroraBackground
import com.example.skillsync.theme.SkillSyncTheme
import org.junit.Rule
import org.junit.Test

/**
 * Real-emulator captures for inner pages 1-4 (Phase 1): This Week, Capacity
 * Runway, Delivery Sentinel (empty and loaded) and Pre-Demand Radar (loading
 * and empty). Each screen is rendered through its production composable with a
 * deterministic fetch lambda, so no network is touched and no data is invented
 * beyond the fixture.
 */
class InnerPagesScreenshotTest {

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

    private fun <T> ok(data: T) = RepositoryResult(data, DataSource.LIVE)

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

    // ── 1. This Week ─────────────────────────────────────────────────────────

    private fun prioritiesPayload() = mapOf<String, Any>(
        "items" to listOf(
            mapOf(
                "id" to "unstaffed_demand:DEM-900", "kind" to "unstaffed_demand",
                "title" to "DP-700T00 Fabric Data Engineer needs a trainer",
                "detail" to "ILT, 12 pax, starts 22 Sep. Niharika Rao is skill-matched.",
                "severity" to "high", "due" to "22 Sep",
                "target_type" to "demand", "target_id" to "DEM-900",
                "rank_score" to 40.0, "coverable" to true,
                // A real skill-matched, verified-available candidate: the card may
                // name her. Without this the action stays a neutral "Message".
                "matching_trainers" to listOf(
                    mapOf(
                        "name" to "Niharika Rao", "email" to "niharika@koenig-solutions.com",
                        "capability_match" to true, "availability" to "AVAILABLE",
                    ),
                ),
            ),
            mapOf(
                "id" to "unstaffed_demand:DEM-901", "kind" to "unstaffed_demand",
                "title" to "SC-200T00 Security Operations needs a trainer",
                "detail" to "ILO, 8 pax, starts 29 Sep. No skill-matched candidate yet.",
                "severity" to "high", "due" to "29 Sep",
                "target_type" to "demand", "target_id" to "DEM-901",
                "rank_score" to 38.0, "coverable" to false,
            ),
            mapOf(
                "id" to "cert_gap:t1", "kind" to "cert_gap",
                "title" to "Priya Sharma is teaching without the certification",
                "detail" to "No matching certification on file for AZ-104.",
                "severity" to "medium", "due" to "",
                "target_type" to "trainer", "target_id" to "priya@koenig-solutions.com",
                "rank_score" to 15.0, "coverable" to false,
            ),
            mapOf(
                "id" to "one_to_one:t2", "kind" to "one_to_one",
                "title" to "Rahul Verma is due a one to one",
                "detail" to "Last recorded conversation was seven weeks ago.",
                "severity" to "low", "due" to "",
                "target_type" to "trainer", "target_id" to "rahul@koenig-solutions.com",
                "rank_score" to 8.0, "coverable" to false,
            ),
        ),
        "counts" to mapOf("unstaffed_demand" to 2, "cert_gap" to 1, "one_to_one" to 1),
        "loading" to false,
    )

    @Test
    fun this_week_screenshot() {
        // ViewModels are built outside setContent (they are not composable state).
        val vm = PrioritiesViewModel(
            fetchPriorities = { _, _ -> ok(prioritiesPayload()) },
            fetchAllocation = { _, _ ->
                ok(
                    mapOf(
                        "batches" to listOf(
                            mapOf("course_name" to "DP-700T00", "demand_id" to "DEM-900", "trainer_name" to ""),
                            mapOf("course_name" to "SC-200T00", "demand_id" to "DEM-901", "trainer_name" to ""),
                        ),
                    ),
                )
            },
        )
        start {
            PrioritiesScreen(
                managerEmail = manager,
                onOpenDemand = {}, onOpenTrainer = { _, _ -> }, onOpenActions = {}, onBack = {},
                vm = vm,
            )
        }
        save("01_this_week")
    }

    // ── 2. Capacity Runway ───────────────────────────────────────────────────

    /** Free trainer-days exceed demanded days while 3 of 9 batches stay uncovered. */
    private fun runwayPayload() = mapOf<String, Any>(
        "weeks" to listOf(
            mapOf("week_start" to "2026-09-14", "week_end" to "2026-09-20", "demand_batches" to 2, "demand_participants" to 24, "team_available" to 3, "gap" to 0),
            mapOf("week_start" to "2026-09-21", "week_end" to "2026-09-27", "demand_batches" to 4, "demand_participants" to 48, "team_available" to 2, "gap" to 2),
            mapOf("week_start" to "2026-09-28", "week_end" to "2026-10-04", "demand_batches" to 1, "demand_participants" to 12, "team_available" to 3, "gap" to 0),
            mapOf("week_start" to "2026-10-05", "week_end" to "2026-10-11", "demand_batches" to 2, "demand_participants" to 20, "team_available" to 1, "gap" to 1),
            mapOf("week_start" to "2026-10-12", "week_end" to "2026-10-18", "demand_batches" to 0, "demand_participants" to 0, "team_available" to 3, "gap" to 0),
            mapOf("week_start" to "2026-10-19", "week_end" to "2026-10-25", "demand_batches" to 0, "demand_participants" to 0, "team_available" to 2, "gap" to 0),
        ),
        "summary" to mapOf(
            "total_demand" to 9, "total_coverable" to 6, "worst_week" to "2026-09-21",
            "trainer_days_available" to 50, "trainer_days_demanded" to 42,
        ),
        "upskilling" to listOf(
            mapOf(
                "exam_code" to "DP-700", "course" to "DP-700T00 Fabric Data Engineer", "opens_batches" to 2,
                "why" to "Two batches in the horizon have no certified trainer on the team.",
                "nearest_trainer" to "rahul@koenig-solutions.com", "nearest_trainer_name" to "Rahul Verma",
            ),
            mapOf(
                "exam_code" to "SC-200", "course" to "SC-200T00 Security Operations Analyst", "opens_batches" to 1,
                "why" to "One batch needs a security analyst skill nobody holds yet.",
                "nearest_trainer" to "priya@koenig-solutions.com", "nearest_trainer_name" to "Priya Sharma",
            ),
        ),
        "loading" to false,
    )

    @Test
    fun capacity_runway_screenshot() {
        val vm = CapacityRunwayViewModel(fetchRunway = { _, _ -> ok(runwayPayload()) })
        start {
            CapacityRunwayScreen(
                managerEmail = manager, onOpenTrainer = { _, _ -> }, onBack = {}, vm = vm,
            )
        }
        save("02_capacity_runway")
    }

    // ── 3. Delivery Sentinel ─────────────────────────────────────────────────

    /** Nothing running: the rate must read N/A, never 100%. */
    private fun complianceEmptyPayload() = mapOf<String, Any>(
        "active_deliveries" to emptyList<Map<String, Any>>(),
        "total_active" to 0, "compliant_count" to 0, "violations_count" to 0, "at_risk_count" to 0,
        "generated_at" to "2026-09-16T04:00:00",
    )

    private fun complianceLoadedPayload() = mapOf<String, Any>(
        "active_deliveries" to listOf(
            mapOf(
                "assignment_id" to "A-1001", "trainer_name" to "Priya Sharma",
                "trainer_email" to "priya@koenig-solutions.com", "course_name" to "AI-102T00 Develop AI Solutions",
                "current_day" to 3, "total_days" to 3, "recording_count" to 3, "recording_links" to listOf("r1", "r2", "r3"), "compliance_status" to "COMPLIANT", "severity" to "good",
                "nudge_message" to "Hello Priya,\n\nDay 2 recording is uploaded, thank you.",
            ),
            mapOf(
                "assignment_id" to "A-1002", "trainer_name" to "Rahul Verma",
                "trainer_email" to "rahul@koenig-solutions.com", "course_name" to "DP-700T00 Fabric Data Engineer",
                "current_day" to 2, "total_days" to 3, "recording_count" to 0, "recording_links" to emptyList<String>(), "compliance_status" to "RECORDING_MISSING_URGENT", "severity" to "crit",
                "nudge_message" to "Hello Rahul,\n\nPlease upload your Day 1 session recording for DP-700T00 today.",
            ),
            mapOf(
                "assignment_id" to "A-1003", "trainer_name" to "Meera Iyer",
                "trainer_email" to "meera@koenig-solutions.com", "course_name" to "SC-200T00 Security Operations",
                "current_day" to 1, "total_days" to 2, "recording_count" to 0, "recording_links" to emptyList<String>(), "compliance_status" to "PENDING_TODAY", "severity" to "warn",
                "nudge_message" to "Hello Meera,\n\nDay 1 recording is due at the end of today.",
            ),
        ),
        "total_active" to 3, "compliant_count" to 1, "violations_count" to 1, "at_risk_count" to 1,
        "compliance_rate_percent" to 33.3,
        "generated_at" to "2026-09-16T04:00:00",
    )

    @Test
    fun delivery_sentinel_empty_screenshot() {
        val vm = DeliveryComplianceViewModel(fetchCompliance = { _, _ -> ok(complianceEmptyPayload()) })
        start {
            DeliveryComplianceScreen(
                managerEmail = manager, onOpenTrainer = { _, _ -> }, onBack = {}, vm = vm,
            )
        }
        save("03_delivery_sentinel_empty")
    }

    @Test
    fun delivery_sentinel_loaded_screenshot() {
        val vm = DeliveryComplianceViewModel(fetchCompliance = { _, _ -> ok(complianceLoadedPayload()) })
        start {
            DeliveryComplianceScreen(
                managerEmail = manager, onOpenTrainer = { _, _ -> }, onBack = {}, vm = vm,
            )
        }
        save("04_delivery_sentinel_loaded")
    }

    // ── 4. Pre-Demand Radar ──────────────────────────────────────────────────

    @Test
    fun predemand_loading_screenshot() {
        // Never completes: holds the branded loading state for capture.
        val vm = PipelineRadarViewModel(fetchPipeline = { _, _ -> kotlinx.coroutines.awaitCancellation() })
        start {
            PipelineRadarScreen(
                managerEmail = manager, onOpenTrainer = { _, _ -> }, onBack = {}, vm = vm,
            )
        }
        save("05_predemand_loading")
    }

    @Test
    fun predemand_empty_screenshot() {
        val vm = PipelineRadarViewModel(
            fetchPipeline = { _, _ ->
                ok(
                    mapOf(
                        "pipeline_items" to emptyList<Map<String, Any>>(),
                        "total_orders" to 0, "covered_orders" to 0, "uncovered_orders" to 0,
                        "generated_at" to "2026-09-16T04:00:00",
                    ),
                )
            },
        )
        start {
            PipelineRadarScreen(
                managerEmail = manager, onOpenTrainer = { _, _ -> }, onBack = {}, vm = vm,
            )
        }
        save("06_predemand_empty_or_loaded")
    }
}
