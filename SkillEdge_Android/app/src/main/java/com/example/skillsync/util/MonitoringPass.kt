package com.example.skillsync.util

import android.content.Context
import android.util.Log
import com.example.skillsync.data.cache.LocalCache
import com.example.skillsync.data.sync.SyncCoordinator

/**
 * One delivery-monitoring pass: refresh the manager snapshots, diff against the
 * shared [NotificationStateStore] seen-set, and fire a system notification for
 * anything new.
 *
 * Both the foreground [MonitoringService] (real-time while the app is closed)
 * and the [SkillSyncNotificationWorker] (WorkManager backstop) call this, so an
 * event is reported exactly once regardless of which path notices it first.
 */
object MonitoringPass {

    sealed class Result {
        object Ok : Result()
        object NotLoggedIn : Result()
        object Retry : Result()
    }

    suspend fun run(context: Context): Result {
        return try {
            SyncCoordinator.initialize(context)
            NotificationStateStore.init(context)

            val email = com.example.skillsync.data.SessionManager.getEmail()
            if (email.isNullOrBlank()) return Result.NotLoggedIn

            if (!SyncCoordinator.sync(context)) return Result.Retry
            val data = LocalCache.loadMap("dashboard_$email") ?: return Result.Retry

            if (NotificationStateStore.isFirstRun(email)) {
                seedSeenState(email, data)
                return Result.Ok
            }

            val events = NotificationEngine.detect(
                data,
                NotificationStateStore.getSeen(email, NotificationEngine.BUCKET_ALLOCATION),
                NotificationStateStore.getSeen(email, NotificationEngine.BUCKET_FEEDBACK),
                NotificationStateStore.getSeen(email, NotificationEngine.BUCKET_DEMAND),
            )
            if (events.isNotEmpty()) {
                NotificationEngine.toNotifications(events).forEach { event ->
                    LocalNotificationService.showNotification(context, event)
                }
                events.groupBy { it.bucket }.forEach { (bucket, group) ->
                    NotificationStateStore.addSeen(email, bucket, group.map { it.id }.toSet())
                }
            }
            Result.Ok
        } catch (e: Exception) {
            Log.e("MonitoringPass", "monitoring pass failed", e)
            Result.Retry
        }
    }

    private fun seedSeenState(email: String, data: Map<String, Any>) {
        NotificationEngine.detect(data, emptySet(), emptySet(), emptySet())
            .groupBy { it.bucket }
            .forEach { (bucket, group) ->
                NotificationStateStore.addSeen(email, bucket, group.map { it.id }.toSet())
            }
        NotificationStateStore.markInitialized(email)
    }
}
