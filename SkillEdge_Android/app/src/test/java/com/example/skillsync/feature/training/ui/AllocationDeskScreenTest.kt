package com.example.skillsync.feature.training.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.skillsync.theme.SkillSyncTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Plan / Demand & Planning V2 — proves the core product boundary: no
 * trainer/reportee matching or "Grow the Team" content anywhere on this
 * screen (that lives in Demand Details / BatchDetailScreen), and that the
 * real unallocated-batch information, filters, expansion and navigation all
 * work against the real production composable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AllocationDeskScreenTest {

    @get:Rule val compose = createComposeRule()

    private fun batch(
        id: String, course: String, customer: String, coverage: String,
        startInDays: Long, international: Boolean = false, atRisk: Boolean = false,
        participants: Int = 12,
    ) = mapOf<String, Any>(
        "demand_id" to id, "course_name" to course, "customer" to customer,
        "coverage_status" to coverage, "delivery_mode" to "ILT",
        "start_date" to LocalDate.now().plusDays(startInDays).toString(),
        "end_date" to LocalDate.now().plusDays(startInDays + 2).toString(),
        "is_international" to international, "at_risk" to atRisk,
        "participants" to participants, "assignment_level" to "5",
        "priority_score" to 60, "assignment_risk" to if (atRisk) "High" else "Low",
        // A candidate array is present in the real payload (Demand Details
        // reads it) — Plan must never surface it even though it's in `data`.
        "candidates" to listOf(mapOf("name" to "Niharika Rao", "match" to 92)),
    )

    private fun payload(vararg batches: Map<String, Any>) = mapOf<String, Any>(
        "batches" to batches.toList(),
        "summary" to mapOf("total" to batches.size, "high" to 0, "medium" to 0, "unmatched" to 0, "priority" to 0, "at_risk" to 0),
    )

    private fun setPlan(data: Map<String, Any>, onOpen: (Map<*, *>) -> Unit = {}) {
        compose.setContent {
            SkillSyncTheme {
                AllocationDeskContent(data = data, newIds = emptySet(), onBatchClick = onOpen)
            }
        }
    }

    @Test
    fun noPersonMatchingOrGrowTheTeamContentAnywhereOnPlan() {
        setPlan(payload(batch("D1", "AZ-104T00 Azure Administrator", "Contoso", "Best Match", 2)))

        compose.onAllNodesWithText("Niharika Rao").assertCountEquals(0)
        compose.onAllNodesWithText("Grow", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("match", substring = true, ignoreCase = true).assertCountEquals(0)
        compose.onAllNodesWithText("candidate", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test
    fun realUnallocatedBatchInformationRenders() {
        setPlan(payload(batch("D1", "AZ-104T00 Azure Administrator", "Contoso", "Best Match", 2)))
        compose.onNodeWithText("AZ-104T00 Azure Administrator").assertIsDisplayed()
        compose.onNodeWithText("Contoso").assertIsDisplayed()
    }

    @Test
    fun urgencyAndBlockedStatusAreDistinguishable() {
        setPlan(
            payload(
                batch("D1", "Urgent Course", "Acme", "No Coverage", startInDays = 1, atRisk = true),
                batch("D2", "Calm Course", "Globex", "Best Match", startInDays = 20),
            ),
        )
        // "URGENT" also appears as a KPI label, and every card's own clickable
        // root merges its children's text into a second matching node — so
        // existence (not a single exact match) is what these prove.
        assertTrue(compose.onAllNodesWithText("URGENT").fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithText("BLOCKED").fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithText("COVERABLE").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun expandRevealsPlanningMetadataAndOpenDetailsRoutes() {
        var opened: Map<*, *>? = null
        setPlan(
            payload(batch("D42", "SC-200T00 Security Operations", "Fabrikam", "No Coverage", startInDays = 3)),
            onOpen = { opened = it },
        )
        // Collapsed: no delivery-section detail yet.
        compose.onAllNodesWithText("DELIVERY").assertCountEquals(0)

        val courseTitle = compose.onNodeWithText("SC-200T00 Security Operations")
        courseTitle.performScrollTo()
        courseTitle.performClick()
        val openDetails = compose.onNodeWithText("Open Details")
        openDetails.performScrollTo()
        openDetails.assertExists()
        openDetails.performClick()

        assertEquals("D42", opened?.get("demand_id"))
    }

    @Test
    fun filterNarrowsToBlockedOnly() {
        setPlan(
            payload(
                batch("D1", "Blocked Course", "Acme", "No Coverage", startInDays = 5),
                batch("D2", "Open Course", "Globex", "Best Match", startInDays = 5),
            ),
        )
        val blockedPill = compose.onNodeWithTag("planFilter_Blocked")
        blockedPill.performScrollTo()
        blockedPill.performClick()
        // Absence/existence checks don't depend on LazyColumn scroll position,
        // unlike visibility — exactly what filtering (removal from the
        // composed list) should be judged on.
        compose.onAllNodesWithText("Open Course").assertCountEquals(0)
        assertTrue(compose.onAllNodesWithText("Blocked Course").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun emptyDemandShowsAnHonestEmptyStateNotAnErrorOrBlank() {
        setPlan(payload())
        compose.onNodeWithText("No unallocated demand").assertIsDisplayed()
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertCountEquals(expected: Int) {
    val actual = fetchSemanticsNodes().size
    assertTrue("expected $expected nodes, found $actual", actual == expected)
}
