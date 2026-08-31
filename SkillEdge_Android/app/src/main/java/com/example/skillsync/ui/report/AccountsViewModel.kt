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

/** One customer/account the team delivers for. */
data class AccountRow(
    val name: String,
    val batchesDelivered: Int,
    val participantsDelivered: Int,
    val batchesUpcoming: Int,
    val openDemandBatches: Int,
    val trainers: List<String>,
    val courses: List<String>,
    val lastDeliveryDate: String,
    val nextStartDate: String,
    val avgLearnerRating: Double?,
)

data class AccountsSummary(
    val accountCount: Int,
    val topAccount: String,
    val topAccountShare: Double,
    val unspecifiedBatches: Int,
)

sealed class AccountsState {
    object Loading : AccountsState()
    data class Success(
        val accounts: List<AccountRow>,
        val summary: AccountsSummary,
        val generatedAt: String,
    ) : AccountsState()
    data class Error(val message: String) : AccountsState()
}

/**
 * Offline-first view model for `GET /api/v2/accounts`, mirroring
 * [CapacityRunwayViewModel]: render the last cached snapshot instantly, poll the
 * backend while it reports `loading`, and never drop a good book on failure.
 */
class AccountsViewModel(
    private val repository: ManagerRepository = ManagerRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow<AccountsState>(AccountsState.Loading)
    val state: StateFlow<AccountsState> = _state

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    private var managerEmail: String = ""
    private var appContext: Context? = null

    private fun cacheKey() = "accounts_$managerEmail"

    fun init(email: String, context: Context) {
        appContext = context.applicationContext
        if (managerEmail == email && _state.value is AccountsState.Success) return
        managerEmail = email
        load()
    }

    fun refresh() = load(userInitiated = true)

    private fun load(userInitiated: Boolean = false) {
        val email = managerEmail.ifBlank { return }

        val cached = LocalCache.loadMap(cacheKey())
        if (cached != null && cached["loading"] != true) {
            _state.value = parse(cached)
        } else if (_state.value !is AccountsState.Success) {
            _state.value = AccountsState.Loading
        }

        if (userInitiated) _refreshing.value = true

        viewModelScope.launch {
            val ctx = appContext
            if (ctx != null && !RetrofitClient.isNetworkAvailable(ctx)) {
                if (_state.value !is AccountsState.Success) {
                    _state.value = AccountsState.Error("Offline — no saved account book yet")
                }
                _refreshing.value = false
                return@launch
            }
            try {
                var data: Map<String, Any>? = repository.accountsReport(email).data
                repeat(10) {
                    val d = data
                    if (d != null && d["loading"] != true) return@repeat
                    delay(3_000)
                    data = repository.accountsReport(email).data ?: data
                }
                val ready = data
                when {
                    ready != null && ready["loading"] != true -> _state.value = parse(ready)
                    _state.value is AccountsState.Success -> { /* keep last snapshot */ }
                    else -> _state.value = AccountsState.Error("Account book is still preparing. Pull to refresh shortly.")
                }
            } catch (e: Exception) {
                if (_state.value !is AccountsState.Success) {
                    _state.value = AccountsState.Error(e.message ?: "Failed to load accounts")
                }
            } finally {
                _refreshing.value = false
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parse(raw: Map<String, Any>): AccountsState.Success {
        fun num(v: Any?): Int = (v as? Number)?.toInt() ?: 0
        fun strList(v: Any?): List<String> =
            (v as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()

        val accounts = (raw["accounts"] as? List<Map<String, Any>> ?: emptyList()).map {
            AccountRow(
                name = it["name"]?.toString() ?: "",
                batchesDelivered = num(it["batches_delivered"]),
                participantsDelivered = num(it["participants_delivered"]),
                batchesUpcoming = num(it["batches_upcoming"]),
                openDemandBatches = num(it["open_demand_batches"]),
                trainers = strList(it["trainers"]),
                courses = strList(it["courses"]),
                lastDeliveryDate = it["last_delivery_date"]?.toString() ?: "",
                nextStartDate = it["next_start_date"]?.toString() ?: "",
                avgLearnerRating = (it["avg_learner_rating"] as? Number)?.toDouble(),
            )
        }
        val s = raw["summary"] as? Map<String, Any> ?: emptyMap()
        val summary = AccountsSummary(
            accountCount = num(s["account_count"]),
            topAccount = s["top_account"]?.toString() ?: "",
            topAccountShare = (s["top_account_share"] as? Number)?.toDouble() ?: 0.0,
            unspecifiedBatches = num(s["unspecified_batches"]),
        )
        return AccountsState.Success(accounts, summary, raw["generated_at"]?.toString() ?: "")
    }
}
