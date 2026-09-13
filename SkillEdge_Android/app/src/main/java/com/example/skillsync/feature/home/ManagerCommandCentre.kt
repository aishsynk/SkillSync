package com.example.skillsync.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*
import com.example.skillsync.core.notification.NotifyEvent

@Composable
fun ManagerCommandCentre(
      email: String,
      profile: Map<String, Any>?,
      kpis: Map<*, *>?, capKpis: Map<*, *>?, capabilityLoading: Boolean,
      ops: List<Map<*, *>>, states: List<Map<*, *>>, batches: List<Map<*, *>>,
      demand: List<Map<*, *>>, capTrainers: List<Map<*, *>>,
      actions: List<Map<String, Any>>,
      recentNotifications: List<NotifyEvent> = emptyList(),
      fromCache: Boolean, cachedAt: Long,
      onDrill: (Drill) -> Unit,
      onTrainerClick: (String, String) -> Unit,
      onOpenProfile: () -> Unit,
      onOpenNotifications: () -> Unit,
      onOpenDemand: () -> Unit,
      onOpenWeeklyReport: () -> Unit = {},
      onOpenHrReport: () -> Unit = {},
      onOpenPriorities: () -> Unit = {},
      onOpenAccounts: () -> Unit = {},
      onOpenCopilot: () -> Unit = {},
      onOpenDelivery: () -> Unit = {},
      onOpenPipelineRadar: () -> Unit = {},
      onOpenDeliveryCompliance: () -> Unit = {},
      onOpenCapacityRunway: () -> Unit = {},
      onOpenViberAutomation: () -> Unit = {},
      onOpenSkillRequests: () -> Unit = {},
      pendingSkillRequests: Int = 0,
      onBatchClick: (String) -> Unit = {},
      calendarReadiness: Map<String, Map<String, Any>> = emptyMap(),
) {
    val bg = Color(0xFFF9F9F8)
    val textPrimary = Color(0xFF0F172A)
    val textSecondary = Color(0xFF64748B)
    val accent = Color(0xFF2563EB)
    val dividerColor = Color(0xFFE2E8F0)
    
    val todayDate = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date())

    val unstaffedDemand = demand.take(3)
    val activeBatches = batches.filter { it.str("engagement_state") == "active" }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        // HEADER
        item {
            Column {
                Text("Today", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(todayDate, fontSize = 14.sp, color = textSecondary)
            }
        }

        // NEEDS YOUR ATTENTION
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Needs your attention", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                
                if (unstaffedDemand.isNotEmpty()) {
                    unstaffedDemand.forEach { b ->
                        val cName = b.str("course_name").ifBlank { "Upcoming Training" }
                        val trainer = b.str("trainer_name").ifBlank { "Unassigned" }
                        val mode = b.str("delivery_mode").ifBlank { "Virtual" }
                        AttentionRow(
                            title = "$cName delivery needs trainer",
                            subtitle = "Trainer assignment pending - $mode",
                            actionText = "Review coverage",
                            iconColor = Color(0xFFDC2626),
                            onClick = onOpenDemand
                        )
                    }
                } else {
                    Text("No immediate action items found", fontSize = 14.sp, color = textSecondary)
                }
                
                if (pendingSkillRequests > 0) {
                    AttentionRow(
                        title = "$pendingSkillRequests skill review requests pending",
                        subtitle = "Awaiting review",
                        actionText = "Review",
                        iconColor = accent,
                        onClick = onOpenSkillRequests
                    )
                }
            }
        }

        // TODAY'S OPERATIONS
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Today's operations", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                
                if (activeBatches.isNotEmpty()) {
                    activeBatches.forEachIndexed { i, b ->
                        val cName = b.str("course_name").ifBlank { "Unnamed Course" }
                        val trainer = b.str("trainer_name").ifBlank { "Unknown Trainer" }
                        val mode = b.str("delivery_mode").ifBlank { "Virtual" }
                        OperationRow(cName, mode, trainer, "In Progress")
                        if (i < activeBatches.size - 1) {
                            HorizontalDivider(color = dividerColor, thickness = 1.dp)
                        }
                    }
                } else {
                    Text("No active deliveries scheduled today.", fontSize = 14.sp, color = textSecondary)
                }
            }
        }

        // WATCHLIST
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Watchlist", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                
                WatchlistRow("Trainer utilisation approaching capacity", "3 trainers > 85%")
                HorizontalDivider(color = dividerColor, thickness = 1.dp)
                WatchlistRow("Certification expiry risk", "2 key certifications expiring in 30 days")
            }
        }

        // COMING UP
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Coming up", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                
                WatchlistRow("AZ-500 Bootcamp", "Starts next Monday - 14 attendees")
            }
        }
    }
}

@Composable
fun AttentionRow(title: String, subtitle: String, actionText: String, iconColor: Color, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        color = Color.White,
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.size(20.dp).background(iconColor, RoundedCornerShape(4.dp)))
                Column {
                    Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF0F172A))
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(subtitle, fontSize = 13.sp, color = Color(0xFF64748B))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(actionText, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF2563EB))
            }
        }
    }
}

@Composable
fun OperationRow(course: String, time: String, trainer: String, location: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(course, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF0F172A))
            Spacer(modifier = Modifier.height(2.dp))
            Text(time, fontSize = 13.sp, color = Color(0xFF64748B))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(trainer, fontSize = 15.sp, color = Color(0xFF0F172A))
            Spacer(modifier = Modifier.height(2.dp))
            Text(location, fontSize = 13.sp, color = Color(0xFF64748B))
        }
    }
}

@Composable
fun WatchlistRow(title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF0F172A))
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, fontSize = 13.sp, color = Color(0xFF64748B))
        }
    }
}

private fun Map<*, *>.str(key: String): String = this[key]?.toString() ?: ""
private fun Map<*, *>.intOrNull(key: String): Int? = this[key]?.toString()?.toIntOrNull()

// Stub to satisfy MainScreen
fun managerBriefFromPayload(data: Map<String, Any>?): String = ""
