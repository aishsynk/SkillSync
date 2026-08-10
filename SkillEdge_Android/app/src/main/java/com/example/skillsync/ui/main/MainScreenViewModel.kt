package com.example.skillsync.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skillsync.data.DataSource
import com.example.skillsync.data.ManagerRepository
import com.example.skillsync.data.api.RetrofitClient
import com.example.skillsync.data.cache.LocalCache
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

sealed class DashboardState {
    object Loading : DashboardState()
    /**
     * [fromCache] is true when this data came from [LocalCache] after a live
     * fetch failed — offline, or the backend/RMS chain is down — rather than
     * from a fresh network response. [cachedAt] is the disk-write time in that
     * case, so the UI can say how old the data actually is instead of guessing.
     */
    data class Success(
        val intelligenceData: Map<String, Any>,
        val fromCache: Boolean = false,
        val cachedAt: Long = 0L,
    ) : DashboardState()
    data class Error(val message: String) : DashboardState()
}

/**
 * The dashboard is assembled from three independent calls:
 *
 *  - `unified-manager-intelligence` — roster, batches, demand, operational KPIs
 *  - `manager-profile`              — who is signed in (fast, three RMS calls)
 *  - `team-capability`              — courses and certification gaps (slow, three
 *                                     RMS calls *per trainer*)
 *
 * They are deliberately not merged server-side. Folding capability into the main
 * payload would hold the whole dashboard behind the slowest query. Each has its
 * own state so one failing never blanks the others.
 *
 * Capability is also not fetched on open: at three RMS calls per trainer it is
 * the one query that grows with team size, so it loads when the Courses tab is
 * opened, or when the manager taps one of the certification KPIs that needs it.
 */
class MainScreenViewModel(
    private val repository: ManagerRepository = ManagerRepository(),
) : ViewModel() {
    private val _uiState = MutableStateFlow<DashboardState>(DashboardState.Loading)
    val uiState: StateFlow<DashboardState> = _uiState

    /** Null until loaded; the header falls back to the email until it arrives. */
    private val _profile = MutableStateFlow<Map<String, Any>?>(null)
    val profile: StateFlow<Map<String, Any>?> = _profile

    private val _capability = MutableStateFlow<Map<String, Any>?>(null)
    val capability: StateFlow<Map<String, Any>?> = _capability

    /** True while capability is in flight, so cert KPIs can show a placeholder. */
    private val _capabilityLoading = MutableStateFlow(false)
    val capabilityLoading: StateFlow<Boolean> = _capabilityLoading

    private val _teamActions = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val teamActions: StateFlow<List<Map<String, Any>>> = _teamActions

    private val _teamDataError = MutableStateFlow<String?>(null)
    val teamDataError: StateFlow<String?> = _teamDataError

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    private var loadedFor: String? = null

    /**
     * First load for [email]; a no-op once that email's data is already on
     * screen.
     *
     * The skeleton is shown only when there is genuinely nothing to display —
     * no live state and no cached snapshot. Setting Loading unconditionally
     * (the previous behaviour) blanked a populated dashboard every time the
     * tab was revisited or the process was recreated, so the manager watched
     * their numbers vanish and reappear on a screen they had already loaded.
     */
    fun loadData(email: String, context: android.content.Context) {
        if (loadedFor == email && _uiState.value is DashboardState.Success) return
        loadedFor = email
        viewModelScope.launch {
            if (_uiState.value !is DashboardState.Success) {
                val cached = LocalCache.loadMap(dashboardCacheKey(email))
                _uiState.value = if (cached != null) {
                    DashboardState.Success(
                        cached, fromCache = true,
                        cachedAt = LocalCache.savedAt(dashboardCacheKey(email)),
                    )
                } else {
                    DashboardState.Loading
                }
            }
            fetchAll(email, context, fresh = false)
        }
    }

    /** Pull-to-refresh and screen-resume: keeps current data visible while re-fetching. */
    fun refresh(email: String, context: android.content.Context) {
        viewModelScope.launch {
            _refreshing.value = true
            fetchAll(email, context, fresh = true)
            _refreshing.value = false
        }
    }

    /** Loads capability the first time something actually needs it. */
    fun ensureCapability(email: String, context: android.content.Context) {
        if (_capability.value != null || _capabilityLoading.value) return
        viewModelScope.launch { fetchCapability(email, context, fresh = false) }
    }

    /** Team requires capability and real action counts immediately on entry. */
    fun ensureTeamIntelligence(email: String, context: android.content.Context) {
        if (_capabilityLoading.value) return
        viewModelScope.launch {
            if (!com.example.skillsync.data.api.RetrofitClient.isNetworkAvailable(context)) {
                if (_capability.value == null) {
                    _capability.value = LocalCache.loadMap(capabilityCacheKey(email))
                }
                _teamDataError.value = "Offline — team intelligence may be stale"
                return@launch
            }
            _capabilityLoading.value = true
            val result = repository.teamIntelligence(email, fresh = false)
            if (result.capability != null) _capability.value = result.capability
            _teamActions.value = result.actions
            _teamDataError.value = listOfNotNull(result.capabilityError, result.actionsError)
                .distinct().joinToString(" · ").takeIf { it.isNotBlank() }
            _capabilityLoading.value = false
        }
    }

    /**
     * Re-reads only what a write can have changed. Marking a skill alters course
     * ownership and certification coverage but not the roster or the batch
     * calendar, so paying for the full dashboard again would be waste. No-op if
     * capability was never loaded — there is nothing on screen to go stale.
     */
    fun refreshCapability(email: String, context: android.content.Context) {
        if (_capability.value == null) return
        viewModelScope.launch { fetchCapability(email, context, fresh = true) }
    }

    /** Adopt WorkManager's persisted revision without another network request. */
    fun adoptBackgroundSync(email: String) {
        viewModelScope.launch {
            LocalCache.loadMap(dashboardCacheKey(email))?.let { data ->
                val previous = (_uiState.value as? DashboardState.Success)?.intelligenceData
                if (previous != data) _uiState.value = DashboardState.Success(data)
            }
            LocalCache.loadMap(profileCacheKey(email))?.let { if (_profile.value != it) _profile.value = it }
            LocalCache.loadMap(capabilityCacheKey(email))?.let { if (_capability.value != it) _capability.value = it }
            LocalCache.loadMap("actions_$email")?.let { body ->
                @Suppress("UNCHECKED_CAST")
                val rows = (body["actions"] as? List<Map<String, Any>>).orEmpty()
                if (_teamActions.value != rows) _teamActions.value = rows
            }
        }
    }

    private suspend fun fetchAll(email: String, context: android.content.Context, fresh: Boolean = false) = coroutineScope {
        // Sync any offline actions first so subsequent fetches get updated data
        com.example.skillsync.data.cache.ActionQueueManager.syncPendingActions(context)

        val dash = async { fetchDashboard(email, context, fresh) }
        val prof = async { fetchProfile(email, context, fresh) }
        // Capability is refreshed rather than fetched: if the manager has never
        // opened anything that needs it, this must not silently pay for it.
        val cap = async { if (_capability.value != null) fetchCapability(email, context, fresh) }
        dash.await(); prof.await(); cap.await()
    }

    /** `?refresh=1` purges the server cache; a plain load is happy to reuse it. */
    private fun flag(fresh: Boolean) = if (fresh) 1 else null

    private fun dashboardCacheKey(email: String) = "dashboard_$email"
    private fun profileCacheKey(email: String) = "profile_$email"
    private fun capabilityCacheKey(email: String) = "capability_$email"

    private suspend fun fetchDashboard(email: String, context: android.content.Context, fresh: Boolean) {
        // 1. Instantly read from LocalCache if not already loaded and no fresh push requested
        if (!fresh && _uiState.value !is DashboardState.Success) {
            val cached = LocalCache.loadMap(dashboardCacheKey(email))
            if (cached != null) {
                _uiState.value = DashboardState.Success(
                    cached,
                    fromCache = true,
                    cachedAt = LocalCache.savedAt(dashboardCacheKey(email))
                )
            }
        }

        // 2. Network Check
        if (!RetrofitClient.isNetworkAvailable(context)) {
            if (_uiState.value !is DashboardState.Success) {
                _uiState.value = DashboardState.Error("No internet connection")
            }
            return
        }

        // 3. Fetch from API in background
        try {
            val result = repository.dashboard(email, fresh)
            val data = result.data ?: throw IllegalStateException(result.error ?: "Failed to load dashboard")
            // Swap only on a real change. Re-emitting an identical payload
            // recomposes every card and chart for nothing, which is what the
            // visible flicker on each poll actually was.
            val prev = (_uiState.value as? DashboardState.Success)?.intelligenceData
            if (prev != data) {
                _uiState.value = DashboardState.Success(
                    data,
                    fromCache = result.source == DataSource.CACHE,
                    cachedAt = result.cachedAt,
                )
            }
            com.example.skillsync.data.SessionManager.setLastSyncTime(System.currentTimeMillis())
        } catch (e: Exception) {
            // Leave UI in its current state (likely cached Success)
            if (_uiState.value !is DashboardState.Success) {
                _uiState.value = DashboardState.Error(e.localizedMessage ?: "Failed to load dashboard data")
            }
        }
    }

    private suspend fun fetchProfile(email: String, context: android.content.Context, fresh: Boolean) {
        if (!fresh && _profile.value == null) {
            _profile.value = LocalCache.loadMap(profileCacheKey(email))
        }

        if (!RetrofitClient.isNetworkAvailable(context)) return

        try {
            val result = repository.managerProfile(email, fresh)
            if (result.data != null) _profile.value = result.data
        } catch (_: Exception) {
            // Ignore error
        }
    }

    private suspend fun fetchCapability(email: String, context: android.content.Context, fresh: Boolean) {
        if (!fresh && _capability.value == null) {
            _capability.value = LocalCache.loadMap(capabilityCacheKey(email))
        }

        if (!RetrofitClient.isNetworkAvailable(context)) return

        _capabilityLoading.value = true
        try {
            val result = repository.teamIntelligence(email, fresh)
            if (result.capability != null) _capability.value = result.capability
            _teamActions.value = result.actions
            _teamDataError.value = listOfNotNull(result.capabilityError, result.actionsError)
                .distinct().joinToString(" · ").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            _teamDataError.value = e.localizedMessage ?: "Could not load team intelligence"
        } finally {
            _capabilityLoading.value = false
        }
    }

    /** Targeted events for both system notifications and the in-app banner. */
    private val _notification = kotlinx.coroutines.flow.MutableSharedFlow<com.example.skillsync.util.NotifyEvent>()
    val notification = _notification.asSharedFlow()

    private var pollingJob: kotlinx.coroutines.Job? = null

    /**
     * Foreground fast-path: while a screen is open, checks every 60s instead
     * of waiting for the 15-min WorkManager floor. Shares
     * [com.example.skillsync.util.NotificationStateStore]'s seen-set with
     * [com.example.skillsync.util.SkillSyncNotificationWorker], so an event is
     * only ever reported once regardless of which path notices it first.
     */
    fun startPolling(email: String, context: android.content.Context) {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(120000)
                try {
                    com.example.skillsync.data.sync.SyncCoordinator.sync(context)
                    adoptBackgroundSync(email)
                    val fresh = (_uiState.value as? DashboardState.Success)?.intelligenceData
                    if (fresh != null) checkForNotifications(email, fresh)
                } catch (e: Exception) {
                    // Ignore — next tick tries again.
                }
            }
        }
    }

    private suspend fun checkForNotifications(email: String, data: Map<String, Any>) {
        val store = com.example.skillsync.util.NotificationStateStore
        val engine = com.example.skillsync.util.NotificationEngine
        if (store.isFirstRun(email)) {
            // A fresh login/first poll must not fire once per pre-existing
            // batch — seed the seen-set from the current snapshot instead.
            engine.detect(data, emptySet(), emptySet(), emptySet())
                .groupBy { it.bucket }
                .forEach { (bucket, group) -> store.addSeen(email, bucket, group.map { it.id }.toSet()) }
            store.markInitialized(email)
            return
        }
        val events = engine.detect(
            data,
            store.getSeen(email, engine.BUCKET_ALLOCATION),
            store.getSeen(email, engine.BUCKET_FEEDBACK),
            store.getSeen(email, engine.BUCKET_DEMAND),
        )
        if (events.isEmpty()) return
        engine.toNotifications(events).forEach { _notification.emit(it) }
        events.groupBy { it.bucket }.forEach { (bucket, group) ->
            store.addSeen(email, bucket, group.map { it.id }.toSet())
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }
}
