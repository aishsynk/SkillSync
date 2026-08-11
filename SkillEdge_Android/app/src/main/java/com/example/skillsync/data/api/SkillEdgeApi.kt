package com.example.skillsync.data.api

import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Body
import retrofit2.http.Query

data class LoginRequest(val email: String)

data class LoginResponse(
    val success: Boolean?,
    val session_id: String?,
    val email: String?,
    val role: String?,
    val error: String?,
    val message: String?,
)

interface SkillEdgeApi {
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("api/auth/logout")
    suspend fun logout(): Map<String, Any>

    /**
     * [refresh] maps to `?refresh=1`, which purges this manager's server-side
     * cache before rebuilding. Sent on pull-to-refresh only: a first load should
     * take the cached answer, otherwise the cache never helps anyone.
     */
    @GET("api/data/unified-manager-intelligence")
    suspend fun getTrainerIntelligence(
        @Query("email") email: String,
        @Query("refresh") refresh: Int? = null,
    ): Map<String, Any>

    /** The signed-in user's own identity — small and fast, gates the header paint. */
    @GET("api/data/manager-profile")
    suspend fun getManagerProfile(
        @Query("email") email: String,
        @Query("refresh") refresh: Int? = null,
    ): Map<String, Any>

    /**
     * Deep single-trainer profile. [manager] is optional and only used to rank
     * the trainer within their own team.
     */
    @GET("api/data/trainer-360")
    suspend fun getTrainer360(
        @Query("email") email: String,
        @Query("manager") manager: String? = null,
        @Query("refresh") refresh: Int? = null,
    ): Map<String, Any>

    /**
     * Course catalogue and certification gaps for the whole team. Three extra RMS
     * round-trips per trainer, so it is fetched alongside the dashboard rather
     * than inside it.
     */
    @GET("api/v2/capability/portfolio")
    suspend fun getTeamCapability(
        @Query("email") email: String,
        @Query("refresh") refresh: Int? = null,
    ): Map<String, Any>

    /** Unallocated batches ranked against this manager's team capability. */
    @GET("api/data/allocation-desk")
    suspend fun getAllocationDesk(
        @Query("email") email: String,
        @Query("refresh") refresh: Int? = null,
    ): Map<String, Any>

    /** Authenticated Version 2 operational evidence for one demand. */
    @GET("api/v2/operations/demand-context")
    suspend fun getDemandContext(
        @Query("manager") manager: String,
        @Query("demandId") demandId: String,
        @Query("courseName") courseName: String,
    ): DemandContextResponse

    @GET("api/v2/planning/capacity")
    suspend fun getCapacityPlan(@Query("manager") manager: String): CapacityPlanResponse

    /**
     * Writes a skill to production RMS. Returns a raw [retrofit2.Response] because
     * a rejected-but-well-formed write answers 409 with a body worth showing; the
     * plain suspend form would throw that away as an HttpException.
     */
    @POST("api/action/mark-skill")
    suspend fun markSkill(@Body request: MarkSkillRequest): retrofit2.Response<MarkSkillResponse>

    /** Authoritative 3-month utilisation history (RMS key 39). Returns an
     *  object: { months: [{month, utilization}], available, emp_code }. */
    @GET("api/data/trainer-utilization-history")
    suspend fun getTrainerUtilizationHistory(@Query("email") email: String): Map<String, Any>

    /** Syllabus lookup for one course. Returns { found, syllabus_url, ... }.
     *  RMS holds a link to a syllabus PDF, not table-of-contents content. */
    @GET("api/data/course-syllabus")
    suspend fun getCourseSyllabus(@Query("courseName") courseName: String): Map<String, Any>

    /** Full RMS catalogue search, including courses not mapped to this team. */
    @GET("api/data/course-search")
    suspend fun searchCourses(@Query("q") query: String): Map<String, Any>

    /** Verified catalogue metadata plus future public schedule dates. */
    @GET("api/data/course-intelligence")
    suspend fun getCourseIntelligence(@Query("courseName") courseName: String): Map<String, Any>

    /** Wider trainer network for a course. Returns { available, trainers }.
     *  `available` is false while RMS rejects every TrainerType value — that
     *  is "cannot ask", not "nobody found". */
    @GET("api/data/alternative-trainers")
    suspend fun getAlternativeTrainers(@Query("course") course: String): Map<String, Any>

    /** Ask Copilot a question */
    @POST("api/agent/ask")
    suspend fun agentAsk(
        @Body request: AgentAskRequest
    ): AgentAskResponse

    // ── Manager action inbox ────────────────────────────────────────────────

    /** Derived + manager-raised actions with their lifecycle state. */
    @GET("api/v2/actions")
    suspend fun getActions(@Query("email") email: String): Map<String, Any>

    /** Raise an action by hand (anything RMS cannot infer). */
    @POST("api/v2/actions")
    suspend fun raiseAction(@Body body: Map<String, String>): Map<String, Any>

    /** Move an action through its lifecycle, optionally with a note. */
    @POST("api/v2/actions/{id}/state")
    suspend fun setActionState(
        @Path("id") id: String,
        @Body body: Map<String, String>,
    ): Map<String, Any>

    /** Append a follow-up note without changing state. */
    @POST("api/v2/actions/{id}/note")
    suspend fun addActionNote(
        @Path("id") id: String,
        @Body body: Map<String, String>,
    ): Map<String, Any>

    @GET("api/v2/actions/{id}/audit")
    suspend fun getActionAudit(
        @Path("id") id: String,
        @Query("manager") manager: String,
    ): Map<String, Any>
}

data class DemandCourseContext(
    val name: String = "",
    val verified: Boolean = false,
    @com.google.gson.annotations.SerializedName("available_in_rms") val availableInRms: Any? = null,
    val status: String = "",
    @com.google.gson.annotations.SerializedName("is_duplicate") val isDuplicate: Any? = null,
    @com.google.gson.annotations.SerializedName("is_discontinued") val isDiscontinued: Any? = null,
)

data class SalesConfirmationContext(
    val verified: Boolean = false,
    val count: Int = 0,
    val ids: List<String> = emptyList(),
)

data class DemandContextResponse(
    @com.google.gson.annotations.SerializedName("schema_version") val schemaVersion: String = "",
    @com.google.gson.annotations.SerializedName("demand_id") val demandId: String = "",
    val course: DemandCourseContext = DemandCourseContext(),
    @com.google.gson.annotations.SerializedName("sales_confirmations") val salesConfirmations: SalesConfirmationContext = SalesConfirmationContext(),
    val confidence: String = "partial",
    val note: String = "",
)

data class CapacityHorizon(val weeks: Int = 0, val start: String = "", val end: String = "")
data class CapacitySummary(
    val demand: Int = 0,
    @com.google.gson.annotations.SerializedName("strong_coverage") val strongCoverage: Int = 0,
    val uncovered: Int = 0,
    val priority: Int = 0,
    val international: Int = 0,
    @com.google.gson.annotations.SerializedName("coverage_pct") val coveragePct: Int? = null,
)
data class CapacityWeek(
    @com.google.gson.annotations.SerializedName("week_start") val weekStart: String = "",
    @com.google.gson.annotations.SerializedName("week_end") val weekEnd: String = "",
    val demand: Int = 0,
    val priority: Int = 0,
    val international: Int = 0,
    @com.google.gson.annotations.SerializedName("strong_coverage") val strongCoverage: Int = 0,
    @com.google.gson.annotations.SerializedName("partial_coverage") val partialCoverage: Int = 0,
    val uncovered: Int = 0,
    @com.google.gson.annotations.SerializedName("verified_available_candidates") val verifiedAvailableCandidates: Int = 0,
    @com.google.gson.annotations.SerializedName("availability_unknown_candidates") val availabilityUnknownCandidates: Int = 0,
    @com.google.gson.annotations.SerializedName("coverage_pct") val coveragePct: Int? = null,
    val pressure: String = "none",
)
data class CapacityConfidence(
    val demand: String = "",
    @com.google.gson.annotations.SerializedName("availability_pct") val availabilityPct: Int? = null,
    val availability: String = "partial",
    val note: String = "",
)
data class CapacityPlanResponse(
    @com.google.gson.annotations.SerializedName("schema_version") val schemaVersion: String = "",
    val ready: Boolean = false,
    val code: String? = null,
    val message: String? = null,
    val horizon: CapacityHorizon = CapacityHorizon(),
    val summary: CapacitySummary = CapacitySummary(),
    val weeks: List<CapacityWeek> = emptyList(),
    val confidence: CapacityConfidence = CapacityConfidence(),
)

data class MarkSkillRequest(
    val course_id: String,
    val trainer_email: String,
    val skill_level: Int,
    val from_date: String,
    val officially_approved: String = "No",
)

/**
 * [verified] is the field that matters: the backend sets it only after re-reading
 * the RMS skill register and finding the course there.
 *
 * RMS answers a *refused* write with HTTP 200 and buries the reason in a nested
 * JSON string ([rms_status] / [rms_message], e.g. "Course not found"). Treating
 * the absence of an exception as success is what made skill assignment look like
 * it saved when it had not. [changed] separates a real write from a no-op
 * re-assert of a skill already on file.
 */
data class MarkSkillResponse(
    val success: Boolean?,
    val verified: Boolean?,
    val changed: Boolean?,
    val trainer_email: String?,
    val course_id: String?,
    val course_name: String?,
    val skill_level: Int?,
    val from_date: String?,
    val already_held: Boolean?,
    val skill_count: Int?,
    val rms_status: String?,
    val rms_message: String?,
    val message: String?,
    val error: String?,
)

data class AgentAskRequest(
    val manager_email: String,
    val target_email: String,
    val question_key: String,
)

data class AgentAskResponse(
    val answer: String,
    val evidence: String?,
    val source: List<String>?,
    val confidence: String?,
    val decisionVersion: String?,
    val error: String?,
)
