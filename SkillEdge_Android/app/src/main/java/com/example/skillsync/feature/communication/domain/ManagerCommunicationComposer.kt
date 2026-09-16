package com.example.skillsync.feature.communication.domain

import com.example.skillsync.feature.communication.engine.CommunicationComposer
import com.example.skillsync.feature.communication.engine.CommunicationPlanner
import com.example.skillsync.feature.communication.engine.CommunicationPurpose
import com.example.skillsync.feature.communication.engine.validateFactualIntegrity
import com.example.skillsync.feature.communication.engine.ContextSelectionPlan
import com.example.skillsync.feature.communication.engine.FactItem
import com.example.skillsync.feature.communication.engine.validate

/**
 * The deterministic composer for [CommunicationRequest]. This is the ONE
 * business-logic path for manager-communication generation — it is used
 * both when a caller wants a message without going through the backend and
 * as the fallback when the backend is unreachable, so the offline path can
 * never silently reintroduce the old free-text-primary
 * `MessageRewriter`/`[User Message]` semantics: there is no `userMessage`
 * anywhere in this file, by construction of [CommunicationRequest] itself.
 *
 * [CommunicationEvidence] is turned into [FactItem]s and always composed
 * first; [CommunicationRequest.managerInstruction] can only append a
 * further sentence (see the `TEAM_PERIODIC_UPDATE`/`INDIVIDUAL_PERIODIC_UPDATE`
 * branches of `CommunicationComposer.composeFromPlan`) — it never replaces
 * or precedes an evidence sentence. [validate] then checks the composed
 * text does not state any course/day/number/name absent from the plan's
 * facts or [CommunicationRequest.managerInstruction] itself, so an
 * instruction cannot cause the generator to state an unsupported claim.
 */
object ManagerCommunicationComposer {

    fun compose(request: CommunicationRequest): String {
        if (request.purpose == CommunicationPurpose.MORNING_TEAM_GREETING) return composeMorningGreeting(request)
        val facts = mutableListOf<FactItem>()
        request.evidence.currentUtilisation?.let {
            facts += FactItem("current_utilization", it, "VERIFIED_SKILLSYNC_CONTEXT")
        }
        if (request.evidence.certGapCourses.isNotEmpty()) {
            facts += FactItem("cert_gap_courses", request.evidence.certGapCourses.joinToString(","), "VERIFIED_SKILLSYNC_CONTEXT")
        }
        request.evidence.learnerRating?.let {
            facts += FactItem("avg_rating", it, "VERIFIED_SKILLSYNC_CONTEXT")
            facts += FactItem("avg_rating_count", request.evidence.learnerRatingCount, "VERIFIED_SKILLSYNC_CONTEXT")
        }

        // The brief purposes compose from the same evidence sentences as the
        // periodic updates, so the offline mirror reuses that branch rather
        // than falling through to the generic one.
        val planPurpose = when (request.purpose) {
            CommunicationPurpose.WEEKLY_TEAM_BRIEF, CommunicationPurpose.MONTHLY_TEAM_REVIEW ->
                CommunicationPurpose.TEAM_PERIODIC_UPDATE.id
            CommunicationPurpose.WEEKLY_REPORTEE_BRIEF, CommunicationPurpose.MONTHLY_REPORTEE_REVIEW ->
                CommunicationPurpose.INDIVIDUAL_PERIODIC_UPDATE.id
            else -> request.purpose.id
        }
        val plan = ContextSelectionPlan(
            intent = planPurpose,
            userMessage = "",
            myMessage = request.managerInstruction,
            recipientName = request.audience.name,
            recipientType = request.audience.type.name,
            purpose = planPurpose,
            urgency = "NORMAL",
            tone = "professional",
            selectedFacts = facts,
            requiresCommunication = true,
            provenance = facts.associate { it.key to it.provenance },
        )

        val text = CommunicationComposer.composeFromPlan(plan)
        val result = validate(text, plan)
        // A validation issue here means the composer produced a claim not
        // grounded in evidence or the manager's own instruction — that is a
        // bug in this composer, not something to silently ship. Falling
        // back to a plain, fact-only sentence keeps the guarantee that
        // nothing ungrounded is ever sent.
        if (!result.passed) {
            return factsOnlyFallback(request)
        }
        return text
    }

    /**
     * Weekday greeting: planned by [CommunicationPlanner.planMorningGreeting]
     * (null, and so an empty result, on a weekend) and composed by the same
     * [CommunicationComposer]. Only the factual-integrity check applies — the
     * letter-shape rules (greeting line, closing block) do not fit a one-line
     * note. Output is the greeting alone: no labels and no code fences.
     */
    private fun composeMorningGreeting(request: CommunicationRequest): String {
        val weekday = request.evidence.localWeekday ?: return ""
        val plan = CommunicationPlanner.planMorningGreeting(
            weekday, request.evidence.recentGreetings, request.evidence.variation,
        ) ?: return ""
        val text = CommunicationComposer.composeFromPlan(plan)
        return if (validateFactualIntegrity(text, plan).isEmpty()) text.replace("```", "") else ""
    }

    private fun factsOnlyFallback(request: CommunicationRequest): String {
        val sentences = mutableListOf<String>()
        request.evidence.currentUtilisation?.let { sentences += "Current utilisation stands at $it%." }
        if (request.evidence.certGapCourses.isNotEmpty()) {
            sentences += "Open certification gaps remain in ${request.evidence.certGapCourses.joinToString(", ")}."
        }
        request.evidence.learnerRating?.let { sentences += "Participant feedback averages $it out of 5." }
        if (sentences.isEmpty()) sentences += "No exceptions to report this period."
        val greeting = if (request.audience.type == CommunicationAudienceType.TEAM) "Hello team," else "Hello,"
        return "$greeting\n\n${sentences.joinToString(" ")}\n\n*Thanks*"
    }
}
