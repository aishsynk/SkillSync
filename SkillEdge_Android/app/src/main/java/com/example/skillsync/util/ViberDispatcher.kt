package com.example.skillsync.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.skillsync.data.ManagerRepository
import com.example.skillsync.data.cache.ViberConfig
import com.example.skillsync.data.cache.ViberConfigStore
import com.example.skillsync.data.cache.ViberOutboxItem
import com.example.skillsync.data.cache.ViberOutboxStore
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
                // If Accessibility Service is running, dispatch one-by-one
                if (ViberAutomationService.isRunning) {
                    for (item in items) {
                        try {
                            ViberAutomationService.currentItem = item
                            launchViberIntent(context, item, config)
                            successCount++
                            // Service will mark status once UI click completes
                        } catch (e: Exception) {
                            Log.e(TAG, "Accessibility launch failed", e)
                            ViberOutboxStore.markStatus(managerEmail, item.id, ViberOutboxItem.STATUS_FAILED, e.localizedMessage)
                        }
                    }
                } else {
                    // Fallback to Intent Notification or Intent direct
                    for (item in items) {
                        dispatchViaIntent(context, item, config)
                        ViberOutboxStore.markStatus(managerEmail, item.id, ViberOutboxItem.STATUS_SENT)
                        successCount++
                    }
                }
            }

            else -> {
                // Default: Direct Intent
                for (item in items) {
                    dispatchViaIntent(context, item, config)
                    ViberOutboxStore.markStatus(managerEmail, item.id, ViberOutboxItem.STATUS_SENT)
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
