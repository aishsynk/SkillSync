package com.example.skillsync.feature.communication.domain

/**
 * Verified, already-computed facts about the recipient that the composer may
 * cite. Every field here comes from a backend/repository payload — never
 * from free text. This is the "evidence" the message-generation contract
 * requires before any wording is produced, and it is what
 * [CommunicationValidator][com.example.skillsync.feature.communication.engine.validate]
 * checks generated text against.
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
 * Communication purposes this contract supports. Kept as a small closed set
 * (not a free string) so a caller cannot invent an unrecognised purpose the
 * composer has no deterministic wording for.
 */
enum class CommunicationPurpose { TEAM_PERIODIC_UPDATE, INDIVIDUAL_PERIODIC_UPDATE }

/**
 * The one structured input the manager-communication boundary accepts.
 *
 * There is no external `[User Message]` field here and none is permitted —
 * Aishwar (the manager) is always the sender, and the only content this
 * contract can act on is [evidence] (verified facts) plus, optionally,
 * [managerInstruction]. If SkillSync later needs a "draft a reply to an
 * inbound message" assistant, that is a different feature with its own,
 * separate contract; it must not be added back onto this one.
 *
 * [managerInstruction] may shape tone, emphasis, which purpose to lead with,
 * or a specific point to mention. It is communication metadata, not
 * business truth: it can never override, replace, or invent a value that
 * belongs in [evidence] — [ManagerCommunicationComposer] and
 * [CommunicationValidator][com.example.skillsync.feature.communication.engine.validate]
 * enforce that a generated message cannot state a fact absent from
 * [evidence] or [managerInstruction] itself, and evidence sentences are
 * always composed before any instruction-derived sentence, never replaced
 * by one.
 */
data class CommunicationRequest(
    val audience: CommunicationAudience,
    val purpose: CommunicationPurpose,
    val cadence: String,
    val evidence: CommunicationEvidence = CommunicationEvidence(),
    val managerInstruction: String = "",
)
