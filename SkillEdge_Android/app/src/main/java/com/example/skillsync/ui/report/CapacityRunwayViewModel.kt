package com.example.skillsync.ui.report

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skillsync.data.ManagerRepository
import com.example.skillsync.data.api.RetrofitClient
import com.example.skillsync.data.cache.LocalCache
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** One week on the runway: incoming demand vs the headcount free to take it. */
data class RunwayWeek(
    val weekStart: String,
    val weekEnd: String,
    val demandBatches: Int,
    val demandParticipants: Int,
    val teamAvailable: Int,
    val coverable: Int,
    val gap: Int,
)

/** One "start this upskill now" recommendation. */
data class RunwayUpskill(
    val course: String,
    val examCode: String,
    val opensBatches: Int,
    val nearestTrainer: String,
    val nearestTrainerName: String,
    val why: String,
)

data class RunwaySummary(
    val totalDemand: Int,
    val totalCoverable: Int,
    val worstWeek: String,
    val trainerDaysAvailable: Int,
    val trainerDaysDemanded: Int,
)

sealed class RunwayState {
    object Loading : RunwayState()
    data class Success(
        val weeks: List<RunwayWeek>,
        val summary: RunwaySummary,
        val upskilling: List<RunwayUpskill>,
        val generatedAt: String,
    ) : RunwayState()
    data class Error(val message: String) : RunwayState()
}

/**
 * Offline-first view model for `GET /api/v2/planning/runway`, mirroring
 * [PrioritiesViewModel]: render the last cached snapshot instantly, poll the
 * backend while it reports `loading`, and never drop a good runway on failure.
 */
class CapacityRunwayViewModel(
    private val repository: ManagerRepository = ManagerRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow<RunwayState>(RunwayState.Loading)
    val state: StateFlow<RunwayState> = _state

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    private var managerEmail: String = ""
    private var appContext: Context? = null

    private fun cacheKey() = "runway_$managerEmail"

    fun init(email: String, context: Context) {
        appContext = context.applicationContext
        if (managerEmail == email && _state.value is RunwayState.Success) return
        managerEmail = email
        load()
    }

    fun refresh() = load(userInitiated = true)

    private fun load(userInitiated: Boolean = false) {
        val email = managerEmail.ifBlank { return }

        val cached = LocalCache.loadMap(cacheKey())
        if (cached != null && cached["loading"] != true) {
            _state.value = parse(cached)
        } else if (_state.value !is RunwayState.Success) {
            _state.value = RunwayState.Loading
        }

        if (userInitiated) _refreshing.value = true

        viewModelScope.launch {
            val ctx = appContext
            if (ctx != null && !RetrofitClient.isNetworkAvailable(ctx)) {
                if (_state.value !is RunwayState.Success) {
                    _state.value = RunwayState.Error("Offline — no saved runway yet")
                }
                _refreshing.value = false
                return@launch
            }
            try {
                var data: Map<String, Any>? = repository.capacityRunway(email).data
                repeat(10) {
                    val d = data
                    if (d != null && d["loading"] != true) return@repeat
                    delay(3_000)
                    data = repository.capacityRunway(email).data ?: data
                }
                val ready = data
                when {
                    ready != null && ready["loading"] != true -> _state.value = parse(ready)
                    _state.value is RunwayState.Success -> { /* keep last snapshot */ }
                    else -> _state.value = RunwayState.Error("Runway is still preparing. Pull to refresh shortly.")
                }
            } catch (e: Exception) {
                if (_state.value !is RunwayState.Success) {
                    _state.value = RunwayState.Error(e.message ?: "Failed to load runway")
                }
            } finally {
                _refreshing.value = false
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parse(raw: Map<String, Any>): RunwayState.Success {
        fun num(v: Any?): Int = (v as? Number)?.toInt() ?: 0

        val weeks = (raw["weeks"] as? List<Map<String, Any>> ?: emptyList()).map {
            RunwayWeek(
                weekStart = it["week_start"]?.toString() ?: "",
                weekEnd = it["week_end"]?.toString() ?: "",
                demandBatches = num(it["demand_batches"]),
                demandParticipants = num(it["demand_participants"]),
                teamAvailable = num(it["team_available"]),
                coverable = num(it["coverable"]),
                gap = num(it["gap"]),
            )
        }
        val s = raw["summary"] as? Map<String, Any> ?: emptyMap()
        val summary = RunwaySummary(
            totalDemand = num(s["total_demand"]),
            totalCoverable = num(s["total_coverable"]),
            worstWeek = s["worst_week"]?.toString() ?: "",
            trainerDaysAvailable = num(s["trainer_days_available"]),
            trainerDaysDemanded = num(s["trainer_days_demanded"]),
        )
        val upskilling = (raw["upskilling"] as? List<Map<String, Any>> ?: emptyList()).map {
            RunwayUpskill(
                course = it["course"]?.toString() ?: "",
                examCode = it["exam_code"]?.toString() ?: "",
                opensBatches = num(it["opens_batches"]),
                nearestTrainer = it["nearest_trainer"]?.toString() ?: "",
                nearestTrainerName = it["nearest_trainer_name"]?.toString() ?: "",
                why = it["why"]?.toString() ?: "",
            )
        }
        return RunwayState.Success(weeks, summary, upskilling, raw["generated_at"]?.toString() ?: "")
    }
}
