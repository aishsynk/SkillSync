package com.example.skillsync.ui.reportee

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skillsync.data.api.MarkSkillRequest
import com.example.skillsync.data.api.RetrofitClient
import com.example.skillsync.ui.components.rows
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Backs the trainer (reportee) shell — Today / Demand / Calendar / Practice.
 * Every read is self-scoped on the backend. The only writes a trainer can make:
 * mark own skill (capped at level 4) and message own manager.
 */
class ReporteeViewModel : ViewModel() {

    private val _home = MutableStateFlow<Map<String, Any>?>(null)
    val home: StateFlow<Map<String, Any>?> = _home
    private val _homeLoading = MutableStateFlow(true)
    val homeLoading: StateFlow<Boolean> = _homeLoading

    private val _demand = MutableStateFlow<List<Map<*, *>>>(emptyList())
    val demand: StateFlow<List<Map<*, *>>> = _demand
    private val _demandLoading = MutableStateFlow(true)
    val demandLoading: StateFlow<Boolean> = _demandLoading
    private val _demandError = MutableStateFlow<String?>(null)
    val demandError: StateFlow<String?> = _demandError

    private val _calendar = MutableStateFlow<Map<String, Any>?>(null)
    val calendar: StateFlow<Map<String, Any>?> = _calendar
    private val _calendarLoading = MutableStateFlow(true)
    val calendarLoading: StateFlow<Boolean> = _calendarLoading

    private val _profile360 = MutableStateFlow<Map<String, Any>?>(null)
    val profile360: StateFlow<Map<String, Any>?> = _profile360
    private val _profile360Loading = MutableStateFlow(true)
    val profile360Loading: StateFlow<Boolean> = _profile360Loading

    private var myEmail: String = ""

    private val _updates = MutableStateFlow<List<Map<*, *>>>(emptyList())
    val updates: StateFlow<List<Map<*, *>>> = _updates

    fun load(email: String = myEmail) {
        myEmail = email
        loadHome(); loadDemand(); loadCalendar(); loadUpdates(); load360()
    }

    fun load360() = viewModelScope.launch {
        _profile360Loading.value = true
        try {
            _profile360.value = RetrofitClient.instance.getTrainer360(email = myEmail)
        } catch (_: Exception) {}
        _profile360Loading.value = false
    }

    fun loadHome() = viewModelScope.launch {
        _homeLoading.value = true
        try { _home.value = RetrofitClient.instance.reporteeHome() } catch (_: Exception) {}
        _homeLoading.value = false
    }

    fun loadDemand() = viewModelScope.launch {
        _demandLoading.value = true; _demandError.value = null
        try {
            _demand.value = RetrofitClient.instance.reporteeDemand().rows("matched_demand")
        } catch (e: Exception) {
            _demandError.value = e.localizedMessage ?: "Could not load matched demand"
        }
        _demandLoading.value = false
    }

    fun loadCalendar() = viewModelScope.launch {
        _calendarLoading.value = true
        try { _calendar.value = RetrofitClient.instance.reporteeCalendar() } catch (_: Exception) {}
        _calendarLoading.value = false
    }

    fun loadUpdates() = viewModelScope.launch {
        try { _updates.value = RetrofitClient.instance.notifications().rows("notifications") }
        catch (_: Exception) {}
    }

    /** Mark own skill. Backend caps at level 4; higher becomes a manager request. */
    fun markSkill(courseId: String, level: Int, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val today = java.time.LocalDate.now().toString()
                val res = RetrofitClient.instance.markSkill(
                    MarkSkillRequest(
                        course_id = courseId, trainer_email = "",
                        skill_level = level, from_date = today,
                    ),
                )
                val body = res.body()
                val ok = res.isSuccessful && body?.success == true
                onResult(ok, body?.message ?: body?.error ?: if (ok) "Saved" else "Could not mark skill")
                loadHome(); loadUpdates()
            } catch (e: Exception) {
                onResult(false, e.localizedMessage ?: "Network error")
            }
        }
    }

    fun messageManager(text: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val res = RetrofitClient.instance.reporteeMessage(mapOf("text" to text))
                val ok = res["success"] == true
                onResult(ok, res["error"]?.toString() ?: if (ok) "Sent to your manager" else "Could not send")
                loadUpdates()
            } catch (e: Exception) {
                onResult(false, e.localizedMessage ?: "Network error")
            }
        }
    }
}
