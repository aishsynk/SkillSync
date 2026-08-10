package com.example.skillsync.data.cache

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Disk-backed JSON cache for API payloads, keyed by an arbitrary string.
 *
 * This exists because OkHttp's HTTP cache (see RetrofitClient) is opaque to app
 * logic — it can silently serve a stale hit or miss entirely depending on exact
 * request/query-param matching, and a ViewModel has no way to tell which happened
 * or how old the data is. This cache is the explicit source of truth for "what did
 * we last successfully show this manager", so a cold start with no network can
 * still render the dashboard instead of an error screen.
 */
object LocalCache {
    private lateinit var dir: File
    private val gson = Gson()

    fun init(context: Context) {
        dir = File(context.filesDir, "offline_cache").apply { mkdirs() }
    }

    private fun keyToFile(key: String): File {
        val safe = key.lowercase().replace(Regex("[^a-z0-9]+"), "_")
        return File(dir, "$safe.json")
    }

    /**
     * Atomically persist a snapshot only when its content changed.
     *
     * Avoiding identical rewrites keeps [savedAt] meaningful: it represents a
     * real data revision, not merely another background poll. The temporary
     * file prevents a killed process from leaving truncated JSON behind.
     */
    @Synchronized
    fun saveMap(key: String, data: Map<String, Any>): Boolean {
        return try {
            val target = keyToFile(key)
            val json = gson.toJson(data)
            if (target.exists() && target.readText() == json) return false
            val temp = File(target.parentFile, target.name + ".tmp")
            temp.writeText(json)
            if (!temp.renameTo(target)) {
                target.writeText(json)
                temp.delete()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun loadMap(key: String): Map<String, Any>? {
        return try {
            val f = keyToFile(key)
            if (!f.exists()) return null
            val type = object : TypeToken<Map<String, Any>>() {}.type
            gson.fromJson<Map<String, Any>>(f.readText(), type)
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun saveObject(key: String, data: Any): Boolean {
        return try {
            val target = keyToFile(key)
            val json = gson.toJson(data)
            if (target.exists() && target.readText() == json) return false
            val temp = File(target.parentFile, target.name + ".tmp")
            temp.writeText(json)
            if (!temp.renameTo(target)) {
                target.writeText(json)
                temp.delete()
            }
            true
        } catch (_: Exception) { false }
    }

    fun <T> loadObject(key: String, type: Class<T>): T? = try {
        val file = keyToFile(key)
        if (!file.exists()) null else gson.fromJson(file.readText(), type)
    } catch (_: Exception) { null }

    /** Epoch millis of the cached write, or 0 if nothing is cached under [key]. */
    fun savedAt(key: String): Long {
        val f = keyToFile(key)
        return if (f.exists()) f.lastModified() else 0L
    }
}
