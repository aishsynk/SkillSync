package com.example.skillsync.feature.communication.domain

import com.example.skillsync.feature.communication.engine.CommunicationComposer
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

        val plan = ContextSelectionPlan(
            intent = request.purpose.name,
            userMessage = "",
            myMessage = request.managerInstruction,
            recipientName = request.audience.name,
            recipientType = request.audience.type.name,
            purpose = request.purpose.name,
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
