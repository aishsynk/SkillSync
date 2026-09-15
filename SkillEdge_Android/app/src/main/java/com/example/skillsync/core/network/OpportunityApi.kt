package com.example.skillsync.core.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Opportunity Guardian domain — see [com.example.skillsync.core.data.ManagerRepository],
 * which composes this alongside [SkillEdgeApi]/[CourseApi]/[CommunicationApi]. `matchOpportunity`
 * reads the skill/capability graph to score a match; it consumes capability data, it does not own
 * a second copy of it. `getSkillProfile`/`updateSkillProfile` are the *manager's own* skill
 * profile for opportunity matching — distinct from trainer capability/course-skill taxonomy,
 * which stays backend-side and is consumed, not owned, here.
 */
interface OpportunityApi {
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
