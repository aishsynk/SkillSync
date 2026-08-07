package com.koenig.skilledge.presentation.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.koenig.skilledge.core.theme.SkillEdgeTheme
import com.koenig.skilledge.core.theme.TealPrimary
import com.koenig.skilledge.core.theme.AmberSecondary
import com.koenig.skilledge.core.theme.SuccessGreen
import com.koenig.skilledge.core.theme.ErrorRed
import com.koenig.skilledge.domain.models.UiState

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onSettingsClick: () -> Unit = {},
    onActionClick: (String) -> Unit = {},
    onTrainerClick: (String) -> Unit = {}
) {
    val intelligenceState by viewModel.intelligenceState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val cacheAge by viewModel.cacheAge.collectAsState()

    SkillEdgeTheme {
        Scaffold(
            topBar = {
                DashboardTopBar(
                    cacheAge = cacheAge,
                    onRefresh = { viewModel.refresh() },
                    onSettings = onSettingsClick,
                    isRefreshing = isRefreshing
                )
            }
        ) { padding ->
            when (intelligenceState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = TealPrimary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Loading your team intelligence...")
                        }
                    }
                }

                is UiState.Success -> {
                    val data = (intelligenceState as UiState.Success).data
                    DashboardContent(
                        viewModel = viewModel,
                        intelligence = data,
                        modifier = Modifier.padding(padding),
                        onActionClick = onActionClick,
                        onTrainerClick = onTrainerClick
                    )
                }

                is UiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text(
                                text = "⚠️",
                                fontSize = 48.sp,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            Text(
                                text = "Failed to Load Intelligence",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = (intelligenceState as UiState.Error).error,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { viewModel.refresh() },
                                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                            ) {
                                Text("Try Again")
                            }
                        }
                    }
                }

                is UiState.Empty -> {
                    // Initial empty state
                }
            }
        }
    }
}

@Composable
private fun DashboardTopBar(
    cacheAge: Int?,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    isRefreshing: Boolean
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = "Dashboard",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (cacheAge != null) {
                    Text(
                        text = "Data from $cacheAge minutes ago",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        actions = {
            IconButton(
                onClick = onRefresh,
                enabled = !isRefreshing
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun DashboardContent(
    viewModel: DashboardViewModel,
    intelligence: com.koenig.skilledge.domain.models.UnifiedManagerIntelligence,
    modifier: Modifier = Modifier,
    onActionClick: (String) -> Unit = {},
    onTrainerClick: (String) -> Unit = {}
) {
    val kpis = viewModel.getKpiData()
    val actions = viewModel.getRecentActions()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp)
    ) {
        if (kpis != null) {
            // Enterprise Intelligence Platform Header
            item {
                Text(
                    text = "Delivery Manager Intelligence",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            // Suite 1: Composite Team Readiness & Certification Coverage
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    EnterpriseKpiTile(
                        title = "Team Readiness Score",
                        value = "${kpis.teamReadinessScore}%",
                        subtitle = "Cert Coverage: ${kpis.certCoveragePct}%",
                        color = SuccessGreen,
                        modifier = Modifier.weight(1f)
                    )

                    EnterpriseKpiTile(
                        title = "Utilization & Trend",
                        value = "${kpis.avgUtilization}%",
                        subtitle = kpis.utilizationTrend,
                        color = TealPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }

            // Suite 2: Capacity & Allocation Breakdown
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Capacity & Load Distribution",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            CapacityMetric("Bench (<60%)", "${kpis.benchTrainers} Trainers", SuccessGreen)
                            CapacityMetric("Optimal (60-85%)", "${kpis.optimalTrainers} Trainers", TealPrimary)
                            CapacityMetric("Overloaded (>85%)", "${kpis.overloadedTrainers} Trainers", ErrorRed)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }

            // Suite 3: Delivery Risk & Regional Distribution Matrix
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    EnterpriseKpiTile(
                        title = "Delivery Risk Matrix",
                        value = "${kpis.deliveryRiskCount} Risk Cases",
                        subtitle = "Feedback/MTI Incidents",
                        color = ErrorRed,
                        modifier = Modifier.weight(1f)
                    )

                    EnterpriseKpiTile(
                        title = "International Deliveries",
                        value = "${kpis.internationalBatches} Global",
                        subtitle = "Domestic: ${kpis.domesticBatches}",
                        color = AmberSecondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            // Attention Queue Section
            if (actions.isNotEmpty()) {
                item {
                    Text(
                        text = "Attention Queue & Skill Workflows",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                items(actions) { action ->
                    ActionQueueCard(
                        action = action,
                        onClick = { onActionClick(action.actionId) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }
            }

            // Primary Unallocated Opportunities Preview
            if (intelligence.unallocatedDemandDf.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Primary Allocation Opportunities",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${intelligence.unallocatedDemandDf.size} Available",
                            fontSize = 12.sp,
                            color = TealPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                items(intelligence.unallocatedDemandDf.take(5)) { demand ->
                    DemandCard(
                        demand = demand,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun EnterpriseKpiTile(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(105.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CapacityMetric(
    title: String,
    value: String,
    color: Color
) {
    Column {
        Text(
            text = title,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun ActionQueueCard(
    action: ActionQueueItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = action.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = when (action.priority) {
                        "High" -> ErrorRed.copy(alpha = 0.1f)
                        else -> AmberSecondary.copy(alpha = 0.1f)
                    },
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = action.priority,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when (action.priority) {
                            "High" -> ErrorRed
                            else -> AmberSecondary
                        },
                        modifier = Modifier.padding(4.dp, 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${action.trainer} · ${action.type}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DemandCard(
    demand: com.koenig.skilledge.domain.models.UnallocatedDemand,
    modifier: Modifier = Modifier
) {
    val isInt = demand.isInternational
    val borderColor = if (isInt) AmberSecondary else MaterialTheme.colorScheme.outlineVariant

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (isInt) 1.5.dp else 1.dp, borderColor),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isInt) {
                        Text(text = "🌐 ${demand.flagEmoji}", fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = demand.courseName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                Surface(
                    color = if (isInt) AmberSecondary.copy(alpha = 0.15f) else TealPrimary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (isInt) "INT'L OPPORTUNITY" else demand.deliveryMode,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isInt) AmberSecondary else TealPrimary,
                        modifier = Modifier.padding(6.dp, 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${demand.startDate} · ${demand.location ?: "Global"} · ${demand.customer ?: "Koenig Partner"}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (demand.mismatchConstraints.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "⚠️ ${demand.mismatchConstraints.first()}",
                    fontSize = 10.sp,
                    color = ErrorRed,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
