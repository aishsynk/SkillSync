package com.example.skillsync.ui.trainer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skillsync.data.api.RetrofitClient
import com.example.skillsync.data.ManagerRepository
import com.example.skillsync.data.cache.LocalCache
import com.example.skillsync.data.models.ActionRow
import com.example.skillsync.ui.common.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class Trainer360State {
    object Loading : Trainer360State()
    /** See [com.example.skillsync.ui.main.DashboardState.Success] — same offline contract. */
    data class Success(
        val data: Map<String, Any>,
        val fromCache: Boolean = false,
        val cachedAt: Long = 0L,
    ) : Trainer360State()
    data class Error(val message: String) : Trainer360State()
}

class Trainer360ViewModel(
    private val repository: ManagerRepository = ManagerRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow<Trainer360State>(Trainer360State.Loading)
    val state: StateFlow<Trainer360State> = _state

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    private var loadedFor: String? = null

    /** { months: [{month, utilization}], available } — null until first load. */
    val utilHistory = MutableStateFlow<Map<String, Any>?>(null)

    /** { found, syllabus_url, course_name } for the tapped course; null while
     *  in flight so the sheet can distinguish loading from "no syllabus". */
    val syllabus = MutableStateFlow<Map<String, Any>?>(null)

/** Real open manager Actions for this trainer; never generated in the UI. */
    val actions = MutableStateFlow<List<ActionRow>>(emptyList())

    /** Development plan for this trainer: { items: [...], suggested: [...] }. */
    val devPlan = MutableStateFlow<Map<String, Any>?>(null)

    fun load(trainerEmail: String, managerEmail: String = "", context: android.content.Context) {
        if (loadedFor == trainerEmail && _state.value is Trainer360State.Success) return
        loadedFor = trainerEmail
        viewModelScope.launch {
            _state.value = Trainer360State.Loading
            fetch(trainerEmail, managerEmail, context, fresh = false)
        }
        fetchUtilHistory(trainerEmail)
        fetchActions(managerEmail, trainerEmail)
        fetchDevPlan(managerEmail, trainerEmail, fresh = false)
    }

    private fun fetchDevPlan(managerEmail: String, trainerEmail: String, fresh: Boolean) {
        if (managerEmail.isBlank() || trainerEmail.isBlank()) return
        viewModelScope.launch {
            val result = repository.devPlan(managerEmail, trainerEmail, fresh)
            result.data?.let { devPlan.value = it }
        }
    }

    /** Adopt a suggestion or add a manual goal, then refresh the plan in place. */
    fun addDevPlanItem(
        managerEmail: String, trainerEmail: String,
        title: String, kind: String, targetDate: String = "", note: String = "",
    ) {
        if (managerEmail.isBlank() || trainerEmail.isBlank() || title.isBlank()) return
        viewModelScope.launch {
            val result = repository.addDevPlanItem(managerEmail, trainerEmail, title, kind, targetDate, note)
            result.data?.let { devPlan.value = it }
        }
    }

    /** Cycle one plan item's status (open -> in_progress -> done -> open). */
    fun cycleDevPlanStatus(managerEmail: String, trainerEmail: String, id: String, nextStatus: String) {
        if (managerEmail.isBlank() || trainerEmail.isBlank() || id.isBlank()) return
        viewModelScope.launch {
            val result = repository.updateDevPlanItem(managerEmail, trainerEmail, id, status = nextStatus)
            result.data?.let { devPlan.value = it }
        }
    }

    /** Pull-to-refresh and screen-resume; keeps the profile on screen while re-reading. */
    fun refresh(trainerEmail: String, managerEmail: String = "", context: android.content.Context) {
        viewModelScope.launch {
            _refreshing.value = true
            fetch(trainerEmail, managerEmail, context, fresh = true)
            fetchUtilHistory(trainerEmail)
            fetchActions(managerEmail, trainerEmail)
            fetchDevPlan(managerEmail, trainerEmail, fresh = true)
            _refreshing.value = false
        }
    }

    private fun fetchUtilHistory(email: String) {
        viewModelScope.launch {
            try {
                utilHistory.value = repository.utilizationHistory(email)
            } catch (e: Exception) {
                // Ignore failure for secondary data
            }
        }
    }

    fun syncSilently(trainerEmail: String, managerEmail: String = "", context: android.content.Context) {
        viewModelScope.launch {
            fetch(trainerEmail, managerEmail, context, fresh = false)
            fetchUtilHistory(trainerEmail)
            fetchActions(managerEmail, trainerEmail)
        }
    }

    private fun fetchActions(managerEmail: String, trainerEmail: String) {
        if (managerEmail.isBlank()) {
            actions.value = emptyList()
            return
        }
viewModelScope.launch {
            val result = repository.actions(managerEmail)
            actions.value = result.data.orEmpty().filter { action ->
                action.trainerEmail.lowercase() == trainerEmail.lowercase() &&
                    action.lifecycleState.lowercase() !in setOf("closed", "resolved")
            }
        }
    }

    fun fetchSyllabus(courseName: String) {
        viewModelScope.launch {
            syllabus.value = null // reset while loading
            try {
                syllabus.value = repository.syllabus(courseName)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun cacheKey(trainerEmail: String) = "trainer360_$trainerEmail"

    private suspend fun fetch(trainerEmail: String, managerEmail: String, context: android.content.Context, fresh: Boolean) {
        // 1. Instantly read from LocalCache if not already loaded and no fresh push requested
        if (!fresh && _state.value !is Trainer360State.Success) {
            val cached = LocalCache.loadMap(cacheKey(trainerEmail))
            if (cached != null) {
                _state.value = Trainer360State.Success(
                    cached,
                    fromCache = true,
                    cachedAt = LocalCache.savedAt(cacheKey(trainerEmail))
                )
            }
        }

        // 2. Network Check
        if (!RetrofitClient.isNetworkAvailable(context)) {
            if (_state.value !is Trainer360State.Success) {
                _state.value = Trainer360State.Error("No internet connection")
            }
            return
        }

        // 3. Fetch from API in background. The backend answers partial-first and
        //    warms the full profile, so poll briefly while it reports loading.
        try {
            var result = repository.trainer360(trainerEmail, managerEmail, fresh)
            var data = result.data ?: throw IllegalStateException(result.error ?: "Failed to load trainer profile")
            repeat(8) {
                if (data["loading"] != true) return@repeat
                kotlinx.coroutines.delay(3_000)
                result = repository.trainer360(trainerEmail, managerEmail, false)
                data = result.data ?: data
            }
            if (data["loading"] == true && _state.value is Trainer360State.Success) return
            _state.value = Trainer360State.Success(
                data,
                fromCache = result.source == com.example.skillsync.data.DataSource.CACHE,
                cachedAt = result.cachedAt,
            )
        } catch (e: Exception) {
            if (_state.value !is Trainer360State.Success) {
                _state.value = Trainer360State.Error(e.userMessage("load the trainer profile"))
            }
        }
    }

    /**
     * Real readiness: leave and commitments from the RMS calendar rather than
     * from the off-date fields, which are empty for every reachable trainer.
     */
    val readiness = kotlinx.coroutines.flow.MutableStateFlow<Map<String, Any>?>(null)
    private var readinessKey: String? = null

    fun loadReadiness(managerEmail: String, trainerEmail: String) {
        val key = "$managerEmail|$trainerEmail"
        if (readinessKey == key) return
        readinessKey = key
        viewModelScope.launch {
            readiness.value = runCatching {
                com.example.skillsync.data.api.RetrofitClient.instance
                    .getTrainerReadiness(managerEmail, trainerEmail)
            }.getOrNull()
        }
    }
}
