package com.example.skillsync.core.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

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

/**
 * Trainer domain — facts the trainer themself owns, matching
 * [com.example.skillsync.core.data.TrainerRepository]'s scope exactly.
 * Deliberately conservative for this first increment: only the endpoints
 * `TrainerRepository` already consumed pre-split (`markSkill`,
 * `getTrainer360`, the dev-plan cluster and `getTrainerUtilizationHistory`
 * stay on `ManagerRepository`/`ManagerApi` for now — moving *repository*
 * ownership, not just the transport interface, is a separate, larger
 * decision tracked in `docs/phase4-api-ownership-matrix.md`).
 */
interface TrainerApi {
    @GET("api/v2/trainer/feedback-log")
    suspend fun trainerFeedbackLog(@Query("email") email: String): Map<String, Any>

    @GET("api/v2/trainer/recordings")
    suspend fun trainerRecordings(@Query("email") email: String): Map<String, Any>

    /** V2 Wider Network & Freelance Trainers (Key 70 / API 157) */
    @GET("api/v2/network/trainers")
    suspend fun getNetworkTrainers(
        @Query("course") course: String,
        @Query("trainerType") trainerType: String = "",
    ): Map<String, Any>

    /** Wider trainer network for a course. Returns { available, trainers }.
     *  `available` is false while RMS rejects every TrainerType value — that
     *  is "cannot ask", not "nobody found". */
    @GET("api/data/alternative-trainers")
    suspend fun getAlternativeTrainers(@Query("course") course: String): Map<String, Any>

    /** "Learner Voice & Sentiment" — keyword clouds, praise ratio, and categorized verbatim quotes. */
    @GET("api/v2/trainer/sentiment")
    suspend fun getTrainerSentiment(@Query("trainer_email") email: String): Map<String, Any>

    /** V2 20-Criteria Koenig HR Trainer Index (TI – 13/08/26) */
    @GET("api/v2/trainer/trainer-index")
    suspend fun getTrainerIndex(
        @Query("email") email: String,
        @Query("month") month: String? = null,
    ): TrainerIndexResponseDto

    /** Real leave, commitments and certification state for one trainer. */
    @GET("api/v2/trainer/readiness")
    suspend fun getTrainerReadiness(
        @Query("manager") manager: String,
        @Query("email") email: String,
    ): Map<String, Any>

    /** Server-composed "please build this skill" ask for one trainer: { plain, html }. */
    @GET("api/data/upskill-message")
    suspend fun getUpskillMessage(
        @Query("course") course: String,
        @Query("trainer_name") trainerName: String? = null,
        @Query("level") level: String? = null,
        @Query("ready_by") readyBy: String? = null,
        @Query("batches") batches: String? = null,
    ): Map<String, Any>

    /** "1-Tap IDP Skill Endorsement" — write verified skill directly to RMS with audit trail. */
    @POST("api/v2/skills/endorse")
    suspend fun endorseSkill(@Body body: Map<String, Any>): Map<String, Any>

    /** One skill to many reportees; each row reports its own outcome. */
    @POST("api/v2/skills/bulk-assign")
    suspend fun bulkAssignSkill(@Body request: BulkAssignRequest): BulkAssignResponse
}
