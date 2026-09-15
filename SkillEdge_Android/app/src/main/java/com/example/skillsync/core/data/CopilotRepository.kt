package com.example.skillsync.core.data

import com.example.skillsync.core.network.AgentAskRequest
import com.example.skillsync.core.network.AgentAskResponse
import com.example.skillsync.core.network.CopilotApi
import com.example.skillsync.core.network.RetrofitClient

/**
 * AI/Copilot domain — the manager's chat interface to the agent, per-trainer
 * (`agentAsk`) or team-level (`askCopilotTeam`). Follows the
 * [AuthRepository]/[BatchRepository] convention (`apiProvider` constructor
 * default, `open suspend fun` per call so tests can fake it directly).
 *
 * Consumes [CopilotApi] (Phase 4, `docs/phase4-api-ownership-matrix.md`).
 */
open class CopilotRepository(
    private val apiProvider: () -> CopilotApi = { RetrofitClient.create() },
) {
    private val api: CopilotApi by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { apiProvider() }

    open suspend fun ask(request: AgentAskRequest): AgentAskResponse = api.agentAsk(request)

    open suspend fun askTeam(body: Map<String, String>): Map<String, Any> = api.askCopilotTeam(body)
}
