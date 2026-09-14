package com.example.skillsync.feature.viber

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.skillsync.core.data.ManagerRepository
import com.example.skillsync.core.storage.ViberConfig
import com.example.skillsync.core.storage.ViberConfigStore
import com.example.skillsync.core.storage.ViberOutboxItem
import com.example.skillsync.core.storage.ViberOutboxStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Multi-strategy message dispatcher for Viber Background Automation.
 */
object ViberDispatcher {

    private const val TAG = "ViberDispatcher"
    private val repository = ManagerRepository()

    /**
     * Dispatches a list of queued outbox items according to the configured mode.
     */
    suspend fun dispatchBatch(
        context: Context,
        managerEmail: String,
        items: List<ViberOutboxItem>,
    ): Int = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext 0
        val config = ViberConfigStore.load(managerEmail)
        var successCount = 0

        when (config.dispatchMode) {
            ViberConfig.MODE_BOT_API -> {
                try {
                    val payload = mapOf(
                        "manager" to managerEmail,
                        "viber_token" to config.viberBotToken,
                        "webhook_url" to config.webhookUrl,
                        "items" to items.map { item ->
                            mapOf(
                                "id" to item.id,
                                "recipient_email" to item.recipientEmail,
                                "recipient_phone" to item.recipientPhone.ifBlank { config.reporteePhoneMap[item.recipientEmail.lowercase()].orEmpty() },
                                "recipient_name" to item.recipientName,
                                "message_text" to item.messageText,
                                "category" to item.category,
                            )
                        },
                    )
                    val resp = repository.dispatchViber(payload)
                    val results = resp["results"] as? List<*> ?: emptyList<Any>()
                    val sentIds = mutableListOf<String>()

                    for (r in results) {
                        val rMap = r as? Map<*, *> ?: continue
                        val id = rMap["id"]?.toString().orEmpty()
                        val status = rMap["status"]?.toString().orEmpty()
                        if (status == "SENT" && id.isNotBlank()) {
                            sentIds.add(id)
                            successCount++
                        } else if (status == "FAILED" && id.isNotBlank()) {
                            val err = rMap["error"]?.toString() ?: "Dispatch failed"
                            ViberOutboxStore.markStatus(managerEmail, id, ViberOutboxItem.STATUS_FAILED, err)
                        } else if (status == "SKIPPED" && id.isNotBlank()) {
                            // No bot token configured server-side — nothing was
                            // transmitted. Record it honestly rather than leaving
                            // the item silently stuck in its prior status.
                            val reason = rMap["reason"]?.toString() ?: "No Viber bot token configured"
                            ViberOutboxStore.markStatus(managerEmail, id, ViberOutboxItem.STATUS_SKIPPED, reason)
                        }
                    }
                    if (sentIds.isNotEmpty()) {
                        ViberOutboxStore.markAllSent(managerEmail, sentIds)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Bot API dispatch failed", e)
                    items.forEach {
                        ViberOutboxStore.markStatus(managerEmail, it.id, ViberOutboxItem.STATUS_FAILED, e.localizedMessage)
                    }
                }
            }

            ViberConfig.MODE_ACCESSIBILITY -> {
                // On-device accessibility automation was withdrawn (Play Protect
                // flags any accessibility service on a sideloaded APK). This mode
                // now behaves as the 1-tap Intent path.
                for (item in items) {
                    dispatchViaIntent(context, item, config)
                    // Opening the Viber share sheet is not delivery confirmation —
                    // the user still has to pick a recipient and tap send inside
                    // Viber. Matches CommunicationScreen.kt's existing
                    // SHARED_EXTERNALLY distinction; never claim SENT here.
                    ViberOutboxStore.markStatus(managerEmail, item.id, ViberOutboxItem.STATUS_SHARED_EXTERNALLY)
                    successCount++
                }
            }

            else -> {
                // Default: Direct Intent
                for (item in items) {
                    dispatchViaIntent(context, item, config)
                    ViberOutboxStore.markStatus(managerEmail, item.id, ViberOutboxItem.STATUS_SHARED_EXTERNALLY)
                    successCount++
                }
            }
        }

        successCount
    }

    private fun launchViberIntent(context: Context, item: ViberOutboxItem, config: ViberConfig) {
        val phone = item.recipientPhone.ifBlank { config.reporteePhoneMap[item.recipientEmail.lowercase()].orEmpty() }
        val intent = if (phone.isNotBlank()) {
            val cleanPhone = phone.replace(Regex("[^0-9+]"), "")
            Intent(Intent.ACTION_VIEW, Uri.parse("viber://chat?number=$cleanPhone")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                setPackage("com.viber.voip")
                putExtra(Intent.EXTRA_TEXT, item.messageText)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
        context.startActivity(intent)
    }

    private fun dispatchViaIntent(context: Context, item: ViberOutboxItem, config: ViberConfig) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage("com.viber.voip")
            putExtra(Intent.EXTRA_TEXT, item.messageText)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, item.messageText)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(Intent.createChooser(fallback, "Share via Viber").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }
    }
}
