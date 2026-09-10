package com.example.skillsync.feature.guardian.retry
import com.example.skillsync.feature.guardian.engine.OpportunityInboundEngine

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.skillsync.core.data.ManagerRepository
import com.example.skillsync.core.storage.LocalCache
import java.util.concurrent.TimeUnit

/**
 * Retries opportunity creates that failed while the device was offline.
 *
 * The inbound engine queues failed creates into `guardian_pending_<manager>`
 * and asks this worker to flush the queue with exponential backoff so a brief
 * outage self-heals without waiting for the next Viber notification.
 */
class OpportunityRetryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val manager = inputData.getString(KEY_MANAGER) ?: return Result.failure()
        val repository = ManagerRepository()
        val key = OpportunityInboundEngine.pendingKey(manager)
        val items = (LocalCache.loadMap(key)?.get("items") as? List<*>) ?: return Result.success()
        if (items.isEmpty()) return Result.success()

        val remaining = mutableListOf<Any>()
        for (item in items) {
            val body = item as? Map<*, *> ?: continue
            val flat = body.entries.associate { it.key.toString() to (it.value ?: "") }
            try {
                repository.createOpportunity(flat)
            } catch (_: Exception) {
                remaining.add(item)
            }
        }
        LocalCache.saveMap(key, mapOf("items" to remaining))
        return if (remaining.isEmpty()) Result.success() else Result.retry()
    }

    companion object {
        const val KEY_MANAGER = "manager"
        private const val UNIQUE = "guardian_retry_worker"

        /** Schedule (or chain onto) a backoff-backed retry for one manager. */
        fun enqueue(context: Context, manager: String) {
            val request = androidx.work.OneTimeWorkRequestBuilder<OpportunityRetryWorker>()
                .setInputData(workDataOf(KEY_MANAGER to manager))
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    5,
                    TimeUnit.MINUTES,
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "$UNIQUE-${manager.trim().lowercase()}",
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
