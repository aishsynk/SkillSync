package com.example.skillsync.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skillsync.data.api.RetrofitClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class DashboardState {
    object Loading : DashboardState()
    data class Success(val intelligenceData: Map<String, Any>) : DashboardState()
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
            fetchAll(email)
        }
    }

    /** Pull-to-refresh and screen-resume: keeps current data visible while re-fetching. */
    fun refresh(email: String) {
        viewModelScope.launch {
            _refreshing.value = true
            fetchAll(email)
            _refreshing.value = false
        }
    }

    /** Loads capability the first time something actually needs it. */
    fun ensureCapability(email: String) {
        if (_capability.value != null || _capabilityLoading.value) return
        viewModelScope.launch { fetchCapability(email) }
    }

    /**
     * Re-reads only what a write can have changed. Marking a skill alters course
     * ownership and certification coverage but not the roster or the batch
     * calendar, so paying for the full dashboard again would be waste. No-op if
     * capability was never loaded — there is nothing on screen to go stale.
     */
    fun refreshCapability(email: String) {
        if (_capability.value == null) return
        viewModelScope.launch { fetchCapability(email) }
    }

    private suspend fun fetchAll(email: String) = coroutineScope {
        val dash = async { fetchDashboard(email) }
        val prof = async { fetchProfile(email) }
        // Capability is refreshed rather than fetched: if the manager has never
        // opened anything that needs it, this must not silently pay for it.
        val cap = async { if (_capability.value != null) fetchCapability(email) }
        dash.await(); prof.await(); cap.await()
    }

    private suspend fun fetchDashboard(email: String) {
        try {
            _uiState.value = DashboardState.Success(
                RetrofitClient.instance.getTrainerIntelligence(email)
            )
        } catch (e: Exception) {
            // A failed refresh must not wipe out data the manager is already reading.
            if (_uiState.value !is DashboardState.Success) {
                _uiState.value = DashboardState.Error(
                    e.localizedMessage ?: "Failed to load dashboard data"
                )
            }
        }
    }

    private suspend fun fetchProfile(email: String) {
        try {
            _profile.value = RetrofitClient.instance.getManagerProfile(email)
        } catch (_: Exception) {
            // Identity is presentation only — the dashboard is still usable
            // without it, so this failure stays silent and the header degrades.
        }
    }

    private suspend fun fetchCapability(email: String) {
        _capabilityLoading.value = true
        try {
            _capability.value = RetrofitClient.instance.getTeamCapability(email)
        } catch (_: Exception) {
            // Leave the previous value in place; cert KPIs show "—" if there is none.
        } finally {
            _capabilityLoading.value = false
        }
    }
}
