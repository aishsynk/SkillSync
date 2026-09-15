package com.example.skillsync.core.network

import retrofit2.http.GET
import retrofit2.http.Query

/** Certification/eligibility domain — see [com.example.skillsync.core.data.EligibilityRepository]. */
interface EligibilityApi {
    @GET("api/v2/eligibility/batch")
    suspend fun getBatchEligibility(
        @Query("manager") manager: String,
        @Query("demand_id") id: String,
    ): Map<String, Any>
}
