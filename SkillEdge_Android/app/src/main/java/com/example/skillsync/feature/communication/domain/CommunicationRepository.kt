package com.example.skillsync.feature.communication.domain

import com.example.skillsync.core.network.RetrofitClient

/**
 * Owns the underlying composition mechanism for [CommunicationRequest]:
 * the authoritative server composer (`GET /api/v2/message/compose`, backend
 * `_compose_manager_message`, which accepts only a manager note, never free
 * text standing in for a business fact) with [ManagerCommunicationComposer]
 * as the local fallback. Both paths consume the exact same
 * [CommunicationRequest]/evidence contract — the fallback is a business-
 * logic-equivalent local mirror, not a different, older engine. Callers go
 * through [ComposeManagerMessageUseCase], not this repository directly.
 */
object CommunicationRepository {

    suspend fun compose(managerEmail: String, request: CommunicationRequest): ComposeResult {
        return try {
            val text = RetrofitClient.instance.composeMessage(
                manager = managerEmail,
                cadence = request.cadence,
                target = if (request.audience.type == CommunicationAudienceType.TEAM) "" else request.audience.email,
                myMessage = request.managerInstruction,
            ).message
            ComposeResult(text, fromServer = true)
        } catch (_: Exception) {
            ComposeResult(composeOffline(request), fromServer = false)
        }
    }

    fun composeOffline(request: CommunicationRequest): String = ManagerCommunicationComposer.compose(request)
}
