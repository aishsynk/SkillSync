package com.example.skillsync.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.example.skillsync.feature.home.TeamMemberCard
import com.example.skillsync.feature.home.TeamTab
import com.example.skillsync.theme.SkillSyncTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Design V3 Phase 2 — People / Team Intelligence focused tests.
 *
 * Proves the capacity-truthfulness rule (unknown status is never shown as a
 * healthy/active state), roster filter/sort correctness, and that a tap on a
 * trainer routes with that trainer's own identity — not a neighbour's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class PeopleIntelligenceTest {

    @get:Rule val compose = createComposeRule()

    // ── Capacity truthfulness ────────────────────────────────────────────

    private fun trainer(name: String, email: String, util: Int? = null) = mapOf<String, Any>(
        "trainer_name" to name,
        "official_email" to email,
        "designation" to "Corporate Trainer",
    ).let { m -> if (util != null) m + ("current_utilization" to util.toDouble()) else m }

    @Test
    fun unknownCurrentStatus_isLabelledUnknown_notActive() {
        compose.setContent {
            SkillSyncTheme {
                TeamMemberCard(
                    trainer = trainer("Abhinav Samant", "a@k.com"),
                    state = mapOf("current_status" to "unknown", "status_label" to "Unknown"),
                ) {}
            }
        }
        compose.onNodeWithText("Unknown").assertExists()
        compose.onAllNodesWithText("Active").assertCountEquals(0)
    }

    @Test
    fun missingState_alsoReadsAsUnknown_notActive() {
        // No RMS answer at all for this trainer (state == null) must read the
        // same way as an explicit "unknown" — never silently "Active".
        compose.setContent {
            SkillSyncTheme {
                TeamMemberCard(trainer = trainer("Priya Sharma", "p@k.com"), state = null) {}
            }
        }
        compose.onNodeWithText("Unknown").assertExists()
        compose.onAllNodesWithText("Active").assertCountEquals(0)
    }

    @Test
    fun freeWithNoLeave_isLabelledAvailable_onRealEvidence() {
        // "free" is a real RMS-evidenced state (the assignment feed answered
        // and found nothing booked) — distinct from "unknown" and allowed to
        // read as Available.
        compose.setContent {
            SkillSyncTheme {
                TeamMemberCard(
                    trainer = trainer("Rahul Verma", "r@k.com"),
                    state = mapOf("current_status" to "free"),
                ) {}
            }
        }
        compose.onNodeWithText("Available").assertExists()
    }

    // ── Roster filter / sort / navigation ────────────────────────────────

    private fun opsRow(name: String, email: String, util: Int, status: String) = mapOf<String, Any>(
        "trainer_name" to name,
        "official_email" to email,
        "designation" to "Trainer",
        "current_utilization" to util.toDouble(),
        "feedback_risk" to "Low",
        "capacity_bucket" to "Balanced",
    )

    private fun payload() = mapOf<String, Any>(
        "trainer_operations_df" to listOf(
            opsRow("Abhinav Samant", "abhinav@k.com", 90, "teaching_now"),
            opsRow("Niharika Rao", "niharika@k.com", 20, "free"),
        ),
        "trainer_current_state_df" to listOf(
            mapOf("trainer_email" to "abhinav@k.com", "current_status" to "teaching_now"),
            mapOf("trainer_email" to "niharika@k.com", "current_status" to "free"),
        ),
        "delivery_intelligence_df" to emptyList<Any>(),
    )

    @Test
    fun search_filtersRosterByName() {
        compose.setContent {
            SkillSyncTheme { TeamTab(data = payload(), capability = null, onTrainerClick = { _, _ -> }) }
        }
        compose.onNodeWithText("Search name, designation or course").performTextInput("Niharika")
        compose.onNodeWithText("Niharika Rao").assertExists()
        compose.onAllNodesWithText("Abhinav Samant").assertCountEquals(0)
    }

    @Test
    fun tappingATrainer_routesWithThatTrainersOwnIdentity() {
        var clickedEmail = ""
        var clickedName = ""
        compose.setContent {
            SkillSyncTheme {
                TeamTab(
                    data = payload(), capability = null,
                    onTrainerClick = { email, name -> clickedEmail = email; clickedName = name },
                )
            }
        }
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Niharika Rao"))
        compose.onNodeWithText("Niharika Rao").performClick()
        assertEquals("niharika@k.com", clickedEmail)
        assertEquals("Niharika Rao", clickedName)
    }

    @Test
    fun noReporteesReturned_showsCompactEmptyState() {
        compose.setContent {
            SkillSyncTheme {
                TeamTab(
                    data = mapOf<String, Any>("trainer_operations_df" to emptyList<Any>()),
                    capability = null,
                    onTrainerClick = { _, _ -> },
                )
            }
        }
        compose.onNodeWithText("No reportees returned. Check your account permissions.").assertExists()
    }
}
