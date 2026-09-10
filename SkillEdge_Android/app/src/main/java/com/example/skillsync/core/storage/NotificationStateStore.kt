package com.example.skillsync.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists which batch/demand IDs a manager has already been notified about,
 * so the foreground poll (every 60s while the app is open, see
 * MainScreenViewModel) and the background check (WorkManager, every 15 min,
 * see SkillSyncNotificationWorker) share one seen-set and never both fire for
 * the same event. Namespaced per manager email.
 */
object NotificationStateStore {
    private const val PREF_NAME = "skilledge_notifications"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    private fun key(email: String, bucket: String) = "${bucket}_${email.trim().lowercase()}"

    /**
     * True until [markInitialized] is called for this email. The caller should
     * seed the seen-set from whatever already exists on a first run rather than
     * notify — otherwise installing the app (or a fresh login) would fire one
     * notification per pre-existing batch instead of only genuinely new ones.
     */
    fun isFirstRun(email: String): Boolean =
        !prefs.getBoolean(key(email, "initialized"), false)

    fun markInitialized(email: String) {
        prefs.edit().putBoolean(key(email, "initialized"), true).apply()
    }

    fun getSeen(email: String, bucket: String): Set<String> =
        prefs.getStringSet(key(email, bucket), emptySet()) ?: emptySet()

    fun addSeen(email: String, bucket: String, ids: Set<String>) {
        if (ids.isEmpty()) return
        val updated = getSeen(email, bucket) + ids
        // Soft cap so years of daily use don't grow this without bound.
        val bounded = if (updated.size > 500) updated.toList().takeLast(500).toSet() else updated
        prefs.edit().putStringSet(key(email, bucket), bounded).apply()
    }
}
