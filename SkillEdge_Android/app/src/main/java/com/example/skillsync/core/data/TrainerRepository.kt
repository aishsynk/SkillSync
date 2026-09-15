package com.example.skillsync.core.data

import com.example.skillsync.core.network.BulkAssignRequest
import com.example.skillsync.core.network.BulkAssignResponse
import com.example.skillsync.core.network.RetrofitClient
import com.example.skillsync.core.network.SkillEdgeApi
import com.example.skillsync.core.network.TrainerIndexResponseDto

/**
 * Trainer domain — facts the trainer themself owns: practice/delivery
 * record, the wider trainer network lookup, and the trainer-skill writes
 * (1-tap endorsement and bulk one-skill-to-many-reportees assignment).
 *
 * Deliberately narrow. A response merely *containing* trainer data is not
 * by itself Trainer-domain — `GET api/v2/allocation/candidates` returns a
 * trainer-shaped list too, but it is a cross-cutting allocation
 * *recommendation* (skill + availability + certification + travel combined
 * for one batch), so it lives in [AllocationRepository] instead, the same
 * reasoning already applied to [EligibilityRepository] for the
 * structurally identical `GET api/v2/eligibility/batch`. Keep this
 * repository to trainer-owned facts and writes only, so it does not
 * gradually absorb every endpoint whose payload happens to describe a
 * trainer — that would just rebuild the god-repository this migration
 * exists to break apart.
 *
 * Follows the [AuthRepository]/[BatchRepository] convention (`apiProvider`
 * constructor default, `open suspend fun` per call so tests can fake it
 * directly).
 */
open class TrainerRepository(
    private val apiProvider: () -> SkillEdgeApi = { RetrofitClient.instance },
) {
    private val api: SkillEdgeApi by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { apiProvider() }

    open suspend fun feedbackLog(email: String): Map<String, Any> = api.trainerFeedbackLog(email)

    open suspend fun recordings(email: String): Map<String, Any> = api.trainerRecordings(email)

    open suspend fun networkTrainers(course: String): Map<String, Any> = api.getNetworkTrainers(course = course)

    open suspend fun sentiment(email: String): Map<String, Any> = api.getTrainerSentiment(email)

    open suspend fun trainerIndex(email: String): TrainerIndexResponseDto = api.getTrainerIndex(email)

    open suspend fun readiness(manager: String, email: String): Map<String, Any> =
        api.getTrainerReadiness(manager, email)

    open suspend fun endorseSkill(body: Map<String, Any>): Map<String, Any> = api.endorseSkill(body)

    open suspend fun alternativeTrainers(course: String): Map<String, Any> = api.getAlternativeTrainers(course)

    open suspend fun bulkAssignSkill(request: BulkAssignRequest): BulkAssignResponse = api.bulkAssignSkill(request)

    /** Server-composed "please build this skill" ask for one trainer — see [SkillEdgeApi.getUpskillMessage]. */
    open suspend fun upskillMessage(
        course: String, trainerName: String? = null, level: String? = null,
        readyBy: String? = null, batches: String? = null,
    ): Map<String, Any> = api.getUpskillMessage(
        course = course, trainerName = trainerName, level = level, readyBy = readyBy, batches = batches,
    )
}
