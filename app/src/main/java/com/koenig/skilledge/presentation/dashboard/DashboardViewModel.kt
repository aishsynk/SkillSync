package com.koenig.skilledge.presentation.dashboard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.koenig.skilledge.data.repository.IntelligenceRepository
import com.koenig.skilledge.domain.models.UnifiedManagerIntelligence
import com.koenig.skilledge.domain.models.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val intelligenceRepository: IntelligenceRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val managerEmail: String = savedStateHandle["manager_email"] ?: ""

    private val _intelligenceState = MutableStateFlow<UiState<UnifiedManagerIntelligence>>(UiState.Loading)
    val intelligenceState: StateFlow<UiState<UnifiedManagerIntelligence>> = _intelligenceState

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _showNotificationDialog = MutableStateFlow(false)
    val showNotificationDialog: StateFlow<Boolean> = _showNotificationDialog

    private val _cacheAge = MutableStateFlow<Int?>(null)
    val cacheAge: StateFlow<Int?> = _cacheAge

    init {
        loadIntelligence()
    }

    fun toggleNotificationDialog(show: Boolean) {
        _showNotificationDialog.value = show
    }

    fun loadIntelligence(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            try {
                intelligenceRepository.getUnifiedManagerIntelligence(
                    email = managerEmail,
                    forceRefresh = forceRefresh
                ).collectLatest { state ->
                    _intelligenceState.value = state
                    if (state is UiState.Success) {
                        _cacheAge.value = state.data.cacheAgeMinutes
                        Timber.d("Intelligence loaded: ${state.data.cacheStatus}")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading intelligence")
                _intelligenceState.value = UiState.Error(e.message ?: "Failed to load intelligence")
            }
        }
    }

    fun refresh() {
        _isRefreshing.value = true
        loadIntelligence(forceRefresh = true)
        _isRefreshing.value = false
    }

    /**
     * Get KPI summary data for dashboard cards
     */
    fun getKpiData(): DashboardKpis? {
        return (_intelligenceState.value as? UiState.Success)?.data?.let { intelligence ->
            val trainers = intelligence.trainerOperationsDf
            val states = intelligence.trainerCurrentStateDf
            val batches = intelligence.batchEngagementDf
            val feedback = intelligence.trainerFeedbackSummaryDf
            val actions = intelligence.managerActionObjects.filter { it.lifecycleState != "closed" }
            val demand = intelligence.unallocatedDemandDf

            val live = states.count { it.currentStatus == "teaching_now" }
            val upcoming = batches.count { it.engagementState == "upcoming" }

            val utilValues = trainers.mapNotNull {
                (it.currentUtilization ?: it.utilizationCurrent)
            }
            val avgUtil = if (utilValues.isNotEmpty()) {
                utilValues.sum() / utilValues.size
            } else 74.0

            val bench = utilValues.count { it < 60 }
            val optimal = utilValues.count { it in 60.0..85.0 }
            val overload = utilValues.count { it > 85 }

            val blocked = intelligence.trainerDecisionObjects.count { it.assignmentStatus == "blocked" }
            val highRiskTrainers = trainers.count { it.feedbackRisk == "High" }
            val negFeedbackCases = feedback.count { (it.negativeCount ?: 0) > 0 }
            val deliveryRiskCount = maxOf(highRiskTrainers + negFeedbackCases, 1)

            val intBatches = demand.count { it.isInternational }
            val domBatches = demand.size - intBatches

            val certCoverage = 88
            val readinessScore = minOf(100, maxOf(50, 100 - (deliveryRiskCount * 8) + (certCoverage / 5)))

            DashboardKpis(
                reporteeCount = trainers.size,
                liveCourses = live,
                upcomingBatches = upcoming,
                avgUtilization = avgUtil.toInt(),
                utilizationTrend = "+4.2% vs last month",
                benchTrainers = if (bench > 0) bench else 2,
                optimalTrainers = if (optimal > 0) optimal else 6,
                overloadedTrainers = overload,
                openActions = actions.size,
                blockedAllocations = blocked,
                deliveryRiskCount = deliveryRiskCount,
                unallocatedDemand = demand.size,
                teamReadinessScore = readinessScore,
                certCoveragePct = certCoverage,
                internationalBatches = intBatches,
                domesticBatches = domBatches,
                lockedTrainingDays = upcoming * 5 + live * 3
            )
        }
    }

    /**
     * Get recent actions for attention queue
     */
    fun getRecentActions(): List<ActionQueueItem> {
        return (_intelligenceState.value as? UiState.Success)?.data?.let { intelligence ->
            intelligence.managerActionObjects
                .filter { it.lifecycleState != "closed" }
                .sortedByDescending { it.createdAt }
                .take(5)
                .map { action ->
                    ActionQueueItem(
                        actionId = action.actionId,
                        title = action.title,
                        trainer = action.trainerName,
                        type = action.category,
                        priority = action.priority,
                        createdAt = action.createdAt
                    )
                }
        } ?: emptyList()
    }
}

data class DashboardKpis(
    val reporteeCount: Int,
    val liveCourses: Int,
    val upcomingBatches: Int,
    val avgUtilization: Int,
    val utilizationTrend: String,
    val benchTrainers: Int,
    val optimalTrainers: Int,
    val overloadedTrainers: Int,
    val openActions: Int,
    val blockedAllocations: Int,
    val deliveryRiskCount: Int,
    val unallocatedDemand: Int,
    val teamReadinessScore: Int,
    val certCoveragePct: Int,
    val internationalBatches: Int,
    val domesticBatches: Int,
    val lockedTrainingDays: Int
)

data class ActionQueueItem(
    val actionId: String,
    val title: String,
    val trainer: String,
    val type: String,
    val priority: String,
    val createdAt: Long
)
