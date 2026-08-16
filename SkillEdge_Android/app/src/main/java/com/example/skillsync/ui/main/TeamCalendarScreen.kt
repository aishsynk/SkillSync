package com.example.skillsync.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.glassSurface
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.components.intOrNull
import com.example.skillsync.ui.components.str

@Composable
fun TeamCalendarScreen(batches: List<Map<*, *>>) {
    val sk = MaterialTheme.skill
    val current = batches.filter { it["engagement_state"]?.toString() == "current" }
    val upcoming = batches.filter { it["engagement_state"]?.toString() == "upcoming" }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        if (current.isNotEmpty()) {
            Text(
                "CURRENTLY DELIVERING (${current.size})",
                style = MaterialTheme.typography.labelSmall,
                color = sk.aqua,
                fontWeight = FontWeight.Bold,
            )
            current.forEach { DeliveryCard(it, sk.aqua) }
        }

        if (upcoming.isNotEmpty()) {
            Text(
                "LINED UP (${upcoming.size})",
                style = MaterialTheme.typography.labelSmall,
                color = sk.sky,
                fontWeight = FontWeight.Bold,
            )
            upcoming.forEach { DeliveryCard(it, sk.sky) }
        }

        if (current.isEmpty() && upcoming.isEmpty()) {
            Text(
                "No batches assigned to the team.",
                style = MaterialTheme.typography.bodyMedium,
                color = sk.subText,
            )
        }
    }
}

@Composable
private fun DeliveryCard(batch: Map<*, *>, tint: Color) {
    val sk = MaterialTheme.skill
    val course = batch.str("course_name").ifBlank { batch.str("demand_id").ifBlank { "Unknown course" } }
    val trainer = batch.str("trainer_name")
    val start = batch.str("start_date")
    val end = batch.str("end_date")
    val mode = batch.str("delivery_mode")
    val customer = batch.str("customer")
    val pax = batch.intOrNull("participants")
    val location = batch.str("location")
    val days = batch.intOrNull("days")
    val recStatus = batch.str("recording_status")

    val dateText = when {
        start.isNotBlank() && end.isNotBlank() -> "$start – $end"
        start.isNotBlank() -> "From $start"
        else -> "Dates pending"
    }

    val recTint: Color? = when {
        recStatus.equals("uploaded", ignoreCase = true) || recStatus.equals("compliant", ignoreCase = true) -> sk.good
        recStatus.equals("overdue", ignoreCase = true) || recStatus.equals("missing", ignoreCase = true) -> sk.crit
        recStatus.isNotBlank() && !recStatus.equals("N/A", ignoreCase = true) && !recStatus.equals("na", ignoreCase = true) -> sk.warn
        else -> null
    }

    Row(
        Modifier
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(Radii.card)),
    ) {
        // Left accent bar — tint matches the section (aqua = live, sky = upcoming)
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(Brush.verticalGradient(listOf(tint, tint.copy(alpha = 0.25f)))),
        )

        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Course name
            Text(
                course,
                style = MaterialTheme.typography.titleSmall,
                color = sk.bodyText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Mode badge + customer + pax
            val hasMetaRow = mode.isNotBlank() || customer.isNotBlank() || pax != null
            if (hasMetaRow) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (mode.isNotBlank()) ModeBadge(mode, tint)
                    if (customer.isNotBlank()) {
                        Text(
                            customer,
                            style = MaterialTheme.typography.labelSmall,
                            color = sk.subText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    if (pax != null) {
                        Text(
                            "$pax pax",
                            style = MaterialTheme.typography.labelSmall,
                            color = sk.subText,
                        )
                    }
                }
            }

            // Trainer · location
            val trainerLocation = listOfNotNull(
                trainer.takeIf { it.isNotBlank() },
                location.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (trainerLocation.isNotBlank()) {
                Text(
                    trainerLocation,
                    style = MaterialTheme.typography.bodySmall,
                    color = sk.labelText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Dates + days + recording badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(dateText, style = MaterialTheme.typography.labelSmall, color = tint)
                if (days != null) {
                    Text("${days}d", style = MaterialTheme.typography.labelSmall, color = sk.subText)
                }
                if (recTint != null) {
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(recTint.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "REC ${recStatus.uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = recTint,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeBadge(mode: String, tint: Color) {
    val label = when (mode.uppercase().trim()) {
        "INSTRUCTOR LED TRAINING", "ILT" -> "ILT"
        "INSTRUCTOR LED ONLINE", "ILO" -> "ILO"
        "FACE TO FACE", "FMAT" -> "FMAT"
        "VIRTUAL", "VIL" -> "VIL"
        else -> mode.take(4).uppercase()
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(tint.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = FontWeight.Bold,
        )
    }
}
