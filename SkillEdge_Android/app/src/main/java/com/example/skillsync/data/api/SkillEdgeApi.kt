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
    @GET("api/data/team-capability")
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

    /** RMS skill register for one trainer — the read-back behind a skill write. */
    @GET("api/data/trainer-skills")
    suspend fun getTrainerSkills(@Query("email") email: String): Map<String, Any>

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
    @GET("api/actions")
    suspend fun getActions(@Query("email") email: String): Map<String, Any>

    /** Raise an action by hand (anything RMS cannot infer). */
    @POST("api/actions")
    suspend fun raiseAction(@Body body: Map<String, String>): Map<String, Any>

    /** Move an action through its lifecycle, optionally with a note. */
    @POST("api/actions/{id}/state")
    suspend fun setActionState(
        @Path("id") id: String,
        @Body body: Map<String, String>,
    ): Map<String, Any>

    /** Append a follow-up note without changing state. */
    @POST("api/actions/{id}/note")
    suspend fun addActionNote(
        @Path("id") id: String,
        @Body body: Map<String, String>,
    ): Map<String, Any>
}

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
