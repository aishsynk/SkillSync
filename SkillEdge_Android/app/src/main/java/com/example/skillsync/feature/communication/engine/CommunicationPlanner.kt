package com.example.skillsync.feature.communication.engine

/**
 * Phase C1/C2 of the Communication Intelligence rebuild.
 *
 * The problem this file exists to fix: [CommunicationContextSelector]'s
 * auto-generation path (Flow B) already has the right shape — it picks a
 * purpose, a requested action, and a set of selected/rejected facts — but it
 * never resolves WHO should actually receive the message. Every caller today
 * passes `recipientType = "TEAM"` up front (see `ManagerCommandCentre.kt`'s
 * `onOpenCommunication("TEAM", "", "AVAILABILITY_REQUEST", ...)`), so an
 * aggregate fact like "2 trainers are free" turns into a message asking the
 * whole team to self-identify, even when the app already knows exactly who
 * is and isn't eligible.
 *
 * [CommunicationPlanner] is the missing "who" step. It takes real, per-trainer
 * facts (capability match + availability — never aggregate counts) and
 * produces one [ContextSelectionPlan] per person who should actually be
 * messaged, reusing [ContextSelectionPlan]/[CommunicationComposer]/
 * [CommunicationValidator] exactly as they exist today. This is deliberately
 * NOT a new parallel engine — it is the fact-selection layer
 * [CommunicationContextSelector] was always supposed to have, added as its
 * own function so Flow A/B are untouched.
 */
object CommunicationPlanner {

    /** A real, unallocated batch — every field here must come from verified data. */
    data class DemandFact(
        val demandId: String,
        val course: String,
        val client: String = "",
        val deliveryMode: String = "",
        val startDate: String = "",
        val location: String = "",
    )

    enum class AvailabilityState {
        /** Verified free for the window (leave/booking data checked, nothing conflicts). */
        AVAILABLE,
        /** Verified booked/on leave for the window — never contacted for this batch. */
        COMMITTED,
        /** No leave/booking record was checked, or the check was inconclusive. */
        UNKNOWN,
    }

    /** One trainer's real, per-person standing against a specific [DemandFact]. */
    data class CandidateTrainer(
        val name: String,
        val email: String,
        /** True only when the trainer's own capability/certification record covers this course. */
        val capabilityMatch: Boolean,
        val availability: AvailabilityState,
    )

    /**
     * Produces one plan per person who should actually be contacted for an
     * unallocated batch — never a single broadcast built from an aggregate
     * count. Priority, in order:
     *
     * 1. Capability-matched AND verified available → an individual
     *    AVAILABILITY_REQUEST naming that person and the real batch.
     * 2. No one verified available, but some people are capability-matched
     *    with unknown availability → individual requests to those specific
     *    people, honestly asking them to confirm (never claiming they are
     *    free).
     * 3. No capability match exists in the given candidate set at all → a
     *    single CAPABILITY_ESCALATION plan (recipientType stays whatever the
     *    caller's escalation target is — this function does not invent one).
     * 4. No candidate data at all (empty list) → a single, honest TEAM
     *    plan whose facts say availability is unconfirmed, never that
     *    anyone specific is free.
     *
     * Returns an empty list only if [demand] itself is blank/invalid — never
     * silently drops a real unallocated batch.
     */
    fun planUnallocatedDemand(
        demand: DemandFact,
        candidates: List<CandidateTrainer>,
    ): List<ContextSelectionPlan> {
        if (demand.demandId.isBlank() || demand.course.isBlank()) return emptyList()

        val verifiedAvailable = candidates.filter { it.capabilityMatch && it.availability == AvailabilityState.AVAILABLE }
        if (verifiedAvailable.isNotEmpty()) {
            return verifiedAvailable.map { candidate -> individualAvailabilityPlan(demand, candidate, availabilityKnown = true) }
        }

        val matchedUnknown = candidates.filter { it.capabilityMatch && it.availability == AvailabilityState.UNKNOWN }
        if (matchedUnknown.isNotEmpty()) {
            return matchedUnknown.map { candidate -> individualAvailabilityPlan(demand, candidate, availabilityKnown = false) }
        }

        // No candidate data at all is a different situation from "we checked
        // and nobody qualifies" — the former is honestly unresolved, the
        // latter is a genuine escalation. Conflating them would turn "we
        // never looked" into a confident-sounding capability claim.
        if (candidates.isEmpty()) {
            return listOf(unresolvedTeamPlan(demand))
        }

        val anyCapabilityMatch = candidates.any { it.capabilityMatch }
        if (!anyCapabilityMatch) {
            return listOf(capabilityEscalationPlan(demand))
        }

        // Every candidate we know about is either a capability mismatch or
        // COMMITTED — there is genuinely no one to name. Broadcasting is the
        // honest fallback here, but the message must say availability is
        // unconfirmed, never that specific people (or "N of us") are free.
        return listOf(unresolvedTeamPlan(demand))
    }

    private fun individualAvailabilityPlan(
        demand: DemandFact,
        candidate: CandidateTrainer,
        availabilityKnown: Boolean,
    ): ContextSelectionPlan {
        val facts = buildList {
            add(FactItem("course", demand.course))
            add(FactItem("demand_id", demand.demandId))
            if (demand.location.isNotBlank()) add(FactItem("location", demand.location))
            if (demand.startDate.isNotBlank()) add(FactItem("start_date", demand.startDate))
            if (demand.deliveryMode.isNotBlank()) add(FactItem("delivery_mode", demand.deliveryMode))
            if (demand.client.isNotBlank()) add(FactItem("client", demand.client))
            add(FactItem("candidate_trainer", candidate.name))
            // Read by CommunicationComposer to phrase "confirm if available" (unknown)
            // rather than "identified as a strong candidate" (known-available) — see
            // composeFromPlan's AVAILABILITY_REQUEST branch.
            add(FactItem("availability_confirmed", availabilityKnown))
        }
        val situationSummary = if (availabilityKnown) {
            "${candidate.name} is capability-matched and verified available for the ${demand.course} batch."
        } else {
            "${candidate.name} is capability-matched for the ${demand.course} batch; availability is not yet confirmed."
        }
        return ContextSelectionPlan(
            intent = "AVAILABILITY_REQUEST",
            recipientName = candidate.name,
            recipientType = "INDIVIDUAL",
            purpose = "AVAILABILITY_REQUEST",
            situationSummary = situationSummary,
            expectedOutcome = "Trainer confirms availability for the specific batch named.",
            urgency = "NORMAL",
            tone = "professional",
            selectedFacts = facts,
            rejectedFacts = emptyList(),
            actionRequired = "request_availability_confirmation",
            requiresCommunication = true,
            provenance = facts.associate { it.key to it.provenance },
        )
    }

    private fun capabilityEscalationPlan(demand: DemandFact): ContextSelectionPlan {
        val facts = listOf(
            FactItem("course", demand.course),
            FactItem("demand_id", demand.demandId),
            FactItem("open_demand", 1),
        )
        return ContextSelectionPlan(
            intent = "CAPABILITY_ESCALATION",
            recipientType = "MANAGER",
            purpose = "CAPABILITY_ESCALATION",
            situationSummary = "The ${demand.course} batch requires skills not currently verified in the available candidate set.",
            expectedOutcome = "External staffing or cross-team support is initiated.",
            urgency = "NORMAL",
            tone = "professional",
            selectedFacts = facts,
            actionRequired = "escalate_external_coverage",
            requiresCommunication = true,
            provenance = facts.associate { it.key to it.provenance },
        )
    }

    private fun unresolvedTeamPlan(demand: DemandFact): ContextSelectionPlan {
        val facts = listOf(
            FactItem("course", demand.course),
            FactItem("demand_id", demand.demandId),
        )
        return ContextSelectionPlan(
            intent = "AVAILABILITY_REQUEST",
            recipientType = "TEAM",
            purpose = "AVAILABILITY_REQUEST",
            // Deliberately does NOT claim anyone is free — no candidate could be
            // resolved, so the honest statement is that availability needs
            // confirming, not that a headcount of people are "free".
            situationSummary = "The ${demand.course} batch is unallocated and no verified-available trainer could be identified; availability needs confirmation.",
            expectedOutcome = "A trainer with genuine availability comes forward so allocation can proceed.",
            urgency = "NORMAL",
            tone = "professional",
            selectedFacts = facts,
            actionRequired = "request_availability_confirmation",
            requiresCommunication = true,
            provenance = facts.associate { it.key to it.provenance },
        )
    }
}
