package com.example.skillsync.core.network

import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Body
import retrofit2.http.Query

/**
 * The remaining, not-yet-domain-split endpoints. Auth, Trainer, Batch,
 * Eligibility, Schedule, Skill-requests and Allocation have already moved to
 * their own domain interfaces (Phase 4, `docs/phase4-api-ownership-matrix.md`,
 * migration status table tracks the rest).
 */
interface SkillEdgeApi {
    @GET("api/v2/notifications")
    suspend fun notifications(): Map<String, Any>

    @GET("api/v2/reportee/home")
    suspend fun reporteeHome(): Map<String, Any>

    @GET("api/v2/reportee/demand")
    suspend fun reporteeDemand(): Map<String, Any>

    @GET("api/v2/reportee/calendar")
    suspend fun reporteeCalendar(): Map<String, Any>

    @POST("api/v2/reportee/message")
    suspend fun reporteeMessage(@Body body: Map<String, String>): Map<String, Any>

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

    /** Real leave and commitments for every reportee, one row each. */
    @GET("api/v2/team/readiness")
    suspend fun getTeamReadiness(@Query("manager") manager: String): Map<String, Any>

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

    /** V2 Enrolled Participant Roster (Key 208) */
    @GET("api/v2/operations/batch-pax")
    suspend fun getBatchPax(
        @Query("assignmentId") assignmentId: String,
    ): Map<String, Any>


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

    // ── Opportunity Guardian ──────────────────────────────────────

    /** Get the manager's Opportunity Guardian configuration. */
    @GET("api/v2/opportunity/guardian-config")
    suspend fun getGuardianConfig(@Query("manager") manager: String): Map<String, Any>

    /** Update the manager's Opportunity Guardian configuration. */
    @POST("api/v2/opportunity/guardian-config")
    suspend fun updateGuardianConfig(@Body body: Map<String, Any>): Map<String, Any>

    /** List opportunities with optional status filter. */
    @GET("api/v2/opportunities")
    suspend fun getOpportunities(
        @Query("manager") manager: String,
        @Query("status") status: String = "",
    ): Map<String, Any>

    /** Create a new opportunity from a detected message. */
    @POST("api/v2/opportunities")
    suspend fun createOpportunity(@Body body: Map<String, Any>): Map<String, Any>

    /** Accept an opportunity. */
    @POST("api/v2/opportunities/{id}/accept")
    suspend fun acceptOpportunity(@Path("id") id: String): Map<String, Any>

    /** Decline an opportunity. */
    @POST("api/v2/opportunities/{id}/decline")
    suspend fun declineOpportunity(@Path("id") id: String): Map<String, Any>

    /** Advance the documentation state of an opportunity (none/mentioned/requested/shared/reviewed). */
    @POST("api/v2/opportunities/{id}/document")
    suspend fun updateOpportunityDocument(@Path("id") id: String, @Body body: Map<String, Any>): Map<String, Any>

    /** Match a skill profile against an opportunity and return scoring. */
    @POST("api/v2/opportunity/match")
    suspend fun matchOpportunity(@Body body: Map<String, Any>): Map<String, Any>

    /** Get the manager's skill profile and capability graph. */
    @GET("api/v2/skill-profile")
    suspend fun getSkillProfile(@Query("manager") manager: String): Map<String, Any>

    /** Update the manager's skill profile. */
    @POST("api/v2/skill-profile")
    suspend fun updateSkillProfile(@Body body: Map<String, Any>): Map<String, Any>

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

