package com.example.skillsync.core.data

import com.example.skillsync.core.network.AuthCheckResponse
import com.example.skillsync.core.network.LoginRequest
import com.example.skillsync.core.network.LoginResponse
import com.example.skillsync.core.network.RetrofitClient
import com.example.skillsync.core.network.SetPasswordRequest
import com.example.skillsync.core.network.SkillEdgeApi

/**
 * Sign-in/authentication — its own small repository rather than a method on
 * [ManagerRepository], which owns manager/team-intelligence domains; auth
 * happens before a manager session even exists. Same
 * apiProvider-default/`open` convention as [ScheduleRepository]/
 * [SkillRequestsRepository] so a test can fake it directly instead of
 * implementing the whole [SkillEdgeApi] interface.
 */
open class AuthRepository(
    private val apiProvider: () -> SkillEdgeApi = { RetrofitClient.instance },
) {
    private val api: SkillEdgeApi by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { apiProvider() }

    open suspend fun authCheck(request: LoginRequest): AuthCheckResponse = api.authCheck(request)
    open suspend fun login(request: LoginRequest): LoginResponse = api.login(request)
    open suspend fun setPassword(request: SetPasswordRequest): Map<String, Any> = api.setPassword(request)
    open suspend fun logout(): Map<String, Any> = api.logout()
}
