package com.example.skillsync.ui.batch

import android.content.Context
import com.example.skillsync.ui.components.rows
import com.example.skillsync.ui.components.str

/**
 * Remembers which batches this manager has already seen, so newly-published ones
 * can be flagged.
 *
 * Deliberately client-side. The backend runs on an instance that restarts freely
 * and has an ephemeral filesystem, so server-held "first seen" state would reset
 * and mark every batch new after each cold start. Local storage survives that and
 * is per-manager, which is what the flag actually means.
 */
object SeenBatches {

    private const val PREFS = "skillsync_seen_batches"

    /**
     * @return the ids present in [payload] that were not present last time.
     *         Empty on first ever run — everything being "new" the first time you
     *         open the app is noise, not a signal.
     */
    fun diffAndRemember(context: Context, managerEmail: String, payload: Map<String, Any>): Set<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "seen_${managerEmail.lowercase()}"

        val current = payload.rows("batches")
            .map { it.str("demand_id") }
            .filter { it.isNotBlank() }
            .toSet()

        val previous = prefs.getStringSet(key, null)
        prefs.edit().putStringSet(key, current).apply()

        // No baseline yet: record it and report nothing as new.
        if (previous == null) return emptySet()
        return current - previous
    }
}
