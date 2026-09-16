package com.example.skillsync.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
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
import androidx.compose.ui.test.hasContentDescription
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
import com.example.skillsync.feature.home.SkillSyncNavBar
import com.example.skillsync.navigation.HomeTab
import com.example.skillsync.theme.AuroraBackground
import com.example.skillsync.theme.SkillSyncTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Real-emulator captures for the Today executive-dashboard refinement pass.
 * Renders MainScreen's real chrome — [ExecutiveHeader], [DashboardTab] and
 * [SkillSyncNavBar] inside the aurora ground + transparent Scaffold — with a
 * deterministic fixture.
 *
 * Photos: Priya's `photo_url` is a controlled PNG this test writes to the app
 * cache and loads through the production Avatar/Coil path (no production URL
 * is invented). Rahul has no URL (initials). Meera's URL points at a file that
 * does not exist, proving the load-failure fallback to initials.
 */
class TodayRefinementScreenshotTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val storage = TestStorage()

    private fun save(name: String) {
        settle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        storage.openOutputFile("$name.png").use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** The clock is manual (the logo's idle animation never lets Compose go idle), so pump it. */
    private fun settle() {
        repeat(6) { compose.mainClock.advanceTimeBy(250); Thread.sleep(200) }
    }

    private fun list() = compose.onAllNodes(hasScrollAction()).onFirst()

    private var scrolled = 0f
    private val anchors = mutableMapOf<String, Float>()

    /** Records each section's offset from the list top while the list is still at scroll 0. */
    private fun measureAnchors(vararg texts: Pair<String, Boolean>) {
        val listTop = list().fetchSemanticsNode().boundsInRoot.top
        texts.forEach { (text, substring) ->
            anchors[text] = compose.onAllNodes(hasText(text, substring = substring), useUnmergedTree = true)
                // positionInRoot is unclipped; boundsInRoot collapses to 0 for off-screen nodes.
                .onFirst().fetchSemanticsNode().positionInRoot.y - listTop
        }
    }

    /** Scrolls by the exact remaining distance so [text]'s section sits just below the header. */
    private fun scrollToTop(text: String) {
        val target = anchors.getValue(text) - 24f
        list().performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, target - scrolled) }
        scrolled = target
        settle()
    }

    /** A controlled, deterministic portrait written to the app cache. */
    private fun testPortraitUri(): String {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(ctx.cacheDir, "today_test_portrait.png")
        val bmp = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 256f, 256f, 0xFFF59E0B.toInt(), 0xFF7C3AED.toInt(), Shader.TileMode.CLAMP) })
        c.drawCircle(128f, 104f, 52f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFDE68A.toInt() })
        c.drawOval(48f, 170f, 208f, 300f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E3A8A.toInt() })
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return "file://${file.absolutePath}"
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
            mapOf("official_email" to "meera@koenig-solutions.com", "trainer_name" to "Meera Iyer"),
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

    private fun capabilityPayload(photo: String) = mapOf<String, Any>(
        "trainers" to listOf(
            mapOf("trainer_name" to "Priya Sharma", "trainer_email" to "priya@koenig-solutions.com", "utilization" to 88.0, "readiness_bucket" to "Ready", "photo_url" to photo),
            mapOf("trainer_name" to "Rahul Verma", "trainer_email" to "rahul@koenig-solutions.com", "utilization" to 74.0, "readiness_bucket" to "Ready"),
            mapOf("trainer_name" to "Meera Iyer", "trainer_email" to "meera@koenig-solutions.com", "utilization" to 61.0, "readiness_bucket" to "Ready", "photo_url" to "file:///data/local/tmp/does-not-exist.png"),
        ),
        "kpis" to emptyMap<String, Any>(),
    )

    @Test
    fun today_refinement_screenshots() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        LocalCache.init(ctx)
        // Start with no persisted draft so the Morning Note composes on first load.
        ctx.getSharedPreferences("skilledge_digests", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        val photo = testPortraitUri()
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
                        bottomBar = { SkillSyncNavBar(HomeTab.DASHBOARD) {} },
                    ) { padding ->
                        Box(Modifier.padding(padding)) {
                            DashboardTab(
                                data = dashboardPayload(),
                                profile = mapOf("email" to "aishwar.c@koenig-solutions.com", "name" to "Aishwar Nigam"),
                                capability = capabilityPayload(photo),
                                capabilityLoading = false,
                                email = "aishwar.c@koenig-solutions.com",
                                onTrainerClick = { _, _ -> }, onOpenProfile = {}, onDrill = {},
                            )
                        }
                    }
                }
            }
        }
        // The Morning Note tries the server first (unauthenticated here, so it
        // falls back to the local composer); pump the clock while that lands.
        repeat(20) { compose.mainClock.advanceTimeBy(250); Thread.sleep(250) }
        save("01_header_manager")

        measureAnchors(
            "MORNING NOTE" to false, "NEEDS YOU TODAY" to false, "PULSE" to false,
            "unallocated batch" to true, "DELIVERY OUTLOOK" to false, "TOP PERFORMERS" to false, "OPERATIONS" to false,
        )
        scrollToTop("MORNING NOTE"); save("02_morning_note")
        scrollToTop("NEEDS YOU TODAY"); save("03_needs_today")
        scrollToTop("PULSE"); save("04_pulse")
        scrollToTop("unallocated batch"); save("05_demand_communicate")
        scrollToTop("DELIVERY OUTLOOK"); save("06_delivery_cert")
        scrollToTop("TOP PERFORMERS"); save("07_top_performers_photo")
        scrollToTop("OPERATIONS"); save("08_operations_matrix_top")

        // Bottom of Today: scroll far past the end; the list clamps at its last item.
        list().performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 20_000f) }
        save("09_operations_matrix_bottom_nav")

        // Gate: the last Operations tile sits fully inside the list viewport,
        // which the Scaffold ends above the bottom navigation.
        val viewport = list().fetchSemanticsNode().boundsInRoot
        val lastTile = compose.onAllNodes(hasContentDescription("Viber automation", substring = true))
            .onFirst().fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Last tile $lastTile is not fully above the nav bar (viewport $viewport)",
            lastTile.bottom <= viewport.bottom && lastTile.top >= viewport.top,
        )
    }
}
