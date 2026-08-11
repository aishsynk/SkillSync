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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.Severity
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.ToneChip
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
import com.example.skillsync.ui.components.strings

/**
 * The roster card, rebuilt to `AI/DESIGN_VISION_V2_2026_08_11.md` §7.2.
 *
 * The previous card was a 379-line block that rendered fourteen fields at
 * roughly equal weight: designation, status label, current course, days left,
 * next course, utilisation bar, readiness score and bucket, certificates held,
 * gaps, feedback risk, availability string, next available date, upcoming
 * count and a recommended action. A manager scanning ten of them could not
 * tell who needed them, because nothing on the card ranked anything.
 *
 * This is one compact row. Severity owns the leading edge, so urgency is read
 * as position and shape before any text. Three micro-figures carry the numbers
 * that actually differ between people, a sparkline carries direction, and
 * everything else moved to the profile — which is one tap away and exists for
 * exactly that.
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
    val util = trainer.intOrNull("current_utilization")
    val cert = capability?.obj("certification")
    val gaps = cert?.int("gap_count") ?: 0
    val held = cert?.list("held")?.size ?: 0

    val severity = teamCardSeverity(trainer, capability, calendarAvailability, openActionCount)
    val tint = severity.tint()

    // The one line that says why this person is where they are in the list.
    val headline = teamCardHeadline(trainer, state, calendarAvailability, gaps, openActionCount)

    // Direction, not just position. Rendered only when RMS returned a real
    // series — an invented trend is worse than none.
    val series = trainer.list("utilization_series")
        .mapNotNull { it.intOrNull("utilization") }

    Row(
        Modifier
            .fillMaxWidth()
            .height(104.dp)
            .glassSurface(RoundedCornerShape(Radii.card))
            .pressable(onClick),
    ) {
        // Severity as position and shape, not only colour.
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
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(name, capability?.str("photo_url"), 32.dp)
                Spacer(Modifier.width(Space.sm))
                Column(Modifier.weight(1f)) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleSmall,
                        color = sk.bodyText,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        headline,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (severity == Severity.Good) sk.subText else tint,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (severity != Severity.Good) {
                    ToneChip(severity.label, tint)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Micro(util?.let { "$it%" } ?: "—", "util", sk)
                Spacer(Modifier.width(Space.lg))
                Micro("$held", "certs", sk)
                Spacer(Modifier.width(Space.lg))
                Micro(if (gaps == 0) "0" else "$gaps", "gaps", sk, warn = gaps > 0)

                // Leave is shown whenever it exists, not only when it happens
                // to be the top severity reason. A trainer with a certification
                // gap *and* leave next week would otherwise have the leave
                // hidden by the headline, which is the availability question
                // the manager most needs answered.
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
                        modifier = Modifier.width(56.dp),
                    )
                }
            }
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
