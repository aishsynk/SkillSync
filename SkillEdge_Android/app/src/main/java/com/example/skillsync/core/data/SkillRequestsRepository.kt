package com.example.skillsync.core.data

import com.example.skillsync.core.network.RetrofitClient
import com.example.skillsync.core.network.SkillRequestResolve
import com.example.skillsync.core.network.SkillRequestsApi

/**
 * Pending reportee skill-level elevation requests
 * (`GET/POST /api/v2/manager/skill-requests`). Its own small repository,
 * not another method on [ManagerRepository] — request-approval workflow is
 * a distinct domain from manager/team intelligence reads.
 *
 * Consumes [SkillRequestsApi] (Phase 4, `docs/phase4-api-ownership-matrix.md`).
 */
open class SkillRequestsRepository(
    private val apiProvider: () -> SkillRequestsApi = { RetrofitClient.create() },
) {
    private val api: SkillRequestsApi by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { apiProvider() }

    // open: SkillRequestsViewModelTest fakes these directly rather than
    // implementing the entire SkillRequestsApi Retrofit interface for two methods.
    open suspend fun pending(status: String = "pending"): Map<String, Any> = api.skillRequests(status)

    open suspend fun resolve(id: String, approve: Boolean): Map<String, Any> =
        api.resolveSkillRequest(id, SkillRequestResolve(if (approve) "approve" else "deny"))
}
