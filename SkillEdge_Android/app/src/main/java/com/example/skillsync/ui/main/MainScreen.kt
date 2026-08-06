package com.example.skillsync.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
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
import com.example.skillsync.HomeTab
import com.example.skillsync.R
import com.example.skillsync.theme.StatusBarIcons
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.components.*

// ── Screen shell ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    email: String,
    tab: String,
    onTabChange: (String) -> Unit,
    onTrainerClick: (email: String, name: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel(),
) {
    LaunchedEffect(email) { viewModel.loadData(email) }
    val state by viewModel.uiState.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    StatusBarIcons(lightIcons = true)

    // Which KPI the manager tapped; drives the drill-down sheet.
    var drill by remember { mutableStateOf<Drill?>(null) }

    Scaffold(
        containerColor = MaterialTheme.skill.pageBg,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SkillSyncLogo(size = 30.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("SkillSync", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color.White)
                            Text(
                                tabTitle(tab),
                                fontSize = 9.5.sp, color = Color.White.copy(alpha = 0.78f),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
        bottomBar = { SkillSyncNavBar(tab, onTabChange) },
    ) { pv ->
        Box(modifier.fillMaxSize().padding(pv)) {
            when (val s = state) {
                is DashboardState.Loading -> DashboardSkeleton()
                is DashboardState.Error -> DashErrorView(s.message) { viewModel.refresh(email) }
                is DashboardState.Success -> PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = { viewModel.refresh(email) },
                ) {
                    val d = s.intelligenceData
                    when (tab) {
                        HomeTab.TEAM -> TeamTab(d, onTrainerClick)
                        HomeTab.DEMAND -> DemandTab(d)
                        HomeTab.ACTIONS -> ActionsTab(d)
                        else -> DashboardTab(d, onTrainerClick) { drill = it }
                    }
                }
            }
        }
    }

    drill?.let { DrillSheet(it) { drill = null } }
}

private fun tabTitle(tab: String) = when (tab) {
    HomeTab.TEAM -> "Team roster"
    HomeTab.DEMAND -> "Unallocated demand"
    HomeTab.ACTIONS -> "Manager actions"
    else -> "Manager Command Dashboard"
}

@Composable
internal fun SkillSyncNavBar(current: String, onSelect: (String) -> Unit) {
    val items = listOf(
        Triple(HomeTab.DASHBOARD, R.drawable.ic_home, "Home"),
        Triple(HomeTab.TEAM, R.drawable.ic_people, "Team"),
        Triple(HomeTab.DEMAND, R.drawable.ic_inbox, "Demand"),
        Triple(HomeTab.ACTIONS, R.drawable.ic_flag, "Actions"),
    )
    NavigationBar(containerColor = MaterialTheme.skill.cardBg, tonalElevation = 3.dp) {
        items.forEach { (key, icon, label) ->
            NavigationBarItem(
                selected = current == key,
                onClick = { onSelect(key) },
                icon = { Icon(painterResource(icon), contentDescription = label, modifier = Modifier.size(21.dp)) },
                label = { Text(label, fontSize = 10.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f),
                    unselectedIconColor = MaterialTheme.skill.subText,
                    unselectedTextColor = MaterialTheme.skill.subText,
                ),
            )
        }
    }
}

// ── Drill-down ────────────────────────────────────────────────────────────────

data class Drill(val title: String, val subtitle: String, val rows: List<Pair<String, String>>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrillSheet(drill: Drill, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.skill.cardBg) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(drill.title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.skill.bodyText)
            Text(drill.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.skill.subText)
            Spacer(Modifier.height(14.dp))
            if (drill.rows.isEmpty()) {
                Text(
                    "Nothing in this bucket right now.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.skill.subText,
                )
            }
            drill.rows.forEach { (primary, secondary) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(primary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.skill.bodyText)
                        if (secondary.isNotBlank()) {
                            Text(secondary, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.skill.subText)
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.skill.cardBorder)
            }
        }
    }
}

// ── Tabs ──────────────────────────────────────────────────────────────────────

@Composable
internal fun DashboardTab(
    data: Map<String, Any>,
    onTrainerClick: (String, String) -> Unit,
    onDrill: (Drill) -> Unit,
) {
    val sk = MaterialTheme.skill
    val ops = data.rows("trainer_operations_df")
    val states = data.rows("trainer_current_state_df")
    val demands = data.rows("unallocated_demand_df")
    val actions = data.rows("manager_action_objects").filter { it.str("lifecycle_state") != "closed" }
    val stateMap = states.associateBy { it.str("trainer_email").lowercase() }

    fun named(list: List<Map<*, *>>) = list.map {
        it.str("trainer_name") to "${it.int("current_utilization")}% · ${it.str("capacity_bucket").ifBlank { it.str("readiness_bucket") }}"
    }

    val live = states.filter { it.str("current_status") == "teaching_now" }
    val soon = states.filter { it.str("current_status") in setOf("scheduled_today", "preparing") }
    val unknown = states.filter { it.str("current_status") == "unknown" }
    val utilVals = ops.mapNotNull { it.intOrNull("current_utilization")?.takeIf { u -> u > 0 } }
    val avgUtil = if (utilVals.isNotEmpty()) utilVals.average().toInt() else 0
    val under = ops.filter { (it.intOrNull("current_utilization") ?: 0) in 1..59 }
    val over = ops.filter { (it.intOrNull("current_utilization") ?: 0) > 85 }

    fun stateNames(rows: List<Map<*, *>>) = rows.map { s ->
        val cur = s.obj("current_batch")?.str("course_name").orEmpty()
        val nxt = s.obj("next_batch")?.str("course_name").orEmpty()
        val who = ops.firstOrNull { it.str("official_email").equals(s.str("trainer_email"), true) }
            ?.str("trainer_name").orEmpty()
        who to listOf(cur.ifBlank { nxt }, s.str("reason")).filter { it.isNotBlank() }.joinToString(" · ")
    }

    LazyColumn(
        Modifier.fillMaxSize().background(sk.pageBg),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Appear(0) {
                HeroCard {
                    HeroLabel("TEAM DEPLOYMENT")
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        AnimatedCount(ops.size, MaterialTheme.typography.displaySmall, sk.heroText)
                        Text(
                            " reportees", style = MaterialTheme.typography.bodyLarge, color = sk.heroMuted,
                            modifier = Modifier.padding(start = 6.dp, top = 10.dp),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DeployStat("Delivering", live.size, sk.teal, ops.size, Modifier.weight(1f)) {
                            onDrill(Drill("Delivering now", "${live.size} trainer(s) mid-batch", stateNames(live)))
                        }
                        DeployStat("Upcoming", soon.size, sk.blue, ops.size, Modifier.weight(1f)) {
                            onDrill(Drill("Starting soon", "${soon.size} scheduled or preparing", stateNames(soon)))
                        }
                        DeployStat("Unknown", unknown.size, sk.subText, ops.size, Modifier.weight(1f)) {
                            onDrill(Drill("Status unknown", "RMS did not return assignment data", stateNames(unknown)))
                        }
                    }
                }
            }
        }

        item {
            Appear(1) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroCard(Modifier.weight(1f), padding = 12.dp) {
                        HeroLabel("CAPACITY SIGNAL")
                        Spacer(Modifier.height(4.dp))
                        AnimatedCount(avgUtil, MaterialTheme.typography.headlineLarge, sk.heroText, suffix = "%")
                        Text(
                            "avg of ${utilVals.size} with data",
                            style = MaterialTheme.typography.labelSmall, color = sk.subText,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                        MiniStat("Under 60%", under.size, sk.green) {
                            onDrill(Drill("Under-utilised", "Below 60% — room to allocate", named(under)))
                        }
                        MiniStat("Over 85%", over.size, sk.red) {
                            onDrill(Drill("Stretched", "Above 85% — protect from more load", named(over)))
                        }
                    }
                    HeroCard(Modifier.weight(1f), padding = 12.dp) {
                        HeroLabel("MANAGER CONTROL")
                        Spacer(Modifier.height(4.dp))
                        AnimatedCount(actions.size, MaterialTheme.typography.headlineLarge, sk.heroText)
                        Text(
                            "open decisions",
                            style = MaterialTheme.typography.labelSmall, color = sk.subText,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                        MiniStat("Demand", demands.size, sk.blue) {
                            onDrill(Drill(
                                "Unallocated demand", "${demands.size} batches without a trainer",
                                demands.map {
                                    it.str("course_name") to listOf(
                                        it.str("start_date").shortDate(), it.str("delivery_mode"),
                                        it.str("customer"),
                                    ).filter(String::isNotBlank).joinToString(" · ")
                                },
                            ))
                        }
                        MiniStat("Actions", actions.size, sk.amber) {
                            onDrill(Drill(
                                "Manager actions", "Open items",
                                actions.map { it.str("title") to it.str("trainer_name") },
                            ))
                        }
                    }
                }
            }
        }

        item { Appear(2) { SectionHeader("Team — right now", ops.size) } }

        if (ops.isEmpty()) {
            item { EmptyCard("No reportees returned. Check your account permissions.") }
        } else {
            itemsIndexed(ops) { i, t ->
                Appear(i + 3) {
                    TrainerCard(t, stateMap[t.str("official_email").lowercase()]) {
                        onTrainerClick(t.str("official_email"), t.str("trainer_name"))
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun TeamTab(data: Map<String, Any>, onTrainerClick: (String, String) -> Unit) {
    val ops = data.rows("trainer_operations_df")
    val stateMap = data.rows("trainer_current_state_df").associateBy { it.str("trainer_email").lowercase() }
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.skill.pageBg),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (ops.isEmpty()) item { EmptyCard("No reportees returned.") }
        itemsIndexed(ops) { i, t ->
            Appear(i) {
                TrainerCard(t, stateMap[t.str("official_email").lowercase()]) {
                    onTrainerClick(t.str("official_email"), t.str("trainer_name"))
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
internal fun DemandTab(data: Map<String, Any>) {
    val demands = data.rows("unallocated_demand_df")
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.skill.pageBg),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (demands.isEmpty()) item { EmptyCard("No unallocated demand right now.") }
        itemsIndexed(demands) { i, d -> Appear(i) { DemandCard(d) } }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun ActionsTab(data: Map<String, Any>) {
    val actions = data.rows("manager_action_objects").filter { it.str("lifecycle_state") != "closed" }
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.skill.pageBg),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (actions.isEmpty()) item { EmptyCard("No open manager actions.") }
        itemsIndexed(actions) { i, a -> Appear(i) { AttentionCard(a) } }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ── Loading & error ───────────────────────────────────────────────────────────

@Composable
private fun DashboardSkeleton() {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.skill.pageBg).padding(12.dp),
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
            ShimmerBox(height = 128.dp, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DashErrorView(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(R.drawable.ic_alert), null, tint = MaterialTheme.skill.amber, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(14.dp))
        Text("Couldn't load your dashboard", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.skill.bodyText)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.skill.subText, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry, shape = RoundedCornerShape(10.dp)) {
            Text("Try again", style = MaterialTheme.typography.labelLarge)
        }
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
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.skill.heroBg),
        elevation = CardDefaults.cardElevation(3.dp),
    ) {
        Column(
            Modifier
                .background(Brush.linearGradient(listOf(MaterialTheme.skill.heroBg, MaterialTheme.skill.heroBgAlt)))
                .padding(padding),
            content = content,
        )
    }
}

@Composable
internal fun TrainerCard(trainer: Map<*, *>, state: Map<*, *>?, onClick: () -> Unit) {
    val sk = MaterialTheme.skill
    val name = trainer.str("trainer_name")
    val desig = trainer.str("designation").ifBlank { trainer.str("direct_or_indirect") }
    val utilRaw = trainer.intOrNull("current_utilization")
    val capacity = trainer.str("capacity_bucket").ifBlank { trainer.str("readiness_bucket") }
    val negCount = trainer.int("negative_count")
    val upcoming = trainer.int("upcoming_count")

    val status = state?.str("current_status") ?: "unknown"
    val statusLabel = state?.str("status_label") ?: "Unknown"
    val confidence = state?.int("confidence") ?: 0
    val reason = state?.str("reason").orEmpty()
    val cur = state?.obj("current_batch")
    val nxt = state?.obj("next_batch")

    val statusColor = when (status) {
        "teaching_now" -> sk.teal
        "scheduled_today" -> sk.blue
        "preparing" -> sk.indigo
        "free" -> sk.green
        "blocked" -> sk.red
        else -> sk.subText
    }
    val utilColor = when {
        utilRaw == null || utilRaw == 0 -> sk.subText
        utilRaw > 85 -> sk.red
        utilRaw >= 60 -> sk.teal
        utilRaw >= 30 -> sk.amber
        else -> sk.subText
    }
    val utilProgress by animateProgressFromZero((utilRaw ?: 0) / 100f)
    val barColor by animateColorAsState(utilColor, tween(Motion.NORMAL), label = "bar")

    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = sk.cardBg),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(
                        Brush.linearGradient(
                            listOf(statusColor.copy(alpha = 0.20f), statusColor.copy(alpha = 0.07f))
                        )
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        name.split(" ").filter(String::isNotBlank).take(2)
                            .joinToString("") { it.take(1).uppercase() },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold, color = statusColor,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, color = sk.bodyText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (desig.isNotBlank()) {
                        Text(desig, style = MaterialTheme.typography.labelSmall, color = sk.subText, maxLines = 1)
                    }
                }
                Spacer(Modifier.width(6.dp))
                Chip(statusLabel, statusColor)
                Icon(
                    painterResource(R.drawable.ic_chevron), null,
                    tint = sk.subText, modifier = Modifier.size(15.dp).padding(start = 2.dp),
                )
            }

            // The batch the trainer is actually in — tinted by engagement.
            BatchBanner(cur, nxt, status, statusColor, reason)

            Spacer(Modifier.height(10.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Util", style = MaterialTheme.typography.labelSmall, color = sk.subText, modifier = Modifier.width(26.dp))
                LinearProgressIndicator(
                    progress = { utilProgress },
                    modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = barColor, trackColor = sk.track,
                    gapSize = 0.dp, drawStopIndicator = {},
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (utilRaw == null || utilRaw == 0) "—" else "$utilRaw%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold, color = barColor,
                )
            }

            Spacer(Modifier.height(9.dp))
            HorizontalDivider(color = sk.cardBorder)
            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Chip(capacity.ifBlank { "Unknown" }, if (capacity == "Unknown" || capacity.isBlank()) sk.subText else utilColor)
                if (upcoming > 0) {
                    Spacer(Modifier.width(5.dp))
                    Chip("$upcoming upcoming", sk.blue)
                }
                if (negCount > 0) {
                    Spacer(Modifier.width(5.dp))
                    Chip("$negCount neg", sk.red)
                }
                Spacer(Modifier.weight(1f))
                if (confidence > 0) {
                    Text("$confidence% conf.", style = MaterialTheme.typography.labelSmall, color = sk.subText)
                }
            }
        }
    }
}

/**
 * Shows the batch the trainer is occupied by. Background tint encodes engagement
 * so the roster is scannable: teal = delivering now, blue = starting soon,
 * grey = nothing scheduled or no data.
 */
@Composable
private fun BatchBanner(
    cur: Map<*, *>?,
    nxt: Map<*, *>?,
    status: String,
    statusColor: Color,
    reason: String,
) {
    val sk = MaterialTheme.skill
    val curName = cur?.str("course_name").orEmpty()
    val nxtName = nxt?.str("course_name").orEmpty()

    val (title, meta) = when {
        curName.isNotBlank() -> curName to listOfNotNull(
            cur?.str("delivery_mode")?.takeIf { it.isNotBlank() },
            cur?.str("vendor")?.takeIf { it.isNotBlank() },
            cur?.intOrNull("participants")?.takeIf { it > 0 }?.let { "$it pax" },
            cur?.intOrNull("days_left")?.let { if (it >= 0) "ends in $it d" else null },
        ).joinToString(" · ")
        nxtName.isNotBlank() -> nxtName to listOfNotNull(
            nxt?.str("start_at")?.takeIf { it.isNotBlank() }?.shortDate(),
            nxt?.str("delivery_mode")?.takeIf { it.isNotBlank() },
            nxt?.intOrNull("days_until")?.let { "in $it d" },
        ).joinToString(" · ")
        else -> "" to reason
    }

    if (title.isBlank() && meta.isBlank()) return

    Spacer(Modifier.height(9.dp))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (title.isBlank()) sk.track.copy(alpha = 0.5f) else statusColor.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_calendar), null,
            tint = if (title.isBlank()) sk.subText else statusColor,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            if (title.isNotBlank()) {
                Text(
                    title, style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold, color = sk.bodyText,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (meta.isNotBlank()) {
                Text(meta, style = MaterialTheme.typography.labelSmall, color = sk.subText, maxLines = 1)
            }
        }
        if (status == "teaching_now") {
            Spacer(Modifier.width(6.dp))
            Chip("LIVE", statusColor)
        }
    }
}

@Composable
private fun AttentionCard(action: Map<*, *>) {
    val sk = MaterialTheme.skill
    val category = action.str("category").ifBlank { "Action" }
    val catColor = when (category.lowercase()) {
        "feedback" -> sk.red
        "allocation" -> sk.blue
        else -> sk.amber
    }
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = sk.cardBg),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row {
            Box(Modifier.width(3.dp).fillMaxHeight().background(catColor))
            Column(Modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Chip(category, catColor)
                    if (action.str("priority").equals("high", true)) {
                        Spacer(Modifier.width(5.dp))
                        Chip("HIGH", sk.red)
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    action.str("title").ifBlank { "Manager action required" },
                    style = MaterialTheme.typography.titleSmall, color = sk.bodyText, maxLines = 2,
                )
                action.str("trainer_name").takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = sk.subText)
                }
            }
        }
    }
}

@Composable
private fun DemandCard(demand: Map<*, *>) {
    val sk = MaterialTheme.skill
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = sk.cardBg),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(sk.blue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_inbox), null, tint = sk.blue, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    demand.str("course_name").ifBlank { "Course not specified" },
                    style = MaterialTheme.typography.titleSmall, color = sk.bodyText,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        demand.str("start_date").takeIf { it.isNotBlank() }?.shortDate(),
                        demand.str("delivery_mode").takeIf { it.isNotBlank() },
                        demand.str("customer").takeIf { it.isNotBlank() },
                        demand.str("participants").takeIf { it.isNotBlank() }?.let { "$it pax" },
                        demand.str("location").takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = sk.subText, maxLines = 2,
                )
            }
        }
    }
}

// ── Small pieces ──────────────────────────────────────────────────────────────

@Composable
private fun Chip(text: String, tint: Color) {
    Surface(color = tint.copy(alpha = 0.14f), shape = RoundedCornerShape(12.dp)) {
        Text(
            text, style = MaterialTheme.typography.labelSmall, color = tint,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun HeroLabel(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold, color = MaterialTheme.skill.heroMuted,
    )
}

@Composable
private fun DeployStat(
    label: String, count: Int, color: Color, total: Int,
    modifier: Modifier, onClick: () -> Unit,
) {
    val p by animateProgressFromZero(if (total > 0) count.toFloat() / total else 0f)
    Column(modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.skill.heroMuted)
        AnimatedCount(count, MaterialTheme.typography.headlineMedium, MaterialTheme.skill.heroText)
        LinearProgressIndicator(
            progress = { p },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(3.dp).clip(RoundedCornerShape(2.dp)),
            color = color, trackColor = Color.White.copy(alpha = 0.10f),
            gapSize = 0.dp, drawStopIndicator = {},
        )
    }
}

@Composable
private fun MiniStat(label: String, value: Int, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp)).clickable(onClick = onClick).padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
            Spacer(Modifier.width(5.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.skill.heroMuted)
        }
        Text(
            "$value", style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.skill.heroText,
        )
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.skill.bodyText)
        Spacer(Modifier.width(8.dp))
        Chip("$count", MaterialTheme.skill.subText)
    }
}

@Composable
private fun EmptyCard(message: String) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.skill.cardBg),
    ) {
        Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.skill.subText)
        }
    }
}
