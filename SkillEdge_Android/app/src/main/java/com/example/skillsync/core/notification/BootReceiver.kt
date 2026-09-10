package com.example.skillsync.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.skillsync.data.SessionManager
import com.example.skillsync.data.sync.SyncCoordinator
import com.example.skillsync.data.sync.SyncScheduler

/**
 * Restarts background monitoring after a device reboot or an app update, so the
 * "keep working when the app is closed" guarantee survives a restart without the
 * manager having to open the app first.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                SyncCoordinator.initialize(context)
                if (SessionManager.getEmail().isNullOrBlank()) return
                SyncScheduler.start(context)
                MonitoringService.start(context)
            }
        }
    }
}
