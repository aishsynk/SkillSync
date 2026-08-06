package com.example.skillsync.data.api

import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Body
import retrofit2.http.Query
import com.google.gson.annotations.SerializedName

data class LoginRequest(val email: String)
data class LoginResponse(val success: Boolean?, val session_id: String?, val error: String?, val message: String?)

interface SkillEdgeApi {
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse
    
    @GET("api/data/unified-manager-intelligence")
    suspend fun getTrainerIntelligence(@Query("email") email: String): Map<String, Any>
}
