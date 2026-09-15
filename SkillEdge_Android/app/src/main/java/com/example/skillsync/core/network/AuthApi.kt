package com.example.skillsync.core.network

import retrofit2.http.Body
import retrofit2.http.POST

data class LoginRequest(val email: String, val password: String? = null)

data class LoginResponse(
    val success: Boolean?,
    val session_id: String?,
    val email: String?,
    val role: String?,
    val code: String?,
    val manager_email: String?,
    val must_change: Boolean?,
    val error: String?,
    val message: String?,
)

data class AuthCheckResponse(
    val ok: Boolean?,
    val email: String?,
    val role: String?,
    val name: String?,
    val needs_password: Boolean?,
    val first_login: Boolean?,
    val error: String?,
)

data class SetPasswordRequest(val new_password: String)

/** Sign-in/authentication domain — see [com.example.skillsync.core.data.AuthRepository]. */
interface AuthApi {
    @POST("api/auth/check")
    suspend fun authCheck(@Body request: LoginRequest): AuthCheckResponse

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("api/auth/set-password")
    suspend fun setPassword(@Body request: SetPasswordRequest): Map<String, Any>

    @POST("api/auth/logout")
    suspend fun logout(): Map<String, Any>
}
