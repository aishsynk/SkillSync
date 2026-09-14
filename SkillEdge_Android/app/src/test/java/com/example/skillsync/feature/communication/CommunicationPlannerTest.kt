package com.example.skillsync.feature.communication

import com.example.skillsync.feature.communication.engine.CommunicationComposer
import com.example.skillsync.feature.communication.engine.CommunicationPlanner
import com.example.skillsync.feature.communication.engine.CommunicationPlanner.AvailabilityState
import com.example.skillsync.feature.communication.engine.CommunicationPlanner.CandidateTrainer
import com.example.skillsync.feature.communication.engine.CommunicationPlanner.DemandFact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Semantic tests — these assert MEANING (who gets addressed, what is claimed),
 * not exact prose, per the "message semantics" test requirement. The whole
 * point of CommunicationPlanner is fixing the exact defect in the task's bad
 * example: "There are 5 open batches... and 2 of us are free" — an aggregate
 * count spoken as if it named specific available people.
 */
class CommunicationPlannerTest {

    private val demand = DemandFact(
        demandId = "DEM-900", course = "DP-700T00", client = "Contoso",
        deliveryMode = "ILT", startDate = "2026-09-20", location = "Remote",
    )

    @Test
    fun verifiedAvailableCandidate_isAddressedIndividually_byName() {
        val plans = CommunicationPlanner.planUnallocatedDemand(
            demand,
            listOf(
                CandidateTrainer("Niharika N", "niharika@koenig-solutions.com", capabilityMatch = true, availability = AvailabilityState.AVAILABLE),
                CandidateTrainer("Abhinav Samant", "abhinav@koenig-solutions.com", capabilityMatch = true, availability = AvailabilityState.COMMITTED),
            ),
        )
        assertEquals(1, plans.size)
        val plan = plans.single()
        assertEquals("INDIVIDUAL", plan.recipientType)
        assertEquals("Niharika N", plan.recipientName)
        // The committed trainer must never appear as a recipient or a claimed fact.
        assertFalse(plan.selectedFacts.any { it.value.toString().contains("Abhinav") })

        val text = CommunicationComposer.composeFromPlan(plan)
        assertTrue(text.contains("Niharika"))
        assertFalse(text.contains("Abhinav"))
        // Must name the real batch, not an aggregate count.
        assertTrue(text.contains("DP-700T00"))
        assertFalse(text.contains(Regex("""\d+\s+of (us|you)""")))
    }

    @Test
    fun aggregateAvailableCount_isNeverSpokenAsAClaimAboutSpecificPeople() {
        // Two trainers "available" in the aggregate sense is exactly the kind of
        // input that produced "2 of us are free" — but here every one of them
        // is either a capability mismatch or has unknown availability, so the
        // planner must not assert that anyone specific is free.
        val plans = CommunicationPlanner.planUnallocatedDemand(
            demand,
            listOf(
                CandidateTrainer("Priya Sharma", "priya@koenig-solutions.com", capabilityMatch = false, availability = AvailabilityState.AVAILABLE),
                CandidateTrainer("Rahul Verma", "rahul@koenig-solutions.com", capabilityMatch = false, availability = AvailabilityState.AVAILABLE),
            ),
        )
        assertEquals(1, plans.size)
        val plan = plans.single()
        assertEquals("CAPABILITY_ESCALATION", plan.purpose)
        val text = CommunicationComposer.composeFromPlan(plan)
        assertFalse(text.contains("free"))
        assertFalse(text.contains(Regex("""\d+\s+of (us|you)""")))
    }

    @Test
    fun capabilityMatchWithUnknownAvailability_asksThatPersonToConfirm_neverClaimsFree() {
        val plans = CommunicationPlanner.planUnallocatedDemand(
            demand,
            listOf(
                CandidateTrainer("Niharika N", "niharika@koenig-solutions.com", capabilityMatch = true, availability = AvailabilityState.UNKNOWN),
            ),
        )
        assertEquals(1, plans.size)
        val plan = plans.single()
        assertEquals("INDIVIDUAL", plan.recipientType)
        assertEquals("Niharika N", plan.recipientName)
        val text = CommunicationComposer.composeFromPlan(plan)
        // Must ask, not assert, availability.
        assertTrue(text.lowercase().contains("confirm"))
        assertFalse(text.contains("is identified as a strong candidate"))
    }

    @Test
    fun noCandidateDataAtAll_isHonestTeamMessage_notAClaimOfFreeHeadcount() {
        val plans = CommunicationPlanner.planUnallocatedDemand(demand, emptyList())
        assertEquals(1, plans.size)
        val plan = plans.single()
        assertEquals("TEAM", plan.recipientType)
        assertTrue(plan.situationSummary.contains("no verified-available trainer"))
        val text = CommunicationComposer.composeFromPlan(plan)
        assertFalse(text.contains(Regex("""\d+\s+of (us|you)\s+(is|are)\s+(free|available)""")))
    }

    @Test
    fun multipleVerifiedAvailableCandidates_getSeparateIndividualPlans_notOneBroadcast() {
        val plans = CommunicationPlanner.planUnallocatedDemand(
            demand,
            listOf(
                CandidateTrainer("Niharika N", "niharika@koenig-solutions.com", capabilityMatch = true, availability = AvailabilityState.AVAILABLE),
                CandidateTrainer("Priya Sharma", "priya@koenig-solutions.com", capabilityMatch = true, availability = AvailabilityState.AVAILABLE),
            ),
        )
        assertEquals(2, plans.size)
        assertTrue(plans.all { it.recipientType == "INDIVIDUAL" })
        assertEquals(setOf("Niharika N", "Priya Sharma"), plans.map { it.recipientName }.toSet())
    }

    @Test
    fun blankDemand_producesNoPlans() {
        assertTrue(CommunicationPlanner.planUnallocatedDemand(DemandFact("", ""), emptyList()).isEmpty())
    }
}
