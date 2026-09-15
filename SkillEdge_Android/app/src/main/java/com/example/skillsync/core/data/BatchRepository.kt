package com.example.skillsync.core.data

import com.example.skillsync.core.network.DemandContextResponse
import com.example.skillsync.core.network.RetrofitClient
import com.example.skillsync.core.network.SkillEdgeApi

/**
 * Batch/delivery-domain data — its own small repository rather than a method
 * bolted onto [ManagerRepository], following the [ScheduleRepository]/
 * [SkillRequestsRepository] convention.
 *
 * Owns the server-composed allocation broadcast text for a batch (`GET
 * api/data/batch-message`) and per-demand operational evidence (`GET
 * api/v2/operations/demand-context`) — both read-only, no local cache.
 * `BatchDetailScreen.kt`'s `serverMsg` and `AllocationViewModel.kt`'s
 * `demandContext` remain the screens' own short-lived UI state, unchanged
 * by this move.
 *
 * Deliberately does not yet own every batch-adjacent endpoint (e.g.
 * allocation candidates, which is Trainer/candidate domain and lives in
 * [TrainerRepository]) — each is evaluated for domain ownership
 * individually as its caller is migrated, not bulk-added here.
 */
open class BatchRepository(
    private val apiProvider: () -> SkillEdgeApi = { RetrofitClient.instance },
) {
    private val api: SkillEdgeApi by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { apiProvider() }

    open suspend fun batchMessage(demandId: String, recipient: String?): Map<String, Any> =
        api.getBatchMessage(demandId, recipient)

    open suspend fun demandContext(manager: String, demandId: String, courseName: String): DemandContextResponse =
        api.getDemandContext(manager, demandId, courseName)
}
