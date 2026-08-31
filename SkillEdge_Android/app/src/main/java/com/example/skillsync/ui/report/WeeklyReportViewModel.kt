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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed class WeeklyReportState {
    object Loading : WeeklyReportState()
    data class Success(val data: WeeklyReportData) : WeeklyReportState()
    data class Error(val message: String) : WeeklyReportState()
}

data class WeeklyReportData(
    val weekLabel: String,
    val weekStart: String,
    val weekEnd: String,
    val teamSummary: WeeklyTeamSummary,
    /** Monday, forward-looking plan. `teamDigest` == this. */
    val teamDigest: String,
    /** Friday, wrap-up variant. Falls back to [teamDigest] when absent. */
    val teamDigestWeekend: String,
    val reportees: List<WeeklyReporteeData>,
)

data class WeeklyTeamSummary(
    val headcount: Int = 0,
    val deliveringCount: Int = 0,
    val benchCount: Int = 0,
    val stretchedCount: Int = 0,
    val atRiskCount: Int = 0,
    val totalCertGaps: Int = 0,
    val totalParticipants: Int = 0,
    val totalBatches: Int = 0,
    val unallocatedDemand: Int = 0,
)

data class WeeklyReporteeData(
    val email: String,
    val name: String,
    val designation: String = "",
    val isDirect: Boolean = true,
    val trainerPlus: Boolean = false,
    val empId: String = "",
    val capacityBucket: String = "Steady",
    val statusHeadline: String = "",
    val currentUtilization: Int? = null,
    val utilization3m: Int? = null,
    val avgQubits: Int = 0,
    val batchCount: Int = 0,
    val totalPax: Int = 0,
    val currentBatch: WeeklyBatchInfo? = null,
    val assignments: List<WeeklyBatchInfo> = emptyList(),
    val feedbackRisk: String = "Low",
    val negativeFeedbackCount: Int = 0,
    val hrPositiveCount: Int = 0,
    val hrNegativeCount: Int = 0,
    val certsHeld: Int = 0,
    val certGaps: Int = 0,
    val certGapCourses: List<String> = emptyList(),
    val standpointNote: String = "",
    /** Monday "this week" message for this reportee. Falls back to standpoint/message. */
    val messageWeekly: String = "",
    /** Friday "weekend" wrap-up message. Falls back to [messageWeekly]. */
    val messageWeekend: String = "",
    val learnerRating: Double? = null,
    val learnerRatingCount: Int = 0,
    val learnerFeedback: Map<String, Any>? = null,
)

data class WeeklyBatchInfo(
    val course: String = "",
    val vendor: String = "",
    val mode: String = "",
    val location: String = "",
    val participants: Int = 0,
    val assignmentId: String = "",
    val startAt: String = "",
    val endAt: String = "",
)

class WeeklyReportViewModel(
    private val repository: ManagerRepository = ManagerRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow<WeeklyReportState>(WeeklyReportState.Loading)
    val state: StateFlow<WeeklyReportState> = _state

    private var targetDate: LocalDate = LocalDate.now()
    private var managerEmail: String = ""
    private var appContext: Context? = null

    private val _displayWeek = MutableStateFlow(formatWeekDisplay(targetDate))
    val displayWeek: StateFlow<String> = _displayWeek

    private fun weekKey(): String =
        targetDate.minusDays((targetDate.dayOfWeek.value - 1).toLong())
            .format(DateTimeFormatter.ISO_LOCAL_DATE)

    private fun cacheKey() = "weekly_report_${managerEmail}_${weekKey()}"

    fun init(email: String, context: Context) {
        appContext = context.applicationContext
        if (managerEmail == email && _state.value is WeeklyReportState.Success) return
        managerEmail = email
        targetDate = LocalDate.now()
        _displayWeek.value = formatWeekDisplay(targetDate)
        load()
    }

    fun previousWeek() {
        targetDate = targetDate.minusWeeks(1)
        _displayWeek.value = formatWeekDisplay(targetDate)
        load()
    }

    fun nextWeek() {
        if (!canGoNext()) return
        targetDate = targetDate.plusWeeks(1)
        _displayWeek.value = formatWeekDisplay(targetDate)
        load()
    }

    fun resetToCurrentWeek() {
        targetDate = LocalDate.now()
        _displayWeek.value = formatWeekDisplay(targetDate)
        load()
    }

    fun canGoNext(): Boolean {
        val nextMonday = targetDate.plusWeeks(1).minusDays((targetDate.plusWeeks(1).dayOfWeek.value - 1).toLong())
        val currentMonday = LocalDate.now().minusDays((LocalDate.now().dayOfWeek.value - 1).toLong())
        return !nextMonday.isAfter(currentMonday.plusWeeks(4)) // Allow planning up to 4 weeks ahead
    }

    fun reload() {
        load()
    }

    private fun load() {
        val email = managerEmail.ifBlank { return }
        val week = weekKey()

        val cached = LocalCache.loadMap(cacheKey())
        _state.value = if (cached != null && cached["loading"] != true) {
            WeeklyReportState.Success(parse(cached))
        } else {
            WeeklyReportState.Loading
        }

        viewModelScope.launch {
            val ctx = appContext
            if (ctx != null && !RetrofitClient.isNetworkAvailable(ctx)) {
                if (_state.value !is WeeklyReportState.Success) {
                    _state.value = WeeklyReportState.Error("Offline — no saved report for this week yet")
                }
                return@launch
            }
            try {
                var result = repository.weeklyReport(email, week, fresh = false)
                var data = result.data
                repeat(12) {
                    if (data != null && data["loading"] != true) return@repeat
                    delay(3_000)
                    result = repository.weeklyReport(email, week, fresh = false)
                    data = result.data ?: data
                }
                val ready = data
                when {
                    ready != null && ready["loading"] != true -> _state.value = WeeklyReportState.Success(parse(ready))
                    _state.value is WeeklyReportState.Success -> { /* keep last snapshot */ }
                    else -> _state.value = WeeklyReportState.Error("Report is still preparing from RMS. Pull to refresh shortly.")
                }
            } catch (e: Exception) {
                if (_state.value !is WeeklyReportState.Success) {
                    _state.value = WeeklyReportState.Error(e.message ?: "Failed to load weekly report")
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parse(raw: Map<String, Any>): WeeklyReportData {
        val weekLabel = raw["week_label"]?.toString() ?: formatWeekDisplay(targetDate)
        val weekStart = raw["week_start"]?.toString() ?: ""
        val weekEnd = raw["week_end"]?.toString() ?: ""
        val teamDigest = raw["team_digest_weekly"]?.toString()
            ?: raw["team_digest"]?.toString() ?: ""
        val teamDigestWeekend = raw["team_digest_weekend"]?.toString()?.takeIf { it.isNotBlank() }
            ?: teamDigest

        val tsMap = raw["team_summary"] as? Map<String, Any> ?: emptyMap()
        val teamSummary = WeeklyTeamSummary(
            headcount = (tsMap["headcount"] as? Number)?.toInt() ?: 0,
            deliveringCount = (tsMap["delivering_count"] as? Number)?.toInt() ?: 0,
            benchCount = (tsMap["bench_count"] as? Number)?.toInt() ?: 0,
            stretchedCount = (tsMap["stretched_count"] as? Number)?.toInt() ?: 0,
            atRiskCount = (tsMap["at_risk_count"] as? Number)?.toInt() ?: 0,
            totalCertGaps = (tsMap["total_cert_gaps"] as? Number)?.toInt() ?: 0,
            totalParticipants = (tsMap["total_participants"] as? Number)?.toInt() ?: 0,
            totalBatches = (tsMap["total_batches"] as? Number)?.toInt() ?: 0,
            unallocatedDemand = (tsMap["unallocated_demand"] as? Number)?.toInt() ?: 0,
        )

        val repList = raw["reportees"] as? List<Map<String, Any>> ?: emptyList()
        val reportees = repList.map { r ->
            val curBatchMap = r["current_batch"] as? Map<String, Any>
            val curBatch = curBatchMap?.let {
                WeeklyBatchInfo(
                    course = it["course"]?.toString() ?: "",
                    vendor = it["vendor"]?.toString() ?: "",
                    mode = it["mode"]?.toString() ?: "",
                    location = it["location"]?.toString() ?: "",
                    participants = (it["participants"] as? Number)?.toInt() ?: 0,
                    assignmentId = it["assignment_id"]?.toString() ?: "",
                    startAt = it["start_at"]?.toString() ?: "",
                    endAt = it["end_at"]?.toString() ?: "",
                )
            }

            val assignList = r["assignments"] as? List<Map<String, Any>> ?: emptyList()
            val assignments = assignList.map { a ->
                WeeklyBatchInfo(
                    course = a["course"]?.toString() ?: "",
                    vendor = a["vendor"]?.toString() ?: "",
                    mode = a["mode"]?.toString() ?: "",
                    location = a["location"]?.toString() ?: "",
                    participants = (a["participants"] as? Number)?.toInt() ?: 0,
                    assignmentId = a["assignment_id"]?.toString() ?: "",
                    startAt = a["start_at"]?.toString() ?: "",
                    endAt = a["end_at"]?.toString() ?: "",
                )
            }

            val certGapCourses = (r["cert_gap_courses"] as? List<*>)
                ?.mapNotNull { it?.toString()?.takeIf { s -> s.isNotBlank() } }
                ?: emptyList()

            WeeklyReporteeData(
                email = r["email"]?.toString() ?: "",
                name = r["name"]?.toString() ?: "",
                designation = r["designation"]?.toString() ?: "",
                isDirect = r["is_direct"] as? Boolean ?: true,
                trainerPlus = r["trainer_plus"] as? Boolean ?: false,
                empId = r["emp_id"]?.toString() ?: "",
                capacityBucket = r["capacity_bucket"]?.toString() ?: "Steady",
                statusHeadline = r["status_headline"]?.toString() ?: "Steady",
                currentUtilization = (r["current_utilization"] as? Number)?.toInt(),
                utilization3m = (r["utilization_3m"] as? Number)?.toInt(),
                avgQubits = (r["avg_qubits"] as? Number)?.toInt() ?: 0,
                batchCount = (r["batch_count"] as? Number)?.toInt() ?: 0,
                totalPax = (r["total_pax"] as? Number)?.toInt() ?: 0,
                currentBatch = curBatch,
                assignments = assignments,
                feedbackRisk = r["feedback_risk"]?.toString() ?: "Low",
                negativeFeedbackCount = (r["negative_feedback_count"] as? Number)?.toInt() ?: 0,
                hrPositiveCount = (r["hr_positive_count"] as? Number)?.toInt() ?: 0,
                hrNegativeCount = (r["hr_negative_count"] as? Number)?.toInt() ?: 0,
                certsHeld = (r["certs_held"] as? Number)?.toInt() ?: 0,
                certGaps = (r["cert_gaps"] as? Number)?.toInt() ?: 0,
                certGapCourses = certGapCourses,
                standpointNote = r["standpoint_note"]?.toString() ?: "",
                messageWeekly = (r["message_weekly"]?.toString()?.takeIf { it.isNotBlank() }
                    ?: r["standpoint_note"]?.toString()?.takeIf { it.isNotBlank() }
                    ?: r["message"]?.toString() ?: ""),
                messageWeekend = (r["message_weekend"]?.toString()?.takeIf { it.isNotBlank() }
                    ?: r["message_weekly"]?.toString()?.takeIf { it.isNotBlank() }
                    ?: r["standpoint_note"]?.toString()?.takeIf { it.isNotBlank() }
                    ?: r["message"]?.toString() ?: ""),
                learnerRating = (r["learner_rating"] as? Number)?.toDouble(),
                learnerRatingCount = (r["learner_rating_count"] as? Number)?.toInt() ?: 0,
                learnerFeedback = r["learner_feedback"] as? Map<String, Any>,
            )
        }

        return WeeklyReportData(
            weekLabel = weekLabel,
            weekStart = weekStart,
            weekEnd = weekEnd,
            teamSummary = teamSummary,
            teamDigest = teamDigest,
            teamDigestWeekend = teamDigestWeekend,
            reportees = reportees,
        )
    }

    companion object {
        fun formatWeekDisplay(date: LocalDate): String {
            val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())
            val sunday = monday.plusDays(6)
            val fmt = DateTimeFormatter.ofPattern("d MMM", Locale.UK)
            val yearFmt = DateTimeFormatter.ofPattern("yyyy", Locale.UK)
            return "${monday.format(fmt)} – ${sunday.format(fmt)} ${sunday.format(yearFmt)}"
        }
    }
}
