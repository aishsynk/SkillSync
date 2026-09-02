package com.example.skillsync.ui.report

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.skillsync.R
import com.example.skillsync.theme.*
import com.example.skillsync.ui.components.list
import com.example.skillsync.ui.components.str

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
                TopAppBar(
                    title = {
                        Column {
                            Text("Pre-Demand Radar", fontWeight = FontWeight.Bold, color = sk.bodyText, style = MaterialTheme.typography.titleLarge)
                            Text("Advance Sales SC pipeline (14–30d horizon)", color = sk.sky, style = MaterialTheme.typography.labelSmall)
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
                    is PipelineRadarState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = sk.brand)
                    }
                    is PipelineRadarState.Error -> Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("Could not load sales pipeline", color = sk.warn, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        Text(s.message, color = sk.subText, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { vm.refresh() }, colors = ButtonDefaults.buttonColors(containerColor = sk.brand)) {
                            Text("Retry")
                        }
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
                                    Surface(
                                        color = sk.surface1.copy(alpha = 0.85f),
                                        shape = RoundedCornerShape(Radii.card),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, sk.glassBorder),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Text("ADVANCE PIPELINE PULSE", style = MaterialTheme.typography.labelSmall, color = sk.labelText, fontWeight = FontWeight.Bold)
                                                ToneChip("Early Warning", tint = sk.cyan)
                                            }
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                PulseMetric(label = "Signed Orders", value = total.toString(), tint = sk.sky, modifier = Modifier.weight(1f))
                                                PulseMetric(label = "Team Covered", value = covered.toString(), tint = sk.good, modifier = Modifier.weight(1f))
                                                PulseMetric(label = "Action Needed", value = uncovered.toString(), tint = if (uncovered > 0) sk.warn else sk.subText, modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }

                                if (items.isEmpty()) {
                                    item {
                                        Surface(
                                            color = sk.surface2.copy(alpha = 0.6f),
                                            shape = RoundedCornerShape(Radii.card),
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                        ) {
                                            Text(
                                                "No pending advance Service Confirmations on the radar.",
                                                color = sk.subText,
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.padding(24.dp),
                                            )
                                        }
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

    Surface(
        color = sk.surface1.copy(alpha = 0.88f),
        shape = RoundedCornerShape(Radii.card),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isCovered) sk.cardBorder else sk.warn.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = scId,
                    style = MaterialTheme.typography.labelMedium,
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
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = sk.bodyText,
            )

            if (csm.isNotBlank()) {
                Text("CSM: $csm", style = MaterialTheme.typography.labelSmall, color = sk.subText)
            }

            HorizontalDivider(color = sk.cardBorder.copy(alpha = 0.5f))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Team Candidates", style = MaterialTheme.typography.labelSmall, color = sk.labelText)
                    if (isCovered) {
                        Text(
                            "${count} trainer${if (count == 1) "" else "s"} match skill",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = sk.good,
                        )
                    } else {
                        Text("0 trainers skilled", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = sk.warn)
                    }
                }

                Surface(
                    color = (if (isCovered) sk.sky else sk.warn).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(Radii.chip),
                    border = androidx.compose.foundation.BorderStroke(1.dp, (if (isCovered) sk.sky else sk.warn).copy(alpha = 0.28f)),
                ) {
                    Text(
                        text = action,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = if (isCovered) sk.sky else sk.warn,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            if (trainers.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
                    trainers.take(3).forEach { t ->
                        val tName = t.str("name")
                        val tEmail = t.str("email")
                        Surface(
                            color = sk.surface2.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, sk.cardBorder),
                            modifier = Modifier.clickable { onOpenTrainer(tEmail, tName) },
                        ) {
                            Text(
                                text = tName,
                                style = MaterialTheme.typography.labelSmall,
                                color = sk.ice,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
