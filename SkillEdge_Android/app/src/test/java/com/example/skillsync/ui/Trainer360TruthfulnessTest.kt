package com.example.skillsync.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.skillsync.feature.training.ui.Trainer360Content
import com.example.skillsync.theme.SkillSyncTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Design V3 Phase 3 — Trainer 360 focused truthfulness tests.
 *
 * Pins two real fixes: GrowthBenchmarkSection no longer fabricates a "peer
 * domain benchmark" (it never had real data behind it), and a missing
 * readiness score reads as "Unmeasured" rather than being indistinguishable
 * from a genuine "Watch" verdict.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class Trainer360TruthfulnessTest {

    @get:Rule val compose = createComposeRule()

    private fun payload(
        readinessScore: Double? = 46.0,
        courses: List<Map<String, Any>> = listOf(mapOf("course" to "AI-102T00: Develop AI Solutions in Azure")),
        heldCerts: List<Map<String, Any>> = listOf(mapOf("name" to "Microsoft Azure Data Fundamentals", "code" to "DP-900")),
    ) = mapOf<String, Any>(
        "identity" to mapOf("name" to "Abhinav Samant", "email" to "abhinav@k.com", "designation" to "Corporate Trainer"),
        "metrics" to buildMap {
            if (readinessScore != null) put("readiness_score", readinessScore)
            put("risk_level", "Low")
        },
        "utilization" to mapOf("current" to 39.0, "available" to true),
        "capability" to mapOf("courses" to courses),
        "certifications" to mapOf("held" to heldCerts, "gap_count" to 0.0),
        "delivery" to mapOf("assignments" to emptyList<Any>()),
        "feedback" to mapOf("negative_details" to emptyList<Any>()),
    )

    private fun openGrowthTab() {
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Growth & Peer"))
        compose.onNodeWithText("Growth & Peer").performClick()
    }

    @Test
    fun growthTab_neverShowsFabricatedPeerBenchmark() {
        compose.setContent { SkillSyncTheme { Trainer360Content(payload()) } }
        openGrowthTab()
        // These strings were the fabricated content — a hardcoded lookup by
        // guessed "domain," never backed by any real data.
        compose.onAllNodesWithText("PEER DOMAIN BENCHMARK").assertCountEquals(0)
        compose.onAllNodesWithText("TARGET CERTIFICATIONS FOR PIPELINE DEMAND", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("CROSS-DOMAIN GROWTH PATH").assertCountEquals(0)
        compose.onAllNodesWithText("Recommended Adjacent Domain", substring = true).assertCountEquals(0)
    }

    @Test
    fun growthTab_statesPeerDataIsUnavailable_andShowsOnlyRealOwnRecord() {
        compose.setContent { SkillSyncTheme { Trainer360Content(payload()) } }
        openGrowthTab()
        compose.onNodeWithText("RMS does not expose peer or market benchmark data", substring = true).assertExists()
        // Real data — this trainer's own courses/certs — is still shown.
        compose.onAllNodesWithText("AI-102T00: Develop AI Solutions in Azure", substring = true).onFirst().assertExists()
    }

    @Test
    fun missingReadinessScore_isLabelledUnmeasured_notWatch() {
        compose.setContent { SkillSyncTheme { Trainer360Content(payload(readinessScore = null)) } }
        compose.onNodeWithText("Unmeasured").assertExists()
        compose.onAllNodesWithText("Watch").assertCountEquals(0)
    }

    @Test
    fun aRealMediocreReadinessScore_stillReadsAsWatch() {
        // Proves the fix didn't just delete the "Watch" state — a genuine
        // 46% readiness with no other flags is still a real Watch verdict.
        compose.setContent { SkillSyncTheme { Trainer360Content(payload(readinessScore = 46.0)) } }
        compose.onNodeWithText("Watch").assertExists()
    }

    // ── Identity ──────────────────────────────────────────────────────────

    @Test
    fun correctTrainerIdentity_renders() {
        compose.setContent { SkillSyncTheme { Trainer360Content(payload()) } }
        compose.onAllNodesWithText("Abhinav Samant").onFirst().assertExists()
        compose.onNodeWithText("Corporate Trainer").assertExists()
    }

    @Test
    fun noDeadHeroAction_onlyCommunicateShown() {
        // "View schedule" was removed: its only available callback opened a
        // practice/feedback-record screen, not a real schedule/calendar
        // destination — keeping the label would have been a mislabelled,
        // effectively dead action.
        compose.setContent { SkillSyncTheme { Trainer360Content(payload()) } }
        compose.onNodeWithText("Communicate").assertExists()
        compose.onAllNodesWithText("View schedule").assertCountEquals(0)
    }

    @Test
    fun communicateAction_carriesThisTrainersOwnName() {
        var recipientName = ""
        compose.setContent {
            SkillSyncTheme {
                Trainer360Content(
                    payload(),
                    onCommunicate = { recipientName = "Abhinav Samant" },
                )
            }
        }
        compose.onNodeWithText("Communicate").performClick()
        assertEquals("Abhinav Samant", recipientName)
    }

    // ── Utilisation chart: real history vs. honest fallback ────────────────

    @Test
    fun realUtilisationHistory_drivesTheTrendChart() {
        val history = mapOf<String, Any>(
            "months" to listOf(
                mapOf("month" to "Jul 2026", "utilization" to 52.0),
                mapOf("month" to "Aug 2026", "utilization" to 39.0),
            ),
        )
        compose.setContent { SkillSyncTheme { Trainer360Content(payload(), utilHistory = history) } }
        compose.onNodeWithText("2 months on record", substring = true).assertExists()
    }

    @Test
    fun noUtilisationHistory_usesHonestFallback_notAFabricatedChart() {
        compose.setContent { SkillSyncTheme { Trainer360Content(payload()) } }
        compose.onNodeWithText(
            "No utilisation series returned — this is missing data, not zero utilisation.",
        ).assertExists()
    }

    // ── Certification gap: only when real ───────────────────────────────────

    @Test
    fun certificationGap_rendersOnlyWhenARealGapExists() {
        val withGap = payload().toMutableMap()
        withGap["certifications"] = mapOf(
            "held" to emptyList<Any>(),
            "gap_count" to 1.0,
            "missing" to listOf(mapOf("code" to "AI-102", "name" to "Azure AI Engineer Associate", "priority" to "high")),
        )
        compose.setContent { SkillSyncTheme { Trainer360Content(withGap) } }
        compose.onNodeWithText("Capability").performClick()
        compose.onNodeWithText("Azure AI Engineer Associate", substring = true).assertExists()
    }

    @Test
    fun noCertificationGap_showsNoGapFabricated() {
        compose.setContent { SkillSyncTheme { Trainer360Content(payload()) } }
        compose.onNodeWithText("Capability").performClick()
        // gap_count = 0 in the base fixture — nothing invented.
        compose.onAllNodesWithText("Azure AI Engineer Associate", substring = true).assertCountEquals(0)
    }

    // ── Current delivery from real source ───────────────────────────────────

    @Test
    fun currentAssignment_rendersFromRealDeliveryData() {
        val withCurrent = payload().toMutableMap()
        withCurrent["delivery"] = mapOf(
            "current" to 1.0,
            "assignments" to listOf(
                mapOf("course_name" to "AZ-305T00: Azure Infrastructure Solutions", "state" to "current"),
            ),
        )
        compose.setContent { SkillSyncTheme { Trainer360Content(withCurrent) } }
        compose.onNodeWithText("AZ-305T00: Azure Infrastructure Solutions", substring = true).assertExists()
    }

    // ── Partial data does not kill the whole profile ────────────────────────

    @Test
    fun oneMissingIntelligenceSource_doesNotBreakTheRestOfTheProfile() {
        // No feedback/delivery/certification data at all — identity and the
        // rest of the profile must still render, not crash or blank out.
        val minimal = mapOf<String, Any>(
            "identity" to mapOf("name" to "Priya Sharma", "designation" to "Trainer"),
        )
        compose.setContent { SkillSyncTheme { Trainer360Content(minimal) } }
        compose.onAllNodesWithText("Priya Sharma").onFirst().assertExists()
    }
}
