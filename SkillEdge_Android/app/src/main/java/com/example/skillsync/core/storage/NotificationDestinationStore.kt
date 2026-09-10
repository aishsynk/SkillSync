package com.example.skillsync.util

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow

data class NotificationDestination(val type: String, val id: String, val label: String)

object NotificationDestinationStore {
    const val EXTRA_TYPE = "notification_target_type"
    const val EXTRA_ID = "notification_target_id"
    const val EXTRA_LABEL = "notification_target_label"

    val pending = MutableStateFlow<NotificationDestination?>(null)

    fun accept(intent: Intent?) {
        val type = intent?.getStringExtra(EXTRA_TYPE).orEmpty()
        if (type.isNotBlank()) pending.value = NotificationDestination(
            type, intent?.getStringExtra(EXTRA_ID).orEmpty(), intent?.getStringExtra(EXTRA_LABEL).orEmpty()
        )
    }

    fun consumed() { pending.value = null }
}
