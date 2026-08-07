package com.example.skillsync.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
class MainScreenViewModel : ViewModel() {
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

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    private var loadedFor: String? = null

    /** First load for [email]; a no-op once that email's data is already on screen. */
    fun loadData(email: String) {
        if (loadedFor == email && _uiState.value is DashboardState.Success) return
        loadedFor = email
        viewModelScope.launch {
            _uiState.value = DashboardState.Loading
            fetchAll(email, fresh = false)
        }
    }

    /** Pull-to-refresh and screen-resume: keeps current data visible while re-fetching. */
    fun refresh(email: String) {
        viewModelScope.launch {
            _refreshing.value = true
            fetchAll(email, fresh = true)
            _refreshing.value = false
        }
    }

    /** Loads capability the first time something actually needs it. */
    fun ensureCapability(email: String) {
        if (_capability.value != null || _capabilityLoading.value) return
        viewModelScope.launch { fetchCapability(email, fresh = false) }
    }

    /**
     * Re-reads only what a write can have changed. Marking a skill alters course
     * ownership and certification coverage but not the roster or the batch
     * calendar, so paying for the full dashboard again would be waste. No-op if
     * capability was never loaded — there is nothing on screen to go stale.
     */
    fun refreshCapability(email: String) {
        if (_capability.value == null) return
        viewModelScope.launch { fetchCapability(email, fresh = true) }
    }

    private suspend fun fetchAll(email: String, fresh: Boolean = false) = coroutineScope {
        val dash = async { fetchDashboard(email, fresh) }
        val prof = async { fetchProfile(email, fresh) }
        // Capability is refreshed rather than fetched: if the manager has never
        // opened anything that needs it, this must not silently pay for it.
        val cap = async { if (_capability.value != null) fetchCapability(email, fresh) }
        dash.await(); prof.await(); cap.await()
    }

    /** `?refresh=1` purges the server cache; a plain load is happy to reuse it. */
    private fun flag(fresh: Boolean) = if (fresh) 1 else null

    private fun dashboardCacheKey(email: String) = "dashboard_$email"
    private fun profileCacheKey(email: String) = "profile_$email"
    private fun capabilityCacheKey(email: String) = "capability_$email"

    private suspend fun fetchDashboard(email: String, fresh: Boolean) {
        try {
            val data = RetrofitClient.instance.getTrainerIntelligence(email, flag(fresh))
            _uiState.value = DashboardState.Success(data)
            LocalCache.saveMap(dashboardCacheKey(email), data)
            com.example.skillsync.data.SessionManager.setLastSyncTime(System.currentTimeMillis())
        } catch (e: Exception) {
            // A failed refresh must not wipe out data the manager is already reading.
            if (_uiState.value !is DashboardState.Success) {
                val cached = LocalCache.loadMap(dashboardCacheKey(email))
                _uiState.value = if (cached != null) {
                    DashboardState.Success(
                        cached,
                        fromCache = true,
                        cachedAt = LocalCache.savedAt(dashboardCacheKey(email)),
                    )
                } else {
                    DashboardState.Error(e.localizedMessage ?: "Failed to load dashboard data")
                }
            }
        }
    }

    private suspend fun fetchProfile(email: String, fresh: Boolean) {
        try {
            val data = RetrofitClient.instance.getManagerProfile(email, flag(fresh))
            _profile.value = data
            LocalCache.saveMap(profileCacheKey(email), data)
        } catch (_: Exception) {
            // Identity is presentation only — the dashboard is still usable
            // without it. Fall back to whatever was last cached so the header
            // stays populated instead of degrading to the bare email.
            if (_profile.value == null) {
                _profile.value = LocalCache.loadMap(profileCacheKey(email))
            }
        }
    }

    private suspend fun fetchCapability(email: String, fresh: Boolean) {
        _capabilityLoading.value = true
        try {
            val data = RetrofitClient.instance.getTeamCapability(email, flag(fresh))
            _capability.value = data
            LocalCache.saveMap(capabilityCacheKey(email), data)
        } catch (_: Exception) {
            // Leave the previous value in place; fall back to disk only if this
            // is the very first attempt, so cert KPIs show something offline
            // rather than "—" when a prior session already fetched it.
            if (_capability.value == null) {
                _capability.value = LocalCache.loadMap(capabilityCacheKey(email))
            }
        } finally {
            _capabilityLoading.value = false
        }
    }

    private val _notification = kotlinx.coroutines.flow.MutableSharedFlow<String>()
    val notification = _notification.asSharedFlow()

    private var pollingJob: kotlinx.coroutines.Job? = null

    fun startPolling(email: String) {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(60000)
                try {
                    val oldState = _uiState.value as? DashboardState.Success
                    val oldBatches = (oldState?.intelligenceData?.get("unallocated_batches") as? List<*>)?.size ?: 0
                    
                    fetchAll(email, fresh = true)
                    
                    val newState = _uiState.value as? DashboardState.Success
                    val newBatches = (newState?.intelligenceData?.get("unallocated_batches") as? List<*>)?.size ?: 0
                    
                    if (newBatches > oldBatches) {
                        _notification.emit("🔔 New Unallocated Batch Available")
                    }
                } catch (e: Exception) {
                    // Ignore polling errors
                }
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }
}
