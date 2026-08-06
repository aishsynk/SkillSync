package com.example.skillsync.data.api

import retrofit2.http.GET
import retrofit2.http.POST
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

    @GET("api/data/unified-manager-intelligence")
    suspend fun getTrainerIntelligence(@Query("email") email: String): Map<String, Any>

    /** Deep single-trainer profile; two extra RMS round-trips, so fetched on demand. */
    @GET("api/data/trainer-360")
    suspend fun getTrainer360(@Query("email") email: String): Map<String, Any>

    /** Unallocated batches ranked against this manager's team capability. */
    @GET("api/data/allocation-desk")
    suspend fun getAllocationDesk(@Query("email") email: String): Map<String, Any>

    /** Writes a skill record to production RMS. */
    @POST("api/action/mark-skill")
    suspend fun markSkill(@Body request: MarkSkillRequest): MarkSkillResponse
}

data class MarkSkillRequest(
    val course_id: String,
    val trainer_email: String,
    val skill_level: Int,
    val from_date: String,
    val officially_approved: String = "No",
)

data class MarkSkillResponse(
    val success: Boolean?,
    val trainer_email: String?,
    val course_id: String?,
    val skill_level: Int?,
    val from_date: String?,
    val error: String?,
)
