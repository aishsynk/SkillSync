package com.example.skillsync.ui.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.skillsync.theme.Figure
import com.example.skillsync.theme.FigureSize
import com.example.skillsync.theme.SectionHeading
import com.example.skillsync.theme.SkillCard
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.components.int
import com.example.skillsync.ui.components.intOrNull
import com.example.skillsync.ui.components.list
import com.example.skillsync.ui.components.obj
import com.example.skillsync.ui.components.str
import com.example.skillsync.ui.components.strings

/**
 * Real readiness for one trainer.
 *
 * Trainer 360 previously described availability from the roaming and IL
 * off-date fields, which live sampling found empty for every trainer this
 * account can reach — so the section was structurally incapable of being
 * right. This reads the RMS day-level calendar instead: approved leave,
 * confirmed bookings, and provisional ones kept distinct from both.
 */
@Composable
internal fun RealReadinessSection(readiness: Map<String, Any>?) {
    if (readiness == null) return
    val sk = MaterialTheme.skill
    val schedule = readiness.obj("schedule") ?: return
    val cert = readiness.obj("certification")

    val leaveDays = schedule.int("leave_days")
    val confirmed = schedule.int("confirmed_days")
    val tentative = schedule.int("tentative_days")

    SectionHeading(
        "Readiness",
        when {
            leaveDays > 0 -> "$leaveDays day${if (leaveDays == 1) "" else "s"} of approved leave in the next 90 days."
            confirmed > 0 -> "$confirmed committed day${if (confirmed == 1) "" else "s"} booked ahead."
            else -> "No leave or confirmed commitments in the next 90 days."
        },
    )

    SkillCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            Figure(leaveDays.toString(), "On leave", FigureSize.Small, Modifier.weight(1f),
                if (leaveDays == 0) sk.good else sk.warn)
            Figure(confirmed.toString(), "Committed", FigureSize.Small, Modifier.weight(1f), sk.sky)
            // Provisional work is deliberately its own figure. Counting it as
            // committed makes a bench look fuller than it is; ignoring it makes
            // a trainer look freer than they are.
            Figure(tentative.toString(), "Provisional", FigureSize.Small, Modifier.weight(1f), sk.labelText)
        }

        val nextLeave = schedule.strings("next_leave")
        if (nextLeave.isNotEmpty()) {
            Text(
                "Next leave: ${nextLeave.take(3).joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall, color = sk.subText,
            )
        }

        val modes = schedule.strings("delivery_modes")
        if (modes.isNotEmpty()) {
            Text(
                "Delivers: ${modes.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall, color = sk.subText,
            )
        }

        val exclusions = schedule.int("client_exclusions")
        if (exclusions > 0) {
            Text(
                "$exclusions client exclusion${if (exclusions == 1) "" else "s"} on record. " +
                    "These block allocation regardless of fit.",
                style = MaterialTheme.typography.bodySmall, color = sk.crit,
            )
        }
    }

    TravelReadinessCard(readiness)

    if (cert != null) {
        val gaps = cert.list("gaps")
        val unknown = cert.int("unknown_requirement")
        SectionHeading(
            "Certification",
            if (gaps.isEmpty()) "No confirmed gaps across ${cert.int("courses_reviewed")} courses."
            else "${gaps.size} confirmed gap${if (gaps.size == 1) "" else "s"}.",
        )
        SkillCard(Modifier.fillMaxWidth()) {
            gaps.take(5).forEach { g ->
                Column {
                    Text(g.str("course"), style = MaterialTheme.typography.bodyMedium, color = sk.bodyText)
                    val exam = g.str("exam_name")
                    Text(
                        if (exam.isNotBlank()) "Likely exam: $exam (inferred from delivery history)"
                        else "Exam not identified in RMS",
                        style = MaterialTheme.typography.bodySmall, color = sk.subText,
                    )
                }
            }
            if (unknown > 0) {
                // Not a gap and not a clean bill: RMS simply has no policy row
                // for these courses, and saying "no gap" would under-report.
                Text(
                    "$unknown course${if (unknown == 1) "" else "s"} have no exam policy in RMS, " +
                        "so their requirement is unknown rather than clear.",
                    style = MaterialTheme.typography.bodySmall, color = sk.warn,
                )
            }
        }
    }
}

/**
 * International readiness for this trainer.
 *
 * Visa, time zone and nearest city are trainer properties, but RMS exposes them
 * only through a course-keyed endpoint, so the backend resolves one of their
 * taught courses on their behalf. When none resolves it says so — an empty
 * travel card would read as "cannot travel", which is a different claim
 * entirely and one the data does not support.
 */
@Composable
private fun TravelReadinessCard(readiness: Map<String, Any>) {
    val sk = MaterialTheme.skill
    val travel = readiness.obj("travel")
    val note = readiness.str("travel_note")

    if (travel == null) {
        if (note.isBlank()) return
        SectionHeading("International readiness", "Not verified")
        SkillCard(Modifier.fillMaxWidth()) {
            Text(
                "Travel readiness could not be checked: $note.",
                style = MaterialTheme.typography.bodySmall, color = sk.subText,
            )
            Text(
                "This does not mean the trainer cannot travel.",
                style = MaterialTheme.typography.bodySmall, color = sk.warn,
            )
        }
        return
    }

    val visas = travel.list("visas")
    val state = travel.str("visa_state")
    SectionHeading(
        "International readiness",
        when (state) {
            "available" -> "${visas.size} valid visa${if (visas.size == 1) "" else "s"} on record."
            "expired" -> "Visa records held, but all have expired."
            else -> "No visa record held. Verification required before international work."
        },
    )
    SkillCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            Figure(
                travel.str("timezone").ifBlank { "—" }.removeSuffix(" Standard Time"),
                "Time zone", FigureSize.Small, Modifier.weight(1f), sk.sky,
            )
            Figure(
                travel.str("nearest_city").split(",").firstOrNull()?.ifBlank { "—" } ?: "—",
                "Based near", FigureSize.Small, Modifier.weight(1f), sk.sky,
            )
            Figure(
                travel.int("free_days_next_90").toString(),
                "Free days", FigureSize.Small, Modifier.weight(1f), sk.aqua,
            )
        }

        visas.forEach { v ->
            val expired = v["expired"] == true
            Column {
                Text(
                    v.str("country") + if (expired) " · expired" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (expired) sk.crit else sk.bodyText,
                )
                val extras = listOfNotNull(
                    v.str("expiry").takeIf { it.isNotBlank() }?.let { "valid to $it" },
                    v.intOrNull("stay_days")?.let { "$it day stay" },
                    v.strings("associate_countries").takeIf { it.isNotEmpty() }
                        ?.let { "also covers ${it.joinToString(", ")}" },
                )
                if (extras.isNotEmpty()) {
                    Text(
                        extras.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = sk.subText,
                    )
                }
            }
        }

        if (visas.isEmpty()) {
            Text(
                "No visa on record. Roughly half of trainers have none, so this is not " +
                    "evidence they cannot travel — it needs checking before assignment.",
                style = MaterialTheme.typography.bodySmall, color = sk.warn,
            )
        }

        val via = travel.str("resolved_via_course")
        if (via.isNotBlank()) {
            Text(
                "Source: RMS schedule for $via",
                style = MaterialTheme.typography.labelSmall, color = sk.labelText,
            )
        }
    }
}
