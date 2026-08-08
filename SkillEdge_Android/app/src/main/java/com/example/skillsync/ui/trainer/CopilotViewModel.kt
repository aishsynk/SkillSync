package com.example.skillsync.ui.trainer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skillsync.data.api.AgentAskRequest
import com.example.skillsync.data.api.AgentAskResponse
import com.example.skillsync.data.api.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class ChatMessage {
    data class User(val text: String) : ChatMessage()
    data class Agent(val response: AgentAskResponse) : ChatMessage()
    object Loading : ChatMessage()
    data class Error(val message: String) : ChatMessage()
}

class CopilotViewModel : ViewModel() {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    fun askQuestion(managerEmail: String, targetEmail: String, questionKey: String, questionLabel: String) {
        viewModelScope.launch {
            // Add user message and loading indicator
            _messages.value = _messages.value + ChatMessage.User(questionLabel) + ChatMessage.Loading
            
            try {
                val req = AgentAskRequest(managerEmail, targetEmail, questionKey)
                val res = RetrofitClient.instance.agentAsk(req)
                
                // Remove loading, add agent response
                _messages.value = _messages.value.filter { it !is ChatMessage.Loading } + ChatMessage.Agent(res)
            } catch (e: Exception) {
                _messages.value = _messages.value.filter { it !is ChatMessage.Loading } + ChatMessage.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    fun clearChat() {
        _messages.value = emptyList()
    }
}
