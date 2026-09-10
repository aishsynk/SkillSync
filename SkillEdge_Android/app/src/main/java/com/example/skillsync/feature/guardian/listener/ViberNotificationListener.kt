package com.example.skillsync.feature.guardian.listener
import com.example.skillsync.feature.guardian.engine.OpportunityInboundEngine

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Listens for inbound Viber notifications and feeds them to
 * [OpportunityInboundEngine] for opportunity detection.
 *
 * This service is what makes "Opportunity Guardian" inbound: without it the app
 * could only read messages it had itself sent out. Users must grant Notification
 * Access in system settings for this to run --- the guardian settings screen must
 * point them at it via an ACTION_NOTIFICATION_LISTENER_SETTINGS intent.
 *
 * The service must stay exported=true (a system-bound service). All work is
 * offloaded onto [io]; callbacks arrive on a binder thread and must never block.
 */
class ViberNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** In-memory cooldown so a re-posted notification is processed once per second. */
    private val recentKeys = linkedSetOf<String>()

    companion object {
        private const val TAG = "ViberNotificationListener"
        const val VIBER_PACKAGE = "com.viber.voip"

        /** True once the user has granted Notification Access to this app. */
        fun hasNotificationAccess(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context)
                .any { it == context.packageName }
    }

    override fun onListenerConnected() {
        Log.i(TAG, "Notification access granted")
        super.onListenerConnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName != VIBER_PACKAGE) return
        if (!isFresh(sbn)) return

        scope.launch {
            try {
                OpportunityInboundEngine.process(applicationContext, sbn)
            } catch (e: Exception) {
                Log.w(TAG, "opportunity processing failed", e)
            }
        }
    }

    /** Suppresses re-processing of the same notification within a short window. */
    private fun isFresh(sbn: StatusBarNotification): Boolean {
        val key = sbn.key ?: return true
        synchronized(recentKeys) {
            if (recentKeys.contains(key)) return false
            recentKeys.add(key)
            while (recentKeys.size > 200) recentKeys.remove(recentKeys.first())
            return true
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
