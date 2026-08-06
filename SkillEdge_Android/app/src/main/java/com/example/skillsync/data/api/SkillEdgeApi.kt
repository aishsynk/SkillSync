package com.example.skillsync.data.api

import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Body
import retrofit2.http.Query
import com.google.gson.annotations.SerializedName

data class LoginRequest(@SerializedName("email") val username: String, val password: String = "")
data class LoginResponse(val email: String?, val error: String?)

interface SkillEdgeApi {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse
    
    @GET("data/unified-manager-intelligence")
    suspend fun getTrainerIntelligence(@Query("email") email: String): Map<String, Any>
}
