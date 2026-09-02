package com.example.skillsync.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of truth for the manager's login state.
 *
 * `loginState` is a nullable Boolean tri-state:
 *   null  — init() has not been called yet (app just started, prefs not read)
 *   true  — logged in (prefs confirmed, or just saved via saveSession)
 *   false — definitively logged out (clearSession was called AFTER init)
 *
 * Navigation must only redirect to Login when the state is `false`, never
 * while it is `null` — that prevents the "starts as false → pushes to Login
 * immediately on cold start" race that caused continuous logouts.
 */
object SessionManager {
    private const val PREF_NAME = "skilledge_session"
    private const val KEY_EMAIL = "logged_in_email"
    private const val KEY_SESSION_ID = "session_id"
    private const val KEY_ROLE = "role"
    private const val KEY_MUST_CHANGE = "must_change"

    private lateinit var prefs: SharedPreferences

    // null = unknown (init not yet called), true = logged in, false = logged out
    private val _loginState = MutableStateFlow<Boolean?>(null)
    val loginState: StateFlow<Boolean?> = _loginState

    fun init(context: Context) {
        if (::prefs.isInitialized) {
            // Already initialised — re-read so the state reflects current prefs
            // (e.g. WorkManager woke a fresh process and called init again).
            _loginState.value = isLoggedIn()
            return
        }
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        _loginState.value = isLoggedIn()
    }

    fun saveSession(
        email: String,
        sessionId: String,
        role: String = "manager",
        mustChange: Boolean = false,
    ) {
        prefs.edit()
            .putString(KEY_EMAIL, email)
            .putString(KEY_SESSION_ID, sessionId)
            .putString(KEY_ROLE, role)
            .putBoolean(KEY_MUST_CHANGE, mustChange)
            .apply()
        _loginState.value = true
    }

    fun getEmail(): String? =
        if (::prefs.isInitialized) prefs.getString(KEY_EMAIL, null) else null

    fun getRole(): String =
        if (::prefs.isInitialized) prefs.getString(KEY_ROLE, "manager") ?: "manager" else "manager"

    fun isReportee(): Boolean = getRole() == "reportee"

    fun mustChangePassword(): Boolean =
        if (::prefs.isInitialized) prefs.getBoolean(KEY_MUST_CHANGE, false) else false

    fun clearMustChange() {
        if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_MUST_CHANGE, false).apply()
    }

    fun getSessionId(): String? =
        if (::prefs.isInitialized) prefs.getString(KEY_SESSION_ID, null) else null

    fun clearSession() {
        if (::prefs.isInitialized) prefs.edit().clear().apply()
        _loginState.value = false
    }

    fun setLastSyncTime(timeMillis: Long) {
        if (::prefs.isInitialized) prefs.edit().putLong("last_sync_time", timeMillis).apply()
    }

    fun getLastSyncTime(): Long =
        if (::prefs.isInitialized) prefs.getLong("last_sync_time", 0L) else 0L

    fun isLoggedIn(): Boolean = getEmail() != null && getSessionId() != null

    /** One-line score explainers: shown until the user dismisses them once. */
    fun isHintDismissed(key: String): Boolean =
        ::prefs.isInitialized && prefs.getBoolean("hint_$key", false)

    fun dismissHint(key: String) {
        if (::prefs.isInitialized) prefs.edit().putBoolean("hint_$key", true).apply()
    }
}
