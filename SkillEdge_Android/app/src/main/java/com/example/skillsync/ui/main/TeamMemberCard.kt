package com.example.skillsync.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    val currentStatus = state?.str("current_status").orEmpty()
    val statusLabel = when (currentStatus) {
        "teaching_now" -> "Delivering"
        "scheduled_today" -> "Scheduled"
        "preparing" -> "Preparing"
        "free" -> if ((calendarAvailability?.get("leave_days") as? Number)?.toInt() ?: 0 > 0) "On Leave" else "Available"
        else -> "Active"
    }
    val statusColor = when (statusLabel) {
        "Delivering" -> Color(0xFF10B981)
        "Scheduled" -> Color(0xFF06B6D4)
        "Preparing" -> Color(0xFF8B5CF6)
        "On Leave" -> Color(0xFFF59E0B)
        "Available" -> Color(0xFF38BDF8)
        else -> Color(0xFF94A3B8)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.card))
            .background(Color(0x22111827))
            .border(1.dp, Color(0x3538BDF8), RoundedCornerShape(Radii.card))
            .pressable(onClick),
    ) {
        // Glowing severity indicator bar on the left edge
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            tint,
                            tint.copy(alpha = 0.40f)
                        )
                    )
                )
        )

        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = Space.md, vertical = Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            // Identity row: avatar with glowing border + name/designation + status/readiness badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0x4038BDF8), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Avatar(name, capability?.str("photo_url"), 44.dp)
                }
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            name,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(statusColor.copy(alpha = 0.16f))
                                .border(1.dp, statusColor.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(
                                    Modifier
                                        .size(5.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(statusColor)
                                )
                                Text(
                                    statusLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = statusColor,
                                    fontSize = 9.sp,
                                )
                            }
                        }
                    }
                    if (designation.isNotBlank()) {
                        Text(
                            designation,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF93C5FD),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (readiness != null) {
                    Spacer(Modifier.width(Space.sm))
                    val rColor = when {
                        readiness >= 80 -> Color(0xFF34D399)
                        readiness >= 60 -> Color(0xFFFBBF24)
                        else -> Color(0xFFFB7185)
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(rColor.copy(alpha = 0.16f))
                            .border(1.dp, rColor.copy(alpha = 0.40f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$readiness%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = rColor,
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
            if (currentCourse.isNotBlank() && !headline.contains(currentCourse, ignoreCase = true)) {
                Text(
                    "Delivering: $currentCourse",
                    style = MaterialTheme.typography.labelSmall,
                    color = sk.good,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Quick actions row
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)),
                ) {
                    Text(
                        "Trainer 360 →",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }

                if (gaps > 0) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = sk.crit.copy(alpha = 0.12f),
                    ) {
                        Text(
                            "$gaps cert gap${if (gaps == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = sk.crit,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                } else if (util != null && util < 40) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = sk.warn.copy(alpha = 0.12f),
                    ) {
                        Text(
                            "Growth opportunity",
                            style = MaterialTheme.typography.labelSmall,
                            color = sk.warn,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }
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
