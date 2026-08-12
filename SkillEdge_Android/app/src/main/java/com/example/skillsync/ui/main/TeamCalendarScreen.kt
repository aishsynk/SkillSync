package com.example.skillsync.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.accentGlass
import com.example.skillsync.theme.glassSurface
import com.example.skillsync.theme.skill

@Composable
fun TeamCalendarScreen(
    batches: List<Map<*, *>>
) {
    val sk = MaterialTheme.skill
    val current = batches.filter { it["engagement_state"]?.toString() == "current" }
    val upcoming = batches.filter { it["engagement_state"]?.toString() == "upcoming" }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.lg)
    ) {
        if (current.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(
                    text = "CURRENTLY DELIVERING",
                    style = MaterialTheme.typography.labelSmall,
                    color = sk.aqua
                )
                current.forEach { batch ->
                    BatchCard(batch = batch, isCurrent = true)
                }
            }
        }

        if (upcoming.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(
                    text = "LINED UP",
                    style = MaterialTheme.typography.labelSmall,
                    color = sk.sky
                )
                upcoming.forEach { batch ->
                    BatchCard(batch = batch, isCurrent = false)
                }
            }
        }

        if (current.isEmpty() && upcoming.isEmpty()) {
            Text(
                text = "No batches assigned to the team.",
                style = MaterialTheme.typography.bodyMedium,
                color = sk.subText
            )
        }
    }
}

@Composable
private fun BatchCard(batch: Map<*, *>, isCurrent: Boolean) {
    val sk = MaterialTheme.skill
    val courseName = batch["course_name"]?.toString()?.takeIf { it.isNotBlank() }
        ?: batch["demand_id"]?.toString() ?: "Unknown Course"
    val trainer = batch["trainer_name"]?.toString() ?: "Unassigned"
    val start = batch["start_date"]?.toString() ?: ""
    val end = batch["end_date"]?.toString() ?: ""
    val dateText = if (start.isNotBlank() && end.isNotBlank()) "$start to $end" else "Dates pending"

    val tint = if (isCurrent) sk.aqua else sk.sky

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .accentGlass(tint, strong = isCurrent)
            .padding(Space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(tint)
                .align(Alignment.CenterVertically)
        )
        Spacer(modifier = Modifier.width(Space.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = courseName,
                style = MaterialTheme.typography.titleMedium,
                color = sk.bodyText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(
                    text = trainer,
                    style = MaterialTheme.typography.bodySmall,
                    color = sk.subText
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = sk.subText
                )
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.labelSmall,
                    color = tint
                )
            }
        }
    }
}
