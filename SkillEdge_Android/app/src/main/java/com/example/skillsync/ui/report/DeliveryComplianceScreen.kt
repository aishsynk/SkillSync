package com.example.skillsync.ui.report

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.skillsync.R
import com.example.skillsync.theme.*
import com.example.skillsync.ui.components.LocalNotify
import com.example.skillsync.ui.components.list
import com.example.skillsync.ui.components.str

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryComplianceScreen(
    managerEmail: String,
    onOpenTrainer: (email: String, name: String) -> Unit = { _, _ -> },
    onBack: () -> Unit,
    vm: DeliveryComplianceViewModel = viewModel(),
) {
    val sk = MaterialTheme.skill
    val context = LocalContext.current

    LaunchedEffect(managerEmail) {
        if (managerEmail.isNotBlank()) vm.init(managerEmail)
    }

    val state by vm.state.collectAsState()
    val refreshing by vm.refreshing.collectAsState()

    fun copyNudge(message: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Nudge Message", message))
        android.widget.Toast.makeText(context.applicationContext, "Copied nudge to clipboard", android.widget.Toast.LENGTH_SHORT).show()
    }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Delivery Sentinel", fontWeight = FontWeight.Bold, color = sk.bodyText, style = MaterialTheme.typography.titleLarge)
                            Text("Daily session recording audit across reportees", color = sk.sky, style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_back), "Back", tint = sk.ice)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (val s = state) {
                    is DeliveryComplianceState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = sk.brand)
                    }
                    is DeliveryComplianceState.Error -> Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("Could not load compliance data", color = sk.warn, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        Text(s.message, color = sk.subText, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { vm.refresh() }, colors = ButtonDefaults.buttonColors(containerColor = sk.brand)) {
                            Text("Retry")
                        }
                    }
                    is DeliveryComplianceState.Success -> {
                        val d = s.data
                        val active = d.list("active_deliveries")
                        val total = (d["total_active"] as? Number)?.toInt() ?: active.size
                        val compliant = (d["compliant_count"] as? Number)?.toInt() ?: 0
                        val violations = (d["violations_count"] as? Number)?.toInt() ?: 0
                        val rate = (d["compliance_rate_percent"] as? Number)?.toDouble() ?: 100.0

                        PullToRefreshBox(
                            isRefreshing = refreshing,
                            onRefresh = { vm.refresh() },
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            LazyColumn(
                                Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                item {
                                    Surface(
                                        color = sk.surface1.copy(alpha = 0.85f),
                                        shape = RoundedCornerShape(Radii.card),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, sk.glassBorder),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Text("RECORDING COMPLIANCE SCORECARD", style = MaterialTheme.typography.labelSmall, color = sk.labelText, fontWeight = FontWeight.Bold)
                                                ToneChip("${rate}% Rate", tint = if (violations > 0) sk.crit else sk.good)
                                            }
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                PulseMetric(label = "Active Batches", value = total.toString(), tint = sk.sky, modifier = Modifier.weight(1f))
                                                PulseMetric(label = "Recordings OK", value = compliant.toString(), tint = sk.good, modifier = Modifier.weight(1f))
                                                PulseMetric(label = "Missing Uploads", value = violations.toString(), tint = if (violations > 0) sk.crit else sk.subText, modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }

                                if (active.isEmpty()) {
                                    item {
                                        Surface(
                                            color = sk.surface2.copy(alpha = 0.6f),
                                            shape = RoundedCornerShape(Radii.card),
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                        ) {
                                            Text(
                                                "No active deliveries running today across your reportees.",
                                                color = sk.subText,
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.padding(24.dp),
                                            )
                                        }
                                    }
                                } else {
                                    items(active) { deliveryMap ->
                                        ComplianceDeliveryCard(
                                            delivery = deliveryMap,
                                            onOpenTrainer = onOpenTrainer,
                                            onNudge = { msg -> copyNudge(msg) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PulseMetric(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    val sk = MaterialTheme.skill
    Surface(
        color = sk.surface2.copy(alpha = 0.5f),
        shape = RoundedCornerShape(Radii.kpi),
        border = androidx.compose.foundation.BorderStroke(1.dp, sk.cardBorder),
        modifier = modifier,
    ) {
        Column(Modifier.padding(vertical = 10.dp, horizontal = 12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tint)
            Text(label, style = MaterialTheme.typography.labelSmall, color = sk.subText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ComplianceDeliveryCard(
    delivery: Map<*, *>,
    onOpenTrainer: (String, String) -> Unit,
    onNudge: (String) -> Unit,
) {
    val sk = MaterialTheme.skill
    val trainerName = delivery.str("trainer_name")
    val trainerEmail = delivery.str("trainer_email")
    val courseName = delivery.str("course_name")
    val assignmentId = delivery.str("assignment_id")
    val currentDay = (delivery["current_day"] as? Number)?.toInt() ?: 1
    val totalDays = (delivery["total_days"] as? Number)?.toInt() ?: 1
    val status = delivery.str("compliance_status")
    val nudgeMsg = delivery.str("nudge_message")
    val recCount = (delivery["recording_count"] as? Number)?.toInt() ?: 0

    val isViolation = status == "RECORDING_MISSING_URGENT"
    val isCompliant = status == "COMPLIANT"

    val statusColor = when {
        isViolation -> sk.crit
        isCompliant -> sk.good
        else -> sk.warn
    }

    val statusText = when {
        isViolation -> "Recording Missing (Day ${currentDay - 1})"
        isCompliant -> "All Recordings Uploaded ($recCount/$totalDays)"
        else -> "Session in Progress (Day $currentDay)"
    }

    Surface(
        color = sk.surface1.copy(alpha = 0.88f),
        shape = RoundedCornerShape(Radii.card),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isViolation) sk.crit.copy(alpha = 0.45f) else sk.cardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = trainerName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = sk.bodyText,
                    modifier = Modifier.clickable { onOpenTrainer(trainerEmail, trainerName) },
                )
                ToneChip(text = "Day $currentDay of $totalDays", tint = sk.sky)
            }

            Text(
                text = "$courseName (Assignment #$assignmentId)",
                style = MaterialTheme.typography.bodyMedium,
                color = sk.subText,
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(8.dp).background(statusColor, RoundedCornerShape(4.dp)))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor,
                    )
                }

                if (isViolation && nudgeMsg.isNotBlank()) {
                    Button(
                        onClick = { onNudge(nudgeMsg) },
                        colors = ButtonDefaults.buttonColors(containerColor = sk.brand),
                        shape = RoundedCornerShape(Radii.chip),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_copy), "Copy", tint = sk.navy, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Nudge Trainer", style = MaterialTheme.typography.labelSmall, color = sk.navy, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
