package com.example.skillsync.data.cache

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        const val STATUS_QUEUED = "QUEUED"
        const val STATUS_SENDING = "SENDING"
        const val STATUS_SENT = "SENT"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_SKIPPED = "SKIPPED"

        const val CAT_DEMAND = "UNALLOCATED_DEMAND"
        const val CAT_WEEKLY = "WEEKLY_STANDPOINT"
        const val CAT_WEEKEND = "WEEKEND_WRAP"
        const val CAT_DELIVERY = "DELIVERY_NUDGE"
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
        val current = getAll(managerEmail).filter { it.status != ViberOutboxItem.STATUS_SENT }
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
