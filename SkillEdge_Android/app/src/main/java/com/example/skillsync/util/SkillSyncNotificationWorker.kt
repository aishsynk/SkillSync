package com.example.skillsync.util

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.skillsync.data.cache.LocalCache
import com.example.skillsync.data.sync.SyncCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs every 15 minutes (WorkManager's floor for periodic work) even when the
 * app is closed — this is the actual push-notification path; the in-app poll
 * in MainScreenViewModel only runs while a screen is open. Both call into
 * [NotificationEngine] against the same [NotificationStateStore] seen-set, so
 * whichever notices an event first is the only one that fires it.
 */
class SkillSyncNotificationWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // WorkManager can spawn a fresh process to run this worker without
            // MainActivity.onCreate() ever executing (no Application subclass
            // exists in this app to guarantee it runs first), so every
            // singleton this worker touches must be (re-)initialized here.
            // .init() is idempotent — safe even if MainActivity already ran.
            SyncCoordinator.initialize(context)
            NotificationStateStore.init(context)

            val email = com.example.skillsync.data.SessionManager.getEmail()
            if (email.isNullOrBlank()) {
                return@withContext Result.success() // Not logged in, nothing to do
            }

            if (!SyncCoordinator.sync(context)) return@withContext Result.retry()
            val data = LocalCache.loadMap("dashboard_$email") ?: return@withContext Result.retry()

            if (NotificationStateStore.isFirstRun(email)) {
                // Seed from whatever already exists without notifying — the
                // first check after install/login must not fire once per
                // pre-existing batch.
                seedSeenState(email, data)
                return@withContext Result.success()
            }

            val seenAlloc = NotificationStateStore.getSeen(email, NotificationEngine.BUCKET_ALLOCATION)
            val seenFb = NotificationStateStore.getSeen(email, NotificationEngine.BUCKET_FEEDBACK)
            val seenDemand = NotificationStateStore.getSeen(email, NotificationEngine.BUCKET_DEMAND)

            val events = NotificationEngine.detect(data, seenAlloc, seenFb, seenDemand)
            if (events.isNotEmpty()) {
                NotificationEngine.toNotifications(events).forEach { (title, message) ->
                    LocalNotificationService.showNotification(context, title, message)
                }
                events.groupBy { it.bucket }.forEach { (bucket, group) ->
                    NotificationStateStore.addSeen(email, bucket, group.map { it.id }.toSet())
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("NotificationWorker", "Exception in background check", e)
            Result.retry()
        }
    }

    private fun seedSeenState(email: String, data: Map<String, Any>) {
        val all = NotificationEngine.detect(data, emptySet(), emptySet(), emptySet())
        all.groupBy { it.bucket }.forEach { (bucket, group) ->
            NotificationStateStore.addSeen(email, bucket, group.map { it.id }.toSet())
        }
        NotificationStateStore.markInitialized(email)
    }
}
