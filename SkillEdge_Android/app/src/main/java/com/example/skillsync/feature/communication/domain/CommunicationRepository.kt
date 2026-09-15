package com.example.skillsync.feature.communication.domain

import com.example.skillsync.core.network.CommunicationApi
import com.example.skillsync.core.network.RetrofitClient
import com.example.skillsync.feature.communication.engine.CommunicationPurpose

/**
 * Owns the underlying composition mechanism for [CommunicationRequest]:
 * the authoritative server composer (`GET /api/v2/message/compose`, backend
 * `_compose_manager_message`, which accepts only a manager note, never free
 * text standing in for a business fact) with [ManagerCommunicationComposer]
 * as the local fallback. Both paths consume the exact same
 * [CommunicationRequest]/evidence contract — the fallback is a business-
 * logic-equivalent local mirror, not a different, older engine. Callers go
 * through [ComposeManagerMessageUseCase], not this repository directly.
 *
 * MORNING_TEAM_GREETING is server-first too, through the backend
 * CommunicationService (`POST /api/v2/communication/generate`), which carries
 * the weekday policy prompt; the local composer is only used when that call
 * fails or returns nothing usable.
 *
 * Consumes [CommunicationApi] (Phase 4, `docs/phase4-api-ownership-matrix.md`).
 */
object CommunicationRepository {

    private val api: CommunicationApi by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { RetrofitClient.create() }

    suspend fun compose(managerEmail: String, request: CommunicationRequest): ComposeResult {
        if (request.purpose == CommunicationPurpose.MORNING_TEAM_GREETING) {
            return composeMorningGreeting(managerEmail, request) { api.generateCommunication(it) }
        }
        return try {
            val text = api.composeMessage(
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

    /**
     * Server first: the backend composes (model-backed when configured) and
     * validates. Weekend/no-message responses return empty. Any failure —
     * network, auth, blank or policy-failing text — falls back to the local
     * composer. Output is always reduced to the greeting alone.
     */
    internal suspend fun composeMorningGreeting(
        managerEmail: String,
        request: CommunicationRequest,
        remote: suspend (Map<String, Any>) -> Map<String, Any>,
    ): ComposeResult {
        val weekday = request.evidence.localWeekday ?: return ComposeResult("", fromServer = false)
        val body = mapOf<String, Any>(
            "manager" to managerEmail,
            "purpose" to CommunicationPurpose.MORNING_TEAM_GREETING.id,
            "recipient" to mapOf("type" to "TEAM"),
            "channel" to "MS_TEAMS_OR_VIBER",
            "localWeekday" to weekday.name,
            "recentGreetings" to request.evidence.recentGreetings,
            "variation" to request.evidence.variation,
        )
        try {
            val res = remote(body)
            if (res["requires_communication"] == false) return ComposeResult("", fromServer = true)
            val text = MorningNoteText.clean(res["message"] as? String ?: "")
            if (text.isNotBlank()) return ComposeResult(text, fromServer = true)
        } catch (_: Exception) {
            // fall through to the local composer
        }
        return ComposeResult(MorningNoteText.clean(composeOffline(request)), fromServer = false)
    }

    fun composeOffline(request: CommunicationRequest): String = ManagerCommunicationComposer.compose(request)
}

/** The copy/share contract for a morning note: the greeting alone, Viber markers kept. */
object MorningNoteText {
    private val label = Regex("""^\s*(generated message|message|greeting|morning note|monday|tuesday|wednesday|thursday|friday)\s*[:\-]?\s*$""", RegexOption.IGNORE_CASE)

    fun clean(raw: String): String {
        var t = raw.replace("\r\n", "\n").trim().replace("```", "").trim()
        val lines = t.lines().toMutableList()
        while (lines.isNotEmpty() && label.matches(lines.first())) lines.removeAt(0)
        t = lines.joinToString("\n")
        t = Regex("""^\s*(generated message|greeting)\s*:\s*""", RegexOption.IGNORE_CASE).replace(t, "")
        t = Regex("""\*\*(.+?)\*\*""").replace(t, "*$1*")
        t = Regex("""__(.+?)__""").replace(t, "_$1_")
        t = Regex("""[ \t]+""").replace(t, " ")
        t = Regex("""\n{3,}""").replace(t, "\n\n")
        return t.trim()
    }
}
