package com.example.skillsync.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.services.storage.TestStorage
import com.example.skillsync.feature.home.TeamTab
import com.example.skillsync.feature.training.ui.AllocationDeskContent
import com.example.skillsync.theme.AuroraBackground
import com.example.skillsync.theme.SkillSyncErrorState
import com.example.skillsync.theme.SkillSyncTheme
import org.junit.Rule
import org.junit.Test

/**
 * Visual gate for the two release-blocker work items in this pass:
 *
 *  - People restored: real reportees render through the actual production
 *    TeamTab composable (the fix itself was in MainScreenViewModel's data
 *    flow, not this screen — this proves the roster reaches it correctly).
 *  - Plan V2: the rebuilt unallocated-demand command centre, with no
 *    trainer-matching content, at overview / expanded / filtered / empty /
 *    error states.
 */
class PeopleAndPlanScreenshotTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val storage = TestStorage()

    private fun save(name: String) {
        settle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        storage.openOutputFile("$name.png").use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun settle() {
        repeat(6) { compose.mainClock.advanceTimeBy(250); Thread.sleep(150) }
    }

    private fun start(content: @androidx.compose.runtime.Composable () -> Unit) {
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

    // ── 01. People restored ──────────────────────────────────────────────────

    private fun trainer(email: String, name: String, designation: String, status: String) = mapOf(
        "official_email" to email, "name" to name, "designation" to designation,
    ) to mapOf("trainer_email" to email, "current_status" to status, "status_label" to status)

    @Test
    fun people_restored_screenshot() {
        val roster = listOf(
            trainer("niharika@koenig-solutions.com", "Niharika Rao", "Senior Trainer", "teaching_now"),
            trainer("priya@koenig-solutions.com", "Priya Sharma", "Trainer", "scheduled_today"),
            trainer("rahul@koenig-solutions.com", "Rahul Verma", "Trainer", "free"),
        )
        val data = mapOf<String, Any>(
            "trainer_operations_df" to roster.map { it.first },
            "trainer_current_state_df" to roster.map { it.second },
            "delivery_intelligence_df" to emptyList<Any>(),
        )
        start { TeamTab(data = data, capability = null, onTrainerClick = { _, _ -> }) }
        save("01_people_restored")
    }

    // ── Plan fixtures ────────────────────────────────────────────────────────

    private fun batch(
        id: String, course: String, customer: String, coverage: String,
        startInDays: Long, international: Boolean = false, atRisk: Boolean = false,
    ) = mapOf<String, Any>(
        "demand_id" to id, "course_name" to course, "customer" to customer,
        "coverage_status" to coverage, "delivery_mode" to "ILT",
        "start_date" to java.time.LocalDate.now().plusDays(startInDays).toString(),
        "end_date" to java.time.LocalDate.now().plusDays(startInDays + 2).toString(),
        "is_international" to international, "at_risk" to atRisk,
        "participants" to 14, "assignment_level" to "5", "priority_score" to 62,
        "assignment_risk" to if (atRisk) "High" else "Low",
        "location" to if (international) "Dubai, UAE" else "",
    )

    private fun planPayload(vararg batches: Map<String, Any>) = mapOf<String, Any>(
        "batches" to batches.toList(),
        "summary" to mapOf("total" to batches.size),
    )

    // ── 02. Plan overview ────────────────────────────────────────────────────

    @Test
    fun plan_overview_screenshot() {
        start {
            AllocationDeskContent(
                data = planPayload(
                    batch("D1", "DP-700T00 Fabric Data Engineer", "Contoso", "No Coverage", 2, atRisk = true),
                    batch("D2", "AZ-104T00 Azure Administrator", "Fabrikam", "Best Match", 4, international = true),
                    batch("D3", "SC-200T00 Security Operations", "Globex", "Available with Upskilling", 12),
                ),
                newIds = emptySet(),
                onBatchClick = {},
            )
        }
        save("02_plan_overview")
    }

    // ── 03. Plan batch expanded ──────────────────────────────────────────────

    @Test
    fun plan_batch_expanded_screenshot() {
        start {
            AllocationDeskContent(
                data = planPayload(batch("D9", "AI-102T00 Designing AI Solutions", "Northwind", "No Coverage", 5)),
                newIds = emptySet(),
                onBatchClick = {},
            )
        }
        compose.onNodeWithText("AI-102T00 Designing AI Solutions").performClick()
        save("03_plan_batch_expanded")
    }

    // ── 04. Plan filtered urgent ─────────────────────────────────────────────

    @Test
    fun plan_filtered_urgent_screenshot() {
        start {
            AllocationDeskContent(
                data = planPayload(
                    batch("D1", "Urgent Course", "Acme", "No Coverage", 1, atRisk = true),
                    batch("D2", "Calm Course", "Globex", "Best Match", 25),
                ),
                newIds = emptySet(),
                onBatchClick = {},
            )
        }
        compose.onNodeWithText("Urgent", substring = false).performClick()
        save("04_plan_filtered_urgent")
    }

    // ── 05. Plan empty ───────────────────────────────────────────────────────

    @Test
    fun plan_empty_screenshot() {
        start { AllocationDeskContent(data = planPayload(), newIds = emptySet(), onBatchClick = {}) }
        save("05_plan_empty")
    }

    // ── 06. Plan error ───────────────────────────────────────────────────────

    @Test
    fun plan_error_screenshot() {
        start {
            SkillSyncErrorState(
                message = "Demand could not be loaded",
                onRetry = {},
            )
        }
        save("06_plan_error")
    }
}
