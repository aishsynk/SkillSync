package com.example.skillsync.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import com.example.skillsync.theme.SkillSyncTheme
import com.example.skillsync.ui.batch.BatchCard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Demand card's expression of the intelligence layer.
 *
 * These pin the three rules the surface must never break: unknown is shown,
 * "could not check" never reads as "nothing found", and no check is claimed
 * that was not performed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class DemandIntelligenceTest {

    @get:Rule
    val compose = createComposeRule()

    private fun batch(
        candidates: List<Map<String, Any>> = emptyList(),
        intelligence: Map<String, Any>? = mapOf(
            "source" to "rms_free_schedule", "note" to "", "pool_size" to 12.0,
            "dnc_checked" to false, "leave_checked" to false,
        ),
        international: Boolean = false,
    ): Map<String, Any> = buildMap {
        put("demand_id", "D-1")
        put("course_name", "AZ-305T00: Designing Microsoft Azure Infrastructure Solutions")
        put("delivery_mode", if (international) "FMAT" else "ILO")
        put("delivery_mode_kind", if (international) "FMAT" else "ILO")
        put("is_international", international)
        put("relevance", 80.0)
        put("coverage_status", "Available")
        put("candidates", candidates)
        intelligence?.let { put("availability_intelligence", it) }
    }

    private fun candidate(
        name: String = "Abhinav Samant",
        availability: String = "available",
        visa: String? = null,
    ): Map<String, Any> = buildMap {
        put("trainer_name", name)
        put("match", 88.0)
        put("backup_role", "Primary")
        put("suitability_score", 88.0)
        put("utilization", 91.0)
        put("real_availability", mapOf("status" to availability, "reason" to
            if (availability == "unavailable") "on approved leave" else ""))
        // Numbers, as Gson actually decodes them from the wire. Strings
        // here would hide "Level 9.0" style rendering bugs.
        put("skill_level", 9.0)
        put("course_deliveries", 12.0)
        visa?.let {
            put("international_readiness",
                mapOf("visa" to it, "visa_detail" to "", "timezone_fit" to "workable"))
        }
    }

    private fun render(b: Map<String, Any>) {
        compose.setContent {
            SkillSyncTheme { BatchCard(b, isNew = false, onClick = {}) }
        }
    }

    @Test
    fun realAvailabilityReplacesTheUtilisationCaption() {
        // Utilisation described how busy someone had been, not whether they can
        // take the batch. It must no longer appear on the candidate line.
        render(batch(listOf(candidate())))
        compose.onNodeWithText("Free on these dates").assertExists()
        compose.onAllNodesWithText("91% utilised").assertCountEquals(0)
    }

    @Test
    fun anUnavailableTrainerStatesTheReason() {
        render(batch(listOf(candidate(availability = "unavailable"))))
        compose.onNodeWithText("on approved leave").assertExists()
    }

    @Test
    fun unknownAvailabilityIsShownRatherThanImpliedAvailable() {
        render(batch(listOf(candidate(availability = "unknown"))))
        compose.onNodeWithText("Availability unknown").assertExists()
    }

    @Test
    fun anUnknownVisaIsSurfacedForVerification() {
        // ~52% of trainers carry no visa record. Hiding them would remove half
        // the pool; the manager is told to verify instead.
        render(batch(listOf(candidate(visa = "unknown")), international = true))
        compose.onNodeWithText("Visa unknown, verify").assertExists()
    }

    @Test
    fun visaStatesAreDistinct() {
        render(batch(listOf(candidate(visa = "available")), international = true))
        compose.onNodeWithText("Visa available").assertExists()
    }

    @Test
    fun emptyFreeScheduleDoesNotClaimThatNoTrainerHasTheSkill() {
        render(batch(candidates = listOf(candidate()), intelligence = mapOf(
            "source" to "availability_unknown", "note" to "2 course-matched trainers found, but date availability was not returned",
            "pool_size" to 0.0, "dnc_checked" to false, "leave_checked" to false,
        )))
        compose.onNodeWithText("COURSE AVAILABILITY NOT VERIFIED").assertExists()
        compose.onAllNodesWithText("NO TRAINER HOLDS THIS COURSE").assertCountEquals(0)
    }

    @Test
    fun anUnverifiableCourseSaysSoRatherThanShowingAnEmptyPool() {
        render(batch(intelligence = mapOf(
            "source" to "unresolved", "note" to "course not found in the RMS catalogue",
            "pool_size" to 0.0, "dnc_checked" to false, "leave_checked" to false,
        )))
        compose.onNodeWithText("AVAILABILITY NOT VERIFIED").assertExists()
    }

    @Test
    fun aHealthyBatchShowsNoWarningStrip() {
        render(batch(listOf(candidate())))
        compose.onAllNodesWithText("AVAILABILITY NOT VERIFIED").assertCountEquals(0)
        compose.onAllNodesWithText("NO TRAINER HOLDS THIS COURSE").assertCountEquals(0)
    }

    @Test
    fun theCardDeclaresWhatItDidNotCheck() {
        // DNC is non-overridable, and the board does not evaluate it. Claiming
        // otherwise by silence would be worse than not checking.
        render(batch(listOf(candidate())))
        compose.onNodeWithText(
            "Client exclusions and leave are checked when you open this batch."
        ).assertExists()
    }

    @Test
    fun experienceOnThisCourseIsVisible() {
        render(batch(listOf(candidate())))
        compose.onNodeWithText("12 delivered").assertExists()
        compose.onNodeWithText("Level 9").assertExists()
    }

    @Test
    fun aBatchWithoutIntelligenceStillRenders() {
        // Backwards compatibility: an older cached payload must not crash.
        render(batch(listOf(mapOf("trainer_name" to "X", "match" to 70.0)), intelligence = null))
        compose.onAllNodesWithText("X").onFirst().assertExists()
    }
}
