package com.example.skillsync.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.skillsync.R
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.accentGlass
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.components.*

/**
 * One trainer, at half screen width, on the manager's team command surface.
 *
 * Two of these sit per row, so everything here has to earn its space. It shows
 * the six things a manager needs to compare people against each other —
 * health, status, utilisation, readiness, certificates held versus gaps, and
 * what is coming up — plus a flag when the trainer needs a decision. Anything
 * that does not help compare two people belongs on the trainer-360 profile,
 * not here.
 */
@Composable
internal fun TeamMemberCard(
    trainer: Map<*, *>,
    state: Map<*, *>?,
    capability: Map<*, *>? = null,
    delivery: Map<*, *>? = null,
    onClick: () -> Unit,
) {
    val sk = MaterialTheme.skill
    val name = trainer.str("trainer_name")
    val util = trainer.intOrNull("current_utilization")
    val upcoming = trainer.int("upcoming_count")
    val statusLabel = state?.str("status_label") ?: "Unknown"
    val status = state?.str("current_status") ?: "unknown"
    val cur = state?.obj("current_batch")
    val curCourse = cur?.str("course_name").orEmpty()
    val daysLeft = cur?.intOrNull("days_left")

    val cert = capability?.obj("certification")
    val held = cert?.list("held")?.size ?: 0
    val gaps = cert?.int("gap_count") ?: 0
    val readiness = capability?.intOrNull("readiness_score")
    val readinessBucket = capability?.str("readiness_bucket").orEmpty()

    val recommended = trainer.str("recommended_action")
        .takeIf { it.isNotBlank() && it != "Monitor performance" }

    val (health, healthBucket) = trainerHealth(trainer, capability, delivery)
    val healthColor = when (healthBucket) {
        "Healthy" -> sk.aqua
        "Watchlist" -> sk.sky
        "Needs Attention" -> sk.warn
        else -> sk.crit
    }
    val statusColor = when (status) {
        "teaching_now" -> sk.cyan
        "scheduled_today" -> sk.sky
        "preparing" -> sk.ice
        "free" -> sk.aqua
        else -> sk.labelText
    }

    Box(
        Modifier
            .fillMaxWidth()
            .accentGlass(healthColor, RoundedCornerShape(Radii.card), strong = healthBucket == "High Risk")
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(Brush.verticalGradient(listOf(healthColor, healthColor.copy(alpha = 0.2f))))
        )
        Column(Modifier.padding(start = 12.dp, top = 11.dp, end = 10.dp, bottom = 11.dp)) {
            // ── Identity + health ───────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(name, capability?.str("photo_url"), 30.dp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = sk.frost,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        statusLabel.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor, fontSize = 8.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 0.07.em,
                        maxLines = 1,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$health",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, color = healthColor, fontSize = 15.sp,
                    )
                    Text(
                        "HEALTH", style = MaterialTheme.typography.labelSmall,
                        color = healthColor, fontSize = 6.5.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(9.dp))

            // ── Utilisation and readiness, side by side for comparison ──────
            Row(Modifier.fillMaxWidth()) {
                MiniMetric(
                    "UTIL",
                    util?.let { "$it%" } ?: "—",
                    when {
                        util == null -> sk.subText
                        util > 85 -> sk.crit
                        util >= 60 -> sk.aqua
                        util >= 30 -> sk.warn
                        else -> sk.subText
                    },
                    Modifier.weight(1f),
                )
                MiniMetric(
                    "READY",
                    readiness?.toString() ?: "—",
                    when {
                        readiness == null -> sk.subText
                        readiness >= 70 -> sk.aqua
                        readiness >= 45 -> sk.warn
                        else -> sk.crit
                    },
                    Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(7.dp))

            // ── Certificates held vs gaps ───────────────────────────────────
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_certificate), null,
                    tint = sk.ice, modifier = Modifier.size(11.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    if (capability == null) "certs loading" else "$held held",
                    style = MaterialTheme.typography.labelSmall,
                    color = sk.labelText, fontSize = 9.5.sp,
                )
                if (gaps > 0) {
                    Spacer(Modifier.width(7.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(4.dp))
                            .background(sk.warn.copy(alpha = 0.18f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(
                            "$gaps gap${if (gaps == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = sk.warn, fontSize = 8.5.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = sk.cardBorder.copy(alpha = 0.45f), thickness = 0.5.dp)
            Spacer(Modifier.height(7.dp))

            // ── What they are on now, and what is next ──────────────────────
            if (curCourse.isNotBlank()) {
                Text(
                    curCourse,
                    style = MaterialTheme.typography.labelSmall,
                    color = sk.frost, fontSize = 9.5.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        daysLeft == null -> "end date unknown"
                        daysLeft <= 0 -> "ends today"
                        daysLeft == 1 -> "ends tomorrow"
                        else -> "ends in $daysLeft days"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = sk.labelText, fontSize = 8.5.sp,
                )
            } else {
                Text(
                    "No current assignment",
                    style = MaterialTheme.typography.labelSmall,
                    color = sk.subText, fontSize = 9.5.sp,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                "$upcoming upcoming",
                style = MaterialTheme.typography.labelSmall,
                color = if (upcoming > 0) sk.sky else sk.subText, fontSize = 8.5.sp,
                fontWeight = if (upcoming > 0) FontWeight.SemiBold else FontWeight.Normal,
            )

            // ── Action flag, only when one is genuinely required ────────────
            if (recommended != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(healthColor.copy(alpha = 0.11f))
                        .padding(horizontal = 7.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_flag), null,
                        tint = healthColor, modifier = Modifier.size(10.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        recommended,
                        style = MaterialTheme.typography.labelSmall,
                        color = healthColor, fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniMetric(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.skill.ice, fontSize = 7.5.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 0.1.em,
        )
        Text(
            value, style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold, color = tint, fontSize = 14.sp,
        )
    }
}
