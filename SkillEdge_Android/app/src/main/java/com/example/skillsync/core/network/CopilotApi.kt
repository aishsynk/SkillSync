package com.example.skillsync.core.network

import retrofit2.http.Body
import retrofit2.http.POST

data class AgentAskRequest(
    val manager_email: String,
    val target_email: String,
    val question_key: String,
)

data class AgentAskResponse(
    val answer: String,
    val evidence: String?,
    val source: List<String>?,
    val confidence: String?,
    val decisionVersion: String?,
    val error: String?,
)

/** AI/Copilot domain — see [com.example.skillsync.core.data.CopilotRepository]. */
interface CopilotApi {
    /** Ask Copilot a question */
    @POST("api/agent/ask")
    suspend fun agentAsk(
        @Body request: AgentAskRequest
    ): AgentAskResponse

    /** Ask the team-level Copilot — opened without a specific trainer target.
     *  Body: { manager, question }  or  { manager, question_key }. */
    @POST("api/v2/copilot/team")
    suspend fun askCopilotTeam(
        @Body body: Map<String, String>
    ): Map<String, Any>
}
