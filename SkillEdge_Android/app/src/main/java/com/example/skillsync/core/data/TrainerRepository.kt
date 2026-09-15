package com.example.skillsync.core.data

import com.example.skillsync.core.network.RetrofitClient
import com.example.skillsync.core.network.SkillEdgeApi

/**
 * Trainer domain — a trainer's own practice/delivery record and the wider
 * trainer network, following the [AuthRepository]/[BatchRepository]
 * convention (`apiProvider` constructor default, `open suspend fun` per
 * call so tests can fake it directly).
 */
open class TrainerRepository(
    private val apiProvider: () -> SkillEdgeApi = { RetrofitClient.instance },
) {
    private val api: SkillEdgeApi by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { apiProvider() }

    open suspend fun feedbackLog(email: String): Map<String, Any> = api.trainerFeedbackLog(email)

    open suspend fun recordings(email: String): Map<String, Any> = api.trainerRecordings(email)

    open suspend fun networkTrainers(course: String): Map<String, Any> = api.getNetworkTrainers(course = course)
}
