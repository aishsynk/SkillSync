package com.koenig.skilledge.data.repository

import com.koenig.skilledge.data.api.SkillEdgeApiService
import com.koenig.skilledge.domain.models.UnifiedManagerIntelligence
import com.koenig.skilledge.domain.models.UiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for manager intelligence data
 * Handles data fetching from API, caching, and error handling
 */
@Singleton
class IntelligenceRepository @Inject constructor(
    private val apiService: SkillEdgeApiService
) {

    /**
     * Fetch unified manager intelligence
     * Implements stale-while-refresh pattern:
     * 1. Check local cache
     * 2. If stale (>4h): Serve cache + refresh in background
     * 3. If fresh: Serve cache
     * 4. If missing: Fetch from API
     */
    suspend fun getUnifiedManagerIntelligence(
        email: String,
        forceRefresh: Boolean = false
    ): Flow<UiState<UnifiedManagerIntelligence>> = flow {
        try {
            emit(UiState.Loading)

            val response = apiService.getUnifiedManagerIntelligence(
                email = email,
                refresh = forceRefresh
            )

            if (response.isSuccessful) {
                val data = response.body()
                if (data != null) {
                    emit(UiState.Success(data))
                    // Log freshness metadata
                    Timber.d("Intelligence loaded: ${data.cacheStatus}, age=${data.cacheAgeMinutes}m")
                } else {
                    emit(UiState.Error("Empty response from server"))
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                emit(UiState.Error("API Error: ${response.code()} - $errorBody"))
                Timber.e("API error: ${response.code()}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching intelligence")
            emit(UiState.Error(e.message ?: "Network error"))
        }
    }

    /**
     * Fetch only essential KPI data for dashboard
     * Lighter call than full intelligence, useful for pull-to-refresh
     */
    suspend fun getRefreshStatus(): Flow<UiState<RefreshStatusData>> = flow {
        try {
            emit(UiState.Loading)

            val response = apiService.getRefreshStatus()

            if (response.isSuccessful) {
                val data = response.body()
                if (data != null) {
                    emit(UiState.Success(
                        RefreshStatusData(
                            isRefreshing = data.isRefreshing,
                            lastRefreshAt = data.lastRefreshAt,
                            lastRefreshError = data.lastRefreshError
                        )
                    ))
                } else {
                    emit(UiState.Error("Empty response"))
                }
            } else {
                emit(UiState.Error("Failed to get refresh status"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error checking refresh status")
            emit(UiState.Error(e.message ?: "Network error"))
        }
    }

    /**
     * Trigger manual refresh of intelligence
     */
    suspend fun runRefresh(): Flow<UiState<String>> = flow {
        try {
            emit(UiState.Loading)

            val response = apiService.runRefresh()

            if (response.isSuccessful) {
                emit(UiState.Success("Refresh initiated"))
            } else {
                emit(UiState.Error("Failed to start refresh"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error running refresh")
            emit(UiState.Error(e.message ?: "Network error"))
        }
    }
}

data class RefreshStatusData(
    val isRefreshing: Boolean,
    val lastRefreshAt: Long? = null,
    val lastRefreshError: String? = null
)

/**
 * Repository for action lifecycle operations
 */
@Singleton
class ActionRepository @Inject constructor(
    private val apiService: SkillEdgeApiService
) {

    suspend fun closeAction(
        actionId: String,
        note: String? = null,
        reason: String? = null
    ): Flow<UiState<String>> = flow {
        try {
            emit(UiState.Loading)

            val response = apiService.closeAction(
                actionId = actionId,
                request = com.koenig.skilledge.data.api.ActionUpdateRequest(
                    note = note,
                    reason = reason
                )
            )

            if (response.isSuccessful) {
                emit(UiState.Success("Action closed"))
                Timber.d("Action closed: $actionId")
            } else {
                emit(UiState.Error("Failed to close action"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error closing action")
            emit(UiState.Error(e.message ?: "Network error"))
        }
    }

    suspend fun escalateAction(
        actionId: String,
        note: String? = null,
        assignee: String? = null
    ): Flow<UiState<String>> = flow {
        try {
            emit(UiState.Loading)

            val response = apiService.escalateAction(
                actionId = actionId,
                request = com.koenig.skilledge.data.api.ActionUpdateRequest(
                    note = note,
                    assignee = assignee
                )
            )

            if (response.isSuccessful) {
                emit(UiState.Success("Action escalated"))
                Timber.d("Action escalated: $actionId")
            } else {
                emit(UiState.Error("Failed to escalate action"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error escalating action")
            emit(UiState.Error(e.message ?: "Network error"))
        }
    }

    suspend fun reassignAction(
        actionId: String,
        note: String? = null,
        assignee: String? = null
    ): Flow<UiState<String>> = flow {
        try {
            emit(UiState.Loading)

            val response = apiService.reassignAction(
                actionId = actionId,
                request = com.koenig.skilledge.data.api.ActionUpdateRequest(
                    note = note,
                    assignee = assignee
                )
            )

            if (response.isSuccessful) {
                emit(UiState.Success("Action reassigned"))
                Timber.d("Action reassigned: $actionId")
            } else {
                emit(UiState.Error("Failed to reassign action"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error reassigning action")
            emit(UiState.Error(e.message ?: "Network error"))
        }
    }
}

/**
 * Repository for agentic services
 */
@Singleton
class AgentRepository @Inject constructor(
    private val apiService: SkillEdgeApiService
) {

    suspend fun askAgent(question: String): Flow<UiState<String>> = flow {
        try {
            emit(UiState.Loading)

            val response = apiService.askAgent(
                request = com.koenig.skilledge.data.api.AgentAskRequest(
                    question = question
                )
            )

            if (response.isSuccessful) {
                val data = response.body()
                if (data != null) {
                    emit(UiState.Success(data.answer))
                } else {
                    emit(UiState.Error("Empty response"))
                }
            } else {
                emit(UiState.Error("Failed to ask agent"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error asking agent")
            emit(UiState.Error(e.message ?: "Network error"))
        }
    }

    suspend fun getBriefing(): Flow<UiState<String>> = flow {
        try {
            emit(UiState.Loading)

            val response = apiService.getAgentBriefing()

            if (response.isSuccessful) {
                val data = response.body()
                if (data != null) {
                    val briefing = buildString {
                        appendLine(data.title)
                        appendLine()
                        appendLine(data.summary)
                        if (data.actionItems.isNotEmpty()) {
                            appendLine()
                            appendLine("Action Items:")
                            data.actionItems.forEach { appendLine("• $it") }
                        }
                    }
                    emit(UiState.Success(briefing))
                } else {
                    emit(UiState.Error("Empty response"))
                }
            } else {
                emit(UiState.Error("Failed to get briefing"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting briefing")
            emit(UiState.Error(e.message ?: "Network error"))
        }
    }
}
