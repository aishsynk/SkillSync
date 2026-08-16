package com.example.skillsync.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.Severity
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.glassSurface
import com.example.skillsync.theme.pressable
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.components.Avatar
import com.example.skillsync.ui.components.Sparkline
import com.example.skillsync.ui.components.int
import com.example.skillsync.ui.components.intOrNull
import com.example.skillsync.ui.components.list
import com.example.skillsync.ui.components.obj
import com.example.skillsync.ui.components.str

/**
 * Roster card for the People & Capability tab — full-width single column.
 *
 * Design principles:
 *  - Severity bar is the only urgency signal: the previous ToneChip duplicated
 *    it and stole name space on narrow cards. Removed.
 *  - Name, designation, and headline all have full width now.
 *  - Readiness score lives as a compact badge top-right — green ≥80, amber ≥60,
 *    red <60. Gives the manager a quick pass/fail on capability without opening
 *    the profile.
 *  - Current course shown as a fourth line when it adds information the headline
 *    does not already carry (e.g. the trainer is on bench but holds a booking
 *    that starts soon).
 *  - Height is content-driven, not fixed — a trainer with leave, a cert gap and
 *    a course booking should not be silently clipped.
 */
@Composable
internal fun TeamMemberCard(
    trainer: Map<*, *>,
    state: Map<*, *>?,
    capability: Map<*, *>? = null,
    delivery: Map<*, *>? = null,
    openActionCount: Int = 0,
    /** Real availability row from `/api/v2/team/readiness`. */
    calendarAvailability: Map<String, Any>? = null,
    onClick: () -> Unit,
) {
    val sk = MaterialTheme.skill
    val name = trainer.str("trainer_name")
    val designation = trainer.str("designation").ifBlank { capability?.str("designation").orEmpty() }
    val util = trainer.intOrNull("current_utilization")
    val cert = capability?.obj("certification")
    val gaps = cert?.int("gap_count") ?: 0
    val held = cert?.list("held")?.size ?: 0
    val readiness = capability?.intOrNull("readiness_score")
    val currentCourse = state?.obj("current_batch")?.str("course_name").orEmpty()

    val severity = teamCardSeverity(trainer, capability, calendarAvailability, openActionCount)
    val tint = severity.tint()
    val headline = teamCardHeadline(trainer, state, calendarAvailability, gaps, openActionCount)

    val series = trainer.list("utilization_series")
        .mapNotNull { it.intOrNull("utilization") }

    Row(
        Modifier
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(Radii.card))
            .pressable(onClick),
    ) {
        // Severity as position and shape, not only colour. The chip is gone —
        // this bar IS the urgency signal.
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(Brush.verticalGradient(listOf(tint, tint.copy(alpha = 0.25f))))
        )

        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = Space.md, vertical = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            // Identity row: avatar + name/designation + readiness badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(name, capability?.str("photo_url"), 40.dp)
                Spacer(Modifier.width(Space.sm))
                Column(Modifier.weight(1f)) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleSmall,
                        color = sk.bodyText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (designation.isNotBlank()) {
                        Text(
                            designation,
                            style = MaterialTheme.typography.bodySmall,
                            color = sk.subText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (readiness != null) {
                    Spacer(Modifier.width(Space.sm))
                    val rColor = when {
                        readiness >= 80 -> sk.good
                        readiness >= 60 -> sk.warn
                        else -> sk.crit
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(rColor.copy(alpha = 0.14f))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$readiness%",
                            style = MaterialTheme.typography.labelSmall,
                            color = rColor,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // Headline — the one sentence that explains the card's urgency
            if (headline.isNotBlank()) {
                Text(
                    headline,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (severity == Severity.Good) sk.subText else tint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Metrics + sparkline
            Row(verticalAlignment = Alignment.CenterVertically) {
                Micro(util?.let { "$it%" } ?: "—", "util", sk)
                Spacer(Modifier.width(Space.lg))
                Micro("$held", "certs", sk)
                Spacer(Modifier.width(Space.lg))
                Micro(if (gaps == 0) "0" else "$gaps", "gaps", sk, warn = gaps > 0)

                val leaveDays = (calendarAvailability?.get("leave_days") as? Number)?.toInt() ?: 0
                if (leaveDays > 0) {
                    Spacer(Modifier.width(Space.lg))
                    Micro("$leaveDays", "leave", sk, warn = true)
                }

                Spacer(Modifier.weight(1f))
                if (series.size >= 2) {
                    Sparkline(
                        series, tint, endpointTint = sk.cyan,
                        height = 18.dp,
                        modifier = Modifier.width(64.dp),
                    )
                }
            }

            // Current course — shown when it is not already the headline.
            // A trainer "on bench" may still have an upcoming course that the
            // headline omits (because the first priority is the bench status).
            if (currentCourse.isNotBlank() && !headline.contains(currentCourse, ignoreCase = true)) {
                Text(
                    currentCourse,
                    style = MaterialTheme.typography.labelSmall,
                    color = sk.sky,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(2.dp))
        }
    }
}

/** A number and a three-letter label, sized so the number dominates. */
@Composable
private fun Micro(
    value: String,
    label: String,
    sk: com.example.skillsync.theme.SkillColors,
    warn: Boolean = false,
) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            value,
            style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
            color = if (warn) sk.warn else sk.bodyText,
        )
        Spacer(Modifier.width(3.dp))
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = sk.labelText,
        )
    }
}

/**
 * Where this person sits in the manager's attention order.
 *
 * Deliberately the same precedence the agent and the weekly message use, so a
 * trainer flagged on one surface is flagged on all of them.
 */
internal fun teamCardSeverity(
    trainer: Map<*, *>,
    capability: Map<*, *>?,
    calendarAvailability: Map<String, Any>?,
    openActionCount: Int,
): Severity {
    val gaps = capability?.obj("certification")?.int("gap_count") ?: 0
    val exclusions = (calendarAvailability?.get("client_exclusions") as? Number)?.toInt() ?: 0
    return when {
        trainer.str("feedback_risk").equals("High", true) -> Severity.Critical
        exclusions > 0 -> Severity.Critical
        gaps > 0 -> Severity.Warning
        trainer.str("capacity_bucket").equals("Stretched", true) -> Severity.Warning
        trainer.str("capacity_bucket").equals("On Bench", true) -> Severity.Watch
        openActionCount > 0 -> Severity.Info
        else -> Severity.Good
    }
}

/** One sentence explaining the severity, in the manager's language. */
internal fun teamCardHeadline(
    trainer: Map<*, *>,
    state: Map<*, *>?,
    calendarAvailability: Map<String, Any>?,
    gaps: Int,
    openActionCount: Int,
): String {
    val exclusions = (calendarAvailability?.get("client_exclusions") as? Number)?.toInt() ?: 0
    val leave = (calendarAvailability?.get("leave_days") as? Number)?.toInt() ?: 0
    val nextLeave = calendarAvailability?.let {
        @Suppress("UNCHECKED_CAST")
        (it["next_leave"] as? List<String>)?.firstOrNull()
    }
    val current = state?.obj("current_batch")?.str("course_name").orEmpty()

    return when {
        trainer.str("feedback_risk").equals("High", true) -> "Feedback flagged for review"
        exclusions > 0 -> "$exclusions client exclusion${if (exclusions == 1) "" else "s"}"
        gaps > 0 -> "$gaps certification gap${if (gaps == 1) "" else "s"} open"
        trainer.str("capacity_bucket").equals("Stretched", true) -> "Carrying more than their share"
        leave > 0 -> nextLeave?.let { "On leave from $it" } ?: "$leave days of leave booked"
        trainer.str("capacity_bucket").equals("On Bench", true) -> "Available, nothing booked"
        openActionCount > 0 -> "$openActionCount open action${if (openActionCount == 1) "" else "s"}"
        current.isNotBlank() -> "Delivering $current"
        else -> state?.str("status_label").orEmpty().ifBlank { "On track" }
    }
}
