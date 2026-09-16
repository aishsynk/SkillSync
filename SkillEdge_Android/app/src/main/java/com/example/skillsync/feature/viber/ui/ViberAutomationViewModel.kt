package com.example.skillsync.feature.viber.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skillsync.core.data.ManagerRepository
import com.example.skillsync.core.storage.ViberConfig
import com.example.skillsync.core.storage.ViberConfigStore
import com.example.skillsync.core.storage.ViberOutboxItem
import com.example.skillsync.core.storage.ViberOutboxStore
import com.example.skillsync.feature.viber.ViberAutomationEngine
import com.example.skillsync.feature.viber.ViberDispatcher
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

    /**
     * Test seam: show a known outbox and config without reading the disk cache
     * or the backend queue, so a screenshot test renders the real composables.
     */
    internal fun renderSnapshot(items: List<ViberOutboxItem>, config: ViberConfig) {
        _uiState.value = _uiState.value.copy(items = items, config = config, isLoading = false)
    }

    fun load(managerEmail: String) {
        // Without a signed-in manager there is no outbox to read and the queue
        // call cannot be scoped, so do not clear what is already on screen.
        if (managerEmail.isBlank()) return
        currentEmail = managerEmail
        val config = ViberConfigStore.load(managerEmail)
        val items = ViberOutboxStore.getAll(managerEmail)
        _uiState.value = _uiState.value.copy(
            config = config,
            items = items,
            isAccessibilityEnabled = false,
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
                isAccessibilityEnabled = false,
            )
        }
    }

    /**
     * True only when a transport that can confirm delivery is configured. Every
     * "sent" claim in this feature is gated on it; without it the app can only
     * prepare a draft and hand it to Viber through the share sheet.
     */
    private fun hasConfirmedTransport(config: ViberConfig) =
        config.dispatchMode == ViberConfig.MODE_BOT_API && config.viberBotToken.isNotBlank()

    /**
     * Transmits the whole queue — only offered when the bot API is configured,
     * because the share-sheet paths would open one Viber chooser per item and
     * nothing would actually be delivered by the app itself.
     */
    fun transmitAllViaBot(context: Context) {
        val email = currentEmail.ifBlank { return }
        val config = ViberConfigStore.load(email)
        if (!hasConfirmedTransport(config)) {
            _uiState.value = _uiState.value.copy(
                bannerMessage = "No Viber bot token configured, so nothing can be transmitted from here.",
            )
            return
        }
        val pending = ViberOutboxStore.getPending(email)
        if (pending.isEmpty()) {
            _uiState.value = _uiState.value.copy(bannerMessage = "Nothing is waiting to be transmitted.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSendingAll = true)
            val sent = ViberDispatcher.dispatchBatch(context, email, pending)
            _uiState.value = _uiState.value.copy(
                isSendingAll = false,
                items = ViberOutboxStore.getAll(email),
                bannerMessage = "Viber confirmed $sent of ${pending.size} message${if (pending.size == 1) "" else "s"}.",
            )
        }
    }

    /**
     * Hands the oldest waiting draft to Viber through the share sheet. One at a
     * time, because that is all the platform can honestly do: the manager picks
     * the recipient and taps send inside Viber itself.
     */
    fun shareNextDraft(context: Context) {
        val email = currentEmail.ifBlank { return }
        val next = ViberOutboxStore.getPending(email).lastOrNull()
        if (next == null) {
            _uiState.value = _uiState.value.copy(bannerMessage = "No drafts are waiting to be shared.")
            return
        }
        shareItem(context, next)
    }

    /** Opens Viber with this draft prefilled; never claims it was delivered. */
    fun shareItem(context: Context, item: ViberOutboxItem) {
        val email = currentEmail.ifBlank { return }
        val confirmed = hasConfirmedTransport(ViberConfigStore.load(email))
        ViberOutboxStore.retry(email, item.id)
        viewModelScope.launch {
            ViberDispatcher.dispatchBatch(context, email, listOf(item))
            _uiState.value = _uiState.value.copy(
                items = ViberOutboxStore.getAll(email),
                bannerMessage = if (confirmed) {
                    "Transmitted to ${item.recipientName} via the Viber bot."
                } else {
                    "Opened Viber with the draft for ${item.recipientName}. " +
                        "Pick the chat and send it there to deliver it."
                },
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

    /** Clears everything already handed off — shared or confirmed sent. */
    fun clearHandedOff() {
        val email = currentEmail.ifBlank { return }
        ViberOutboxStore.clearSent(email)
        _uiState.value = _uiState.value.copy(items = ViberOutboxStore.getAll(email))
    }

    fun clearBanner() {
        _uiState.value = _uiState.value.copy(bannerMessage = null)
    }
}
