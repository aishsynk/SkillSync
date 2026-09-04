package com.example.skillsync.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.skillsync.R
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.Surface0
import com.example.skillsync.theme.pressable
import com.example.skillsync.theme.skill
import com.example.skillsync.util.NotificationEngine
import com.example.skillsync.util.NotifyEvent

private val BUCKET_PRIORITY = mapOf(
    NotificationEngine.BUCKET_FEEDBACK to 0,
    NotificationEngine.BUCKET_DEMAND to 1,
    NotificationEngine.BUCKET_ALLOCATION to 2,
)

@Composable
fun NotificationCenter(
    actions: List<Map<String, Any>>,
    events: List<NotifyEvent> = emptyList(),
    onTrainerTap: (email: String, name: String) -> Unit = { _, _ -> },
    onDemandTap: (demandId: String) -> Unit = {},
    onDeliveryTap: () -> Unit = {},
) {
    val sk = MaterialTheme.skill
    val openActions = actions.filter {
        (it["lifecycle_state"]?.toString() ?: "open") !in setOf("closed", "resolved")
    }
    val sorted = events.sortedBy { BUCKET_PRIORITY[it.bucket] ?: 99 }
    val totalNew = sorted.size + openActions.size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.card))
            .background(Surface0.copy(alpha = 0.6f))
            .padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Notifications",
                style = MaterialTheme.typography.titleMedium,
                color = sk.bodyText,
                modifier = Modifier.weight(1f),
            )
            if (totalNew > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radii.chip))
                        .background(sk.crit.copy(alpha = 0.2f))
                        .padding(horizontal = Space.sm, vertical = 2.dp),
                ) {
                    Text(
                        text = "$totalNew new",
                        style = MaterialTheme.typography.labelSmall,
                        color = sk.crit,
                    )
                }
            }
        }

        if (sorted.isEmpty() && openActions.isEmpty()) {
            Spacer(Modifier.height(Space.xs))
            Text(
                text = "Nothing needs your attention right now.",
                style = MaterialTheme.typography.bodyMedium,
                color = sk.subText,
            )
            return@Column
        }

        // Live events — sorted by priority (feedback > demand > allocation)
        if (sorted.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            sorted.forEach { event ->
                NotifyEventRow(
                    event = event,
                    onTap = {
                        when (event.targetType) {
                            "trainer" -> if (event.targetId.isNotBlank()) {
                                onTrainerTap(event.targetId, event.targetLabel)
                            }
                            "demand" -> onDemandTap(event.targetId)
                            "demand_list" -> onDemandTap("")
                            "delivery_list" -> onDeliveryTap()
                            else -> if (event.bucket == NotificationEngine.BUCKET_DEMAND) onDemandTap(event.targetId)
                        }
                    },
                )
            }
        }

        // Manager action items
        if (openActions.isNotEmpty()) {
            if (sorted.isNotEmpty()) {
                HorizontalDivider(color = sk.track, modifier = Modifier.padding(vertical = 2.dp))
            }
            Text(
                text = "Open actions (${openActions.size})",
                style = MaterialTheme.typography.labelSmall,
                color = sk.subText,
                modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
            )
            openActions.take(5).forEach { action ->
                ActionRow(action = action)
            }
            if (openActions.size > 5) {
                Text(
                    text = "… and ${openActions.size - 5} more",
                    style = MaterialTheme.typography.labelSmall,
                    color = sk.subText,
                    modifier = Modifier.padding(top = 2.dp, start = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun NotifyEventRow(event: NotifyEvent, onTap: () -> Unit) {
    val sk = MaterialTheme.skill
    val (tint, icon, label) = when (event.bucket) {
        NotificationEngine.BUCKET_FEEDBACK -> Triple(sk.crit, R.drawable.ic_alert, "FEEDBACK DUE")
        NotificationEngine.BUCKET_DEMAND -> Triple(sk.warn, R.drawable.ic_flag, "DEMAND")
        NotificationEngine.BUCKET_ALLOCATION -> Triple(sk.good, R.drawable.ic_check, "ASSIGNED")
        else -> Triple(sk.brand, R.drawable.ic_flag, "INFO")
    }
    val isTappable = event.targetType == "trainer" && event.targetId.isNotBlank()
        || event.bucket == NotificationEngine.BUCKET_DEMAND
        || event.targetType in setOf("demand", "demand_list", "delivery_list")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.08f))
            .then(if (isTappable) Modifier.pressable(onTap) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Category icon
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(tint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    event.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = sk.bodyText,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    label,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = tint,
                    letterSpacing = 0.5.sp,
                )
            }
            if (event.message.isNotBlank()) {
                Text(
                    event.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = sk.subText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Tap affordance
        if (isTappable) {
            Spacer(Modifier.width(6.dp))
            Icon(
                painterResource(R.drawable.ic_forward),
                contentDescription = "Open",
                tint = tint.copy(alpha = 0.6f),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun ActionRow(action: Map<String, Any>) {
    val sk = MaterialTheme.skill
    val title = action["title"]?.toString()?.takeIf { it.isNotBlank() } ?: "Manager action"
    val priority = action["priority"]?.toString()?.lowercase() ?: ""
    val tint: Color = when (priority) {
        "high", "critical" -> sk.crit
        "medium" -> sk.warn
        else -> sk.brand
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.07f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(tint),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = sk.bodyText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val description = action["description"]?.toString()
            if (!description.isNullOrBlank()) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = sk.subText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (priority in setOf("high", "critical", "medium")) {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(tint.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(priority.replaceFirstChar { it.uppercase() }, fontSize = 9.sp, color = tint, fontWeight = FontWeight.Bold)
            }
        }
    }
}
