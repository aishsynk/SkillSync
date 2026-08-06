package com.koenig.skilledge.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
        // KPI Cards Section
        if (kpis != null) {
            item {
                Text(
                    text = "Team Deployment",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    KpiCard(
                        title = "Reportees",
                        value = kpis.reporteeCount.toString(),
                        color = TealPrimary,
                        modifier = Modifier.width(120.dp)
                    )

                    KpiCard(
                        title = "Delivering Now",
                        value = kpis.liveCourses.toString(),
                        color = SuccessGreen,
                        modifier = Modifier.width(120.dp)
                    )

                    KpiCard(
                        title = "Upcoming",
                        value = kpis.upcomingBatches.toString(),
                        color = AmberSecondary,
                        modifier = Modifier.width(120.dp)
                    )

                    KpiCard(
                        title = "Avg Utilization",
                        value = "${kpis.avgUtilization}%",
                        color = TealPrimary,
                        modifier = Modifier.width(120.dp)
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            // Capacity Section
            item {
                Text(
                    text = "Capacity & Utilization",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Available Capacity",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = kpis.capacityHeadroom.toString(),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen
                        )
                        Text(
                            text = "trainers < 60% utilized",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Overloaded",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = kpis.overloaded.toString(),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = ErrorRed
                        )
                        Text(
                            text = "trainers > 85% utilized",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            // Manager Control Section
            item {
                Text(
                    text = "Manager Control",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ControlMetric(
                        title = "Open Actions",
                        value = kpis.openActions.toString(),
                        color = AmberSecondary,
                        modifier = Modifier.weight(1f)
                    )

                    ControlMetric(
                        title = "Blocked",
                        value = kpis.blockedAllocations.toString(),
                        color = ErrorRed,
                        modifier = Modifier.weight(1f)
                    )

                    ControlMetric(
                        title = "Feedback",
                        value = kpis.feedbackCases.toString(),
                        color = AmberSecondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            // Action Queue Section
            if (actions.isNotEmpty()) {
                item {
                    Text(
                        text = "Attention Queue",
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

            // Unallocated Demand Section
            if (intelligence.unallocatedDemandDf.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Unallocated Demand",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
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
private fun KpiCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(100.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.material3.BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun ControlMetric(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 22.sp,
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
        border = androidx.compose.material3.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
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
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.material3.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = demand.courseName,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${demand.startDate} · ${demand.deliveryMode}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun Modifier.clickable(onClick: () -> Unit) = this
    .clickable(onClick = onClick, enabled = true)

@Composable
private fun androidx.compose.material3.BorderStroke(width: androidx.compose.ui.unit.Dp, color: Color) =
    androidx.compose.material3.BorderStroke(width, color)

@Composable
private fun rememberScrollState() = androidx.compose.foundation.rememberScrollState()
