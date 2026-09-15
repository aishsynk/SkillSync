package com.example.skillsync.core.data

import com.example.skillsync.core.network.AuthApi
import com.example.skillsync.core.network.AuthCheckResponse
import com.example.skillsync.core.network.LoginRequest
import com.example.skillsync.core.network.LoginResponse
import com.example.skillsync.core.network.RetrofitClient
import com.example.skillsync.core.network.SetPasswordRequest

/**
 * Sign-in/authentication — its own small repository rather than a method on
 * [ManagerRepository], which owns manager/team-intelligence domains; auth
 * happens before a manager session even exists. Same
 * apiProvider-default/`open` convention as [ScheduleRepository]/
 * [SkillRequestsRepository] so a test can fake it directly instead of
 * implementing the whole [AuthApi] interface.
 *
 * Consumes [AuthApi] (Phase 4, `docs/phase4-api-ownership-matrix.md`) rather
 * than the full `SkillEdgeApi` — the first domain interface split out of it.
 */
open class AuthRepository(
    private val apiProvider: () -> AuthApi = { RetrofitClient.create() },
) {
    private val api: AuthApi by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { apiProvider() }

    open suspend fun authCheck(request: LoginRequest): AuthCheckResponse = api.authCheck(request)
    open suspend fun login(request: LoginRequest): LoginResponse = api.login(request)
    open suspend fun setPassword(request: SetPasswordRequest): Map<String, Any> = api.setPassword(request)
    open suspend fun logout(): Map<String, Any> = api.logout()
}
