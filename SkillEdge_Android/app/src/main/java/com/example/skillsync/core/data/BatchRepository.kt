package com.example.skillsync.core.data

import com.example.skillsync.core.network.RetrofitClient
import com.example.skillsync.core.network.SkillEdgeApi

/**
 * Batch/delivery-domain data — its own small repository rather than a method
 * bolted onto [ManagerRepository], following the [ScheduleRepository]/
 * [SkillRequestsRepository] convention.
 *
 * Starts with the one confirmed batch-domain violation
 * (`BatchDetailScreen.kt` calling `RetrofitClient.instance.getBatchMessage`
 * directly): the server-composed allocation broadcast text for a batch
 * (`GET api/data/batch-message`). This is read-only, no local cache — the
 * screen already holds its own short-lived `serverMsg` UI state and falls
 * back to `BatchShare`'s local composition on failure, both unchanged by
 * this move.
 *
 * Deliberately does not yet own other batch endpoints (e.g. demand context,
 * allocation candidates) — those are evaluated for domain ownership
 * individually as their callers are migrated, not bulk-added here.
 */
open class BatchRepository(
    private val apiProvider: () -> SkillEdgeApi = { RetrofitClient.instance },
) {
    private val api: SkillEdgeApi by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { apiProvider() }

    open suspend fun batchMessage(demandId: String, recipient: String?): Map<String, Any> =
        api.getBatchMessage(demandId, recipient)
}
