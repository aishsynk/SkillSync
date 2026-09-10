package com.example.skillsync.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.skillsync.MainActivity
import com.example.skillsync.R
import com.example.skillsync.data.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Always-on delivery monitoring.
 *
 * A `dataSync` foreground service so the notification pipeline keeps running
 * after the app is swiped away or Doze kicks in — WorkManager alone gets
 * deferred by the OS and OEM battery managers. The persistent low-importance
 * notification is the cost Android requires for that guarantee.
 *
 * The loop delegates to [MonitoringPass], the same body the WorkManager
 * backstop ([SkillSyncNotificationWorker]) runs, so events are de-duplicated
 * through the shared [NotificationStateStore] seen-set.
 */
class MonitoringService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundSafely()
        if (loop?.isActive != true) {
            loop = scope.launch {
                while (isActive) {
                    when (MonitoringPass.run(applicationContext)) {
                        is MonitoringPass.Result.NotLoggedIn -> {
                            stopSelfAndCleanup()
                            return@launch
                        }
                        else -> Unit
                    }
                    delay(POLL_INTERVAL_MS)
                }
            }
        }
        // Restart if the OS kills us; onStartCommand re-arms the loop.
        return START_STICKY
    }

    override fun onDestroy() {
        loop?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun stopSelfAndCleanup() {
        loop?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundSafely() {
        ensureChannel(this)
        val pending = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("SkillEdge is monitoring delivery activity")
            .setContentText("New allocations, demand and feedback alerts stay live in the background.")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(pending)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "skilledge_monitoring"
        private const val NOTIFICATION_ID = 4711
        private const val POLL_INTERVAL_MS = 90_000L

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "Background monitoring", NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = "Keeps delivery alerts live when the app is closed"
                    setShowBadge(false)
                }
            )
        }

        /** Start monitoring if a manager is signed in; safe to call repeatedly. */
        fun start(context: Context) {
            if (SessionManager.getEmail().isNullOrBlank()) return
            val app = context.applicationContext
            val intent = Intent(app, MonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(intent)
            } else {
                app.startService(intent)
            }
        }

        fun stop(context: Context) {
            val app = context.applicationContext
            app.stopService(Intent(app, MonitoringService::class.java))
        }
    }
}
