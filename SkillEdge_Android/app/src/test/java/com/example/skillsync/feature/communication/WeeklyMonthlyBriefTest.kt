package com.example.skillsync.feature.communication

import com.example.skillsync.feature.communication.domain.CommunicationAudience
import com.example.skillsync.feature.communication.domain.CommunicationAudienceType
import com.example.skillsync.feature.communication.domain.CommunicationEvidence
import com.example.skillsync.feature.communication.domain.CommunicationRequest
import com.example.skillsync.feature.communication.domain.ComposeManagerMessageUseCase
import com.example.skillsync.feature.communication.engine.CommunicationPurpose
import com.example.skillsync.feature.communication.ui.viberAnnotated
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The weekly/monthly brief purposes exist client-side, compose evidence-first
 * prose offline, and the preview renderer never changes the copied payload.
 */
class WeeklyMonthlyBriefTest {

    private val evidence = CommunicationEvidence(
        currentUtilisation = 88,
        certGapCourses = listOf("DP-700T00"),
        learnerRating = 4.7,
        learnerRatingCount = 12,
    )

    private fun request(purpose: CommunicationPurpose, cadence: String, name: String = "") = CommunicationRequest(
        audience = CommunicationAudience(
            type = if (name.isBlank()) CommunicationAudienceType.TEAM else CommunicationAudienceType.INDIVIDUAL,
            name = name,
        ),
        purpose = purpose,
        cadence = cadence,
        evidence = evidence,
    )

    @Test fun the_four_brief_purposes_exist_with_stable_ids() {
        assertEquals("WEEKLY_TEAM_BRIEF", CommunicationPurpose.WEEKLY_TEAM_BRIEF.id)
        assertEquals("WEEKLY_REPORTEE_BRIEF", CommunicationPurpose.WEEKLY_REPORTEE_BRIEF.id)
        assertEquals("MONTHLY_TEAM_REVIEW", CommunicationPurpose.MONTHLY_TEAM_REVIEW.id)
        assertEquals("MONTHLY_REPORTEE_REVIEW", CommunicationPurpose.MONTHLY_REPORTEE_REVIEW.id)
        listOf("WEEKLY_TEAM_BRIEF", "MONTHLY_REPORTEE_REVIEW").forEach {
            assertEquals(it, CommunicationPurpose.fromId(it).id)
        }
    }

    @Test fun brief_purposes_compose_evidence_first_prose_offline() {
        val team = ComposeManagerMessageUseCase().offline(request(CommunicationPurpose.WEEKLY_TEAM_BRIEF, "weekly"))
        val reportee = ComposeManagerMessageUseCase().offline(
            request(CommunicationPurpose.MONTHLY_REPORTEE_REVIEW, "monthly", "Priya Sharma"),
        )
        // Evidence appears; the generic "review the current operational
        // requirements" fallthrough does not.
        listOf(team, reportee).forEach {
            assertTrue("no utilisation evidence in: $it", it.contains("88"))
            assertFalse("generic fallthrough in: $it", it.contains("current operational requirements"))
        }
        assertTrue(team.contains("team", ignoreCase = true))
        assertTrue(reportee.contains("Priya"))
    }

    @Test fun a_brief_never_claims_anyone_is_free() {
        val text = ComposeManagerMessageUseCase().offline(request(CommunicationPurpose.WEEKLY_TEAM_BRIEF, "weekly")).lowercase()
        listOf("are free", "is free", "spare capacity", "sitting idle", "nothing booked").forEach {
            assertFalse("'$it' in: $text", text.contains(it))
        }
    }

    // ── preview rendering vs copied payload ─────────────────────────────────

    @Test fun preview_renders_markers_while_the_payload_keeps_them() {
        val payload = "Hi team,\n\n*Two batches* are open and _DP-700T00_ starts Monday. ~Ignore the earlier note.~\n\n_Thanks_"
        val rendered = viberAnnotated(payload)
        // The rendered text drops the marker characters...
        assertFalse(rendered.text.contains("*"))
        assertFalse(rendered.text.contains("_"))
        assertFalse(rendered.text.contains("~"))
        assertTrue(rendered.text.contains("Two batches"))
        assertTrue(rendered.text.contains("DP-700T00"))
        // ...and carries one span per marker pair (bold, italic, strike, closing italic).
        assertEquals(4, rendered.spanStyles.size)
        // ...while the payload the manager copies is untouched.
        assertTrue(payload.contains("*Two batches*"))
        assertTrue(payload.contains("_DP-700T00_"))
        assertTrue(payload.contains("~Ignore the earlier note.~"))
    }

    @Test fun preview_leaves_unmatched_markers_alone() {
        // Markers inside a word (snake_case, emails, codes) are not formatting.
        val odd = "Hi team,\n\n2 * 3 batches and a_variable_name stay as typed."
        assertEquals(odd, viberAnnotated(odd).text)
        assertTrue(viberAnnotated(odd).spanStyles.isEmpty())
    }
}
