package com.example.skillsync.data.sync

import android.content.Context
import com.example.skillsync.data.ManagerRepository
import com.example.skillsync.data.SessionManager
import com.example.skillsync.data.cache.ActionQueueManager
import com.example.skillsync.data.cache.LocalCache
import com.example.skillsync.data.api.RetrofitClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One owner for foreground, connectivity-restored and WorkManager sync. */
object SyncCoordinator {
    private val mutex = Mutex()
    private val _revisions = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val revisions = _revisions.asSharedFlow()

    fun initialize(context: Context) {
        val app = context.applicationContext
        SessionManager.init(app)
        RetrofitClient.init(app)
        LocalCache.init(app)
        ActionQueueManager.init(app)
    }

    suspend fun sync(context: Context): Boolean = mutex.withLock {
        val app = context.applicationContext
        initialize(app)
        val email = SessionManager.getEmail()?.takeIf { it.isNotBlank() } ?: return false
        if (!RetrofitClient.isNetworkAvailable(app)) return false

        ActionQueueManager.syncPendingActions(app)
        val result = ManagerRepository().syncAll(email)
        if (result.succeeded) {
            val now = System.currentTimeMillis()
            SessionManager.setLastSyncTime(now)
            _revisions.tryEmit(now)
        }
        result.succeeded
    }
}
