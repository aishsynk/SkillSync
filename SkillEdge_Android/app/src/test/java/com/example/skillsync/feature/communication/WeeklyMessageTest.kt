package com.example.skillsync.feature.communication

import com.example.skillsync.feature.communication.engine.MessageStyle
import com.example.skillsync.feature.communication.engine.ReporteeSignals
import com.example.skillsync.feature.communication.engine.composeManagerStandpointNote
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `composeTeamMessage`/`composeReporteeMessage` (a second, independent
 * Teams/Viber prose engine) and `MessageRewriter` (the free-text
 * `[User Message]`/`[My Message]` intent-precedence engine) were both
 * retired in Phase 2 of the architecture restructuring: both had zero
 * production callers, and preserving either as an unused parallel engine is
 * exactly what this restructuring exists to remove. Live weekly/reportee
 * Teams messages go through `feature.communication.domain.
 * ManagerCommunicationComposer` (offline) or the backend
 * `/api/v2/message/compose` endpoint (online) — see
 * `ManagerCommunicationComposerTest`. `composeManagerStandpointNote`
 * remains: it is a distinct, still-live artifact type (an internal
 * labelled-field manager note, not Teams/Viber prose).
 */
class WeeklyMessageTest {

    @Test
    fun managerStandpoint_containsStandpointMockReadinessAndImmediateFocus() {
        val signals = ReporteeSignals(
            name = "Abhinav Samant",
            utilisation = 88,
            capacityBucket = "Stretched",
            readiness = 82,
            certGaps = 1,
            certGapCourses = listOf("AZ-104"),
            learnerRating = 4.2,
            learnerRatingCount = 5,
            learnerRecentDate = "10 Aug 2026",
        )
        val note = composeManagerStandpointNote(signals, MessageStyle.TEAMS)
        assertTrue(note.contains("Weekly Manager Standpoint for Abhinav"))
        assertTrue(note.contains("**Standpoint:**"))
        assertTrue(note.contains("High Workload (Stretched at 88% util)"))
        // Evidence-only: learner rating replaces the old mock & readiness boilerplate
        assertTrue(note.contains("**Learner rating 90 day:**"))
        assertTrue(note.contains("4.2/5 from 5 responses"))
        assertTrue(note.contains("**Immediate Focus:**"))
        assertTrue(note.contains("AZ-104") && note.contains("certification exam"))
        assertFalse(note.contains("Mock & Readiness"))
        assertFalse(note.contains("Goal → Steps → Verify"))
        assertFalse(note.contains("•"))
    }

    @Test
    fun managerStandpoint_handlesBenchAndFeedbackRisk() {
        val signals = ReporteeSignals(
            name = "Divya Nair",
            utilisation = 20,
            capacityBucket = "On Bench",
            feedbackRisk = "High",
            targetGrowthCourses = listOf("DP-203"),
            negativeFeedbackCount = 2,
            hrNegativeCount = 1,
        )
        val note = composeManagerStandpointNote(signals, MessageStyle.PLAIN)
        assertTrue(note.contains("Weekly Manager Standpoint for Divya"))
        assertTrue(note.contains("Standpoint: Available / On Bench (20% util)"))
        assertTrue(note.contains("Learner rating 90 day:"))
        assertTrue(note.contains("Immediate Focus: review the 2 negative feedback"))
        assertFalse(note.contains("•"))
    }
}
