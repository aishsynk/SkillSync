package com.example.skillsync.core.data

import com.example.skillsync.core.network.RetrofitClient
import com.example.skillsync.core.network.SkillEdgeApi

/**
 * A trainer/manager's own delivery calendar (`GET /api/v2/trainer/calendar`).
 * Deliberately its own small repository rather than another method bolted
 * onto [ManagerRepository] — a personal schedule is a different domain from
 * manager/team intelligence, and giving it its own owner keeps that
 * boundary legible instead of growing one repository to own everything.
 */
open class ScheduleRepository(
    private val apiProvider: () -> SkillEdgeApi = { RetrofitClient.instance },
) {
    private val api: SkillEdgeApi by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { apiProvider() }

    // open: MyScheduleViewModelTest fakes this directly rather than
    // implementing the entire SkillEdgeApi Retrofit interface for one method.
    open suspend fun myCalendar(email: String): Map<String, Any> = api.trainerCalendar(email)
}
