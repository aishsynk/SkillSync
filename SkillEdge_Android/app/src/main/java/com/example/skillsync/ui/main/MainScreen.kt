package com.example.skillsync.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.skillsync.R
import com.example.skillsync.theme.StatusBarIcons
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.components.AnimatedCount
import com.example.skillsync.ui.components.Appear
import com.example.skillsync.ui.components.Motion
import com.example.skillsync.ui.components.ShimmerBox
import com.example.skillsync.ui.components.SkillSyncLogo
import com.example.skillsync.ui.components.animateProgressFromZero

// ── Screen entry point ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    email: String,
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel(),
) {
    LaunchedEffect(email) { viewModel.loadData(email) }
    val state by viewModel.uiState.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()

    StatusBarIcons(lightIcons = true)

    Scaffold(
        containerColor = MaterialTheme.skill.pageBg,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SkillSyncLogo(size = 30.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "SkillSync",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = Color.White,
                            )
                            Text(
                                "Manager Command Dashboard",
                                fontSize = 9.5.sp,
                                color = Color.White.copy(alpha = 0.78f),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                ),
            )
        },
    ) { pv ->
        Box(modifier.fillMaxSize().padding(pv)) {
            when (state) {
                is DashboardState.Loading -> DashboardSkeleton()
                is DashboardState.Error -> DashErrorView(
                    message = (state as DashboardState.Error).message,
                    onRetry = { viewModel.refresh(email) },
                )
                is DashboardState.Success -> PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = { viewModel.refresh(email) },
                ) {
                    DashboardContent((state as DashboardState.Success).intelligenceData)
                }
            }
        }
    }
}

// ── Loading & error states ────────────────────────────────────────────────────

/** Skeleton mirroring the real layout, so the page doesn't jump when data lands. */
@Composable
private fun DashboardSkeleton() {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.skill.pageBg)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ShimmerBox(height = 168.dp, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShimmerBox(height = 132.dp, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f))
            ShimmerBox(height = 132.dp, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
        ShimmerBox(width = 170.dp, height = 14.dp)
        repeat(4) {
            ShimmerBox(height = 116.dp, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DashErrorView(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_alert),
            contentDescription = null,
            tint = MaterialTheme.skill.amber,
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "Couldn't load your dashboard",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.skill.bodyText,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.skill.subText,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Text("Try again", style = MaterialTheme.typography.labelLarge)
        }
    }
}

// ── Dashboard content ─────────────────────────────────────────────────────────

@Composable
fun DashboardContent(data: Map<String, Any>) {
    val trainerOps    = data.listOf("trainer_operations_df")
    val trainerStates = data.listOf("trainer_current_state_df")
    val demands       = data.listOf("unallocated_demand_df")
    val openActions   = data.listOf("manager_action_objects").filter { it.str("lifecycle_state") != "closed" }
    val decisions     = data.listOf("trainer_decision_objects")

    val stateMap   = trainerStates.associateBy { it.str("trainer_email").lowercase() }
    val live       = trainerStates.count { it.str("current_status") == "teaching_now" }
    val preparing  = trainerStates.count { it.str("current_status") in setOf("scheduled_today", "preparing") }
    val unknown    = trainerStates.count { it.str("current_status") == "unknown" }
    val blocked    = decisions.count { it.str("assignment_status") == "blocked" }
    val negFeedback= trainerOps.count { it.int("negative_count") > 0 }

    val utilVals = trainerOps.mapNotNull { t ->
        when (val v = t["current_utilization"]) {
            is Number -> v.toDouble().takeIf { it.isFinite() && it > 0 }
            is String -> v.toDoubleOrNull()?.takeIf { it > 0 }
            else -> null
        }
    }
    val avgUtil   = if (utilVals.isNotEmpty()) utilVals.average().toInt() else 0
    val underUtil = utilVals.count { it < 60 }
    val overUtil  = utilVals.count { it > 85 }
    val known     = trainerStates.count { it.str("current_status") != "unknown" }
    val knownPct  = if (trainerOps.isNotEmpty()) known * 100 / trainerOps.size else 0

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.skill.pageBg),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 1 · Team deployment hero
        item {
            Appear(0) {
                HeroCard {
                    HeroLabel("TEAM DEPLOYMENT")
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        AnimatedCount(
                            target = trainerOps.size,
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.skill.heroText,
                        )
                        Text(
                            " reportees",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.skill.heroMuted,
                            modifier = Modifier.padding(start = 6.dp, top = 10.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        Chip("$knownPct% verified", MaterialTheme.skill.teal)
                    }
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DeployStat("Delivering", live, MaterialTheme.skill.teal, trainerOps.size, Modifier.weight(1f))
                        DeployStat("Upcoming", preparing, MaterialTheme.skill.blue, trainerOps.size, Modifier.weight(1f))
                        DeployStat("Unknown", unknown, MaterialTheme.skill.subText, trainerOps.size, Modifier.weight(1f))
                    }
                }
            }
        }

        // 2 · Capacity + control
        item {
            Appear(1) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroCard(Modifier.weight(1f), padding = 12.dp) {
                        HeroLabel("CAPACITY SIGNAL")
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            AnimatedCount(
                                target = avgUtil,
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.skill.heroText,
                                suffix = "%",
                            )
                        }
                        Text(
                            "avg utilization · ${utilVals.size} trainers",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.skill.subText,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                        MiniStat("< 60% util", underUtil, MaterialTheme.skill.green)
                        MiniStat("> 85% util", overUtil, MaterialTheme.skill.red)
                    }
                    HeroCard(Modifier.weight(1f), padding = 12.dp) {
                        HeroLabel("MANAGER CONTROL")
                        Spacer(Modifier.height(4.dp))
                        AnimatedCount(
                            target = openActions.size,
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.skill.heroText,
                        )
                        Text(
                            "open decisions",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.skill.subText,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                        MiniStat("Blocked", blocked, MaterialTheme.skill.red)
                        MiniStat("Neg feedback", negFeedback, MaterialTheme.skill.amber)
                        MiniStat("Demand", demands.size, MaterialTheme.skill.blue)
                    }
                }
            }
        }

        // 3 · Team roster
        item { Appear(2) { SectionHeader("Team — right now", trainerOps.size) } }

        if (trainerOps.isEmpty()) {
            item { EmptyCard("No reportees returned. Check your account permissions.") }
        } else {
            itemsIndexed(trainerOps) { i, trainer ->
                Appear(i + 3) {
                    TrainerCard(trainer, stateMap[trainer.str("official_email").lowercase()])
                }
            }
        }

        // 4 · Attention queue
        if (openActions.isNotEmpty()) {
            item { SectionHeader("Manager attention", openActions.size) }
            itemsIndexed(openActions.take(6)) { i, action ->
                Appear(i) { AttentionCard(action) }
            }
        }

        // 5 · Unallocated demand
        if (demands.isNotEmpty()) {
            item { SectionHeader("Unallocated sales demand", demands.size) }
            itemsIndexed(demands.take(8)) { i, demand ->
                Appear(i) { DemandCard(demand) }
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}

// ── Cards ─────────────────────────────────────────────────────────────────────

@Composable
private fun HeroCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.skill.heroBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(
            Modifier
                .background(
                    Brush.linearGradient(
                        listOf(MaterialTheme.skill.heroBg, MaterialTheme.skill.heroBgAlt)
                    )
                )
                .padding(padding),
            content = content,
        )
    }
}

@Composable
private fun TrainerCard(trainer: Map<*, *>, state: Map<*, *>?) {
    val name      = trainer.str("trainer_name")
    val desig     = trainer.str("designation").ifBlank { trainer.str("direct_or_indirect") }
    val util      = trainer.int("current_utilization")
    val fbRisk    = trainer.str("feedback_risk")
    val negCount  = trainer.int("negative_count")
    val readiness = trainer.str("readiness_bucket")
    val actionLbl = trainer.str("recommended_action")

    val status      = state?.str("current_status") ?: "unknown"
    val statusLabel = state?.str("status_label") ?: "Unknown"
    val confidence  = state?.int("confidence") ?: 0
    val curBatch    = state?.get("current_batch") as? Map<*, *>
    val nxtBatch    = state?.get("next_batch") as? Map<*, *>
    val curCourse   = curBatch?.str("course_name")?.takeIf { it.isNotBlank() }
    val curMode     = curBatch?.str("delivery_mode")?.takeIf { it.isNotBlank() }
    val nxtCourse   = nxtBatch?.str("course_name")?.takeIf { it.isNotBlank() }
    val nxtDate     = nxtBatch?.str("start_at")?.takeIf { it.isNotBlank() }

    val sk = MaterialTheme.skill
    val statusColor = when (status) {
        "teaching_now" -> sk.teal
        "scheduled_today" -> sk.blue
        "preparing" -> sk.indigo
        "free" -> sk.green
        "blocked" -> sk.red
        else -> sk.subText
    }
    val fbColor = when (fbRisk.lowercase()) {
        "high" -> sk.red
        "medium" -> sk.amber
        else -> sk.green
    }
    val utilColor = when {
        util > 85 -> sk.red
        util > 60 -> sk.teal
        util > 30 -> sk.amber
        else -> sk.subText
    }

    val utilProgress by animateProgressFromZero(util / 100f)
    val barColor by animateColorAsState(utilColor, tween(Motion.NORMAL), label = "barColor")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = sk.cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(statusColor.copy(alpha = 0.20f), statusColor.copy(alpha = 0.07f))
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        name.split(" ").filter { it.isNotBlank() }.take(2)
                            .joinToString("") { it.take(1).uppercase() },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = statusColor,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        color = sk.bodyText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (desig.isNotBlank()) {
                        Text(
                            desig,
                            style = MaterialTheme.typography.labelSmall,
                            color = sk.subText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Chip(statusLabel, statusColor)
            }

            Spacer(Modifier.height(10.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Util", style = MaterialTheme.typography.labelSmall, color = sk.subText, modifier = Modifier.width(26.dp))
                LinearProgressIndicator(
                    progress = { utilProgress },
                    modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = barColor,
                    trackColor = sk.track,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "$util%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = barColor,
                )
                if (confidence > 0) {
                    Text(
                        "  $confidence% conf.",
                        style = MaterialTheme.typography.labelSmall,
                        color = sk.subText,
                    )
                }
            }

            if (curCourse != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Text("▸ ", style = MaterialTheme.typography.labelSmall, color = sk.teal)
                    Column {
                        Text(
                            curCourse,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = sk.bodyText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!curMode.isNullOrBlank()) {
                            Text(curMode, style = MaterialTheme.typography.labelSmall, color = sk.subText)
                        }
                    }
                }
            }

            if (nxtCourse != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        append("↗ Next: ").append(nxtCourse)
                        if (!nxtDate.isNullOrBlank()) append("  ($nxtDate)")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = sk.subText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(9.dp))
            HorizontalDivider(color = sk.cardBorder)
            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Chip("FB: $fbRisk${if (negCount > 0) " ($negCount)" else ""}", fbColor)
                Spacer(Modifier.width(5.dp))
                Chip(readiness, sk.subText)
                Spacer(Modifier.weight(1f))
                Text(
                    actionLbl,
                    style = MaterialTheme.typography.labelSmall,
                    color = sk.subText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 150.dp),
                )
            }
        }
    }
}

@Composable
private fun AttentionCard(action: Map<*, *>) {
    val sk = MaterialTheme.skill
    val title    = action.str("title").ifBlank { "Manager action required" }
    val trainer  = action.str("trainer_name")
    val category = action.str("category").ifBlank { "Action" }
    val priority = action.str("priority")
    val catColor = when (category.lowercase()) {
        "feedback" -> sk.red
        "allocation" -> sk.blue
        else -> sk.amber
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = sk.cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row {
            Box(Modifier.width(3.dp).fillMaxHeight().background(catColor))
            Column(Modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Chip(category, catColor)
                    if (priority.equals("high", ignoreCase = true)) {
                        Spacer(Modifier.width(5.dp))
                        Chip("HIGH", sk.red)
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, color = sk.bodyText, maxLines = 2)
                if (trainer.isNotBlank()) {
                    Text(trainer, style = MaterialTheme.typography.labelSmall, color = sk.subText)
                }
            }
        }
    }
}

@Composable
private fun DemandCard(demand: Map<*, *>) {
    val sk = MaterialTheme.skill
    val course       = demand.str("course_name").ifBlank { "Course not specified" }
    val date         = demand.str("start_date")
    val mode         = demand.str("delivery_mode")
    val customer     = demand.str("customer")
    val participants = demand.str("participants")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = sk.cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(sk.blue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("!", style = MaterialTheme.typography.titleLarge, color = sk.blue)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    course,
                    style = MaterialTheme.typography.titleSmall,
                    color = sk.bodyText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOf(date, mode, customer, participants.takeIf { it.isNotBlank() }?.let { "$it pax" })
                        .filterNotNull().filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = sk.subText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Chip("Match", sk.blue)
        }
    }
}

// ── Small building blocks ─────────────────────────────────────────────────────

@Composable
private fun Chip(text: String, tint: Color) {
    Surface(color = tint.copy(alpha = 0.14f), shape = RoundedCornerShape(12.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun HeroLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.skill.heroMuted,
    )
}

@Composable
private fun DeployStat(label: String, count: Int, color: Color, total: Int, modifier: Modifier) {
    val p by animateProgressFromZero(if (total > 0) count.toFloat() / total else 0f)
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.skill.heroMuted)
        AnimatedCount(
            target = count,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.skill.heroText,
        )
        LinearProgressIndicator(
            progress = { p },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = color,
            trackColor = Color.White.copy(alpha = 0.10f),
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun MiniStat(label: String, value: Int, color: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
            Spacer(Modifier.width(5.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.skill.heroMuted)
        }
        Text(
            "$value",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.skill.heroText,
        )
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.skill.bodyText,
        )
        Spacer(Modifier.width(8.dp))
        Chip("$count", MaterialTheme.skill.subText)
    }
}

@Composable
private fun EmptyCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.skill.cardBg),
    ) {
        Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.skill.subText,
            )
        }
    }
}

// ── Extension helpers ─────────────────────────────────────────────────────────

private fun Map<String, Any>.listOf(key: String): List<Map<*, *>> =
    (this[key] as? List<*>)?.filterIsInstance<Map<*, *>>() ?: emptyList()

private fun Map<*, *>.str(key: String): String {
    val v = this[key] ?: return ""
    return if (v is String) v.trim() else v.toString().trim()
}

private fun Map<*, *>.int(key: String): Int = when (val v = this[key]) {
    null -> 0
    is Int -> v
    is Double -> v.toInt()
    is Float -> v.toInt()
    is Long -> v.toInt()
    is String -> v.toDoubleOrNull()?.toInt() ?: 0
    else -> 0
}
