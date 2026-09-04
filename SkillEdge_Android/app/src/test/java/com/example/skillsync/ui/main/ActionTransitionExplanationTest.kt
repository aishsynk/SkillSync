package com.example.skillsync.ui.main

import org.junit.Assert.assertTrue
import org.junit.Test

class ActionTransitionExplanationTest {
    @Test
    fun demandStartExplainsThatNoAllocationOccurs() {
        val copy = actionTransitionExplanation("in_progress", "Demand")
        assertTrue(copy.contains("In progress"))
        assertTrue(copy.contains("does not assign a trainer"))
        assertTrue(copy.contains("RMS"))
    }

    @Test
    fun feedbackEscalationExplainsQueueOnlyAndNoAutomaticMessage() {
        val copy = actionTransitionExplanation("escalated", "Feedback")
        assertTrue(copy.contains("escalated queue"))
        assertTrue(copy.contains("does not reply to the learner"))
        assertTrue(copy.contains("No message is sent automatically"))
    }

    @Test
    fun allocationCloseDoesNotClaimTheUnderlyingIssueWasFixed() {
        val copy = actionTransitionExplanation("closed", "Allocation")
        assertTrue(copy.contains("removes it from the open queue"))
        assertTrue(copy.contains("does not allocate the trainer"))
    }
}
