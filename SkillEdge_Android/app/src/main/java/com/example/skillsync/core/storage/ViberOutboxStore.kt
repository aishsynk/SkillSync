package com.example.skillsync.core.storage

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material3.Text

/**
 * An item in the Viber Background Automation outbox queue.
 */
data class ViberOutboxItem(
    val id: String,
    val category: String,
    val recipientName: String,
    val recipientEmail: String,
    val recipientPhone: String = "",
    val courseName: String = "",
    val messageText: String = "",
    val status: String = STATUS_QUEUED,
    val createdAt: String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()),
    val sentAt: String? = null,
    val errorMessage: String? = null,
) {
    companion object {
        /**
         * Prepared but not yet cleared for sharing. Kept distinct from
         * [STATUS_READY_TO_SHARE] so "the app wrote something" and "the manager
         * accepted it" are never the same fact.
         */
        const val STATUS_DRAFT = "DRAFT"

        /**
         * Reviewed and waiting for the manager to hand it to Viber. This is what
         * the legacy [STATUS_QUEUED] value has always meant in practice, so both
         * are treated as the same state on read and [STATUS_QUEUED] stays as the
         * persisted value to avoid rewriting outboxes already on disk.
         */
        const val STATUS_READY_TO_SHARE = "READY_TO_SHARE"

        const val STATUS_QUEUED = "QUEUED"
        const val STATUS_SENDING = "SENDING"
        /** Confirmed sent — only ever set from a real Viber Bot API 200 response. */
        const val STATUS_SENT = "SENT"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_SKIPPED = "SKIPPED"
        /**
         * A share Intent to Viber was opened with this message prefilled — the
         * same honest distinction CommunicationScreen.kt already makes for its
         * manual composer. Opening Viber is not delivery confirmation: the user
         * still has to pick a recipient and tap send inside Viber itself.
         */
        const val STATUS_SHARED_EXTERNALLY = "SHARED_EXTERNALLY"

        const val CAT_DEMAND = "UNALLOCATED_DEMAND"
        const val CAT_WEEKLY = "WEEKLY_STANDPOINT"
        const val CAT_WEEKEND = "WEEKEND_WRAP"
        const val CAT_DELIVERY = "DELIVERY_NUDGE"

        /** Statuses that still await the manager's hand-off to Viber. */
        val AWAITING_HANDOFF = setOf(STATUS_QUEUED, STATUS_READY_TO_SHARE, STATUS_DRAFT)

        /**
         * What the UI is allowed to call each status. "Sent" appears only for
         * [STATUS_SENT], which the dispatcher sets only from a confirmed Viber
         * Bot API response; everything the share sheet produces reads as shared.
         */
        fun label(status: String): String = when (status) {
            STATUS_SENT -> "Sent"
            STATUS_SHARED_EXTERNALLY -> "Shared externally"
            STATUS_FAILED -> "Failed"
            STATUS_SKIPPED -> "Not transmitted"
            STATUS_SENDING -> "Transmitting"
            STATUS_DRAFT -> "Draft"
            else -> "Ready to share"
        }
    }
}

/**
 * Thread-safe disk-backed persistence store for Viber Outbox queue.
 */
object ViberOutboxStore {

    private val gson = Gson()

    private fun cacheKey(managerEmail: String) =
        "viber_outbox_${managerEmail.trim().lowercase()}"

    @Synchronized
    fun getAll(managerEmail: String): List<ViberOutboxItem> {
        val raw = LocalCache.loadMap(cacheKey(managerEmail)) ?: return emptyList()
        val itemsList = raw["items"] as? List<*> ?: return emptyList()
        val json = gson.toJson(itemsList)
        val type = object : TypeToken<List<ViberOutboxItem>>() {}.type
        return runCatching { gson.fromJson<List<ViberOutboxItem>>(json, type) }.getOrDefault(emptyList())
    }

    @Synchronized
    fun getPending(managerEmail: String): List<ViberOutboxItem> {
        return getAll(managerEmail).filter { it.status == ViberOutboxItem.STATUS_QUEUED || it.status == ViberOutboxItem.STATUS_FAILED }
    }

    @Synchronized
    fun enqueue(managerEmail: String, newItems: List<ViberOutboxItem>): Int {
        if (newItems.isEmpty()) return 0
        val current = getAll(managerEmail).toMutableList()
        val existingIds = current.map { it.id }.toSet()
        var added = 0

        for (item in newItems) {
            if (item.id !in existingIds) {
                current.add(0, item) // prepend newest
                added++
            }
        }

        if (added > 0) {
            save(managerEmail, current)
        }
        return added
    }

    @Synchronized
    fun markStatus(managerEmail: String, id: String, status: String, error: String? = null) {
        val current = getAll(managerEmail).map { item ->
            if (item.id == id) {
                item.copy(
                    status = status,
                    sentAt = if (status == ViberOutboxItem.STATUS_SENT) SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()) else item.sentAt,
                    errorMessage = error,
                )
            } else item
        }
        save(managerEmail, current)
    }

    @Synchronized
    fun markAllSent(managerEmail: String, ids: List<String>) {
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())
        val idSet = ids.toSet()
        val current = getAll(managerEmail).map { item ->
            if (item.id in idSet) {
                item.copy(status = ViberOutboxItem.STATUS_SENT, sentAt = now, errorMessage = null)
            } else item
        }
        save(managerEmail, current)
    }

    @Synchronized
    fun retry(managerEmail: String, id: String) {
        val current = getAll(managerEmail).map { item ->
            if (item.id == id) {
                item.copy(status = ViberOutboxItem.STATUS_QUEUED, errorMessage = null)
            } else item
        }
        save(managerEmail, current)
    }

    @Synchronized
    fun clearSent(managerEmail: String) {
        val current = getAll(managerEmail).filter {
            it.status != ViberOutboxItem.STATUS_SENT && it.status != ViberOutboxItem.STATUS_SHARED_EXTERNALLY
        }
        save(managerEmail, current)
    }

    private fun save(managerEmail: String, items: List<ViberOutboxItem>) {
        val map = mapOf(
            "manager" to managerEmail,
            "updated_at" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()),
            "items" to items,
        )
        LocalCache.saveMap(cacheKey(managerEmail), map)
    }
}
