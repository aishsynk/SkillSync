package com.example.skillsync.data.api

import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Body
import retrofit2.http.Query

data class LoginRequest(val email: String, val password: String? = null)

data class LoginResponse(
    val success: Boolean?,
    val session_id: String?,
    val email: String?,
    val role: String?,
    val code: String?,
    val manager_email: String?,
    val must_change: Boolean?,
    val error: String?,
    val message: String?,
)

data class AuthCheckResponse(
    val ok: Boolean?,
    val email: String?,
    val role: String?,
    val name: String?,
    val needs_password: Boolean?,
    val first_login: Boolean?,
    val error: String?,
)

data class SetPasswordRequest(val new_password: String)

data class SkillRequestResolve(val decision: String)

interface SkillEdgeApi {
    @POST("api/auth/check")
    suspend fun authCheck(@Body request: LoginRequest): AuthCheckResponse

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("api/auth/set-password")
    suspend fun setPassword(@Body request: SetPasswordRequest): Map<String, Any>

    @GET("api/v2/notifications")
    suspend fun notifications(): Map<String, Any>

    @GET("api/v2/trainer/feedback-log")
    suspend fun trainerFeedbackLog(@Query("email") email: String): Map<String, Any>

    @GET("api/v2/trainer/recordings")
    suspend fun trainerRecordings(@Query("email") email: String): Map<String, Any>

    @GET("api/v2/trainer/calendar")
    suspend fun trainerCalendar(@Query("email") email: String): Map<String, Any>

    @GET("api/v2/reportee/home")
    suspend fun reporteeHome(): Map<String, Any>

    @GET("api/v2/reportee/demand")
    suspend fun reporteeDemand(): Map<String, Any>

    @GET("api/v2/reportee/calendar")
    suspend fun reporteeCalendar(): Map<String, Any>

    @POST("api/v2/reportee/message")
    suspend fun reporteeMessage(@Body body: Map<String, String>): Map<String, Any>

    @GET("api/v2/manager/skill-requests")
    suspend fun skillRequests(@Query("status") status: String = "pending"): Map<String, Any>

    @POST("api/v2/manager/skill-requests/{id}")
    suspend fun resolveSkillRequest(
        @Path("id") id: String,
        @Body body: SkillRequestResolve,
    ): Map<String, Any>

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

    /** Certification calendar + demand-led certification ranking for a manager. */
    @GET("api/v2/capability/cert-intel")
    suspend fun getCertIntel(@Query("email") m: String): Map<String, Any>

    /** Unallocated batches ranked against this manager's team capability. */
    @GET("api/data/allocation-desk")
    suspend fun getAllocationDesk(
        @Query("email") email: String,
        @Query("refresh") refresh: Int? = null,
    ): Map<String, Any>

    /** Demand-led upskilling opportunities correlated against team competency. */
    @GET("api/v2/upskilling/demand-opportunities")
    suspend fun getDemandUpskillingOpportunities(
        @Query("manager") manager: String? = null,
    ): Map<String, Any>

    /** Authenticated Version 2 operational evidence for one demand. */
    @GET("api/v2/operations/demand-context")
    suspend fun getDemandContext(
        @Query("manager") manager: String,
        @Query("demandId") demandId: String,
        @Query("courseName") courseName: String,
    ): DemandContextResponse

    /**
     * Fully gated candidate evaluation for one batch.
     *
     * Distinct from the demand board, which overlays availability but cannot
     * afford the per-trainer calls that client exclusions and leave require.
     * This route applies every hard gate — DNC, leave, confirmed bookings,
     * skill floor and visa — and returns the per-factor breakdown behind each
     * score. Returns 422 when the course cannot be resolved, which means
     * "could not verify", never "nobody is available".
     */
    /** One skill to many reportees; each row reports its own outcome. */
    @POST("api/v2/skills/bulk-assign")
    suspend fun bulkAssignSkill(@Body request: BulkAssignRequest): BulkAssignResponse

    /** Real leave and commitments for every reportee, one row each. */
    @GET("api/v2/team/readiness")
    suspend fun getTeamReadiness(@Query("manager") manager: String): Map<String, Any>

    /** Real leave, commitments and certification state for one trainer. */
    @GET("api/v2/trainer/readiness")
    suspend fun getTrainerReadiness(
        @Query("manager") manager: String,
        @Query("email") email: String,
    ): Map<String, Any>

    @GET("api/v2/allocation/candidates")
    suspend fun getAllocationCandidates(
        @Query("manager") manager: String,
        @Query("course") course: String,
        @Query("start") start: String,
        @Query("end") end: String,
        @Query("country") country: String = "",
        @Query("customer") customer: String = "",
        @Query("delivery_mode") deliveryMode: String = "",
        @Query("international") international: String = "",
    ): AllocationCandidatesResponse

    @GET("api/v2/eligibility/batch")
    suspend fun getBatchEligibility(
        @Query("manager") manager: String,
        @Query("demand_id") id: String,
    ): Map<String, Any>

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

    /** V2 Course Curriculum: Modules, lab URLs, TOC, public schedules (Keys 206, 156, 246, 248) */
    @GET("api/v2/course/curriculum")
    suspend fun getCourseCurriculum(
        @Query("courseName") courseName: String = "",
        @Query("courseId") courseId: String = "",
    ): Map<String, Any>

    /** V2 Wider Network & Freelance Trainers (Key 70 / API 157) */
    @GET("api/v2/network/trainers")
    suspend fun getNetworkTrainers(
        @Query("course") course: String,
        @Query("trainerType") trainerType: String = "",
    ): Map<String, Any>

    /** V2 20-Criteria Koenig HR Trainer Index (TI – 13/08/26) */
    @GET("api/v2/trainer/trainer-index")
    suspend fun getTrainerIndex(
        @Query("email") email: String,
        @Query("month") month: String? = null,
    ): TrainerIndexResponseDto

    /** V2 Enrolled Participant Roster (Key 208) */
    @GET("api/v2/operations/batch-pax")
    suspend fun getBatchPax(
        @Query("assignmentId") assignmentId: String,
    ): Map<String, Any>


    /** Ask Copilot a question */
    @POST("api/agent/ask")
    suspend fun agentAsk(
        @Body request: AgentAskRequest
    ): AgentAskResponse

    /** Ask the team-level Copilot — opened without a specific trainer target.
     *  Body: { manager, question }  or  { manager, question_key }. */
    @POST("api/v2/copilot/team")
    suspend fun askCopilotTeam(
        @Body body: Map<String, String>
    ): Map<String, Any>

    @POST("api/v2/message/rewrite")
    suspend fun rewriteMessage(
        @Body request: RewriteRequest
    ): RewriteResponse

    /**
     * The house-style weekly/monthly message for a reportee (pass [target]) or
     * the team (omit [target]), composed from the analysed data with an
     * optional manager note ([myMessage]) woven in.
     */
    @GET("api/v2/message/compose")
    suspend fun composeMessage(
        @Query("manager") manager: String,
        @Query("cadence") cadence: String = "weekly",
        @Query("target") target: String = "",
        @Query("my_message") myMessage: String = "",
    ): ComposeMessageResponse

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

    @GET("api/v2/hr/monthly-report")
    suspend fun getHrMonthlyReport(
        @Query("manager") manager: String,
        @Query("month") month: String,
    ): Map<String, Any>

    /** "Your Week" — one ranked worklist: unstaffed batches by deadline, 1:1s
     *  needed, overloaded trainers, cert gaps, overdue actions. */
    @GET("api/v2/manager/priorities")
    suspend fun getManagerPriorities(@Query("manager") manager: String): Map<String, Any>

    /** "Capacity Runway" — next 8 weeks of incoming demand vs the team's free
     *  capacity per week, the gap, and a ranked upskilling list. */
    @GET("api/v2/planning/runway")
    suspend fun getCapacityRunway(@Query("manager") manager: String): Map<String, Any>

    /** "How your team compares" — team health vs an honest, documented baseline
     *  (no fabricated peer-manager average). */
    @GET("api/v2/benchmark")
    suspend fun getBenchmark(@Query("manager") m: String): Map<String, Any>

    /** Proactive digest: kind = "morning" (day-start brief) or "weekly" (Fri wrap). */
    @GET("api/v2/digest")
    suspend fun getDigest(
        @Query("manager") manager: String,
        @Query("kind") kind: String,
    ): Map<String, Any>

    /** "New trainer ramp" — onboarding progress for reportees who joined <12mo ago. */
    @GET("api/v2/ramp")
    suspend fun getRamp(@Query("manager") manager: String): Map<String, Any>

    /** V2 Weekly Delivery & Operations Intelligence Snapshot */
    @GET("api/v2/report/weekly")
    suspend fun getWeeklyReport(
        @Query("manager") manager: String,
        @Query("week") week: String? = null,
    ): Map<String, Any>

    /** Monthly delivery matrix with day-level active delivering trainers and leaves. */
    @GET("api/v2/team/calendar")
    suspend fun getCalendar(
        @Query("manager") manager: String,
        @Query("month") month: String? = null,
    ): Map<String, Any>

    /** Cross-domain peer benchmarking and upskilling roadmaps for a reportee. */
    @GET("api/v2/trainer/growth-benchmark")
    suspend fun getGrowthBenchmark(
        @Query("email") email: String,
        @Query("manager") manager: String,
    ): Map<String, Any>

    /** Dedicated deep-dive evaluation and mock assessment for a trainer. */
    @GET("api/v2/trainer/evaluation")
    suspend fun getTrainerEvaluation(
        @Query("email") email: String,
        @Query("month") month: String? = null,
    ): Map<String, Any>

    /** "Accounts" — the manager's team seen through the customers they deliver
     *  for: batches delivered / upcoming / open demand per account, plus a
     *  single-account concentration signal. */
    @GET("api/v2/accounts")
    suspend fun getAccounts(@Query("manager") m: String): Map<String, Any>

    /**
     * Development plan for one reportee: stored manager-authored goals plus
     * deterministic `suggested` items (cert gaps tied to demand, a coaching
     * item on weak feedback, a portfolio item when the trainer teaches < 3
     * courses). Plan items are prep/coaching goals, never allocations.
     */
    @GET("api/v2/devplan")
    suspend fun getDevPlan(
        @Query("manager") manager: String,
        @Query("trainer") trainer: String,
    ): Map<String, Any>

    /** Create a plan item. Body: manager, trainer, title, kind, target_date?, note?. */
    @POST("api/v2/devplan/item")
    suspend fun createDevPlanItem(@Body body: Map<String, String>): Map<String, Any>

    /** Update a plan item. Body: manager, id, status?, note?, target_date?. */
    @PATCH("api/v2/devplan/item")
    suspend fun updateDevPlanItem(@Body body: Map<String, String>): Map<String, Any>

    /** "Pre-Demand Pipeline Radar" — advance Service Confirmations with lead times and candidate matching. */
    @GET("api/v2/planning/pipeline")
    suspend fun getPreDemandPipeline(@Query("manager") manager: String): Map<String, Any>

    /** "Delivery Compliance Sentinel" — checks daily recording uploads for ongoing batches across reportees. */
    @GET("api/v2/delivery/compliance")
    suspend fun getDeliveryCompliance(@Query("manager") manager: String): Map<String, Any>

    /** "1-Tap IDP Skill Endorsement" — write verified skill directly to RMS with audit trail. */
    @POST("api/v2/skills/endorse")
    suspend fun endorseSkill(@Body body: Map<String, Any>): Map<String, Any>

    /** "Learner Voice & Sentiment" — keyword clouds, praise ratio, and categorized verbatim quotes. */
    @GET("api/v2/trainer/sentiment")
    suspend fun getTrainerSentiment(@Query("trainer_email") email: String): Map<String, Any>

    /** "Viber Background Automation Queue" — fetches pre-composed candidate messages for demand, standpoints, and alerts. */
    @GET("api/v2/viber/queue")
    suspend fun getViberQueue(@Query("manager") manager: String): Map<String, Any>

    /** "Viber Background Automation Dispatch" — dispatches one or more messages to Viber. */
    @POST("api/v2/viber/dispatch")
    suspend fun dispatchViber(@Body body: Map<String, Any>): Map<String, Any>

    /** "Viber Automation Preferences" — gets manager automation settings. */
    @GET("api/v2/viber/config")
    suspend fun getViberConfig(@Query("manager") manager: String): Map<String, Any>

    /** "Viber Automation Preferences" — updates manager automation settings. */
    @POST("api/v2/viber/config")
    suspend fun updateViberConfig(@Body body: Map<String, Any>): Map<String, Any>
}

data class StructuredFeedbackDto(
    val strength: String = "",
    @com.google.gson.annotations.SerializedName("area_of_improvement") val areaOfImprovement: String = "",
    @com.google.gson.annotations.SerializedName("other_feedback") val otherFeedback: String = "",
    val trajectory: String = "Improving",
    val sentiment: String = "Constructive",
    @com.google.gson.annotations.SerializedName("mock_summary") val mockSummary: String = "",
    @com.google.gson.annotations.SerializedName("formatted_text") val formattedText: String = "",
)

data class DemandCourseContext(
    val name: String = "",
    val verified: Boolean = false,
    @com.google.gson.annotations.SerializedName("available_in_rms") val availableInRms: Any? = null,
    val status: String = "",
    @com.google.gson.annotations.SerializedName("is_duplicate") val isDuplicate: Any? = null,
    @com.google.gson.annotations.SerializedName("is_discontinued") val isDiscontinued: Any? = null,
    @com.google.gson.annotations.SerializedName("content_url") val contentUrl: String = "",
    @com.google.gson.annotations.SerializedName("latest_version") val latestVersion: String = "",
    @com.google.gson.annotations.SerializedName("is_fast_track") val isFastTrack: Boolean = false,
)

data class ParticipantInfo(
    val name: String = "",
    val email: String = "",
    val company: String = "",
)

data class ParticipantRosterContext(
    val count: Int = 0,
    val students: List<ParticipantInfo> = emptyList(),
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
    @com.google.gson.annotations.SerializedName("participants_roster") val participantsRoster: ParticipantRosterContext = ParticipantRosterContext(),
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
    /** Reportee self-mark above level 4: queued for manager approval, not written. */
    val pending: Boolean? = null,
    val request_id: String? = null,
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

/**
 * Response of `GET /api/v2/allocation/candidates`.
 *
 * `blocked` is deliberately retained rather than filtered away: a manager needs
 * to see that a strong trainer was excluded and why, otherwise the absence
 * looks like an oversight. Field names are snake_case to match the wire format
 * Gson reads directly.
 */
data class AllocationCandidatesResponse(
    val schema_version: String = "",
    val ready: Boolean = false,
    val code: String = "",
    val message: String = "",
    val course_resolved: String = "",
    val match_confidence: String = "",
    val counts: Map<String, Double> = emptyMap(),
    val candidates: List<Map<String, Any>> = emptyList(),
    val blocked: List<Map<String, Any>> = emptyList(),
    val note: String = "",
)

data class BulkAssignRequest(
    val course_id: String,
    val trainers: List<BulkAssignRow>,
    val from_date: String = "",
    val officially_approved: String = "",
)

data class BulkAssignRow(val trainer_email: String, val skill_level: Int)

/**
 * Per-row outcomes. A bulk write against production RMS partially fails as a
 * matter of course, so there is no single success flag here by design.
 */
data class BulkAssignResponse(
    val requested: Int = 0,
    val succeeded: Int = 0,
    val failed: Int = 0,
    val results: List<BulkAssignResult> = emptyList(),
    val note: String = "",
)

data class BulkAssignResult(
    val trainer_email: String = "",
    val ok: Boolean = false,
    val verified: Boolean = false,
    val message: String = "",
)

data class TrainerIndexCriteriaDto(
    val s_no: Int = 0,
    val criteria: String = "",
    val raw_value: String = "",
    val remarks: String = "",
    val weightage: String = "",
    val capping: String = "",
    val points: Double = 0.0,
)

data class TrainerIndexDto(
    val email: String = "",
    val name: String = "",
    val total_score: Double = 0.0,
    val tier: String = "",
    val tier_badge: String = "",
    val tier_level: Int = 0,
    val tier_description: String = "",
    val is_fde_qualified: Boolean = false,
    val utilization_pts: Double = 0.0,
    val quality_pts: Double = 0.0,
    val beast_ai_pts: Double = 0.0,
    val certifications_pts: Double = 0.0,
    val instructor_pts: Double = 0.0,
    val knowledge_sharing_pts: Double = 0.0,
    val deductions_pts: Double = 0.0,
    val criteria: List<TrainerIndexCriteriaDto> = emptyList(),
    val measured_criteria: List<Int> = emptyList(),
    val estimated_criteria: List<Int> = emptyList(),
    val confidence: String = "",
    val confidence_note: String = "",
)

data class TrainerIndexResponseDto(
    val email: String = "",
    val name: String = "",
    val month: String = "",
    val trainer_index: TrainerIndexDto = TrainerIndexDto(),
    val timestamp: String = "",
)

data class RewriteRequest(
    val manager_email: String,
    val user_message: String = "",
    val my_message: String = "",
    val target_name: String = "",
    val is_team: Boolean = false,
    val style: String = "teams",
    val evidence_context: Map<String, Any>? = null,
)

data class RewriteResponse(
    val rewritten: String = "",
    val style: String = "teams",
    val length: Int = 0,
    val detected: Map<String, Any>? = null,
    val greeting: String = "",
    val error: String? = null,
)

data class ComposeMessageResponse(
    val message: String = "",
    val scope: String = "",
    val cadence: String = "weekly",
    val target: String = "",
    val length: Int = 0,
    val error: String? = null,
)

