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

data class StructuredFeedback(
    val strength: String = "",
    val areaOfImprovement: String = "",
    val otherFeedback: String = "",
    val trajectory: String = "Improving",
    val sentiment: String = "Constructive",
    val mockSummary: String = "",
    val formattedText: String = "",
)

data class TrainerIndexCriteria(
    val sNo: Int = 0,
    val criteria: String = "",
    val rawValue: String = "",
    val remarks: String = "",
    val weightage: String = "",
    val capping: String = "",
    val points: Double = 0.0,
)

data class TrainerIndexSummary(
    val totalScore: Double = 0.0,
    val tier: String = "Tier 3: Gold",
    val tierBadge: String = "🔷 Gold",
    val tierLevel: Int = 3,
    val tierDescription: String = "",
    val isFdeQualified: Boolean = false,
    val utilizationPts: Double = 0.0,
    val qualityPts: Double = 0.0,
    val beastAiPts: Double = 0.0,
    val certificationsPts: Double = 0.0,
    val instructorPts: Double = 0.0,
    val knowledgeSharingPts: Double = 0.0,
    val deductionsPts: Double = 0.0,
    val criteria: List<TrainerIndexCriteria> = emptyList(),
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
    val trajectory: String = "Improving",
    val structuredFeedback: StructuredFeedback = StructuredFeedback(),
    val trainerIndex: TrainerIndexSummary = TrainerIndexSummary(),
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
            headcount = (ts["headcount"] as? Number)?.toInt() ?: (ts["reportee_count"] as? Number)?.toInt() ?: 0,
            avgUtilisation = (ts["avg_utilisation"] as? Number)?.toDouble() ?: (ts["avg_utilization"] as? Number)?.toDouble() ?: 0.0,
            avgHrScore = (ts["avg_hr_score"] as? Number)?.toDouble() ?: 0.0,
            totalBatches = (ts["total_batches"] as? Number)?.toInt() ?: (ts["total_batches_delivered"] as? Number)?.toInt() ?: 0,
            totalNegativeFeedback = (ts["total_negative_feedback"] as? Number)?.toInt() ?: 0,
            totalPositiveHr = (ts["total_positive_hr"] as? Number)?.toInt() ?: 0,
            totalNegativeHr = (ts["total_negative_hr"] as? Number)?.toInt() ?: 0,
            certGapCount = (ts["cert_gap_count"] as? Number)?.toInt() ?: 0,
        )
        val reportees = ((raw["reportees"] as? List<*>) ?: emptyList<Any>())
            .filterIsInstance<Map<String, Any>>()
            .map { r ->
                val utilMap = r["utilization"] as? Map<String, Any>
                val delMap = r["delivery"] as? Map<String, Any>
                val capMap = r["capability"] as? Map<String, Any>
                val qualMap = r["quality"] as? Map<String, Any>
                val certMap = r["certifications"] as? Map<String, Any>

                val utilPct = (r["utilisation_pct"] as? Number)?.toDouble()
                    ?: (utilMap?.get("month") as? Number)?.toDouble()
                    ?: 0.0

                val batchCount = (r["batch_count"] as? Number)?.toInt()
                    ?: (delMap?.get("batches") as? Number)?.toInt()
                    ?: 0

                val avgQubits = (r["avg_qubits"] as? Number)?.toDouble()
                    ?: (capMap?.get("avg_qubits") as? Number)?.toDouble()
                    ?: 0.0

                val negFeedback = (r["negative_feedback_count"] as? Number)?.toInt()
                    ?: (qualMap?.get("negative_feedback") as? Number)?.toInt()
                    ?: 0

                val hrPos = (r["hr_positive_count"] as? Number)?.toInt()
                    ?: (qualMap?.get("hr_positive") as? Number)?.toInt()
                    ?: 0

                val hrNeg = (r["hr_negative_count"] as? Number)?.toInt()
                    ?: (qualMap?.get("hr_negative") as? Number)?.toInt()
                    ?: 0

                val certsMissing = (r["certs_missing"] as? Number)?.toInt()
                    ?: (certMap?.get("gap_count") as? Number)?.toInt()
                    ?: 0

                val certsHeld = (r["certs_held"] as? Number)?.toInt()
                    ?: (certMap?.get("held") as? Number)?.toInt()
                    ?: 0

                val sfRaw = (r["structured_feedback"] as? Map<String, Any>) ?: emptyMap()
                val trajectoryVal = sfRaw["trajectory"]?.toString() ?: r["trajectory"]?.toString() ?: "Improving"
                val structuredFeedback = StructuredFeedback(
                    strength = sfRaw["strength"]?.toString() ?: "",
                    areaOfImprovement = sfRaw["area_of_improvement"]?.toString() ?: "",
                    otherFeedback = sfRaw["other_feedback"]?.toString() ?: "",
                    trajectory = trajectoryVal,
                    sentiment = sfRaw["sentiment"]?.toString() ?: "Constructive",
                    mockSummary = sfRaw["mock_summary"]?.toString() ?: "",
                    formattedText = sfRaw["formatted_text"]?.toString() ?: "",
                )

                val topCoursesList = ((r["top_courses"] as? List<*>) ?: (capMap?.get("top_courses") as? List<*>) ?: emptyList<Any>())
                    .mapNotNull {
                        when (it) {
                            is Map<*, *> -> it["course_name"]?.toString()
                            else -> it?.toString()
                        }
                    }.take(3)

                val tiMap = (r["trainer_index"] as? Map<String, Any>) ?: emptyMap()
                val criteriaList = ((tiMap["criteria"] as? List<*>) ?: emptyList<Any>())
                    .filterIsInstance<Map<String, Any>>()
                    .map { c ->
                        TrainerIndexCriteria(
                            sNo = (c["s_no"] as? Number)?.toInt() ?: 0,
                            criteria = c["criteria"]?.toString() ?: "",
                            rawValue = c["raw_value"]?.toString() ?: "",
                            remarks = c["remarks"]?.toString() ?: "",
                            weightage = c["weightage"]?.toString() ?: "",
                            capping = c["capping"]?.toString() ?: "",
                            points = (c["points"] as? Number)?.toDouble() ?: 0.0,
                        )
                    }
                val tiScore = (r["ti_score"] as? Number)?.toDouble()
                    ?: (tiMap["total_score"] as? Number)?.toDouble()
                    ?: 0.0
                val tiTier = r["ti_tier"]?.toString()
                    ?: tiMap["tier"]?.toString()
                    ?: "Tier 3: Gold"
                val tiBadge = r["ti_badge"]?.toString()
                    ?: tiMap["tier_badge"]?.toString()
                    ?: "🔷 Gold"
                val tiLevel = (tiMap["tier_level"] as? Number)?.toInt() ?: 3
                val tiDesc = tiMap["tier_description"]?.toString() ?: ""
                val isFde = (tiMap["is_fde_qualified"] as? Boolean) ?: false

                val trainerIndexSummary = TrainerIndexSummary(
                    totalScore = tiScore,
                    tier = tiTier,
                    tierBadge = tiBadge,
                    tierLevel = tiLevel,
                    tierDescription = tiDesc,
                    isFdeQualified = isFde,
                    utilizationPts = (tiMap["utilization_pts"] as? Number)?.toDouble() ?: 0.0,
                    qualityPts = (tiMap["quality_pts"] as? Number)?.toDouble() ?: 0.0,
                    beastAiPts = (tiMap["beast_ai_pts"] as? Number)?.toDouble() ?: 0.0,
                    certificationsPts = (tiMap["certifications_pts"] as? Number)?.toDouble() ?: 0.0,
                    instructorPts = (tiMap["instructor_pts"] as? Number)?.toDouble() ?: 0.0,
                    knowledgeSharingPts = (tiMap["knowledge_sharing_pts"] as? Number)?.toDouble() ?: 0.0,
                    deductionsPts = (tiMap["deductions_pts"] as? Number)?.toDouble() ?: 0.0,
                    criteria = criteriaList,
                )

                ReporteeSnapshot(
                    name = r["name"]?.toString() ?: "",
                    email = r["email"]?.toString() ?: "",
                    hrScore = (r["hr_score"] as? Number)?.toInt() ?: 0,
                    utilisationPct = utilPct,
                    batchCount = batchCount,
                    avgQubits = avgQubits,
                    negativeFeedbackCount = negFeedback,
                    hrPositiveCount = hrPos,
                    hrNegativeCount = hrNeg,
                    certsMissing = certsMissing,
                    certsHeld = certsHeld,
                    topCourses = topCoursesList,
                    flag = r["flag"]?.toString(),
                    trajectory = trajectoryVal,
                    structuredFeedback = structuredFeedback,
                    trainerIndex = trainerIndexSummary,
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
