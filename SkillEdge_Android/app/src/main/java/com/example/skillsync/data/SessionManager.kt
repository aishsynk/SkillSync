package com.example.skillsync.data

import android.content.Context
import android.content.SharedPreferences

object SessionManager {
    private const val PREF_NAME = "skilledge_session"
    private const val KEY_EMAIL = "logged_in_email"
    private const val KEY_SESSION_ID = "session_id"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun saveSession(email: String, sessionId: String) {
        prefs.edit()
            .putString(KEY_EMAIL, email)
            .putString(KEY_SESSION_ID, sessionId)
            .apply()
    }

    fun getEmail(): String? = prefs.getString(KEY_EMAIL, null)

    fun getSessionId(): String? = prefs.getString(KEY_SESSION_ID, null)

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    fun isLoggedIn(): Boolean = getEmail() != null && getSessionId() != null
}
