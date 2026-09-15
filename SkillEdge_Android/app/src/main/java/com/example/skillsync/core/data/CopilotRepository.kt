package com.example.skillsync.core.data

import com.example.skillsync.core.network.AgentAskRequest
import com.example.skillsync.core.network.AgentAskResponse
import com.example.skillsync.core.network.RetrofitClient
import com.example.skillsync.core.network.SkillEdgeApi

/**
 * AI/Copilot domain — the manager's chat interface to the agent, per-trainer
 * (`agentAsk`) or team-level (`askCopilotTeam`). Follows the
 * [AuthRepository]/[BatchRepository] convention (`apiProvider` constructor
 * default, `open suspend fun` per call so tests can fake it directly).
 */
open class CopilotRepository(
    private val apiProvider: () -> SkillEdgeApi = { RetrofitClient.instance },
) {
    private val api: SkillEdgeApi by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { apiProvider() }

    open suspend fun ask(request: AgentAskRequest): AgentAskResponse = api.agentAsk(request)

    open suspend fun askTeam(body: Map<String, String>): Map<String, Any> = api.askCopilotTeam(body)
}
