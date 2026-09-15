package com.example.skillsync.core.network

import retrofit2.http.GET
import retrofit2.http.Query

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

/** Batch/delivery domain — see [com.example.skillsync.core.data.BatchRepository]. */
interface BatchApi {
    /** Server-composed allocation broadcast for one batch: { plain, html, viber }.
     *  Kept server-side so the wording can change without an app release. */
    @GET("api/data/batch-message")
    suspend fun getBatchMessage(
        @Query("demand_id") demandId: String,
        @Query("recipient") recipient: String? = null,
    ): Map<String, Any>

    /** Authenticated Version 2 operational evidence for one demand. */
    @GET("api/v2/operations/demand-context")
    suspend fun getDemandContext(
        @Query("manager") manager: String,
        @Query("demandId") demandId: String,
        @Query("courseName") courseName: String,
    ): DemandContextResponse
}
