package com.example.skillsync.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Tracks when the proactive digests were last posted so the monitoring pass
 * fires each one at most once per period (morning: once a calendar day;
 * weekly: once an ISO week). Mirrors [NotificationStateStore], namespaced per
 * manager email.
 */
object DigestStateStore {
    private const val PREF_NAME = "skilledge_digests"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    private fun key(email: String, slot: String) = "${slot}_${email.trim().lowercase()}"

    fun lastMorningDate(email: String): String =
        prefs.getString(key(email, "morning_date"), "") ?: ""

    fun setMorningDate(email: String, date: String) {
        prefs.edit().putString(key(email, "morning_date"), date).apply()
    }

    fun lastWeeklyWeek(email: String): String =
        prefs.getString(key(email, "weekly_week"), "") ?: ""

    fun setWeeklyWeek(email: String, week: String) {
        prefs.edit().putString(key(email, "weekly_week"), week).apply()
    }
}
