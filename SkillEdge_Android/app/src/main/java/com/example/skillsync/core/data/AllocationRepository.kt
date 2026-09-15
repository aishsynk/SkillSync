package com.example.skillsync.core.data

import com.example.skillsync.core.network.AllocationApi
import com.example.skillsync.core.network.AllocationCandidatesResponse
import com.example.skillsync.core.network.RetrofitClient

/**
 * Allocation-recommendation domain — the fully gated candidate evaluation
 * for one batch (`GET api/v2/allocation/candidates`, backend
 * `evaluate_candidate`/`_evaluate_team_against_batch`): hard eligibility
 * gates (DNC, real availability, travel window, skill level, visa, mock)
 * evaluated first, then a weighted, explainable fit score for survivors.
 *
 * Deliberately its own repository, not folded into [TrainerRepository],
 * following the same reasoning already applied to
 * `EligibilityRepository` for the structurally identical
 * `GET api/v2/eligibility/batch`: this is a cross-cutting recommendation
 * that combines trainer capability, availability, certification and travel
 * for one specific batch — not a fact owned by the trainer. A repository
 * that returns trainer-shaped data is not automatically Trainer-domain;
 * ownership follows the operation's semantics (an allocation decision),
 * not the shape of its response.
 *
 * Consumes [AllocationApi] (Phase 4, `docs/phase4-api-ownership-matrix.md`) —
 * its own independent increment, kept separate from `TrainerApi`/`BatchApi`
 * per explicit instruction, since allocation ownership is the one boundary
 * Phase 3 had to correct once already.
 */
open class AllocationRepository(
    private val apiProvider: () -> AllocationApi = { RetrofitClient.create() },
) {
    private val api: AllocationApi by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { apiProvider() }

    open suspend fun candidates(
        manager: String, course: String, start: String, end: String,
        country: String = "", customer: String = "",
        deliveryMode: String = "", international: String = "",
    ): AllocationCandidatesResponse = api.getAllocationCandidates(
        manager = manager, course = course, start = start, end = end,
        country = country, customer = customer,
        deliveryMode = deliveryMode, international = international,
    )
}
