package com.example.skillsync.core.data

import com.example.skillsync.core.network.AllocationCandidatesResponse
import com.example.skillsync.core.network.BulkAssignRequest
import com.example.skillsync.core.network.BulkAssignResponse
import com.example.skillsync.core.network.RetrofitClient
import com.example.skillsync.core.network.SkillEdgeApi
import com.example.skillsync.core.network.TrainerIndexResponseDto

/**
 * Trainer domain — a trainer's own practice/delivery record, the wider
 * trainer network, candidate evaluation for allocation, and the
 * trainer-skill writes it owns directly (1-tap endorsement and bulk
 * one-skill-to-many-reportees assignment; a *batch's* structured broadcast
 * write, if one is ever added, would not belong here). Follows the
 * [AuthRepository]/[BatchRepository] convention (`apiProvider` constructor
 * default, `open suspend fun` per call so tests can fake it directly).
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

    open suspend fun allocationCandidates(
        manager: String, course: String, start: String, end: String,
        country: String = "", customer: String = "",
        deliveryMode: String = "", international: String = "",
    ): AllocationCandidatesResponse = api.getAllocationCandidates(
        manager = manager, course = course, start = start, end = end,
        country = country, customer = customer,
        deliveryMode = deliveryMode, international = international,
    )

    open suspend fun alternativeTrainers(course: String): Map<String, Any> = api.getAlternativeTrainers(course)

    open suspend fun bulkAssignSkill(request: BulkAssignRequest): BulkAssignResponse = api.bulkAssignSkill(request)
}
