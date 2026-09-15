package com.example.skillsync.feature.communication.domain

import com.example.skillsync.feature.communication.engine.MessageStyle

/**
 * Verified, already-computed facts about the recipient that the composer may
 * cite. Every field here must come from a backend/repository payload, never
 * from free text — this is the "evidence" the message-generation contract
 * requires before any wording is produced.
 */
data class CommunicationEvidence(
    val currentUtilisation: Int? = null,
    val certGapCourses: List<String> = emptyList(),
    val learnerRating: Double? = null,
    val learnerRatingCount: Int = 0,
)

enum class CommunicationAudienceType { TEAM, INDIVIDUAL }

data class CommunicationAudience(
    val type: CommunicationAudienceType,
    val name: String = "",
    val email: String = "",
)

/**
 * The one structured input the communication boundary accepts. It replaces
 * ad-hoc `userMessage` / `myMessage` string pairs passed straight into the
 * rewrite engine from Composables.
 *
 * [managerInstruction] is the manager's own note on what she wants conveyed
 * (backend field `my_message`) — it may shape tone/emphasis, never business
 * facts.
 *
 * [quotedInboundText] exists only for the "draft a reply to what someone
 * sent me" assistant: text the manager pasted from Teams/Viber that she is
 * replying to. It is never sent to the authoritative backend composer (that
 * endpoint has no such parameter) and is not a source of verified facts —
 * it is deliberately kept out of [CommunicationEvidence]. There is no
 * concept here of an external message "controlling" the generated output;
 * the manager is always the sender, and [evidence] is always what the
 * composer may state as fact.
 */
data class CommunicationRequest(
    val audience: CommunicationAudience,
    val cadence: String,
    val evidence: CommunicationEvidence = CommunicationEvidence(),
    val managerInstruction: String = "",
    val quotedInboundText: String = "",
    val style: MessageStyle = MessageStyle.TEAMS,
)
