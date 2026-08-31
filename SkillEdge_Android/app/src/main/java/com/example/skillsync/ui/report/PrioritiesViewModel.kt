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

    fun init(email: String, context: Context) {
        appContext = context.applicationContext
        if (managerEmail == email && _state.value is PrioritiesState.Success) return
        managerEmail = email
        load()
    }

    fun refresh() = load(userInitiated = true)

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
    }
}
