package com.koenig.skilledge.data.api

import com.koenig.skilledge.domain.models.UnifiedManagerIntelligence
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for SkillEdge API
 * All endpoints match the Python backend (backend/app.py)
 */
interface SkillEdgeApiService {

    // ============= Authentication =============

    @POST("auth/login")
    suspend fun login(@Body loginRequest: LoginRequest): Response<LoginResponse>

    @GET("auth/session")
    suspend fun getSession(): Response<SessionResponse>

    @GET("auth/logout")
    suspend fun logout(): Response<LogoutResponse>

    // ============= Intelligence & Data =============

    @GET("data/unified-manager-intelligence")
    suspend fun getUnifiedManagerIntelligence(
        @Query("email") email: String,
        @Query("refresh") refresh: Boolean = false,
        @Query("course_outline") courseOutline: String? = null,
        @Query("course_title") courseTitle: String? = null,
        @Query("course_vendor") courseVendor: String? = null,
        @Query("course_domain") courseDomain: String? = null,
        @Query("course_level") courseLevel: String? = null,
        @Query("delivery_type") deliveryType: String? = null
    ): Response<UnifiedManagerIntelligence>

    @GET("api/refresh/status")
    suspend fun getRefreshStatus(): Response<RefreshStatusResponse>

    @POST("api/refresh/run")
    suspend fun runRefresh(): Response<RefreshResponse>

    // ============= RMS API Proxy (Server-side relay) =============

    @POST("rms/{api_name}")
    suspend fun callRmsApi(
        @Path("api_name") apiName: String,
        @Body requestBody: Map<String, Any>
    ): Response<Map<String, Any>>

    // ============= Action Lifecycle =============

    @POST("api/actions/{action_id}/close")
    suspend fun closeAction(
        @Path("action_id") actionId: String,
        @Body request: ActionUpdateRequest
    ): Response<ActionUpdateResponse>

    @POST("api/actions/{action_id}/escalate")
    suspend fun escalateAction(
        @Path("action_id") actionId: String,
        @Body request: ActionUpdateRequest
    ): Response<ActionUpdateResponse>

    @POST("api/actions/{action_id}/reassign")
    suspend fun reassignAction(
        @Path("action_id") actionId: String,
        @Body request: ActionUpdateRequest
    ): Response<ActionUpdateResponse>

    @GET("api/actions")
    suspend fun getActions(): Response<List<ActionUpdateResponse>>

    // ============= Review Flags =============

    @POST("api/review-flags/{flag_id}/acknowledge")
    suspend fun acknowledgeReviewFlag(
        @Path("flag_id") flagId: String,
        @Body request: ReviewFlagRequest
    ): Response<ReviewFlagResponse>

    @POST("api/review-flags/{flag_id}/resolve")
    suspend fun resolveReviewFlag(
        @Path("flag_id") flagId: String,
        @Body request: ReviewFlagRequest
    ): Response<ReviewFlagResponse>

    @POST("api/review-flags/{flag_id}/escalate")
    suspend fun escalateReviewFlag(
        @Path("flag_id") flagId: String,
        @Body request: ReviewFlagRequest
    ): Response<ReviewFlagResponse>

    @GET("api/review-flags")
    suspend fun getReviewFlags(): Response<List<ReviewFlagResponse>>

    // ============= Agentic Services =============

    @GET("api/agent/briefing")
    suspend fun getAgentBriefing(): Response<AgentBriefingResponse>

    @GET("api/agent/learning")
    suspend fun getAgentLearningStatus(): Response<AgentLearningResponse>

    @POST("api/agent/ask")
    suspend fun askAgent(@Body request: AgentAskRequest): Response<AgentAnswerResponse>

    @POST("api/agent/feedback")
    suspend fun sendAgentFeedback(@Body request: AgentFeedbackRequest): Response<AgentFeedbackResponse>

    @POST("api/agent/tune")
    suspend fun tuneAgentWeights(): Response<AgentTuneResponse>

    // ============= Diagnostics =============

    @GET("healthz")
    suspend fun getHealth(): Response<HealthResponse>
}

// ============= Request Models =============

data class LoginRequest(
    val email: String
)

data class ActionUpdateRequest(
    val note: String? = null,
    val assignee: String? = null,
    val reason: String? = null
)

data class ReviewFlagRequest(
    val note: String? = null,
    val assignee: String? = null
)

data class AgentAskRequest(
    val question: String,
    val context: String? = null
)

data class AgentFeedbackRequest(
    val recommendationId: String,
    val rating: Int, // 1-5
    val feedback: String? = null
)

// ============= Response Models =============

data class LoginResponse(
    val email: String,
    val status: String = "ok",
    val sessionToken: String? = null
)

data class SessionResponse(
    val email: String?,
    val isAuthenticated: Boolean,
    val expiresAt: Long? = null
)

data class LogoutResponse(
    val status: String = "ok"
)

data class RefreshStatusResponse(
    val isRefreshing: Boolean,
    val lastRefreshAt: Long? = null,
    val lastRefreshError: String? = null
)

data class RefreshResponse(
    val status: String = "ok",
    val message: String? = null
)

data class ActionUpdateResponse(
    val actionId: String,
    val newState: String,
    val updatedAt: Long = System.currentTimeMillis()
)

data class ReviewFlagResponse(
    val flagId: String,
    val newState: String,
    val updatedAt: Long = System.currentTimeMillis()
)

data class AgentBriefingResponse(
    val title: String,
    val summary: String,
    val actionItems: List<String> = emptyList(),
    val riskItems: List<String> = emptyList(),
    val generatedAt: Long = System.currentTimeMillis()
)

data class AgentLearningResponse(
    val modelVersion: String,
    val feedbackCount: Int,
    val lastUpdateAt: Long? = null,
    val weightDrift: Double? = null
)

data class AgentAnswerResponse(
    val answer: String,
    val reasoning: List<String> = emptyList(),
    val confidence: Double = 0.5,
    val toolsUsed: List<String> = emptyList()
)

data class AgentFeedbackResponse(
    val status: String = "ok",
    val message: String? = null
)

data class AgentTuneResponse(
    val status: String = "ok",
    val newModelVersion: String? = null
)

data class HealthResponse(
    val status: String,
    val sessionCount: Int = 0,
    val uptime: Long = 0,
    val version: String? = null
)
