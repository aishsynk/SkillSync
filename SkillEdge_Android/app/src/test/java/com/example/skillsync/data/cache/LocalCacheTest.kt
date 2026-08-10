package com.example.skillsync.data.cache

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LocalCacheTest {
    private val key = "offline_sync_test"

    @Before
    fun setUp() {
        LocalCache.init(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun identicalSnapshotDoesNotCreateANewRevision() {
        val snapshot = mapOf<String, Any>("count" to 2.0, "status" to "ready")

        assertTrue(LocalCache.saveMap(key, snapshot))
        val firstRevision = LocalCache.savedAt(key)
        assertFalse(LocalCache.saveMap(key, snapshot))

        assertEquals(firstRevision, LocalCache.savedAt(key))
        assertEquals("ready", LocalCache.loadMap(key)?.get("status"))
    }

    @Test
    fun changedSnapshotIsPersistedAsANewRevision() {
        assertTrue(LocalCache.saveMap(key, mapOf("count" to 2.0)))
        assertTrue(LocalCache.saveMap(key, mapOf("count" to 3.0)))

        assertEquals(3.0, LocalCache.loadMap(key)?.get("count"))
    }
}
