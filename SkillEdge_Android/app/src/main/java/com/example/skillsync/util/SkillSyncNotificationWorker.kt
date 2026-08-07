package com.example.skillsync.util

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.skillsync.data.api.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SkillSyncNotificationWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Retrieve session email
            val email = com.example.skillsync.data.SessionManager.getEmail()
            if (email.isNullOrBlank()) {
                return@withContext Result.success() // Not logged in, nothing to do
            }

            // Check API for unallocated demand
            val data = RetrofitClient.instance.getAllocationDesk(email)
            val batches = data["batches"] as? List<*> ?: emptyList<Any>()
            val unallocatedCount = batches.size
            
            // Usually we'd compare this to a local DB or DataStore to see if there are *new* items.
            // For demonstration, we'll notify if there's any pending demand.
            if (unallocatedCount > 0) {
                LocalNotificationService.showNotification(
                    context,
                    "Pending Allocations",
                    "You have $unallocatedCount unallocated batches requiring attention."
                )
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("NotificationWorker", "Exception in background check", e)
            Result.retry()
        }
    }
}
