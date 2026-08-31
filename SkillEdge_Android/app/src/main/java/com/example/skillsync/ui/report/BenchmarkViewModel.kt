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

/** One benchmarked metric: the team value next to an honest baseline. */
data class BenchmarkMetric(
    val key: String,
    val label: String,
    val teamValue: Double?,
    val baselineValue: Double?,
    val unit: String,
    val direction: String,   // "higher_better" | "lower_better"
    val verdict: String,     // "ahead" | "on_par" | "behind" | "unknown"
    val gap: Double?,
)

sealed class BenchmarkState {
    object Loading : BenchmarkState()
    data class Success(
        val headline: String,
        val baselineSource: String,
        val aheadCount: Int,
        val behindCount: Int,
        val metrics: List<BenchmarkMetric>,
        val generatedAt: String,
    ) : BenchmarkState()
    data class Error(val message: String) : BenchmarkState()
}

/**
 * Offline-first view model for `GET /api/v2/benchmark`, mirroring
 * [CapacityRunwayViewModel]: render the last cached snapshot instantly, poll the
 * backend while it reports `loading`, and never drop a good snapshot on failure.
 */
class BenchmarkViewModel(
    private val repository: ManagerRepository = ManagerRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow<BenchmarkState>(BenchmarkState.Loading)
    val state: StateFlow<BenchmarkState> = _state

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    private var managerEmail: String = ""
    private var appContext: Context? = null

    private fun cacheKey() = "benchmark_$managerEmail"

    fun init(email: String, context: Context) {
        appContext = context.applicationContext
        if (managerEmail == email && _state.value is BenchmarkState.Success) return
        managerEmail = email
        load()
    }

    fun refresh() = load(userInitiated = true)

    private fun load(userInitiated: Boolean = false) {
        val email = managerEmail.ifBlank { return }

        val cached = LocalCache.loadMap(cacheKey())
        if (cached != null && cached["loading"] != true) {
            _state.value = parse(cached)
        } else if (_state.value !is BenchmarkState.Success) {
            _state.value = BenchmarkState.Loading
        }

        if (userInitiated) _refreshing.value = true

        viewModelScope.launch {
            val ctx = appContext
            if (ctx != null && !RetrofitClient.isNetworkAvailable(ctx)) {
                if (_state.value !is BenchmarkState.Success) {
                    _state.value = BenchmarkState.Error("Offline — no saved comparison yet")
                }
                _refreshing.value = false
                return@launch
            }
            try {
                var data: Map<String, Any>? = repository.benchmarkReport(email).data
                repeat(10) {
                    val d = data
                    if (d != null && d["loading"] != true) return@repeat
                    delay(3_000)
                    data = repository.benchmarkReport(email).data ?: data
                }
                val ready = data
                when {
                    ready != null && ready["loading"] != true -> _state.value = parse(ready)
                    _state.value is BenchmarkState.Success -> { /* keep last snapshot */ }
                    else -> _state.value = BenchmarkState.Error("Comparison is still preparing. Pull to refresh shortly.")
                }
            } catch (e: Exception) {
                if (_state.value !is BenchmarkState.Success) {
                    _state.value = BenchmarkState.Error(e.message ?: "Failed to load comparison")
                }
            } finally {
                _refreshing.value = false
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parse(raw: Map<String, Any>): BenchmarkState.Success {
        fun dbl(v: Any?): Double? = (v as? Number)?.toDouble()

        val metrics = (raw["metrics"] as? List<Map<String, Any>> ?: emptyList()).map {
            BenchmarkMetric(
                key = it["key"]?.toString() ?: "",
                label = it["label"]?.toString() ?: "",
                teamValue = dbl(it["team_value"]),
                baselineValue = dbl(it["baseline_value"]),
                unit = it["unit"]?.toString() ?: "",
                direction = it["direction"]?.toString() ?: "higher_better",
                verdict = it["verdict"]?.toString() ?: "unknown",
                gap = dbl(it["gap"]),
            )
        }
        val s = raw["summary"] as? Map<String, Any> ?: emptyMap()
        return BenchmarkState.Success(
            headline = s["headline"]?.toString() ?: "",
            baselineSource = raw["baseline_source"]?.toString() ?: "",
            aheadCount = (s["ahead_count"] as? Number)?.toInt() ?: 0,
            behindCount = (s["behind_count"] as? Number)?.toInt() ?: 0,
            metrics = metrics,
            generatedAt = raw["generated_at"]?.toString() ?: "",
        )
    }
}
