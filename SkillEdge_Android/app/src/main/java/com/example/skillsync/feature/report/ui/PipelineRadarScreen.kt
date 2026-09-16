package com.example.skillsync.feature.report.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.skillsync.R
import com.example.skillsync.core.ui.list
import com.example.skillsync.core.ui.str
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import com.example.skillsync.core.ui.ShimmerBox
import com.example.skillsync.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PipelineRadarScreen(
    managerEmail: String,
    onOpenTrainer: (email: String, name: String) -> Unit = { _, _ -> },
    onBack: () -> Unit,
    vm: PipelineRadarViewModel = viewModel(),
) {
    val sk = MaterialTheme.skill
    LaunchedEffect(managerEmail) {
        if (managerEmail.isNotBlank()) vm.init(managerEmail)
    }

    val state by vm.state.collectAsState()
    val refreshing by vm.refreshing.collectAsState()

    Box(Modifier.fillMaxSize()) {
        AuroraBackground()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                SkillSyncTopBar(
                    title = "Pre-Demand Radar",
                    subtitle = "Advance Sales Pipeline (14–30d Horizon)",
                    onBack = onBack,
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (val s = state) {
                    is PipelineRadarState.Loading -> PreDemandSkeleton()
                    is PipelineRadarState.Error -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        SkillSyncErrorState(
                            message = s.message,
                            onRetry = { vm.refresh() },
                        )
                    }
                    is PipelineRadarState.Success -> {
                        val d = s.data
                        val items = d.list("pipeline_items")
                        val total = (d["total_orders"] as? Number)?.toInt() ?: items.size
                        val covered = (d["covered_orders"] as? Number)?.toInt() ?: 0
                        val uncovered = (d["uncovered_orders"] as? Number)?.toInt() ?: 0

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
                                    val horizonNote = if (total > 0) {
                                        "$total signed confirmation" + (if (total == 1) "" else "s") +
                                            " in the 14-30 day horizon" +
                                            (if (uncovered > 0) ", $uncovered with nobody on the team able to teach it yet." else ", all coverable by the team.")
                                    } else {
                                        "Nothing signed in the 14-30 day horizon."
                                    }
                                    Text(
                                        horizonNote,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = sk.bodyText,
                                    )
                                }

                                // Pulse Metric Strip
                                item {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        SkillSyncMetric(
                                            label = "Signed Orders",
                                            value = total.toString(),
                                            tint = sk.sky,
                                            modifier = Modifier.weight(1f),
                                        )
                                        SkillSyncMetric(
                                            label = "Team Covered",
                                            value = covered.toString(),
                                            tint = sk.good,
                                            modifier = Modifier.weight(1f),
                                        )
                                        SkillSyncMetric(
                                            label = "Action Needed",
                                            value = uncovered.toString(),
                                            tint = if (uncovered > 0) sk.warn else sk.subText,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }

                                if (items.isEmpty()) {
                                    item {
                                        SkillSyncEmptyState(
                                            title = "No signed or likely demand detected in the next 14-30 days.",
                                            description = "Advance Service Confirmations land here as sales confirms them, with the " +
                                                "courses, lead times and which reportees can already teach them. Nothing is " +
                                                "projected or invented while the radar is clear.",
                                            iconRes = R.drawable.ic_search,
                                        )
                                    }
                                } else {
                                    items(items) { itemMap ->
                                        PipelineItemCard(itemMap, onOpenTrainer)
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
private fun PipelineItemCard(item: Map<*, *>, onOpenTrainer: (String, String) -> Unit) {
    val sk = MaterialTheme.skill
    val scId = item.str("sc_id")
    val courseName = item.str("course_name")
    val csm = item.str("csm")
    val leadDays = (item["lead_time_days"] as? Number)?.toInt() ?: 0
    val trainers = item.list("matching_trainers")
    val count = (item["matching_trainers_count"] as? Number)?.toInt() ?: trainers.size
    val action = item.str("recommended_action")
    val isCovered = count > 0

    SkillSyncCard(
        modifier = Modifier.fillMaxWidth(),
        severity = if (isCovered) null else Severity.Warning,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (scId.startsWith("SC", ignoreCase = true)) "Confirmed Order #$scId" else "Order Reference #$scId",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = sk.cyan,
            )
            ToneChip(
                text = if (leadDays <= 1) "Signed Today" else "Signed ${leadDays}d ago",
                tint = if (leadDays > 14) sk.amber else sk.sky,
            )
        }

        Text(
            text = courseName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = sk.frost,
        )

        if (csm.isNotBlank()) {
            Text(
                text = "Client Success Lead: $csm",
                style = MaterialTheme.typography.bodySmall,
                color = sk.subText,
            )
        }

        HorizontalDivider(color = sk.cardBorder.copy(alpha = 0.5f))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Team Readiness", style = MaterialTheme.typography.labelSmall, color = sk.labelText)
                if (isCovered) {
                    Text(
                        "${count} verified trainer${if (count == 1) "" else "s"} available",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = sk.good,
                    )
                } else {
                    Text(
                        "0 trainers skilled · Staffing required",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = sk.warn,
                    )
                }
            }

            if (action.isNotBlank()) {
                ToneChip(
                    text = action,
                    tint = if (isCovered) sk.sky else sk.warn,
                )
            }
        }

        if (trainers.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                trainers.take(3).forEach { t ->
                    val tName = t.str("name")
                    val tEmail = t.str("email")
                    ToneChip(
                        text = tName,
                        tint = sk.ice,
                        modifier = Modifier.pressable { onOpenTrainer(tEmail, tName) },
                    )
                }
            }
        }
    }
}


/**
 * Radar loading state: the shape of the real page (horizon line, metric strip,
 * confirmation rows) in shimmer, so a slow build reads as "the radar is
 * sweeping" rather than as three empty grey boxes on a blank screen.
 */
@Composable
private fun PreDemandSkeleton() {
    val sk = MaterialTheme.skill
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = sk.sky)
            Spacer(Modifier.width(8.dp))
            Text(
                "Sweeping the 14-30 day pipeline\u2026",
                style = MaterialTheme.typography.bodySmall, color = sk.subText,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("SIGNED ORDERS", "TEAM COVERED", "ACTION NEEDED").forEach { label ->
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Radii.kpi))
                        .background(sk.surface1)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ShimmerBox(width = 28.dp, height = 22.dp)
                    Text(
                        label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = sk.labelText, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    )
                }
            }
        }
        repeat(3) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.card))
                    .background(sk.surface1)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShimmerBox(width = 30.dp, height = 30.dp, shape = RoundedCornerShape(8.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ShimmerBox(width = 190.dp, height = 12.dp)
                        ShimmerBox(width = 120.dp, height = 10.dp)
                    }
                }
                ShimmerBox(height = 10.dp, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
