package com.example.skillsync.feature.communication.domain

import com.example.skillsync.feature.communication.engine.CommunicationPlanner

/**
 * A recipient/purpose to hand to the Communication screen — never a
 * pre-written sentence; the engine still selects its own facts. This is
 * recipient-resolution/communication-purpose reasoning, so it lives here,
 * not in a feature ViewModel: a ViewModel decides *when* to ask for a hint
 * (e.g. which kind of board item this is), this resolver decides *who* and
 * *why*.
 */
data class CommunicationHint(
    val recipientType: String,
    val recipientName: String,
    val purpose: String,
    val relatedEntityType: String,
    val relatedEntityId: String,
)

/** A candidate trainer for an unstaffed-demand hint, independent of any feature's own model shape. */
data class HintCandidateTrainer(
    val name: String,
    val email: String,
    val capabilityMatch: Boolean,
    /** "AVAILABLE" | "COMMITTED" | "UNKNOWN" — see backend.py _match_trainers_for_demand. */
    val availability: String,
)

object CommunicationHintResolver {

    /**
     * Real, skill-matched candidates from the backend (see
     * backend.py _match_trainers_for_demand) — never an aggregate "team is
     * coverable" broadcast when a real person can be named. Same
     * CommunicationPlanner pattern as Today's unallocated-demand card
     * (ManagerCommandCentre.kt).
     */
    fun forUnstaffedDemand(demandId: String, course: String, candidates: List<HintCandidateTrainer>): CommunicationHint {
        val plannerCandidates = candidates.map { t ->
            CommunicationPlanner.CandidateTrainer(
                name = t.name, email = t.email, capabilityMatch = t.capabilityMatch,
                availability = when (t.availability) {
                    "AVAILABLE" -> CommunicationPlanner.AvailabilityState.AVAILABLE
                    "COMMITTED" -> CommunicationPlanner.AvailabilityState.COMMITTED
                    else -> CommunicationPlanner.AvailabilityState.UNKNOWN
                },
            )
        }
        val plan = CommunicationPlanner.planUnallocatedDemand(
            CommunicationPlanner.DemandFact(demandId = demandId, course = course),
            plannerCandidates,
        ).firstOrNull()
        return CommunicationHint(
            recipientType = plan?.recipientType ?: "TEAM", recipientName = plan?.recipientName.orEmpty(),
            purpose = "AVAILABILITY_REQUEST",
            relatedEntityType = "demand", relatedEntityId = demandId,
        )
    }

    fun forOneToOne(trainerName: String, trainerId: String): CommunicationHint = CommunicationHint(
        recipientType = "INDIVIDUAL", recipientName = trainerName,
        purpose = "GENERAL_PROFESSIONAL", relatedEntityType = "trainer", relatedEntityId = trainerId,
    )

    fun forOverload(trainerName: String, trainerId: String): CommunicationHint = CommunicationHint(
        recipientType = "INDIVIDUAL", recipientName = trainerName,
        purpose = "GENERAL_PROFESSIONAL", relatedEntityType = "trainer", relatedEntityId = trainerId,
    )

    fun forCertGap(trainerName: String, trainerId: String): CommunicationHint = CommunicationHint(
        recipientType = "INDIVIDUAL", recipientName = trainerName,
        purpose = "CAPABILITY_DEVELOPMENT", relatedEntityType = "trainer", relatedEntityId = trainerId,
    )
}
