package com.example.skillsync.core.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class SkillRequestResolve(val decision: String)

/** Skill-request-approval domain — see [com.example.skillsync.core.data.SkillRequestsRepository]. */
interface SkillRequestsApi {
    @GET("api/v2/manager/skill-requests")
    suspend fun skillRequests(@Query("status") status: String = "pending"): Map<String, Any>

    @POST("api/v2/manager/skill-requests/{id}")
    suspend fun resolveSkillRequest(
        @Path("id") id: String,
        @Body body: SkillRequestResolve,
    ): Map<String, Any>
}
