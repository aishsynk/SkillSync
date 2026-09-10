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

/** One actionable item on the manager's "This Week" board. */
data class PriorityItem(
    val id: String,
    val kind: String,
    val title: String,
    val detail: String,
    val severity: String,          // "high" | "medium" | "low"
    val due: String,               // ISO date or ""
    val targetType: String,        // "demand" | "trainer" | "action"
    val targetId: String,
    val rankScore: Double,
    val coverable: Boolean,
) {
    val targetName: String get() = title
}

sealed class PrioritiesState {
    object Loading : PrioritiesState()
    data class Success(
        val items: List<PriorityItem>,
        val counts: Map<String, Int>,
        val generatedAt: String,
    ) : PrioritiesState() {
        val bySeverity: Map<String, List<PriorityItem>> get() = items.groupBy { it.severity.lowercase() }
        val byKind: Map<String, List<PriorityItem>> get() = items.groupBy { it.kind }
        val openCount: Int get() = items.size
    }
    data class Error(val message: String) : PrioritiesState()
}

/**
 * Offline-first view model for `GET /api/v2/manager/priorities`, mirroring
 * [WeeklyReportViewModel]: render the last cached snapshot instantly, poll the
 * backend while it reports `loading`, and never drop a good board on failure.
 */
class PrioritiesViewModel(
    private val repository: ManagerRepository = ManagerRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow<PrioritiesState>(PrioritiesState.Loading)
    val state: StateFlow<PrioritiesState> = _state

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    private var managerEmail: String = ""
    private var appContext: Context? = null

    private fun cacheKey() = "priorities_$managerEmail"

    // Full unallocated batches for the bulk-share message — the whole pipeline
    // a manager sees on the Plan tab, reused here so "This Week" can share
    // them in one tap rather than one at a time.
    private val _bulkBatches = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val bulkBatches: StateFlow<List<Map<String, Any>>> = _bulkBatches

    fun init(email: String, context: Context) {
        appContext = context.applicationContext
        if (managerEmail == email && _state.value is PrioritiesState.Success) return
        managerEmail = email
        load()
        loadBulkBatches()
    }

    fun refresh() {
        load(userInitiated = true)
        loadBulkBatches(fresh = true)
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadBulkBatches(fresh: Boolean = false) {
        val email = managerEmail.ifBlank { return }
        // Instant offline render from whatever the Demand tab or dashboard
        // already cached — never wait for network to show the share button.
        // Cache key is "allocation_$email" (see ManagerRepository.allocation via
        // DataRepository.cachedMap). Dashboard fallback is "dashboard_$email".
        // Both payloads may carry the list under "batches" (allocation-desk) or
        // "unallocated_demand_df" (dashboard) — handle either so a backend key
        // rename never empties the share button.
        val cachedAlloc = LocalCache.loadMap("allocation_$email")
        val cachedDash = LocalCache.loadMap("dashboard_$email")
        val initial = (cachedAlloc?.get("batches") as? List<Map<String, Any>>)
            ?: (cachedDash?.get("unallocated_demand_df") as? List<Map<String, Any>>)
            ?: emptyList()
        if (initial.isNotEmpty()) _bulkBatches.value = initial

        viewModelScope.launch {
            try {
                val result = repository.allocation(email, fresh)
                val data = result.data
                // ManagerRepository.allocation caches under "allocation_$email" and
                // returns either "batches" or "unallocated_demand_df" — accept both.
                val batches = (data?.get("batches") as? List<Map<String, Any>>)
                    ?: (data?.get("unallocated_demand_df") as? List<Map<String, Any>>)
                if (!batches.isNullOrEmpty()) {
                    _bulkBatches.value = batches
                } else if (cachedAlloc != null || cachedDash != null) {
                    // Keep the cache we already emitted; do not clear to empty.
                    // An empty network payload never wipes a good offline snapshot.
                } else {
                    // Fallback: derive minimal rows from the priority items
                    // themselves when no allocation cache exists at all.
                    // Race note: _state.value may still be Loading here because
                    // loadBulkBatches is called from init() in parallel with
                    // load(). That race is harmless: if we miss the fallback
                    // now, the priorities Success will land shortly and the next
                    // refresh() (or the user pulling to refresh) re-calls
                    // loadBulkBatches with state populated. We intentionally do
                    // not suspend/collect state here to keep this path
                    // fire-and-forget and offline-first.
                    val items = (_state.value as? PrioritiesState.Success)?.items
                        ?.filter { it.kind == "unstaffed_demand" }
                    if (!items.isNullOrEmpty()) {
                        _bulkBatches.value = items.map { it ->
                            mapOf<String, Any>(
                                "course_name" to it.title.removePrefix("Unstaffed: ").trim(),
                                "start_date" to it.due,
                                "end_date" to "",
                                "delivery_mode" to "",
                                "location" to "",
                                "participants" to 0,
                                "customer" to "",
                                "demand_id" to it.targetId,
                                "session_time" to "",
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                // Keep whatever we already emitted from cache.
            }
        }
    }

    private fun load(userInitiated: Boolean = false) {
        val email = managerEmail.ifBlank { return }

        val cached = LocalCache.loadMap(cacheKey())
        if (cached != null && cached["loading"] != true) {
            _state.value = PrioritiesState.Success(
                parseItems(cached), parseCounts(cached), cached["generated_at"]?.toString() ?: "",
            )
        } else if (_state.value !is PrioritiesState.Success) {
            _state.value = PrioritiesState.Loading
        }

        if (userInitiated) _refreshing.value = true

        viewModelScope.launch {
            val ctx = appContext
            if (ctx != null && !RetrofitClient.isNetworkAvailable(ctx)) {
                if (_state.value !is PrioritiesState.Success) {
                    _state.value = PrioritiesState.Error("Offline — no saved priorities yet")
                }
                _refreshing.value = false
                return@launch
            }
            try {
                var data: Map<String, Any>? = repository.priorities(email).data
                repeat(10) {
                    val d = data
                    if (d != null && d["loading"] != true) return@repeat
                    delay(3_000)
                    data = repository.priorities(email).data ?: data
                }
                val ready = data
                when {
                    ready != null && ready["loading"] != true ->
                        _state.value = PrioritiesState.Success(
                            parseItems(ready), parseCounts(ready),
                            ready["generated_at"]?.toString() ?: "",
                        )
                    _state.value is PrioritiesState.Success -> { /* keep last snapshot */ }
                    else -> _state.value = PrioritiesState.Error("Priorities are still preparing. Pull to refresh shortly.")
                }
            } catch (e: Exception) {
                if (_state.value !is PrioritiesState.Success) {
                    _state.value = PrioritiesState.Error(e.message ?: "Failed to load priorities")
                }
            } finally {
                _refreshing.value = false
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseItems(raw: Map<String, Any>): List<PriorityItem> {
        val list = raw["items"] as? List<Map<String, Any>> ?: emptyList()
        return list.map { it ->
            PriorityItem(
                id = it["id"]?.toString() ?: "",
                kind = it["kind"]?.toString() ?: "",
                title = it["title"]?.toString() ?: "",
                detail = it["detail"]?.toString() ?: "",
                severity = (it["severity"]?.toString() ?: "low").lowercase(),
                due = it["due"]?.toString() ?: "",
                targetType = it["target_type"]?.toString() ?: "action",
                targetId = it["target_id"]?.toString() ?: "",
                rankScore = (it["rank_score"] as? Number)?.toDouble() ?: 0.0,
                coverable = it["coverable"] as? Boolean ?: false,
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseCounts(raw: Map<String, Any>): Map<String, Int> {
        val counts = raw["counts"] as? Map<String, Any> ?: emptyMap()
        return counts.mapValues { (_, v) -> (v as? Number)?.toInt() ?: 0 }
    }

    companion object {
        /** Last known open-item count from disk, for the dashboard entry card. */
        fun cachedOpenCount(email: String): Int {
            val raw = LocalCache.loadMap("priorities_$email") ?: return 0
            @Suppress("UNCHECKED_CAST")
            val items = raw["items"] as? List<Map<String, Any>> ?: return 0
            return items.size
        }

        /** Cached bulk-batch count for the pipeline share bar, mirroring [cachedOpenCount]. */
        fun cachedBulkCount(email: String): Int {
            @Suppress("UNCHECKED_CAST")
            val alloc = LocalCache.loadMap("allocation_$email")
            val batches = (alloc?.get("batches") as? List<*>) ?: (alloc?.get("unallocated_demand_df") as? List<*>)
            if (!batches.isNullOrEmpty()) return batches.size
            @Suppress("UNCHECKED_CAST")
            val dash = LocalCache.loadMap("dashboard_$email")
            val demand = dash?.get("unallocated_demand_df") as? List<*>
            return demand?.size ?: 0
        }
    }
}
