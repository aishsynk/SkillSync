package com.example.skillsync.data.api

import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Body
import retrofit2.http.Path

data class LoginRequest(val username: String, val password: String = "")
data class LoginResponse(val success: Boolean, val token: String?, val message: String?)

interface SkillEdgeApi {
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse
    
    @GET("api/intelligence/trainer/{id}")
    suspend fun getTrainerIntelligence(@Path("id") trainerId: String): Map<String, Any>
}
