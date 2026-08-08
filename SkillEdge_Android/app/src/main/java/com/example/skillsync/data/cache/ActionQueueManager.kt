package com.example.skillsync.data.cache

import android.content.Context
import com.example.skillsync.data.api.MarkSkillRequest
import com.example.skillsync.data.api.RetrofitClient
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class QueuedAction(
    val id: String = UUID.randomUUID().toString(),
    val type: String = "MARK_SKILL",
    val timestamp: Long = System.currentTimeMillis(),
    val payload: MarkSkillRequest
)

object ActionQueueManager {
    private lateinit var dir: File
    private val gson = Gson()
    private val queueFile: File get() = File(dir, "action_queue.json")

    fun init(context: Context) {
        dir = File(context.filesDir, "offline_cache").apply { mkdirs() }
    }

    @Synchronized
    private fun readQueue(): MutableList<QueuedAction> {
        return try {
            if (!queueFile.exists()) return mutableListOf()
            val type = object : TypeToken<List<QueuedAction>>() {}.type
            val list = gson.fromJson<List<QueuedAction>>(queueFile.readText(), type)
            list?.toMutableList() ?: mutableListOf()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    @Synchronized
    private fun writeQueue(queue: List<QueuedAction>) {
        try {
            queueFile.writeText(gson.toJson(queue))
        } catch (_: Exception) {}
    }

    fun enqueueAction(action: QueuedAction) {
        val q = readQueue()
        q.add(action)
        writeQueue(q)
    }

    suspend fun syncPendingActions(context: Context) {
        if (!RetrofitClient.isNetworkAvailable(context)) return

        val q = readQueue()
        if (q.isEmpty()) return

        withContext(Dispatchers.IO) {
            val remaining = mutableListOf<QueuedAction>()
            for (action in q) {
                try {
                    when (action.type) {
                        "MARK_SKILL" -> {
                            val resp = RetrofitClient.instance.markSkill(action.payload)
                            if (resp.isSuccessful && resp.body()?.verified == true) {
                                // Synced successfully
                            } else {
                                // If 4xx client error, it's rejected by RMS (e.g. course not found).
                                // We drop it. Keep it only for 5xx server errors or timeouts.
                                if (resp.code() in 500..599) {
                                    remaining.add(action)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Network error during sync, keep it in queue
                    remaining.add(action)
                }
            }
            writeQueue(remaining)
        }
    }
}
