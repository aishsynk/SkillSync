package com.example.skillsync.data.models

import com.example.skillsync.ui.components.intOrNull
import com.example.skillsync.ui.components.str
import com.example.skillsync.ui.components.strings

/**
 * Outcome of a `course-intelligence` lookup, deliberately a closed model rather
 * than a bare map so the assignment sheet can never mistake "the request
 * failed" for "RMS has no schedule".
 *
 * [Verified] means the backend actually answered with catalogue metadata.
 * [Unverified] is the honest rendering of a failed request: it is not the same
 * thing as an empty schedule, and the UI must not paint it as one.
 */
sealed interface CourseIntelligence {

    /** Backend answered with catalogue metadata; [scheduleDates] may still be
     *  empty because RMS genuinely returned none. */
    data class Verified(
        val courseName: String,
        val vendor: String = "",
        val durationDays: Int? = null,
        val scheduleAvailable: Boolean = false,
        val scheduleDates: List<String> = emptyList(),
        val note: String = "",
    ) : CourseIntelligence

    /** The request could not be answered at all — shown as such, never as
     *  "no schedule". */
    data class Unverified(
        val courseName: String,
        val note: String = "Course schedule could not be verified.",
    ) : CourseIntelligence

    companion object {
        fun from(map: Map<String, Any>?): CourseIntelligence {
            if (map == null) return Unverified("")
            return Verified(
                courseName = map.str("course_name"),
                vendor = map.str("vendor"),
                durationDays = map.intOrNull("duration_days"),
                scheduleAvailable = map.boolOr("schedule_available"),
                scheduleDates = map.strings("schedule_dates"),
                note = map.str("note"),
            )
        }
    }
}

private fun Map<*, *>.boolOr(key: String): Boolean = when (val v = this[key]) {
    is Boolean -> v
    is String -> v.equals("true", ignoreCase = true)
    is Number -> v.toInt() != 0
    else -> false
}