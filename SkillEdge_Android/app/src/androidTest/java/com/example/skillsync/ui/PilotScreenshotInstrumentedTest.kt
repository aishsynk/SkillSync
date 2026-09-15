package com.example.skillsync.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage
import com.example.skillsync.core.data.DataSource
import com.example.skillsync.core.data.RepositoryResult
import com.example.skillsync.core.storage.LocalCache
import com.example.skillsync.feature.home.DashboardTab
import com.example.skillsync.feature.report.ui.CapacityRunwayScreen
import com.example.skillsync.feature.report.ui.CapacityRunwayViewModel
import com.example.skillsync.feature.report.ui.PrioritiesScreen
import com.example.skillsync.feature.report.ui.PrioritiesViewModel
import com.example.skillsync.theme.AuroraBackground
import com.example.skillsync.theme.SkillSyncTheme
import org.junit.Rule
import org.junit.Test

/**
 * Real-device/emulator screenshots of the D2 pilots for the Design V2 visual
 * checkpoint.
 *
 * Robolectric's `captureToImage()` (see `PilotScreenshotTest` in the unit
 * test source set) deadlocks in `WindowCapture.forceRedraw`/`PixelCopy` on
 * both Windows and Linux — confirmed by running the identical test on an
 * ubuntu-latest CI runner. That path is not usable for this checkpoint.
 * `createAndroidComposeRule` runs against real Android framework rendering
 * (a device or emulator), where the same `captureToImage()` call works
 * normally, so this test renders the same production composables with the
 * same deterministic ViewModel seams and writes real PNGs to app-private
 * external storage for the CI job to `adb pull`.
 */
class PilotScreenshotInstrumentedTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    /**
     * Writing to getExternalFilesDir() and adb-pulling afterwards races
     * Gradle's own cleanup: connectedDebugAndroidTest uninstalls both the app
     * and the test APK as soon as the instrumentation run finishes, wiping
     * that directory before the workflow's adb pull step runs (confirmed:
     * "run-as: unknown package" immediately after a successful, 4/4-passing
     * test run). Test Storage's output path is copied off the device by the
     * Test Orchestrator itself, before that uninstall happens.
     */
    private val storage = TestStorage()

    private fun save(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        storage.openOutputFile("$name.png").use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    private fun ok(data: Map<String, Any>) = RepositoryResult(data, DataSource.LIVE)

    // ── Today ────────────────────────────────────────────────────────────────

    private fun dashboardPayload() = mapOf<String, Any>(
        "manager_kpis" to mapOf(
            "total_team_members" to 8.0, "active_trainers" to 6.0, "avg_team_utilization" to 74.0,
            "utilization_trend" to "+3 vs last week", "open_demand" to 4.0, "high_risk_trainers" to 1.0,
            "bench_trainers" to 1.0, "optimal_trainers" to 5.0, "stretched_trainers" to 2.0,
            "cert_coverage_pct" to 62.0, "international_batches" to 1.0, "team_readiness_score" to 78.0,
            "readiness_trend" to "+4 vs last month", "unread_notifications" to 2.0,
        ),
        "trainer_operations_df" to listOf(
            mapOf("official_email" to "priya@koenig-solutions.com", "trainer_name" to "Priya Sharma"),
            mapOf("official_email" to "rahul@koenig-solutions.com", "trainer_name" to "Rahul Verma"),
        ),
        "trainer_current_state_df" to emptyList<Map<String, Any>>(),
        "batch_engagement_df" to listOf(
            mapOf(
                "demand_id" to "B-1", "course_name" to "AI-102T00: Develop AI Solutions in Azure",
                "trainer_name" to "Priya Sharma", "engagement_state" to "current", "delivery_mode" to "ILO",
            ),
            mapOf(
                "demand_id" to "B-2", "course_name" to "DP-700T00: Implementing a Data Fabric",
                "trainer_name" to "Rahul Verma", "engagement_state" to "upcoming", "delivery_mode" to "ILT",
            ),
        ),
        "unallocated_demand_df" to listOf(
            mapOf(
                "demand_id" to "DEM-900", "course_name" to "DP-700T00: Fabric Data Engineer",
                "trainer_name" to "", "delivery_mode" to "ILT",
            ),
        ),
        "manager_action_objects" to emptyList<Map<String, Any>>(),
        "trainer_decision_objects" to emptyList<Map<String, Any>>(),
    )

    private fun managerProfile() = mapOf<String, Any>(
        "email" to "aishwar.c@koenig-solutions.com", "name" to "Aishwar Nigam",
    )

    private fun capabilityPayload() = mapOf<String, Any>(
        "trainers" to listOf(
            mapOf("trainer_name" to "Priya Sharma", "utilization" to 88.0, "readiness_bucket" to "Ready"),
            mapOf("trainer_name" to "Rahul Verma", "utilization" to 74.0, "readiness_bucket" to "Ready"),
        ),
        "kpis" to emptyMap<String, Any>(),
    )

    @Test
    fun today_screenshot() {
        LocalCache.init(InstrumentationRegistry.getInstrumentation().targetContext)
        compose.setContent {
            SkillSyncTheme {
                // DashboardTab alone has no background — in production it always
                // renders inside MainScreen's Box(AuroraBackground() + a
                // transparent Scaffold). Rendering it bare (as this test did
                // originally) leaves it on the raw white activity background,
                // which made every light-on-dark Text (e.g. SectionHeading's
                // "conclusion" line) invisible — a test-fixture gap, not a real
                // app defect. Reproduce MainScreen's actual container here.
                Box(Modifier.fillMaxSize()) {
                    AuroraBackground()
                    Scaffold(containerColor = Color.Transparent) { padding ->
                        Box(Modifier.padding(padding)) {
                            DashboardTab(
                                data = dashboardPayload(),
                                profile = managerProfile(),
                                capability = capabilityPayload(),
                                capabilityLoading = false,
                                email = "aishwar.c@koenig-solutions.com",
                                onTrainerClick = { _, _ -> }, onOpenProfile = {}, onDrill = {},
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        save("today_top")

        // Mid: past Pulse/Needs You, into Capacity balance / Demand.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("CAPACITY BALANCE"))
        compose.waitForIdle()
        save("today_mid")

        // Lower: Delivery outlook / Certification / Top performers.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("TOP PERFORMERS"))
        compose.waitForIdle()
        save("today_lower")

        // Operations: the launchpad grid at the very bottom.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("OPERATIONS"))
        compose.waitForIdle()
        save("today_operations")
    }

    /**
     * The 2026-09-15 "Today / Manager Brief — Complete UI/UX Recovery" pass —
     * seven shots covering the full vertical page at the operator's requested
     * granularity, so each redesigned section (identity row, Needs You Today's
     * icon actions, the compact Pulse grid, the compact Demand action, Top
     * Performers' real photos/bars, and the compact Operations rows) has its
     * own real-device capture rather than one that only covers part of it.
     */
    @Test
    fun today_uiux_recovery_screenshot() {
        LocalCache.init(InstrumentationRegistry.getInstrumentation().targetContext)
        compose.setContent {
            SkillSyncTheme {
                Box(Modifier.fillMaxSize()) {
                    AuroraBackground()
                    Scaffold(containerColor = Color.Transparent) { padding ->
                        Box(Modifier.padding(padding)) {
                            DashboardTab(
                                data = dashboardPayload(),
                                profile = managerProfile(),
                                capability = capabilityPayload(),
                                capabilityLoading = false,
                                email = "aishwar.c@koenig-solutions.com",
                                onTrainerClick = { _, _ -> }, onOpenProfile = {}, onDrill = {},
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        save("01_header_identity")

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("NEEDS YOU TODAY"))
        compose.waitForIdle()
        save("02_readiness_needs")

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("PULSE"))
        compose.waitForIdle()
        save("03_pulse")

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("COMMUNICATE"))
        compose.waitForIdle()
        save("04_demand_communicate")

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("CERTIFICATION COVERAGE"))
        compose.waitForIdle()
        save("05_delivery_certification")

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("TOP PERFORMERS"))
        compose.waitForIdle()
        save("06_top_performers")

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("OPERATIONS"))
        compose.waitForIdle()
        save("07_operations")

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("AUTOMATION"))
        compose.waitForIdle()
        save("07b_operations_tiles")
    }

    // ── This Week ────────────────────────────────────────────────────────────

    private fun prioritiesPayload() = mapOf<String, Any>(
        "items" to listOf<Map<String, Any>>(
            mapOf(
                "id" to "unstaffed_demand:DEM-900", "kind" to "unstaffed_demand",
                "title" to "Unstaffed: DP-700T00", "detail" to "Open batch 01 Oct needs a trainer.",
                "severity" to "high", "due" to "2026-10-01",
                "target_type" to "demand", "target_id" to "DEM-900",
                "rank_score" to 40.0, "coverable" to true,
            ),
            mapOf(
                "id" to "cert_gap:t1", "kind" to "cert_gap",
                "title" to "Priya Sharma teaching without cert", "detail" to "No matching certification for: AZ-104",
                "severity" to "medium", "due" to "",
                "target_type" to "trainer", "target_id" to "priya@koenig-solutions.com",
                "rank_score" to 15.0, "coverable" to false,
            ),
        ),
        "counts" to mapOf("unstaffed_demand" to 1, "cert_gap" to 1),
        "loading" to false,
        "generated_at" to "2026-09-14T00:00:00Z",
    )

    @Test
    fun thisWeek_populated_screenshot() {
        LocalCache.init(InstrumentationRegistry.getInstrumentation().targetContext)
        compose.setContent {
            SkillSyncTheme {
                PrioritiesScreen(
                    managerEmail = "aishwar.c@koenig-solutions.com",
                    onOpenDemand = {}, onOpenTrainer = { _, _ -> }, onOpenActions = {}, onBack = {},
                    vm = PrioritiesViewModel(
                        fetchPriorities = { _, _ -> ok(prioritiesPayload()) },
                        fetchAllocation = { _, _ -> ok(mapOf("batches" to emptyList<Map<String, Any>>())) },
                    ),
                )
            }
        }
        compose.waitForIdle()
        save("this_week_populated")
    }

    @Test
    fun thisWeek_empty_screenshot() {
        LocalCache.init(InstrumentationRegistry.getInstrumentation().targetContext)
        compose.setContent {
            SkillSyncTheme {
                PrioritiesScreen(
                    managerEmail = "aishwar.c@koenig-solutions.com",
                    onOpenDemand = {}, onOpenTrainer = { _, _ -> }, onOpenActions = {}, onBack = {},
                    vm = PrioritiesViewModel(
                        fetchPriorities = { _, _ ->
                            ok(mapOf("items" to emptyList<Map<String, Any>>(), "counts" to emptyMap<String, Any>(), "loading" to false))
                        },
                        fetchAllocation = { _, _ -> ok(mapOf("batches" to emptyList<Map<String, Any>>())) },
                    ),
                )
            }
        }
        compose.waitForIdle()
        save("this_week_empty")
    }

    // ── Capacity Runway ──────────────────────────────────────────────────────

    private fun runwayPayload() = mapOf<String, Any>(
        "weeks" to listOf(
            mapOf("week_start" to "2026-09-14", "week_end" to "2026-09-20", "demand_batches" to 3, "demand_participants" to 40, "team_available" to 4, "gap" to 0),
            mapOf("week_start" to "2026-09-21", "week_end" to "2026-09-27", "demand_batches" to 5, "demand_participants" to 60, "team_available" to 3, "gap" to 2),
            mapOf("week_start" to "2026-09-28", "week_end" to "2026-10-04", "demand_batches" to 2, "demand_participants" to 20, "team_available" to 4, "gap" to 0),
        ),
        "summary" to mapOf(
            "total_demand" to 10, "total_coverable" to 8, "worst_week" to "2026-09-21",
            "trainer_days_available" to 55, "trainer_days_demanded" to 70,
        ),
        "upskilling" to listOf(
            mapOf(
                "exam_code" to "AZ-104", "course" to "Microsoft Azure Administrator", "opens_batches" to 3,
                "why" to "3 upcoming batches have no certified trainer on the bench.",
                "nearest_trainer" to "rahul@koenig-solutions.com", "nearest_trainer_name" to "Rahul Verma",
            ),
        ),
        "loading" to false,
    )

    @Test
    fun capacityRunway_screenshot() {
        LocalCache.init(InstrumentationRegistry.getInstrumentation().targetContext)
        compose.setContent {
            SkillSyncTheme {
                CapacityRunwayScreen(
                    managerEmail = "aishwar.c@koenig-solutions.com",
                    onOpenTrainer = { _, _ -> }, onBack = {},
                    vm = CapacityRunwayViewModel(fetchRunway = { _, _ -> ok(runwayPayload()) }),
                )
            }
        }
        compose.waitForIdle()
        save("capacity_runway")
    }
}
