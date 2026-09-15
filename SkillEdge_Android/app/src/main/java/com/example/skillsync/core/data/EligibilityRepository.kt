package com.example.skillsync.core.data

import com.example.skillsync.core.network.EligibilityApi
import com.example.skillsync.core.network.RetrofitClient

/**
 * Certification/eligibility — its own domain, not Batch or Trainer: this is a
 * cross-cutting, backend-authoritative evaluation (trainer capability +
 * certification + batch requirement + availability, combined server-side by
 * `GET api/v2/eligibility/batch`). The Android side never re-derives this
 * result; it only displays it and offers the one write the manager is
 * allowed to make (marking a skill), which lands through the existing
 * skill-request write path, not this repository.
 *
 * Consumes [EligibilityApi] (Phase 4, `docs/phase4-api-ownership-matrix.md`).
 */
open class EligibilityRepository(
    private val apiProvider: () -> EligibilityApi = { RetrofitClient.create() },
) {
    private val api: EligibilityApi by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { apiProvider() }

    open suspend fun batchEligibility(managerEmail: String, demandId: String): Map<String, Any> =
        api.getBatchEligibility(managerEmail, demandId)
}
