package com.example.skillsync.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
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
import com.example.skillsync.ui.batch.AllocationDeskContent
import com.example.skillsync.ui.batch.AllocationState
import com.example.skillsync.ui.batch.AllocationViewModel
import com.example.skillsync.ui.components.*

// ── Screen shell ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    email: String,
    tab: String,
    onTabChange: (String) -> Unit,
    onTrainerClick: (email: String, name: String) -> Unit,
    onBatchClick: (demandId: String) -> Unit = {},
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel(),
    allocationViewModel: AllocationViewModel = viewModel(),
) {
    val context = LocalContext.current


    LaunchedEffect(email, tab) {
        viewModel.loadData(email)
        if (tab == HomeTab.DEMAND) allocationViewModel.load(email, context)
        if (tab == HomeTab.COURSES) viewModel.ensureCapability(email)
        
        // In-app polling for the Unallocated Batch Intelligence Center
        if (tab == HomeTab.DEMAND) {
            while(true) {
                kotlinx.coroutines.delay(60_000L) // 1 minute
                allocationViewModel.refresh(email, context)
            }
        }
    }
    // Coming back to the app re-reads everything, so the manager is never acting
    // on numbers that went stale while the phone was in their pocket.
    RefreshOnResume(key = email) {
        viewModel.refresh(email)
        if (tab == HomeTab.DEMAND) allocationViewModel.refresh(email, context)
    }

    val state by viewModel.uiState.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val capability by viewModel.capability.collectAsState()
    val capLoading by viewModel.capabilityLoading.collectAsState()
    val allocState by allocationViewModel.state.collectAsState()
    val allocRefreshing by allocationViewModel.refreshing.collectAsState()
    val newIds by allocationViewModel.newIds.collectAsState()
    StatusBarIcons(lightIcons = true)

    // Which KPI the manager tapped; drives the drill-down sheet.
    var drill by remember { mutableStateOf<Drill?>(null) }

    var bannerMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.notification.collect { message: String ->
            // System Push Notification (if in background or even foreground)
            com.example.skillsync.util.LocalNotificationService.showNotification(
                context, "SkillSync Alert", message
            )
            // Show custom in-app banner
            bannerMessage = message
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.skill.pageBg,
            topBar = {
                TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SkillSyncLogo(size = 28.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("SkillSync", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Color.White)
                            Text(
                                tabTitle(tab),
                                fontSize = 9.5.sp, color = Color.White.copy(alpha = 0.78f),
                            )
                        }
                    }
                },
                actions = {
                    // Manual fallback next to pull-to-refresh, for anyone who
                    // does not think to drag a screen that is already at the top.
                    IconButton(onClick = {
                        viewModel.refresh(email)
                        if (tab == HomeTab.DEMAND) allocationViewModel.refresh(email, context)
                    }) {
                        Icon(
                            painterResource(R.drawable.ic_trend),
                            contentDescription = "Refresh",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    androidx.compose.material3.TextButton(onClick = {
                        com.example.skillsync.data.SessionManager.clearSession()
                        onLogout()
                    }) {
                        Text(
                            "Logout",
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
        bottomBar = { SkillSyncNavBar(tab, onTabChange) },
    ) { pv ->
        Column(modifier.fillMaxSize().padding(pv)) {
            // Driven by whether the data actually on screen came from disk
            // (fetch failed and LocalCache answered) rather than just guessing
            // from connectivity — a live network with a down backend/RMS chain
            // looks identical to the manager as being offline.
            val hasNetwork = com.example.skillsync.data.api.RetrofitClient.isNetworkAvailable(context)
            val dashSuccess = state as? DashboardState.Success
            val lastSync = com.example.skillsync.data.SessionManager.getLastSyncTime()
            val showBanner = dashSuccess?.fromCache == true || !hasNetwork || lastSync > 0
            if (showBanner) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(if (dashSuccess?.fromCache == true || !hasNetwork) MaterialTheme.colorScheme.errorContainer else MaterialTheme.skill.cardBg)
                        .padding(vertical = 4.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val syncMsg = when {
                        dashSuccess?.fromCache == true -> "Offline — showing data from ${relativeAge(dashSuccess.cachedAt)}"
                        !hasNetwork -> "Offline Mode - Showing Cached Data"
                        else -> {
                            val diff = System.currentTimeMillis() - lastSync
                            val mins = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(diff)
                            if (mins == 0L) "Synced just now" else "Last synced $mins mins ago"
                        }
                    }
                    Text(
                        syncMsg,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (dashSuccess?.fromCache == true || !hasNetwork) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.skill.subText,
                    )
                }
            }
            Box(Modifier.weight(1f)) {
            when (tab) {
                HomeTab.DEMAND -> when (val a = allocState) {
                    // Allocation desk has its own query, state and refresh.
                    is AllocationState.Loading -> DashboardSkeleton()
                    is AllocationState.Error -> DashErrorView(a.message) {
                        allocationViewModel.refresh(email, context)
                    }
                    is AllocationState.Success -> PullToRefreshBox(
                        isRefreshing = allocRefreshing,
                        onRefresh = { allocationViewModel.refresh(email, context) },
                    ) {
                        AllocationDeskContent(a.data, newIds) { b -> onBatchClick(b.str("demand_id")) }
                    }
                }

                HomeTab.COURSES -> PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = { viewModel.refresh(email) },
                ) {
                    CoursesTab(capability, capLoading, onTrainerClick)
                }

                else -> when (val s = state) {
                    is DashboardState.Loading -> DashboardSkeleton()
                    is DashboardState.Error -> DashErrorView(s.message) { viewModel.refresh(email) }
                    is DashboardState.Success -> PullToRefreshBox(
                        isRefreshing = refreshing,
                        onRefresh = { viewModel.refresh(email) },
                    ) {
                        val d = s.intelligenceData
                        when (tab) {
                            HomeTab.TEAM -> TeamTab(d, capability, onTrainerClick)
                            HomeTab.ACTIONS -> ActionsTab(d, capability, onTrainerClick)
                            else -> DashboardTab(
                                data = d,
                                profile = profile,
                                capability = capability,
                                capabilityLoading = capLoading,
                                email = email,
                                onTrainerClick = onTrainerClick,
                                onOpenProfile = { onTrainerClick(email, profile?.str("name").orEmpty()) },
                                onLogout = onLogout,
                                onDrill = { drill = it },
                                onLoadCapability = { viewModel.ensureCapability(email) },
                            )
                        }
                    }
                }
            }
        }
    }

    }
        
        // Show banner overlaying everything
        com.example.skillsync.ui.components.TopBannerNotification(
            message = bannerMessage,
            onDismiss = { bannerMessage = null }
        )
    }

    drill?.let { DrillSheet(it) { drill = null } }
}

/** "3 mins ago" / "2 hours ago" / "5 days ago" for a past epoch-millis timestamp. */
internal fun relativeAge(epochMillis: Long): String {
    if (epochMillis <= 0L) return "an earlier session"
    val diff = System.currentTimeMillis() - epochMillis
    val mins = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(diff)
    val days = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        mins < 1L -> "just now"
        mins < 60L -> "$mins min${if (mins == 1L) "" else "s"} ago"
        hours < 24L -> "$hours hour${if (hours == 1L) "" else "s"} ago"
        else -> "$days day${if (days == 1L) "" else "s"} ago"
    }
}

private fun tabTitle(tab: String) = when (tab) {
    HomeTab.TEAM -> "Team roster"
    HomeTab.COURSES -> "Course catalogue"
    HomeTab.DEMAND -> "Unallocated demand"
    HomeTab.ACTIONS -> "Manager actions"
    else -> "Manager Command Dashboard"
}

/**
 * Compact bottom bar.
 *
 * Material's `NavigationBar` is 80dp tall and reserves a large indicator pill per
 * item — on a 6" phone that is a tenth of the screen spent on five words. This is
 * a hand-rolled 56dp row with a slim top-edge accent for the active tab instead.
 * Each item still fills the full bar height, so every touch target stays at or
 * above the 48dp accessibility minimum despite the smaller visual footprint.
 */
@Composable
internal fun SkillSyncNavBar(current: String, onSelect: (String) -> Unit) {
    val sk = MaterialTheme.skill
    val items = listOf(
        Triple(HomeTab.DASHBOARD, R.drawable.ic_home, "Home"),
        Triple(HomeTab.TEAM, R.drawable.ic_people, "Team"),
        Triple(HomeTab.COURSES, R.drawable.ic_book, "Courses"),
        Triple(HomeTab.DEMAND, R.drawable.ic_inbox, "Demand"),
        Triple(HomeTab.ACTIONS, R.drawable.ic_flag, "Actions"),
    )
    Surface(color = sk.cardBg, tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { (key, icon, label) ->
                val selected = current == key
                val tint by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary else sk.subText,
                    tween(Motion.FAST), label = "navTint",
                )
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = selected,
                            onClick = { onSelect(key) },
                            role = Role.Tab,
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .width(20.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    )
                    Spacer(Modifier.height(5.dp))
                    Icon(
                        painterResource(icon),
                        contentDescription = label,
                        tint = tint,
                        modifier = Modifier.size(19.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        label,
                        fontSize = 9.sp,
                        color = tint,
                        maxLines = 1,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
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

// ── Dashboard ─────────────────────────────────────────────────────────────────

@Composable
internal fun DashboardTab(
    data: Map<String, Any>,
    profile: Map<String, Any>?,
    capability: Map<String, Any>?,
    capabilityLoading: Boolean,
    email: String,
    onTrainerClick: (String, String) -> Unit,
    onOpenProfile: () -> Unit,
    onLogout: () -> Unit,
    onDrill: (Drill) -> Unit,
    onLoadCapability: () -> Unit = {},
) {
    val sk = MaterialTheme.skill
    val ops = data.rows("trainer_operations_df")
    val states = data.rows("trainer_current_state_df")
    val batches = data.rows("batch_engagement_df")
    val kpis = data.obj("manager_kpis")
    val capKpis = capability?.obj("kpis")
    val capTrainers = capability?.rows("trainers").orEmpty()
    val stateMap = states.associateBy { it.str("trainer_email").lowercase() }
    val capMap = capTrainers.associateBy { it.str("trainer_email").lowercase() }
    // Delivery readiness rows — always present in the unified payload, no extra API call.
    val deliveryRows = data.rows("delivery_intelligence_df")
    val deliveryByEmail = remember(deliveryRows) {
        deliveryRows.associateBy { it.str("trainer_email").lowercase() }
    }

    var showProfileMenu by remember { mutableStateOf(false) }

    if (showProfileMenu) {
        ProfileMenuBottomSheet(
            email = email,
            onDismiss = { showProfileMenu = false },
            onLogout = {
                showProfileMenu = false
                com.example.skillsync.data.SessionManager.clearSession()
                onLogout()
            },
            onViewProfile = {
                showProfileMenu = false
                onOpenProfile()
            }
        )
    }

    LazyColumn(
        Modifier.fillMaxSize().background(sk.pageBg),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileHeader(
                    email = email,
                    profile = profile,
                    kpis = kpis,
                    capKpis = capKpis,
                    onOpenProfile = onOpenProfile,
                )
            }
        }

        item { Appear(1) { DashSectionHeader("Your numbers", "Every figure is counted live from RMS") } }

        item {
            Appear(2) {
                ManagerKpiGrid(
                    kpis = kpis,
                    capKpis = capKpis,
                    capabilityLoading = capabilityLoading,
                    ops = ops,
                    states = states,
                    batches = batches,
                    capTrainers = capTrainers,
                    onDrill = onDrill,
                    onLoadCapability = onLoadCapability,
                )
            }
        }

        // Delivery Readiness summary — sourced from delivery_intelligence_df
        // Always available in the unified payload, shown right after KPI numbers.
        if (deliveryRows.isNotEmpty()) {
            item { Appear(3) { DashSectionHeader("Delivery readiness", "Which trainers are deployment-ready today") } }
            item { Appear(4) { TeamReadinessSummaryCard(deliveryRows) } }
        }

        // Feedback Risk summary — sourced from trainer_operations_df
        if (ops.isNotEmpty()) {
            item { Appear(4) { DashSectionHeader("Feedback risk", "Trainers with unresolved feedback issues") } }
            item { Appear(4) { TeamRiskSummaryCard(ops) } }
        }

        // Capacity & Bench Risk (Stream 4) — sourced from trainer_operations_df
        if (ops.isNotEmpty()) {
            item { Appear(5) { DashSectionHeader("Capacity", "Optimize team utilization and deployment") } }
            item { Appear(5) { TeamCapacityAlertCard(ops) } }
        }

        // Capacity Forecast (Stream 6, predictive) — trend projection from each
        // trainer's own utilization_series; renders nothing if no one is trending
        // toward overload or bench, so it never adds noise to a healthy team.
        if (ops.isNotEmpty()) {
            item { Appear(5) { TeamCapacityForecastCard(ops) } }
        }

        item { Appear(6) { DashSectionHeader("Team health", "What needs attention right now") } }

        item {
            Appear(7) {
                TeamAnalytics(
                    ops = ops,
                    states = states,
                    capKpis = capKpis,
                    capTrainers = capTrainers,
                    capabilityLoading = capabilityLoading,
                )
            }
        }

        item { Appear(8) { TopPerformers(ops, capMap, onTrainerClick) } }

        item {
            Appear(9) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Team — right now", style = MaterialTheme.typography.titleLarge, color = sk.bodyText)
                    Spacer(Modifier.width(8.dp))
                    Chip("${ops.size}", sk.subText)
                }
            }
        }

        if (ops.isEmpty()) {
            item { EmptyStateCard("No reportees returned. Check your account permissions.") }
        } else {
            itemsIndexed(ops) { i, t ->
                Appear(i + 9) {
                    TrainerCard(
                        trainer = t,
                        state = stateMap[t.str("official_email").lowercase()],
                        capability = capMap[t.str("official_email").lowercase()],
                        delivery = deliveryByEmail[t.str("official_email").lowercase()],
                    ) {
                        onTrainerClick(t.str("official_email"), t.str("trainer_name"))
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun DashSectionHeader(title: String, subtitle: String) {
    Column(Modifier.padding(top = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.skill.bodyText)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.skill.subText)
    }
}

/** Top of the roster by utilisation — a quick read on who is carrying delivery. */
@Composable
private fun TopPerformers(
    ops: List<Map<*, *>>,
    capMap: Map<String, Map<*, *>>,
    onTrainerClick: (String, String) -> Unit,
) {
    val sk = MaterialTheme.skill
    val top = remember(ops) {
        ops.filter { (it.intOrNull("current_utilization") ?: 0) > 0 }
            .sortedByDescending { it.int("current_utilization") }
            .take(5)
    }
    if (top.isEmpty()) return

    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = sk.cardBg),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_award), null, tint = sk.amber, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text("Top performing", style = MaterialTheme.typography.titleLarge, color = sk.bodyText)
            }
            Text(
                "Ranked by utilisation over the last three months",
                style = MaterialTheme.typography.labelSmall, color = sk.subText,
            )
            Spacer(Modifier.height(10.dp))
            top.forEachIndexed { i, t ->
                val util = t.int("current_utilization")
                val cap = capMap[t.str("official_email").lowercase()]
                val tint = when {
                    util > 85 -> sk.red
                    util >= 60 -> sk.teal
                    else -> sk.amber
                }
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onTrainerClick(t.str("official_email"), t.str("trainer_name")) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${i + 1}", style = MaterialTheme.typography.labelMedium,
                        color = sk.subText, modifier = Modifier.width(16.dp),
                    )
                    Avatar(t.str("trainer_name"), cap?.str("photo_url"), 28.dp)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            t.str("trainer_name"), style = MaterialTheme.typography.titleSmall,
                            color = sk.bodyText, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            t.str("capacity_bucket").ifBlank { t.str("designation") },
                            style = MaterialTheme.typography.labelSmall, color = sk.subText,
                        )
                    }
                    Text(
                        "$util%", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = tint,
                    )
                }
            }
        }
    }
}

/**
 * Manager actions, with the certification gaps folded in. Feedback and allocation
 * items come from the backend's action objects; certification gaps are derived
 * client-side from capability, because they are the same kind of thing — a
 * decision waiting on the manager — and splitting them across two screens would
 * hide half the queue.
 */
@Composable
internal fun ActionsTab(
    data: Map<String, Any>,
    capability: Map<String, Any>?,
    onTrainerClick: (String, String) -> Unit,
) {
    val sk = MaterialTheme.skill
    val actions = data.rows("manager_action_objects").filter { it.str("lifecycle_state") != "closed" }
    val gapTrainers = capability?.rows("trainers").orEmpty()
        .filter { (it.obj("certification")?.int("gap_count") ?: 0) > 0 }

    LazyColumn(
        Modifier.fillMaxSize().background(sk.pageBg),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (actions.isEmpty() && gapTrainers.isEmpty()) {
            item { EmptyStateCard("No open manager actions.") }
        }
        itemsIndexed(actions) { i, a -> Appear(i) { AttentionCard(a) } }

        if (gapTrainers.isNotEmpty()) {
            item {
                Text(
                    "Certification gaps",
                    style = MaterialTheme.typography.titleLarge,
                    color = sk.bodyText,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            itemsIndexed(gapTrainers) { i, t ->
                Appear(i) {
                    CertGapActionCard(t) {
                        onTrainerClick(t.str("trainer_email"), t.str("trainer_name"))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun CertGapActionCard(trainer: Map<*, *>, onClick: () -> Unit) {
    val sk = MaterialTheme.skill
    val cert = trainer.obj("certification")
    val missing = cert?.list("missing").orEmpty()
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = sk.cardBg),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row {
            Box(Modifier.width(3.dp).fillMaxHeight().background(sk.amber))
            Column(Modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(trainer.str("trainer_name"), trainer.str("photo_url"), 28.dp)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            trainer.str("trainer_name"),
                            style = MaterialTheme.typography.titleSmall, color = sk.bodyText,
                        )
                        Text(
                            "${missing.size} course${if (missing.size == 1) "" else "s"} taught " +
                                "without the matching certification",
                            style = MaterialTheme.typography.labelSmall, color = sk.subText,
                        )
                    }
                    Chip("${missing.size}", sk.amber)
                }
                Spacer(Modifier.height(8.dp))
                missing.take(4).forEach { m ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Surface(
                            color = (if (m.str("priority") == "high") sk.red else sk.amber)
                                .copy(alpha = 0.14f),
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                m.str("code"),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (m.str("priority") == "high") sk.red else sk.amber,
                                fontWeight = FontWeight.Bold, fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                m.str("name"),
                                style = MaterialTheme.typography.bodySmall, color = sk.bodyText,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "teaches ${m.str("because")}",
                                style = MaterialTheme.typography.labelSmall, color = sk.subText,
                                fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (missing.size > 4) {
                    Text(
                        "+ ${missing.size - 4} more",
                        style = MaterialTheme.typography.labelSmall, color = sk.subText,
                    )
                }
            }
        }
    }
}

// ── Loading & error ───────────────────────────────────────────────────────────

@Composable
private fun DashboardSkeleton() {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.skill.pageBg).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ShimmerBox(height = 168.dp, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) {
                    ShimmerBox(height = 78.dp, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        ShimmerBox(width = 170.dp, height = 14.dp)
        repeat(3) {
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
internal fun TrainerCard(
    trainer: Map<*, *>,
    state: Map<*, *>?,
    capability: Map<*, *>? = null,
    /** Row from delivery_intelligence_df keyed by trainer_email. */
    delivery: Map<*, *>? = null,
    onClick: () -> Unit,
) {
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

    val cert = capability?.obj("certification")
    val gaps = cert?.int("gap_count") ?: 0
    val certCount = cert?.list("held")?.size ?: 0
    val readiness = capability?.str("readiness_bucket").orEmpty()

    // Delivery readiness — from delivery_intelligence_df (always in main payload)
    val deliveryLabel    = delivery?.str("delivery_readiness_label").orEmpty()
    val deliveryCapacity = delivery?.str("delivery_capacity_status").orEmpty()
    val deliveryRisk     = delivery?.str("delivery_risk_level").orEmpty()

    // Feedback risk — from trainer_operations_df
    val feedbackRisk = trainer.str("feedback_risk").orEmpty()
    val feedbackCount = trainer.int("negative_count")

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
                Avatar(name, capability?.str("photo_url"), 38.dp)
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
                // —— Delivery Readiness Badge (from delivery_intelligence_df) ——
                // Always shown when available; no extra API call required.
                if (deliveryLabel.isNotBlank()) {
                    val (rdLabel, rdColor) = when (deliveryLabel) {
                        "Ready"            -> "🟢 Ready"          to sk.green
                        "Ready with Prep"  -> "🟡 Ready w/ Prep"  to sk.teal
                        "Needs Mentoring" -> "🟠 Needs Mentoring" to sk.amber
                        else               -> "🔴 Hold"            to sk.red
                    }
                    Chip(rdLabel, rdColor)
                    Spacer(Modifier.width(5.dp))
                } else {
                    // Fallback to capability-based readiness if delivery data not yet available
                    Chip(capacity.ifBlank { "Unknown" }, if (capacity == "Unknown" || capacity.isBlank()) sk.subText else utilColor)
                    if (readiness.isNotBlank() && readiness != "Unknown") {
                        Spacer(Modifier.width(5.dp))
                        Chip(
                            readiness,
                            when (readiness) {
                                "Ready"      -> sk.green
                                "Developing" -> sk.amber
                                else         -> sk.red
                            },
                        )
                    }
                }

                // —— Capacity Badge (from delivery_intelligence_df) ——
                if (deliveryCapacity.isNotBlank()) {
                    val capColor = when (deliveryCapacity) {
                        "Overloaded"   -> sk.red
                        "Balanced"     -> sk.teal
                        "Underutilized" -> sk.green
                        else           -> sk.subText
                    }
                    Chip(deliveryCapacity, capColor)
                    Spacer(Modifier.width(5.dp))
                }

                // —— Delivery Risk Indicator ——
                if (deliveryRisk == "High") {
                    Chip("⚠️ Delivery Risk", sk.red)
                    Spacer(Modifier.width(5.dp))
                }

                // —— Feedback Risk Indicator ——
                if (feedbackRisk.isNotBlank() && feedbackRisk != "Low") {
                    val (riskLabel, riskColor) = when (feedbackRisk) {
                        "High"   -> "🔴 Feedback Issues" to sk.red
                        "Medium" -> "🟡 Feedback Alert" to sk.amber
                        else     -> null to null
                    }
                    if (riskColor != null) {
                        Chip(riskLabel!!, riskColor!!)
                        Spacer(Modifier.width(5.dp))
                    }
                }

                if (certCount > 0) {
                    Chip("$certCount cert", sk.indigo)
                    Spacer(Modifier.width(5.dp))
                }
                if (gaps > 0) {
                    Chip("$gaps gap", sk.amber)
                    Spacer(Modifier.width(5.dp))
                }
                if (upcoming > 0) {
                    Chip("$upcoming upcoming", sk.blue)
                    Spacer(Modifier.width(5.dp))
                }
                if (negCount > 0) {
                    Spacer(Modifier.width(5.dp))
                    Chip("$negCount neg", sk.red)
                }
                Spacer(Modifier.weight(1f))
                if (confidence > 0) {
                    Text("$confidence%", style = MaterialTheme.typography.labelSmall, color = sk.subText)
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

// ── Small pieces ──────────────────────────────────────────────────────────────

@Composable
internal fun Chip(text: String, tint: Color) {
    Surface(color = tint.copy(alpha = 0.14f), shape = RoundedCornerShape(12.dp)) {
        Text(
            text, style = MaterialTheme.typography.labelSmall, color = tint,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
