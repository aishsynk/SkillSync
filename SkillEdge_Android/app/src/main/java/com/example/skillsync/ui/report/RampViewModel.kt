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

/** One new trainer's ramp record. */
data class RampTrainer(
    val name: String,
    val email: String,
    val doj: String,
    val tenureMonths: Int,
    val coursesCertified: Int,
    val batchesDelivered: Int,
    val firstBatchDate: String?,
    val daysToFirstBatch: Int?,
    val currentUtilization: Int?,
    val avgLearnerRating: Double?,
    val ratingSample: Int,
    val rampStage: String,
    val stalled: Boolean,
    val nextStep: String,
)

data class RampSummary(
    val newCount: Int,
    val stalledCount: Int,
    val avgDaysToFirstBatch: Int?,
    val note: String,
)

sealed class RampState {
    object Loading : RampState()
    data class Success(
        val trainers: List<RampTrainer>,
        val summary: RampSummary,
        val generatedAt: String,
    ) : RampState()
    data class Error(val message: String) : RampState()
}

/**
 * Offline-first view model for `GET /api/v2/ramp`, mirroring
 * [CapacityRunwayViewModel]: render the last cached snapshot instantly, poll
 * the backend while it reports `loading`, keep the last good ramp on failure.
 */
class RampViewModel(
    private val repository: ManagerRepository = ManagerRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow<RampState>(RampState.Loading)
    val state: StateFlow<RampState> = _state

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    private var managerEmail: String = ""
    private var appContext: Context? = null

    private fun cacheKey() = "ramp_$managerEmail"

    fun init(email: String, context: Context) {
        appContext = context.applicationContext
        if (managerEmail == email && _state.value is RampState.Success) return
        managerEmail = email
        load()
    }

    fun refresh() = load(userInitiated = true)

    private fun load(userInitiated: Boolean = false) {
        val email = managerEmail.ifBlank { return }

        val cached = LocalCache.loadMap(cacheKey())
        if (cached != null && cached["loading"] != true) {
            _state.value = parse(cached)
        } else if (_state.value !is RampState.Success) {
            _state.value = RampState.Loading
        }

        if (userInitiated) _refreshing.value = true

        viewModelScope.launch {
            val ctx = appContext
            if (ctx != null && !RetrofitClient.isNetworkAvailable(ctx)) {
                if (_state.value !is RampState.Success) {
                    _state.value = RampState.Error("Offline — no saved ramp yet")
                }
                _refreshing.value = false
                return@launch
            }
            try {
                var data: Map<String, Any>? = repository.rampReport(email).data
                repeat(10) {
                    val d = data
                    if (d != null && d["loading"] != true) return@repeat
                    delay(3_000)
                    data = repository.rampReport(email).data ?: data
                }
                val ready = data
                when {
                    ready != null && ready["loading"] != true -> _state.value = parse(ready)
                    _state.value is RampState.Success -> { /* keep last snapshot */ }
                    else -> _state.value = RampState.Error("Ramp is still preparing. Pull to refresh shortly.")
                }
            } catch (e: Exception) {
                if (_state.value !is RampState.Success) {
                    _state.value = RampState.Error(e.message ?: "Failed to load ramp")
                }
            } finally {
                _refreshing.value = false
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parse(raw: Map<String, Any>): RampState.Success {
        fun num(v: Any?): Int = (v as? Number)?.toInt() ?: 0
        fun numOrNull(v: Any?): Int? = (v as? Number)?.toInt()
        fun dblOrNull(v: Any?): Double? = (v as? Number)?.toDouble()

        val trainers = (raw["trainers"] as? List<Map<String, Any>> ?: emptyList()).map {
            RampTrainer(
                name = it["name"]?.toString() ?: "",
                email = it["email"]?.toString() ?: "",
                doj = it["doj"]?.toString() ?: "",
                tenureMonths = num(it["tenure_months"]),
                coursesCertified = num(it["courses_certified"]),
                batchesDelivered = num(it["batches_delivered"]),
                firstBatchDate = it["first_batch_date"]?.toString(),
                daysToFirstBatch = numOrNull(it["days_to_first_batch"]),
                currentUtilization = numOrNull(it["current_utilization"]),
                avgLearnerRating = dblOrNull(it["avg_learner_rating"]),
                ratingSample = num(it["rating_sample"]),
                rampStage = it["ramp_stage"]?.toString() ?: "",
                stalled = it["stalled"] == true,
                nextStep = it["next_step"]?.toString() ?: "",
            )
        }
        val s = raw["summary"] as? Map<String, Any> ?: emptyMap()
        val summary = RampSummary(
            newCount = num(s["new_count"]),
            stalledCount = num(s["stalled_count"]),
            avgDaysToFirstBatch = numOrNull(s["avg_days_to_first_batch"]),
            note = s["note"]?.toString() ?: "",
        )
        return RampState.Success(trainers, summary, raw["generated_at"]?.toString() ?: "")
    }
}
