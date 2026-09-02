package com.example.skillsync.ui.report

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skillsync.data.ManagerRepository
import com.example.skillsync.data.cache.ViberConfig
import com.example.skillsync.data.cache.ViberConfigStore
import com.example.skillsync.data.cache.ViberOutboxItem
import com.example.skillsync.data.cache.ViberOutboxStore
import com.example.skillsync.util.ViberAutomationEngine
import com.example.skillsync.util.ViberAutomationService
import com.example.skillsync.util.ViberDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ViberAutomationUiState(
    val isLoading: Boolean = false,
    val isSendingAll: Boolean = false,
    val items: List<ViberOutboxItem> = emptyList(),
    val config: ViberConfig = ViberConfig(),
    val isAccessibilityEnabled: Boolean = false,
    val bannerMessage: String? = null,
)

class ViberAutomationViewModel(
    private val repository: ManagerRepository = ManagerRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(ViberAutomationUiState())
    val uiState: StateFlow<ViberAutomationUiState> = _uiState.asStateFlow()

    private var currentEmail: String = ""

    fun load(managerEmail: String) {
        currentEmail = managerEmail
        val config = ViberConfigStore.load(managerEmail)
        val items = ViberOutboxStore.getAll(managerEmail)
        _uiState.value = _uiState.value.copy(
            config = config,
            items = items,
            isAccessibilityEnabled = ViberAutomationService.isRunning,
        )

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                // Also fetch remote queue items from backend to populate outbox if empty
                val remoteQueue = repository.viberQueue(managerEmail, fresh = false)
                val itemsRaw = remoteQueue.data?.get("items") as? List<*> ?: emptyList<Any>()
                val candidates = mutableListOf<ViberOutboxItem>()

                for (it in itemsRaw) {
                    val m = it as? Map<*, *> ?: continue
                    val id = m["id"]?.toString().orEmpty()
                    if (id.isNotBlank()) {
                        candidates.add(
                            ViberOutboxItem(
                                id = id,
                                category = m["category"]?.toString().orEmpty(),
                                recipientName = m["recipient_name"]?.toString().orEmpty(),
                                recipientEmail = m["recipient_email"]?.toString().orEmpty(),
                                recipientPhone = m["recipient_phone"]?.toString().orEmpty(),
                                courseName = m["course_name"]?.toString().orEmpty(),
                                messageText = m["message_text"]?.toString().orEmpty(),
                            )
                        )
                    }
                }
                if (candidates.isNotEmpty()) {
                    ViberOutboxStore.enqueue(managerEmail, candidates)
                }
            } catch (_: Exception) {}

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                items = ViberOutboxStore.getAll(managerEmail),
                isAccessibilityEnabled = ViberAutomationService.isRunning,
            )
        }
    }

    fun sendAllNow(context: Context) {
        val email = currentEmail.ifBlank { return }
        val pending = ViberOutboxStore.getPending(email)
        if (pending.isEmpty()) {
            _uiState.value = _uiState.value.copy(bannerMessage = "Outbox is clear — no pending messages")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSendingAll = true)
            val sent = ViberDispatcher.dispatchBatch(context, email, pending)
            _uiState.value = _uiState.value.copy(
                isSendingAll = false,
                items = ViberOutboxStore.getAll(email),
                bannerMessage = "Dispatched $sent message${if (sent == 1) "" else "s"} to Viber",
            )
        }
    }

    fun retryItem(context: Context, item: ViberOutboxItem) {
        val email = currentEmail.ifBlank { return }
        ViberOutboxStore.retry(email, item.id)
        viewModelScope.launch {
            ViberDispatcher.dispatchBatch(context, email, listOf(item))
            _uiState.value = _uiState.value.copy(
                items = ViberOutboxStore.getAll(email),
                bannerMessage = "Retried dispatch to ${item.recipientName}",
            )
        }
    }

    fun updateConfig(newConfig: ViberConfig) {
        val email = currentEmail.ifBlank { return }
        ViberConfigStore.save(email, newConfig)
        _uiState.value = _uiState.value.copy(config = newConfig)

        viewModelScope.launch {
            try {
                repository.updateViberConfig(
                    mapOf(
                        "email" to email,
                        "auto_send_demand" to newConfig.autoSendDemand,
                        "auto_send_weekly" to newConfig.autoSendWeekly,
                        "dispatch_mode" to newConfig.dispatchMode,
                        "viber_bot_token" to newConfig.viberBotToken,
                        "webhook_url" to newConfig.webhookUrl,
                    )
                )
            } catch (_: Exception) {}
        }
    }

    fun clearSent() {
        val email = currentEmail.ifBlank { return }
        ViberOutboxStore.clearSent(email)
        _uiState.value = _uiState.value.copy(items = ViberOutboxStore.getAll(email))
    }

    fun clearBanner() {
        _uiState.value = _uiState.value.copy(bannerMessage = null)
    }
}
