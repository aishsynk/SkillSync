package com.example.skillsync.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object SessionManager {
    private const val PREF_NAME = "skilledge_session"
    private const val KEY_EMAIL = "logged_in_email"
    private const val KEY_SESSION_ID = "session_id"

    private lateinit var prefs: SharedPreferences

    private val _loginState = MutableStateFlow(false)
    val loginState: StateFlow<Boolean> = _loginState

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        _loginState.value = isLoggedIn()
    }

    fun saveSession(email: String, sessionId: String) {
        prefs.edit()
            .putString(KEY_EMAIL, email)
            .putString(KEY_SESSION_ID, sessionId)
            .apply()
        _loginState.value = true
    }

    fun getEmail(): String? = prefs.getString(KEY_EMAIL, null)

    fun getSessionId(): String? = prefs.getString(KEY_SESSION_ID, null)

    fun clearSession() {
        prefs.edit().clear().apply()
        _loginState.value = false
    }

    fun setLastSyncTime(timeMillis: Long) {
        prefs.edit().putLong("last_sync_time", timeMillis).apply()
    }

    fun getLastSyncTime(): Long = prefs.getLong("last_sync_time", 0L)

    fun isLoggedIn(): Boolean = getEmail() != null && getSessionId() != null
}
