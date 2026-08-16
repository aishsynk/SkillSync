package com.example.skillsync.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skillsync.data.api.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter

sealed class HrReportState {
    object Loading : HrReportState()
    data class Success(val data: HrReportData) : HrReportState()
    data class Error(val message: String) : HrReportState()
}

data class HrReportData(
    val month: String,
    val monthKey: String,
    val generatedAt: String,
    val teamSummary: TeamSummaryData,
    val reportees: List<ReporteeSnapshot>,
)

data class TeamSummaryData(
    val headcount: Int,
    val avgUtilisation: Double,
    val avgHrScore: Double,
    val totalBatches: Int,
    val totalNegativeFeedback: Int,
    val totalPositiveHr: Int,
    val totalNegativeHr: Int,
    val certGapCount: Int,
)

data class ReporteeSnapshot(
    val name: String,
    val email: String,
    val hrScore: Int,
    val utilisationPct: Double,
    val batchCount: Int,
    val avgQubits: Double,
    val negativeFeedbackCount: Int,
    val hrPositiveCount: Int,
    val hrNegativeCount: Int,
    val certsMissing: Int,
    val certsHeld: Int,
    val topCourses: List<String>,
    val flag: String?,
)

class HrMonthlyReportViewModel : ViewModel() {

    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM")
    private var currentMonth = YearMonth.now()
    private var managerEmail = ""

    private val _state = MutableStateFlow<HrReportState>(HrReportState.Loading)
    val state: StateFlow<HrReportState> = _state

    private val _displayMonth = MutableStateFlow(currentMonth.format(fmt))
    val displayMonth: StateFlow<String> = _displayMonth

    fun init(email: String) {
        managerEmail = email
        load()
    }

    fun previousMonth() {
        currentMonth = currentMonth.minusMonths(1)
        _displayMonth.value = currentMonth.format(fmt)
        load()
    }

    fun nextMonth() {
        val next = currentMonth.plusMonths(1)
        if (!next.isAfter(YearMonth.now())) {
            currentMonth = next
            _displayMonth.value = currentMonth.format(fmt)
            load()
        }
    }

    fun canGoNext() = !currentMonth.plusMonths(1).isAfter(YearMonth.now())

    private fun load() {
        _state.value = HrReportState.Loading
        viewModelScope.launch {
            try {
                val raw = RetrofitClient.instance.getHrMonthlyReport(
                    manager = managerEmail,
                    month = currentMonth.format(fmt),
                )
                _state.value = HrReportState.Success(parse(raw))
            } catch (e: Exception) {
                _state.value = HrReportState.Error(e.message ?: "Failed to load HR report")
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parse(raw: Map<String, Any>): HrReportData {
        val ts = (raw["team_summary"] as? Map<String, Any>) ?: emptyMap()
        val teamSummary = TeamSummaryData(
            headcount = (ts["headcount"] as? Double)?.toInt() ?: 0,
            avgUtilisation = (ts["avg_utilisation"] as? Double) ?: 0.0,
            avgHrScore = (ts["avg_hr_score"] as? Double) ?: 0.0,
            totalBatches = (ts["total_batches"] as? Double)?.toInt() ?: 0,
            totalNegativeFeedback = (ts["total_negative_feedback"] as? Double)?.toInt() ?: 0,
            totalPositiveHr = (ts["total_positive_hr"] as? Double)?.toInt() ?: 0,
            totalNegativeHr = (ts["total_negative_hr"] as? Double)?.toInt() ?: 0,
            certGapCount = (ts["cert_gap_count"] as? Double)?.toInt() ?: 0,
        )
        val reportees = ((raw["reportees"] as? List<*>) ?: emptyList<Any>())
            .filterIsInstance<Map<String, Any>>()
            .map { r ->
                ReporteeSnapshot(
                    name = r["name"]?.toString() ?: "",
                    email = r["email"]?.toString() ?: "",
                    hrScore = (r["hr_score"] as? Double)?.toInt() ?: 0,
                    utilisationPct = (r["utilisation_pct"] as? Double) ?: 0.0,
                    batchCount = (r["batch_count"] as? Double)?.toInt() ?: 0,
                    avgQubits = (r["avg_qubits"] as? Double) ?: 0.0,
                    negativeFeedbackCount = (r["negative_feedback_count"] as? Double)?.toInt() ?: 0,
                    hrPositiveCount = (r["hr_positive_count"] as? Double)?.toInt() ?: 0,
                    hrNegativeCount = (r["hr_negative_count"] as? Double)?.toInt() ?: 0,
                    certsMissing = (r["certs_missing"] as? Double)?.toInt() ?: 0,
                    certsHeld = (r["certs_held"] as? Double)?.toInt() ?: 0,
                    topCourses = ((r["top_courses"] as? List<*>) ?: emptyList<Any>())
                        .mapNotNull { it?.toString() }.take(3),
                    flag = r["flag"]?.toString(),
                )
            }
        return HrReportData(
            month = raw["month"]?.toString() ?: "",
            monthKey = raw["month_key"]?.toString() ?: "",
            generatedAt = raw["generated_at"]?.toString() ?: "",
            teamSummary = teamSummary,
            reportees = reportees,
        )
    }
}
