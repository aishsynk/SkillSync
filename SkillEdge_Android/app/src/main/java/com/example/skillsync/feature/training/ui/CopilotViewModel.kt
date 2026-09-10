package com.example.skillsync.ui.trainer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skillsync.data.api.AgentAskRequest
import com.example.skillsync.data.api.AgentAskResponse
import com.example.skillsync.data.api.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** One row of the team Copilot's structured `data` list. */
data class TeamDatum(val name: String, val email: String, val note: String)

data class TeamAnswer(
    val answer: String,
    val evidence: String?,
    val confidence: String?,
    val decisionVersion: String?,
    val questionKey: String?,
    val data: List<TeamDatum>,
)

sealed class ChatMessage {
    data class User(val text: String) : ChatMessage()
    data class Agent(val response: AgentAskResponse) : ChatMessage()
    data class Team(val response: TeamAnswer) : ChatMessage()
    object Loading : ChatMessage()
    data class Error(val message: String) : ChatMessage()
}

class CopilotViewModel : ViewModel() {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    /** Per-trainer question — unchanged path. */
    fun askQuestion(managerEmail: String, targetEmail: String, questionKey: String, questionLabel: String) {
        viewModelScope.launch {
            _messages.value = _messages.value + ChatMessage.User(questionLabel) + ChatMessage.Loading
            try {
                val req = AgentAskRequest(managerEmail, targetEmail, questionKey)
                val res = RetrofitClient.instance.agentAsk(req)
                _messages.value = _messages.value.filter { it !is ChatMessage.Loading } + ChatMessage.Agent(res)
            } catch (e: Exception) {
                _messages.value = _messages.value.filter { it !is ChatMessage.Loading } + ChatMessage.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Team-level question — used when the Copilot is opened without a trainer
     * target. Pass a [questionKey] for a chip tap, or [freeText] for typed input.
     */
    fun askTeam(managerEmail: String, questionLabel: String, questionKey: String? = null, freeText: String? = null) {
        viewModelScope.launch {
            _messages.value = _messages.value + ChatMessage.User(questionLabel) + ChatMessage.Loading
            try {
                val body = buildMap {
                    put("manager", managerEmail)
                    if (!questionKey.isNullOrBlank()) put("question_key", questionKey)
                    if (!freeText.isNullOrBlank()) put("question", freeText)
                }
                val raw = RetrofitClient.instance.askCopilotTeam(body)
                _messages.value = _messages.value.filter { it !is ChatMessage.Loading } +
                    ChatMessage.Team(raw.toTeamAnswer())
            } catch (e: Exception) {
                _messages.value = _messages.value.filter { it !is ChatMessage.Loading } + ChatMessage.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun clearChat() {
        _messages.value = emptyList()
    }
}

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any>.toTeamAnswer(): TeamAnswer {
    val rows = (this["data"] as? List<*>).orEmpty().mapNotNull { row ->
        (row as? Map<String, Any>)?.let {
            TeamDatum(
                name = (it["name"] ?: it["course"] ?: "").toString(),
                email = (it["email"] ?: "").toString(),
                note = (it["note"] ?: it["count"]?.let { c -> "$c open batch(es)" } ?: "").toString(),
            )
        }
    }
    return TeamAnswer(
        answer = (this["answer"] ?: "").toString(),
        evidence = this["evidence"]?.toString(),
        confidence = this["confidence"]?.toString(),
        decisionVersion = this["decisionVersion"]?.toString(),
        questionKey = this["question_key"]?.toString(),
        data = rows,
    )
}
