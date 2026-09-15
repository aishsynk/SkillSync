package com.example.skillsync.core.network

import retrofit2.http.GET
import retrofit2.http.Query

/** Personal-schedule domain — see [com.example.skillsync.core.data.ScheduleRepository]. */
interface ScheduleApi {
    @GET("api/v2/trainer/calendar")
    suspend fun trainerCalendar(@Query("email") email: String): Map<String, Any>
}
