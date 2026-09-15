package com.example.skillsync.core.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

data class ComposeMessageResponse(
    val message: String = "",
    val scope: String = "",
    val cadence: String = "weekly",
    val target: String = "",
    val length: Int = 0,
    val error: String? = null,
)

/**
 * Communication Intelligence domain — see
 * [com.example.skillsync.feature.communication.domain.CommunicationRepository]
 * (`composeMessage`) and [com.example.skillsync.core.data.ManagerRepository]
 * (`generateCommunication`/`saveCommunication`/`communicationHistory`, still
 * composed alongside `ManagerApi` there rather than in a dedicated
 * repository, following the [CourseApi] pattern).
 */
interface CommunicationApi {
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

    /** Generate one professional message from a Communication request. */
    @POST("api/v2/communication/generate")
    suspend fun generateCommunication(@Body body: Map<String, Any>): Map<String, Any>

    /** Persist a generated/sent communication with structured metadata. */
    @POST("api/v2/communication/save")
    suspend fun saveCommunication(@Body body: Map<String, Any>): Map<String, Any>

    /** Structured history of generated messages for one manager. */
    @GET("api/v2/communication/history")
    suspend fun communicationHistory(@Query("manager") manager: String): Map<String, Any>
}
