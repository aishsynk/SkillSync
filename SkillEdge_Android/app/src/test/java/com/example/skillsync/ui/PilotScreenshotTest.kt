package com.example.skillsync.ui

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.skillsync.core.data.DataSource
import com.example.skillsync.core.data.RepositoryResult
import com.example.skillsync.core.storage.LocalCache
import com.example.skillsync.feature.home.DashboardTab
import com.example.skillsync.feature.report.ui.CapacityRunwayScreen
import com.example.skillsync.feature.report.ui.CapacityRunwayViewModel
import com.example.skillsync.feature.report.ui.PrioritiesScreen
import com.example.skillsync.feature.report.ui.PrioritiesViewModel
import com.example.skillsync.theme.SkillSyncTheme
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Real screenshots of the D2 pilots — the actual production Composables, not
 * a mock UI, rendered with the deterministic fetch seams added to
 * `PrioritiesViewModel`/`CapacityRunwayViewModel` for exactly this purpose.
 *
 * Root cause of the earlier deadlock: `ManagerRepository`'s `cachedMap`
 * always calls the network first (cache is only a fallback on exception), so
 * every previous attempt to seed `LocalCache` still let a real Retrofit call
 * through under Robolectric — which hangs well past Compose's idle timeout.
 * The injected `fetchPriorities`/`fetchRunway`/`fetchAllocation` lambdas
 * replace that call with an instant, deterministic one; production code
 * paths and defaults are untouched.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PilotScreenshotTest {
    @get:Rule val compose = createComposeRule()

    /**
     * These screens' pulse/glow primitives (`HeroRing`, `IconSlot`, etc.) run
     * `rememberInfiniteTransition` animations, which by design never reach
     * Compose "idle" — `waitForIdle()` deadlocks waiting for something that
     * is permanently pending. Disabling clock auto-advance and stepping a
     * fixed number of frames by hand sidesteps that without touching any
     * production animation code.
     */
    private fun settle() {
        compose.mainClock.autoAdvance = false
        repeat(10) { compose.mainClock.advanceTimeByFrame() }
    }

    private fun save(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/reports/pilot-screenshots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
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
                "trainer_name" to "Priya Sharma", "engagement_state" to "active", "delivery_mode" to "ILO",
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
        LocalCache.init(ApplicationProvider.getApplicationContext())
        compose.setContent {
            SkillSyncTheme {
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
        settle()
        save("today")
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
        LocalCache.init(ApplicationProvider.getApplicationContext())
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
        settle()
        save("this_week_populated")
    }

    @Test
    fun thisWeek_empty_screenshot() {
        LocalCache.init(ApplicationProvider.getApplicationContext())
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
        settle()
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
        LocalCache.init(ApplicationProvider.getApplicationContext())
        compose.setContent {
            SkillSyncTheme {
                CapacityRunwayScreen(
                    managerEmail = "aishwar.c@koenig-solutions.com",
                    onOpenTrainer = { _, _ -> }, onBack = {},
                    vm = CapacityRunwayViewModel(fetchRunway = { _, _ -> ok(runwayPayload()) }),
                )
            }
        }
        settle()
        save("capacity_runway")
    }
}
