package com.example.skillsync.core.network

import retrofit2.http.GET
import retrofit2.http.Query

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

/**
 * Allocation-recommendation domain — see [com.example.skillsync.core.data.AllocationRepository]
 * for why this stays separate from [TrainerApi] and [BatchApi] (the demand board's
 * `getAllocationDesk` overlay vs. this route's fully gated per-batch evaluation).
 */
interface AllocationApi {
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
}
