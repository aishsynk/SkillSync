package com.example.skillsync.feature.communication.domain

import com.example.skillsync.core.network.RetrofitClient
import com.example.skillsync.feature.communication.engine.MessageRewriter

/**
 * Single entry point weekly/monthly report ViewModels use to turn a
 * [CommunicationRequest] into final message text.
 *
 * Screens must not call [RetrofitClient] or [MessageRewriter] directly for
 * this — this repository owns both the authoritative server composer
 * (`GET /api/v2/message/compose`, backend `_compose_manager_message`, which
 * accepts only a manager note, never free text standing in for a business
 * fact) and the deterministic offline fallback, so the "try server, fall
 * back to a local deterministic mirror" logic exists in exactly one place
 * instead of being duplicated per screen.
 */
data class ComposeResult(val text: String, val fromServer: Boolean)

object CommunicationRepository {

    suspend fun compose(
        managerEmail: String,
        request: CommunicationRequest,
        offlineFallback: String = "",
    ): ComposeResult {
        return try {
            val text = RetrofitClient.instance.composeMessage(
                manager = managerEmail,
                cadence = request.cadence,
                target = if (request.audience.type == CommunicationAudienceType.TEAM) "" else request.audience.email,
                myMessage = request.managerInstruction,
            ).message
            ComposeResult(text, fromServer = true)
        } catch (_: Exception) {
            ComposeResult(offlineFallback.ifBlank { composeOffline(request) }, fromServer = false)
        }
    }

    /**
     * Deterministic local mirror, used only when the server is unreachable.
     * [CommunicationRequest.quotedInboundText] is honoured here (this is the
     * one path that can draft a reply to a pasted message); it is never
     * forwarded to the server composer above and never appears in
     * [CommunicationEvidence].
     */
    fun composeOffline(request: CommunicationRequest): String {
        val isTeam = request.audience.type == CommunicationAudienceType.TEAM
        require(request.managerInstruction.isNotBlank() || request.quotedInboundText.isNotBlank()) {
            "At least one of managerInstruction or quotedInboundText is required"
        }
        return MessageRewriter.compose(
            userMessage = request.quotedInboundText,
            myMessage = request.managerInstruction,
            style = request.style,
            targetName = request.audience.name,
            isTeam = isTeam,
            evidence = MessageRewriter.EvidenceContext(
                certGapCourses = request.evidence.certGapCourses,
                learnerRating = request.evidence.learnerRating,
                learnerRatingCount = request.evidence.learnerRatingCount,
                utilisation = request.evidence.currentUtilisation,
            ),
        )
    }
}
