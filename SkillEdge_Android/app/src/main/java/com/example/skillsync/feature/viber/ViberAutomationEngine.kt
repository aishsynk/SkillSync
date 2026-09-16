package com.example.skillsync.feature.viber

import android.content.Context
import android.util.Log
import com.example.skillsync.core.data.ManagerRepository
import com.example.skillsync.core.storage.ViberConfig
import com.example.skillsync.core.storage.ViberConfigStore
import com.example.skillsync.core.storage.ViberOutboxItem
import com.example.skillsync.core.storage.ViberOutboxStore

/**
 * Background Automation Engine:
 * Generates and processes Viber outbox queues during background monitoring passes.
 */
object ViberAutomationEngine {

    private const val TAG = "ViberAutomationEngine"
    private val repository = ManagerRepository()

    /**
     * Executes one automation pass:
     * 1. Fetches candidate-matched unallocated demand and weekly reportee standpoint messages.
     * 2. Enqueues new items in [ViberOutboxStore].
     * 3. Transmits them only when a confirmed transport (the Viber Bot API with
     *    a token) is configured. Otherwise it prepares drafts and stops — the
     *    category switches decide what gets *prepared*, never what gets sent.
     */
    suspend fun processPass(
        context: Context,
        managerEmail: String,
        data: Map<String, Any>? = null,
    ) {
        try {
            val config = ViberConfigStore.load(managerEmail)
            val queueResult = repository.viberQueue(managerEmail, fresh = false)
            val queueMap = queueResult.data ?: return

            val itemsRaw = queueMap["items"] as? List<*> ?: return
            val candidatesToEnqueue = mutableListOf<ViberOutboxItem>()

            for (it in itemsRaw) {
                val itMap = it as? Map<*, *> ?: continue
                val id = itMap["id"]?.toString().orEmpty()
                if (id.isBlank()) continue

                val cat = itMap["category"]?.toString().orEmpty()
                val repName = itMap["recipient_name"]?.toString().orEmpty()
                val repEmail = itMap["recipient_email"]?.toString().orEmpty()
                val repPhone = itMap["recipient_phone"]?.toString().orEmpty()
                val courseName = itMap["course_name"]?.toString().orEmpty()
                val msgText = itMap["message_text"]?.toString().orEmpty()

                // Check category switches
                val shouldInclude = when (cat) {
                    ViberOutboxItem.CAT_DEMAND -> config.autoSendDemand
                    ViberOutboxItem.CAT_WEEKLY, ViberOutboxItem.CAT_WEEKEND -> config.autoSendWeekly
                    ViberOutboxItem.CAT_DELIVERY -> config.autoSendNudges
                    else -> true
                }

                if (shouldInclude && msgText.isNotBlank()) {
                    candidatesToEnqueue.add(
                        ViberOutboxItem(
                            id = id,
                            category = cat,
                            recipientName = repName,
                            recipientEmail = repEmail,
                            recipientPhone = repPhone,
                            courseName = courseName,
                            messageText = msgText,
                            status = ViberOutboxItem.STATUS_QUEUED,
                        )
                    )
                }
            }

            if (candidatesToEnqueue.isNotEmpty()) {
                val addedCount = ViberOutboxStore.enqueue(managerEmail, candidatesToEnqueue)
                Log.i(TAG, "Prepared $addedCount new Viber drafts")

                // A background pass may only transmit when a real transport
                // exists. The Intent paths need a foreground activity start —
                // from here they are silently dropped by the platform, so
                // dispatching them would mark items shared when nothing left the
                // device. Those stay ready for the manager to share by hand.
                val hasRealTransport = config.dispatchMode == ViberConfig.MODE_BOT_API &&
                    config.viberBotToken.isNotBlank()
                if (!hasRealTransport) {
                    Log.i(TAG, "No confirmed transport configured; drafts left ready to share")
                    return
                }
                val pending = ViberOutboxStore.getPending(managerEmail)
                if (pending.isNotEmpty()) {
                    val dispatched = ViberDispatcher.dispatchBatch(context, managerEmail, pending)
                    Log.i(TAG, "Transmitted $dispatched Viber messages via the bot API")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Viber automation pass encountered an error", e)
        }
    }
}
