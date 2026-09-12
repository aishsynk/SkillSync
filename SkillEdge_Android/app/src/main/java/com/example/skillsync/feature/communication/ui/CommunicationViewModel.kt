package com.example.skillsync.feature.communication.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skillsync.core.data.ManagerRepository
import com.example.skillsync.feature.communication.engine.CommunicationGenerator
import com.example.skillsync.feature.communication.engine.GeneratedMessage
import com.example.skillsync.feature.communication.engine.ValidationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CommunicationHistoryItem(
    val id: String = "",
    val recipient: String = "",
    val recipientType: String = "UNKNOWN",
    val channel: String = "",
    val purpose: String = "",
    val relatedEntityType: String = "",
    val relatedEntityId: String = "",
    val message: String = "",
    val status: String = "",
    val createdAt: String = "",
)

data class CommunicationUiState(
    val recipientName: String = "",
    val recipientType: String = "MANAGER",
    val recipientRelationship: String = "",
    val purpose: String = "GENERAL_PROFESSIONAL",
    val userMessage: String = "",
    val myMessage: String = "",
    val relatedEntityType: String = "",
    val relatedEntityId: String = "",
    val result: GeneratedMessage? = null,
    val usedServer: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val history: List<CommunicationHistoryItem> = emptyList(),
    val historyLoading: Boolean = false,
    val lastSavedId: String? = null,
)

class CommunicationViewModel : ViewModel() {
    private val repository = ManagerRepository()
    private val _uiState = MutableStateFlow(CommunicationUiState())
    val uiState: StateFlow<CommunicationUiState> = _uiState.asStateFlow()

    fun setRecipientName(value: String) { _uiState.value = _uiState.value.copy(recipientName = value) }
    fun setRecipientType(value: String) { _uiState.value = _uiState.value.copy(recipientType = value) }
    fun setRecipientRelationship(value: String) { _uiState.value = _uiState.value.copy(recipientRelationship = value) }
    fun setPurpose(value: String) { _uiState.value = _uiState.value.copy(purpose = value) }
    fun setUserMessage(value: String) { _uiState.value = _uiState.value.copy(userMessage = value) }
    fun setMyMessage(value: String) { _uiState.value = _uiState.value.copy(myMessage = value) }
    fun setRelated(type: String, id: String) {
        _uiState.value = _uiState.value.copy(relatedEntityType = type, relatedEntityId = id)
    }

    fun clearResult() {
        _uiState.value = _uiState.value.copy(result = null, usedServer = false, error = null, lastSavedId = null)
    }

    fun generate(manager: String) {
        viewModelScope.launch {
            val s = _uiState.value
            _uiState.value = s.copy(loading = true, error = null, lastSavedId = null)
            val request = buildRequest(manager)
            try {
                val live = repository.generateCommunication(request)
                val parsed = parseServerResult(live)
                _uiState.value = _uiState.value.copy(
                    result = parsed, usedServer = true, loading = false, error = null,
                )
            } catch (e: Exception) {
                val local = CommunicationGenerator.generate(request)
                _uiState.value = _uiState.value.copy(
                    result = local, usedServer = false, loading = false,
                    error = "Server unavailable: generated locally (offline). ${e.localizedMessage ?: ""}",
                )
            }
        }
    }

    fun save(manager: String, status: String) {
        val result = _uiState.value.result ?: return
        viewModelScope.launch {
            val s = _uiState.value
            val record = buildRequest(manager).toMutableMap().apply {
                this["message"] = result.text
                this["status"] = status
            }
            try {
                val saved = repository.saveCommunication(record)
                val id = saved["id"] as? String
                _uiState.value = _uiState.value.copy(lastSavedId = id, error = null)
                loadHistory(manager)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Could not save to history: ${e.localizedMessage ?: "unknown error"}",
                )
            }
        }
    }

    fun loadHistory(manager: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(historyLoading = true)
            val res = repository.communicationHistory(manager)
            val items = (res.data?.get("items") as? List<*>)
                ?.mapNotNull { parseHistoryItem(it) } ?: emptyList()
            _uiState.value = _uiState.value.copy(history = items, historyLoading = false)
        }
    }

    private fun buildRequest(manager: String): Map<String, Any> {
        val s = _uiState.value
        return mapOf(
            "manager" to manager,
            "recipient" to mapOf(
                "name" to s.recipientName,
                "type" to s.recipientType,
                "relationship" to s.recipientRelationship,
            ),
            "channel" to "MS_TEAMS_OR_VIBER",
            "purpose" to s.purpose,
            "userMessage" to s.userMessage,
            "myMessage" to s.myMessage,
            "relatedEntityType" to s.relatedEntityType,
            "relatedEntityId" to s.relatedEntityId,
        )
    }

    private fun parseServerResult(live: Map<String, Any>): GeneratedMessage {
        val validationMap = (live["validation"] as? Map<*, *>)
            ?.mapKeys { it.key.toString() } ?: emptyMap()
        val passed = validationMap["passed"] as? Boolean ?: false
        val issues = (validationMap["issues"] as? List<*>)
            ?.mapNotNull { it as? String } ?: emptyList()
        val purpose = live["purpose"] as? String ?: _uiState.value.purpose
        val factsUsed = (live["facts_used"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val selectedFacts = (live["selected_facts"] as? List<*>)?.mapNotNull { it as? String } ?: factsUsed
        val rejectedFacts = (live["rejected_facts"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val genMode = live["generation_mode"] as? String ?: "INTELLIGENT_ENGINE"
        val requiresComm = live["requires_communication"] as? Boolean ?: true
        val noReason = live["no_message_reason"] as? String
        return GeneratedMessage(
            text = live["message"] as? String ?: "",
            validation = ValidationResult(passed, issues),
            factsUsed = factsUsed,
            purpose = purpose,
            tone = live["tone"] as? String ?: "",
            selectedFacts = selectedFacts,
            rejectedFacts = rejectedFacts,
            generationMode = genMode,
            requiresCommunication = requiresComm,
            noMessageReason = noReason,
        )
    }

    private fun parseHistoryItem(raw: Any?): CommunicationHistoryItem? {
        val m = raw as? Map<*, *> ?: return null
        val map = m.mapKeys { it.key.toString() }
        return CommunicationHistoryItem(
            id = map["id"] as? String ?: "",
            recipient = map["recipient"] as? String ?: "",
            recipientType = map["recipientType"] as? String ?: "UNKNOWN",
            channel = map["channel"] as? String ?: "",
            purpose = map["purpose"] as? String ?: "",
            relatedEntityType = map["relatedEntityType"] as? String ?: "",
            relatedEntityId = map["relatedEntityId"] as? String ?: "",
            message = map["message"] as? String ?: "",
            status = map["status"] as? String ?: "",
            createdAt = map["createdAt"] as? String ?: "",
        )
    }
}