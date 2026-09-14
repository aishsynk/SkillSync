package com.example.skillsync.core.data

import com.example.skillsync.core.network.RetrofitClient
import com.example.skillsync.core.network.SkillEdgeApi
import com.example.skillsync.core.network.SkillRequestResolve

/**
 * Pending reportee skill-level elevation requests
 * (`GET/POST /api/v2/manager/skill-requests`). Its own small repository,
 * not another method on [ManagerRepository] — request-approval workflow is
 * a distinct domain from manager/team intelligence reads.
 */
open class SkillRequestsRepository(
    private val apiProvider: () -> SkillEdgeApi = { RetrofitClient.instance },
) {
    private val api: SkillEdgeApi by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { apiProvider() }

    // open: SkillRequestsViewModelTest fakes these directly rather than
    // implementing the entire SkillEdgeApi Retrofit interface for two methods.
    open suspend fun pending(status: String = "pending"): Map<String, Any> = api.skillRequests(status)

    open suspend fun resolve(id: String, approve: Boolean): Map<String, Any> =
        api.resolveSkillRequest(id, SkillRequestResolve(if (approve) "approve" else "deny"))
}
