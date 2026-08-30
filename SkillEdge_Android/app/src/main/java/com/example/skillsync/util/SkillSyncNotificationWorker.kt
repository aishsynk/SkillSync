package com.example.skillsync.util

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WorkManager backstop for delivery notifications. Runs every 15 minutes (the
 * periodic floor) even from a cold process; the foreground [MonitoringService]
 * owns real-time detection while the app is closed. Both share
 * [MonitoringPass] against the same [NotificationStateStore] seen-set, so an
 * event is reported exactly once.
 */
class SkillSyncNotificationWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // WorkManager can spawn a fresh process without MainActivity or the
        // Application subclass running first; MonitoringPass.run re-initialises
        // every singleton it touches. This worker is now only the backstop —
        // the foreground MonitoringService owns real-time detection.
        when (MonitoringPass.run(context)) {
            is MonitoringPass.Result.Retry -> Result.retry()
            else -> Result.success()
        }
    }
}
