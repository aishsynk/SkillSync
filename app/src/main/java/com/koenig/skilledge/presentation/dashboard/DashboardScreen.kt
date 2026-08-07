package com.koenig.skilledge.presentation.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.koenig.skilledge.core.theme.CyanAccent
import com.koenig.skilledge.core.theme.ElectricBlue
import com.koenig.skilledge.core.theme.ErrorRed
import com.koenig.skilledge.core.theme.GlassBorder
import com.koenig.skilledge.core.theme.NavyCard
import com.koenig.skilledge.core.theme.NavyPrimary
import com.koenig.skilledge.core.theme.SkillEdgeTheme
import com.koenig.skilledge.core.theme.SuccessGreen
import com.koenig.skilledge.core.theme.WarningYellow
import com.koenig.skilledge.domain.models.TrainerOperation
import com.koenig.skilledge.domain.models.UiState
import com.koenig.skilledge.domain.models.UnifiedManagerIntelligence
import com.koenig.skilledge.presentation.common.CapacityDonutChart
import com.koenig.skilledge.presentation.common.NotificationCenterDialog
import com.koenig.skilledge.presentation.common.ReadinessRingGauge
import com.koenig.skilledge.presentation.common.SparklineChart

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onSettingsClick: () -> Unit = {},
    onActionClick: (String) -> Unit = {},
    onTrainerClick: (String) -> Unit = {}
) {
    val intelligenceState by viewModel.intelligenceState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val showNotifDialog by viewModel.showNotificationDialog.collectAsState()

    SkillEdgeTheme {
        Scaffold(
            topBar = {
                val data = (intelligenceState as? UiState.Success)?.data
                ExecutiveDashboardTopBar(
                    managerName = data?.organizationIntelligence?.firstOrNull()?.name ?: "Delivery Manager",
                    unreadNotifCount = data?.notifications?.size ?: 3,
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    onNotifClick = { viewModel.toggleNotificationDialog(true) }
                )
            },
            containerColor = NavyPrimary
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(NavyPrimary)
            ) {
                when (intelligenceState) {
                    is UiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = CyanAccent, modifier = Modifier.size(44.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Loading Executive Cockpit...", color = Color.White, fontSize = 14.sp)
                            }
                        }
                    }

                    is UiState.Success -> {
                        val data = (intelligenceState as UiState.Success).data
                        ExecutiveDashboardContent(
                            intelligence = data,
                            onActionClick = onActionClick,
                            onTrainerClick = onTrainerClick
                        )

                        if (showNotifDialog) {
                            NotificationCenterDialog(
                                notifications = data.notifications,
                                onDismiss = { viewModel.toggleNotificationDialog(false) },
                                onMarkAllRead = { viewModel.toggleNotificationDialog(false) }
                            )
                        }
                    }

                    is UiState.Error -> {
                        val err = (intelligenceState as UiState.Error).message
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Error",
                                    tint = ErrorRed,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Cockpit Load Failure", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(err, color = Color(0xFF94A3B8), fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { viewModel.refresh() },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                                ) {
                                    Text("Retry Load")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact, Professional Executive Profile & Top Bar
 */
@Composable
fun ExecutiveDashboardTopBar(
    managerName: String,
    unreadNotifCount: Int,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onNotifClick: () -> Unit
) {
    Surface(
        color = NavyCard,
        tonalElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GlassBorder, RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Compact Profile Pill
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(CyanAccent.copy(alpha = 0.2f))
                        .border(1.dp, CyanAccent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile",
                        tint = CyanAccent,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = managerName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(SuccessGreen)
                        )
                    }
                    Text(
                        text = "Delivery Manager Cockpit",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            // Right: Notification Bell & Refresh Action
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Notification Bell with Badge
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onNotifClick)
                        .padding(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Alerts",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    if (unreadNotifCount > 0) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(ErrorRed)
                                .align(Alignment.TopEnd),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$unreadNotifCount",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onRefresh,
                    enabled = !isRefreshing
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Data",
                        tint = CyanAccent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ExecutiveDashboardContent(
    intelligence: UnifiedManagerIntelligence,
    onActionClick: (String) -> Unit,
    onTrainerClick: (String) -> Unit
) {
    val kpis = intelligence.executiveIntelligence
    val trainers = intelligence.trainerOperationsDf
    val actions = intelligence.managerActionObjects

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        // ── 1. Executive KPI Summary Suite (6 High-Density Cards) ──
        item {
            ExecutiveKpiGrid(
                readinessScore = 100,
                readinessTrend = "+2.4%",
                avgUtil = 76,
                utilTrend = "+4.2%",
                utilHistory = listOf(68f, 71f, 74f, 72f, 76f),
                benchCount = 2,
                optimalCount = 6,
                stretchedCount = 2,
                highRiskCount = 1,
                certCoveragePct = 100,
                intBatches = 7,
                domBatches = 1
            )
        }

        // ── 2. Critical Attention Queue Section ──
        item {
            ExecutiveSectionHeader(title = "⚡ Priority Attention Queue", count = actions.size)
        }

        if (actions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GlassBorder, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = NavyCard)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Clear", tint = SuccessGreen)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "No pending risks or approvals. All operations normal.",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        } else {
            items(actions.take(3)) { action ->
                ExecutiveActionQueueCard(
                    actionId = action.actionId,
                    title = action.title,
                    trainer = action.trainerName,
                    priority = action.priority,
                    category = action.category,
                    onClick = { onActionClick(action.actionId) }
                )
            }
        }

        // ── 3. Live Trainer Pool Roster ──
        item {
            ExecutiveSectionHeader(title = "👥 Operational Trainer Pool", count = trainers.size)
        }

        items(trainers.take(6)) { trainer ->
            ExecutiveTrainerRowCard(
                trainer = trainer,
                onClick = { onTrainerClick(trainer.empCode) }
            )
        }
    }
}

/**
 * 6 Executive Cockpit KPI Grid
 */
@Composable
fun ExecutiveKpiGrid(
    readinessScore: Int,
    readinessTrend: String,
    avgUtil: Int,
    utilTrend: String,
    utilHistory: List<Float>,
    benchCount: Int,
    optimalCount: Int,
    stretchedCount: Int,
    highRiskCount: Int,
    certCoveragePct: Int,
    intBatches: Int,
    domBatches: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card 1: Team Readiness Score
            CockpitKpiCard(
                modifier = Modifier.weight(1f),
                title = "Team Readiness",
                primaryValue = "$readinessScore%",
                trendBadge = readinessTrend,
                trendPositive = true,
                statusChip = "Target Met",
                chipColor = SuccessGreen,
                rightGraphic = { ReadinessRingGauge(score = readinessScore) }
            )

            // Card 2: Average Utilization
            CockpitKpiCard(
                modifier = Modifier.weight(1f),
                title = "Avg Utilization",
                primaryValue = "$avgUtil%",
                trendBadge = utilTrend,
                trendPositive = true,
                statusChip = "Optimal Range",
                chipColor = CyanAccent,
                bottomGraphic = { SparklineChart(dataPoints = utilHistory) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card 3: Capacity Distribution
            CockpitKpiCard(
                modifier = Modifier.weight(1f),
                title = "Capacity Distribution",
                primaryValue = "${optimalCount + benchCount + stretchedCount} Members",
                subDetails = "6 Opt • 2 Bench • 2 Stretch",
                statusChip = "Balanced",
                chipColor = SuccessGreen,
                rightGraphic = {
                    CapacityDonutChart(
                        benchCount = benchCount,
                        optimalCount = optimalCount,
                        stretchedCount = stretchedCount
                    )
                }
            )

            // Card 4: Delivery Risk Matrix
            CockpitKpiCard(
                modifier = Modifier.weight(1f),
                title = "Delivery Risks",
                primaryValue = "$highRiskCount High Risk",
                subDetails = "1 Feedback Alert",
                statusChip = "Action Needed",
                chipColor = ErrorRed,
                trendBadge = "Escalate",
                trendPositive = false
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card 5: Vendor Cert Coverage
            CockpitKpiCard(
                modifier = Modifier.weight(1f),
                title = "Cert Coverage",
                primaryValue = "$certCoveragePct%",
                subDetails = "MSFT • AWS • CISCO • RH",
                statusChip = "Accredited",
                chipColor = SuccessGreen
            )

            // Card 6: International Split
            CockpitKpiCard(
                modifier = Modifier.weight(1f),
                title = "Delivery Scope",
                primaryValue = "$intBatches Overseas 🌐",
                subDetails = "🇬🇧 🇺🇸 🇦🇪 🇸🇬 | $domBatches Local",
                statusChip = "High Revenue",
                chipColor = ElectricBlue
            )
        }
    }
}

/**
 * Reusable Enterprise Cockpit KPI Card Component
 */
@Composable
fun CockpitKpiCard(
    modifier: Modifier = Modifier,
    title: String,
    primaryValue: String,
    subDetails: String? = null,
    trendBadge: String? = null,
    trendPositive: Boolean = true,
    statusChip: String? = null,
    chipColor: Color = CyanAccent,
    rightGraphic: (@Composable () -> Unit)? = null,
    bottomGraphic: (@Composable () -> Unit)? = null
) {
    Card(
        modifier = modifier.border(1.dp, GlassBorder, RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8)
                )

                if (trendBadge != null) {
                    Surface(
                        color = (if (trendPositive) SuccessGreen else ErrorRed).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = trendBadge,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (trendPositive) SuccessGreen else ErrorRed
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = primaryValue,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    if (subDetails != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subDetails,
                            fontSize = 10.sp,
                            color = Color(0xFFCBD5E1),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (rightGraphic != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    rightGraphic()
                }
            }

            if (bottomGraphic != null) {
                Spacer(modifier = Modifier.height(8.dp))
                bottomGraphic()
            }

            if (statusChip != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = chipColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = statusChip,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = chipColor
                    )
                }
            }
        }
    }
}

@Composable
fun ExecutiveSectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Surface(
            color = CyanAccent.copy(alpha = 0.2f),
            shape = CircleShape
        ) {
            Text(
                text = "$count",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CyanAccent
            )
        }
    }
}

@Composable
fun ExecutiveActionQueueCard(
    actionId: String,
    title: String,
    trainer: String,
    priority: String,
    category: String,
    onClick: () -> Unit
) {
    val prioColor = when (priority.uppercase()) {
        "HIGH", "CRITICAL" -> ErrorRed
        "MEDIUM" -> WarningYellow
        else -> CyanAccent
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(1.dp, GlassBorder, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = NavyCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = prioColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = priority.uppercase(),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = prioColor
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = category,
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = "Trainer: $trainer",
                    fontSize = 11.sp,
                    color = Color(0xFFCBD5E1)
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Open",
                tint = CyanAccent
            )
        }
    }
}

@Composable
fun ExecutiveTrainerRowCard(
    trainer: TrainerOperation,
    onClick: () -> Unit
) {
    val util = trainer.currentUtilization ?: 0
    val riskColor = when (trainer.feedbackRisk) {
        "High" -> ErrorRed
        "Medium" -> WarningYellow
        else -> SuccessGreen
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(1.dp, GlassBorder, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = NavyCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = trainer.trainerName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = "${trainer.designation} • ${trainer.primarySkill}",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    color = riskColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "$util% Util",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = riskColor
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = trainer.capacityBucket,
                    fontSize = 10.sp,
                    color = Color(0xFFCBD5E1)
                )
            }
        }
    }
}
