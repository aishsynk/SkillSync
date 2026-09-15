package com.example.skillsync.core.data

import com.example.skillsync.core.network.RetrofitClient
import com.example.skillsync.core.network.SkillEdgeApi
import com.example.skillsync.core.network.TrainerIndexResponseDto

/**
 * Trainer domain — a trainer's own practice/delivery record, the wider
 * trainer network, and the Trainer 360 profile's trainer-specific reads and
 * the one trainer-skill write it owns directly (endorsement; bulk/batch
 * skill writes are a separate concern, not folded in here). Follows the
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
}
