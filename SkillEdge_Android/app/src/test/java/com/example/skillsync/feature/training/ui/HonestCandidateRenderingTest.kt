package com.example.skillsync.feature.training.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.example.skillsync.theme.SkillSyncTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HonestCandidateRenderingTest {
    @get:Rule val compose = createComposeRule()

    private fun setCandidate(candidates: List<Map<String, Any>>) {
        val b = mapOf("demand_id" to "123", "delivery_mode" to "ILT", "is_international" to false, "candidates" to candidates)
        compose.setContent {
            SkillSyncTheme {
                PlanBatchCard(
                    b = b,
                    urgent = false,
                    blocked = false,
                    thisWeek = false,
                    isNew = false,
                    expanded = true,
                    onToggleExpand = {},
                    onClick = {},
                    categoryTheme = "ilo"
                )
            }
        }
    }

    private fun assertNoText(text: String, substring: Boolean = true, ignoreCase: Boolean = true) {
        assertTrue(
            "Found unexpected text: $text",
            compose.onAllNodesWithText(text, substring = substring, ignoreCase = ignoreCase).fetchSemanticsNodes().isEmpty()
        )
    }

    // A. full candidate payload
    @Test
    fun testA_fullCandidatePayload() {
        setCandidate(listOf(mapOf(
            "trainer_name" to "Alice",
            "backup_role" to "Secondary",
            "suitability_score" to 85,
            "availability_status" to "available",
            "coverage" to "Good Match",
            "suitability_components" to mapOf(
                "skill" to 90, "readiness" to 80, "availability" to 100, "certification" to 100, "language" to 100
            )
        )))
        compose.onNodeWithText("Alice").assertIsDisplayed()
        compose.onNodeWithText("Secondary · 85 suitability", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Skill 90 · Ready 80 · Avail 100 · Cert 100 · Lang 100", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Good Match").assertIsDisplayed()
        compose.onNodeWithText("Available for these dates").assertIsDisplayed()
    }

    // B. suitability_score missing
    @Test
    fun testB_suitabilityScoreMissing() {
        setCandidate(listOf(mapOf(
            "trainer_name" to "Bob",
            "backup_role" to "Backup"
        )))
        assertNoText("suitability")
        assertNoText("90")
    }

    // C. suitability_components entirely missing
    @Test
    fun testC_suitabilityComponentsMissing() {
        setCandidate(listOf(mapOf("trainer_name" to "Charlie")))
        assertNoText("Skill")
        assertNoText("Ready")
        assertNoText("Avail 100")
        assertNoText("Cert 100")
        assertNoText("Lang 100")
    }

    // D. partial suitability_components
    @Test
    fun testD_partialSuitabilityComponents() {
        setCandidate(listOf(mapOf(
            "trainer_name" to "Dave",
            "suitability_components" to mapOf("skill" to 92, "certification" to 87)
        )))
        compose.onNodeWithText("Skill 92 · Cert 87", substring = true).assertIsDisplayed()
        assertNoText("Ready")
        assertNoText("0")
    }

    // E. every individual suitability component missing
    @Test
    fun testE_everyIndividualComponentMissing() {
        setCandidate(listOf(mapOf(
            "trainer_name" to "Eve",
            "suitability_components" to mapOf<String, Any>()
        )))
        assertNoText("Skill")
        assertNoText("0")
    }

    // F. availability_status missing
    @Test
    fun testF_availabilityStatusMissing() {
        setCandidate(listOf(mapOf("trainer_name" to "Frank")))
        compose.onNodeWithText("Availability unverified").assertIsDisplayed()
    }

    // G. availability_status = available
    @Test
    fun testG_availabilityStatusAvailable() {
        setCandidate(listOf(mapOf("trainer_name" to "Grace", "availability_status" to "available")))
        compose.onNodeWithText("Available for these dates").assertIsDisplayed()
    }

    // H. availability_status = conflict
    @Test
    fun testH_availabilityStatusConflict() {
        setCandidate(listOf(mapOf("trainer_name" to "Heidi", "availability_status" to "conflict")))
        compose.onNodeWithText("Schedule conflict").assertIsDisplayed()
    }

    // I. backup_role missing
    @Test
    fun testI_backupRoleMissing() {
        setCandidate(listOf(mapOf("trainer_name" to "Ivan", "suitability_score" to 70)))
        assertNoText("Primary Trainer")
        compose.onNodeWithText("70 suitability").assertIsDisplayed()
    }

    // J. coverage missing
    @Test
    fun testJ_coverageMissing() {
        setCandidate(listOf(mapOf("trainer_name" to "Judy")))
        compose.onNodeWithText("Not Assessed").assertIsDisplayed()
    }

    // K. blocked candidate
    @Test
    fun testK_blockedCandidate() {
        setCandidate(listOf(mapOf("trainer_name" to "Karl", "blocked" to true, "coverage" to "Best Match")))
        compose.onNodeWithText("Blocked").assertIsDisplayed()
        assertNoText("Best Match")
    }

    // L. coverage = No Coverage while blocked = false
    @Test
    fun testL_noCoverageUnblocked() {
        setCandidate(listOf(mapOf("trainer_name" to "Liam", "blocked" to false, "coverage" to "No Coverage", "match" to 99)))
        compose.onNodeWithText("No Coverage").assertIsDisplayed()
        // verify it doesn't have the healthy match string
        assertNoText("Best Match")
    }

    // M. trainer_name missing
    @Test
    fun testM_trainerNameMissing() {
        setCandidate(listOf(mapOf("trainer_name" to "", "suitability_score" to 99)))
        // Should skip the row entirely
        assertNoText("99 suitability")
    }

    // N. empty candidates list
    @Test
    fun testN_emptyCandidatesList() {
        setCandidate(emptyList())
        assertNoText("suitability")
        assertNoText("Availability unverified")
    }
}
