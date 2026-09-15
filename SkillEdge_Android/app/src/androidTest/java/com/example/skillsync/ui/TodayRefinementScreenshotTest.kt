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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage
import com.example.skillsync.core.storage.LocalCache
import com.example.skillsync.feature.home.DashboardTab
import com.example.skillsync.feature.home.ExecutiveHeader
import com.example.skillsync.feature.home.ExecutiveHeaderTitle
import com.example.skillsync.theme.AuroraBackground
import com.example.skillsync.theme.SkillSyncTheme
import org.junit.Rule
import org.junit.Test

/**
 * Real-emulator captures for the Today executive-dashboard refinement pass.
 * Renders the production header ([ExecutiveHeader]) and [DashboardTab] inside
 * MainScreen's container (aurora ground + transparent Scaffold) with the same
 * deterministic fixture shape as [PilotScreenshotInstrumentedTest]. The
 * fixture carries no photo URLs, so avatars show their initials fallback.
 */
class TodayRefinementScreenshotTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val storage = TestStorage()

    private fun save(name: String) {
        compose.mainClock.advanceTimeBy(1_500)
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        storage.openOutputFile("$name.png").use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Scrolls the Today list so the node with [text] sits just below the header. */
    private fun scrollToTop(text: String, substring: Boolean = false) {
        settle()
        val list = compose.onAllNodes(hasScrollAction()).onFirst()
        val listTop = list.fetchSemanticsNode().boundsInRoot.top
        val nodeTop = compose.onAllNodes(hasText(text, substring = substring), useUnmergedTree = true)
            .onFirst().fetchSemanticsNode().boundsInRoot.top
        list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, nodeTop - listTop - 24f) }
        settle()
    }

    /** The clock is manual (the logo's idle animation never lets Compose go idle), so pump it. */
    private fun settle() {
        repeat(4) { compose.mainClock.advanceTimeBy(250); Thread.sleep(150) }
    }

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
            mapOf("demand_id" to "B-1", "course_name" to "AI-102T00: Develop AI Solutions in Azure", "trainer_name" to "Priya Sharma", "engagement_state" to "current", "delivery_mode" to "ILO"),
            mapOf("demand_id" to "B-2", "course_name" to "DP-700T00: Implementing a Data Fabric", "trainer_name" to "Rahul Verma", "engagement_state" to "upcoming", "delivery_mode" to "ILT"),
        ),
        "unallocated_demand_df" to listOf(
            mapOf(
                "demand_id" to "DEM-900", "course_name" to "DP-700T00: Fabric Data Engineer", "trainer_name" to "", "delivery_mode" to "ILT",
                "matching_trainers" to listOf(mapOf("name" to "Niharika Rao", "email" to "niharika@koenig-solutions.com", "capability_match" to true, "availability" to "AVAILABLE")),
            ),
            mapOf("demand_id" to "DEM-901", "course_name" to "SC-200T00: Microsoft Security Operations Analyst", "trainer_name" to "", "delivery_mode" to "ILO"),
        ),
        "manager_action_objects" to emptyList<Map<String, Any>>(),
        "trainer_decision_objects" to emptyList<Map<String, Any>>(),
    )

    private fun capabilityPayload() = mapOf<String, Any>(
        "trainers" to listOf(
            mapOf("trainer_name" to "Priya Sharma", "trainer_email" to "priya@koenig-solutions.com", "utilization" to 88.0, "readiness_bucket" to "Ready"),
            mapOf("trainer_name" to "Rahul Verma", "trainer_email" to "rahul@koenig-solutions.com", "utilization" to 74.0, "readiness_bucket" to "Ready"),
        ),
        "kpis" to emptyMap<String, Any>(),
    )

    @Test
    fun today_refinement_screenshots() {
        LocalCache.init(InstrumentationRegistry.getInstrumentation().targetContext)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SkillSyncTheme {
                Box(Modifier.fillMaxSize()) {
                    AuroraBackground()
                    Scaffold(
                        containerColor = Color.Transparent,
                        topBar = {
                            ExecutiveHeader(
                                notificationCount = 2, profileName = "Aishwar Nigam", photoUrl = null,
                                onAnalytics = {}, onNotifications = {}, onProfile = {},
                            ) { ExecutiveHeaderTitle("SKILLEDGE · EXECUTIVE CONSOLE", "Today · Manager Brief") }
                        },
                    ) { padding ->
                        Box(Modifier.padding(padding)) {
                            DashboardTab(
                                data = dashboardPayload(),
                                profile = mapOf("email" to "aishwar.c@koenig-solutions.com", "name" to "Aishwar Nigam"),
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
        compose.mainClock.advanceTimeBy(2_000)
        // Morning Note composes on a real coroutine; give it a moment to land.
        Thread.sleep(1_500)
        save("01_header_manager")

        scrollToTop("MORNING NOTE"); save("02_morning_greeting")
        scrollToTop("NEEDS YOU TODAY"); save("03_needs_today")
        scrollToTop("PULSE"); save("04_pulse")
        scrollToTop("unallocated batch", substring = true); save("05_demand_communicate")
        scrollToTop("DELIVERY OUTLOOK"); save("06_delivery_cert")
        scrollToTop("TOP PERFORMERS"); save("07_top_performers")
        scrollToTop("OPERATIONS"); save("08_operations_matrix")
        scrollToTop("PEOPLE"); save("08b_operations_matrix_lower")
    }
}
