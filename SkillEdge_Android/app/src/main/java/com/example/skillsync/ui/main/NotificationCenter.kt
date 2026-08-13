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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.accentGlass
import com.example.skillsync.theme.Surface0
import com.example.skillsync.theme.skill
import com.example.skillsync.util.NotifyEvent

@Composable
fun NotificationCenter(
    actions: List<Map<String, Any>>,
    events: List<NotifyEvent> = emptyList(),
) {
    val sk = MaterialTheme.skill
    val totalNew = events.size + actions.size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.card))
            .background(Surface0.copy(alpha = 0.6f))
            .padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Notifications",
                style = MaterialTheme.typography.titleMedium,
                color = sk.bodyText,
                modifier = Modifier.weight(1f)
            )
            if (totalNew > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radii.chip))
                        .background(sk.crit.copy(alpha = 0.2f))
                        .padding(horizontal = Space.sm, vertical = 2.dp)
                ) {
                    Text(
                        text = "$totalNew new",
                        style = MaterialTheme.typography.labelSmall,
                        color = sk.crit
                    )
                }
            }
        }

        if (events.isEmpty() && actions.isEmpty()) {
            Text(
                text = "You're all caught up.",
                style = MaterialTheme.typography.bodyMedium,
                color = sk.subText
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                // Live notification events (new batches, allocations) appear first.
                events.forEach { event ->
                    NotifyEventCard(event = event)
                }
                // Manager action queue items below.
                actions.forEach { action ->
                    NotificationCard(action = action)
                }
            }
        }
    }
}

@Composable
private fun NotifyEventCard(event: NotifyEvent) {
    val sk = MaterialTheme.skill
    val bucketColor = when (event.bucket) {
        "demand" -> sk.warn
        "allocation" -> sk.good
        else -> sk.brand
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .accentGlass(bucketColor)
            .padding(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.xs)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(bucketColor)
            )
            Spacer(modifier = Modifier.width(Space.sm))
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleSmall,
                color = sk.heroText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "NEW",
                style = MaterialTheme.typography.labelSmall,
                color = bucketColor
            )
        }
        if (event.message.isNotBlank()) {
            Text(
                text = event.message,
                style = MaterialTheme.typography.bodySmall,
                color = sk.subText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 14.dp)
            )
        }
    }
}

@Composable
private fun NotificationCard(action: Map<String, Any>) {
    val sk = MaterialTheme.skill
    val title = action["title"]?.toString()?.takeIf { it.isNotBlank() } ?: "Manager action"
    val state = action["lifecycle_state"]?.toString() ?: "open"
    val description = action["description"]?.toString()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .accentGlass(sk.brand)
            .padding(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.xs)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(sk.brand)
            )
            Spacer(modifier = Modifier.width(Space.sm))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = sk.heroText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = state.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = sk.brand
            )
        }

        if (!description.isNullOrBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = sk.subText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 14.dp)
            )
        }
    }
}
