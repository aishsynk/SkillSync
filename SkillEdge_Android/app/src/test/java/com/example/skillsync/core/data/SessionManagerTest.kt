package com.example.skillsync.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression test for the CI-caught bug where saveSession() was the one
 * SessionManager accessor missing the `::prefs.isInitialized` guard every
 * sibling method has, so it threw UninitializedPropertyAccessException when
 * called before init(context) — as any plain JVM unit test does, since only
 * SyncCoordinator calls init() in production. saveSession() must behave like
 * every other accessor here: a no-op on the SharedPreferences write, but it
 * must not throw, and loginState must still update.
 */
class SessionManagerTest {

    @Test
    fun saveSession_beforeInit_doesNotThrowAndUpdatesLoginState() {
        SessionManager.saveSession(email = "aishwar.c@koenig-solutions.com", sessionId = "sid")

        assertEquals(true, SessionManager.loginState.value)
    }
}
