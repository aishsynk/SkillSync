package com.example.skillsync.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.skillsync.HomeTab
import com.example.skillsync.R
import com.example.skillsync.theme.AuroraBackground
import com.example.skillsync.theme.IconSlot
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.StatusBarIcons
import com.example.skillsync.theme.accentGlass
import com.example.skillsync.theme.glassSurface
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

    DisposableEffect(email) {
        viewModel.startPolling(email, context)
        onDispose { viewModel.stopPolling() }
    }

    LaunchedEffect(email, tab) {
        viewModel.loadData(email, context)
        if (tab == HomeTab.DEMAND) allocationViewModel.load(email, context)
        if (tab == HomeTab.COURSES) viewModel.ensureCapability(email, context)
        
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
        viewModel.refresh(email, context)
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
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showNotificationsSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.notification.collect { (title, message) ->
            // System notification — fires even if the manager is on a
            // different screen than the dashboard right now.
            com.example.skillsync.util.LocalNotificationService.showNotification(
                context, title, message
            )
            // In-app banner for immediate visibility while the app is open.
            bannerMessage = message
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Confirm Logout") },
            text = { Text("Are you sure you want to log out of SkillEdge?") },
            confirmButton = {
                Button(onClick = { 
                    showLogoutConfirm = false
                    onLogout()
                }) { Text("Logout") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancel") }
            },
            containerColor = MaterialTheme.skill.cardBg
        )
    }

    if (showNotificationsSheet) {
        ModalBottomSheet(onDismissRequest = { showNotificationsSheet = false }, containerColor = MaterialTheme.skill.cardBg) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Text("Notifications", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.skill.bodyText)
                Spacer(Modifier.height(14.dp))
                Text("No recent notifications.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.skill.subText)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // The aurora ground is drawn once, behind everything, and every surface
        // above it is translucent — that is what makes the glass cards read as
        // glass rather than as flat grey panels.
        AuroraBackground()
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.skill.frost,
            topBar = {
                TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SkillSyncLogo(size = 26.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "SkillEdge",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = MaterialTheme.skill.frost,
                                letterSpacing = (-0.01).em,
                            )
                            Text(
                                tabTitle(tab).uppercase(),
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.13.em,
                                color = MaterialTheme.skill.labelText,
                            )
                        }
                    }
                },
                actions = {
                    // Manual fallback next to pull-to-refresh, for anyone who
                    // does not think to drag a screen that is already at the top.
                    IconButton(onClick = {
                        viewModel.refresh(email, context)
                        if (tab == HomeTab.DEMAND) allocationViewModel.refresh(email, context)
                    }) {
                        Icon(
                            painterResource(R.drawable.ic_trend),
                            contentDescription = "Refresh",
                            tint = MaterialTheme.skill.ice,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    androidx.compose.material3.TextButton(onClick = {
                        showLogoutConfirm = true
                    }) {
                        Text(
                            "Logout",
                            color = MaterialTheme.skill.ice,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                },
                // Transparent over the aurora — the teal band is gone entirely.
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
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
                val stale = dashSuccess?.fromCache == true || !hasNetwork
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(
                            if (stale) MaterialTheme.skill.warn.copy(alpha = 0.16f)
                            else Color.Transparent
                        )
                        .padding(vertical = 4.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val syncMsg = when {
                        dashSuccess?.fromCache == true -> "Offline — showing data from ${relativeAge(dashSuccess.cachedAt)}"
                        !hasNetwork -> "Offline Mode - Showing Cached Data"
                        refreshing -> "Syncing..."
                        else -> {
                            val diff = System.currentTimeMillis() - lastSync
                            val mins = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(diff)
                            if (mins == 0L) "Synced just now" else "Last synced $mins mins ago"
                        }
                    }
                    Text(
                        syncMsg,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (stale) MaterialTheme.skill.warn else MaterialTheme.skill.labelText,
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
                    onRefresh = { viewModel.refresh(email, context) },
                ) {
                    CoursesTab(capability, capLoading, onTrainerClick)
                }

                else -> when (val s = state) {
                    is DashboardState.Loading -> DashboardSkeleton()
                    is DashboardState.Error -> DashErrorView(s.message) { viewModel.refresh(email, context) }
                    is DashboardState.Success -> PullToRefreshBox(
                        isRefreshing = refreshing,
                        onRefresh = { viewModel.refresh(email, context) },
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
                                onLogout = { showLogoutConfirm = true },
                                onDrill = { drill = it },
                                onLoadCapability = { viewModel.ensureCapability(email, context) },
                                onOpenTeam = { onTabChange(HomeTab.TEAM) },
                                onOpenNotifications = { showNotificationsSheet = true }
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
    // Frosted over the aurora. Material's indicator pill is gone — the active
    // tab is marked by a cyan glow bar and a lit icon instead, which costs no
    // vertical space on a bar that already has to stay under 60dp.
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0x000D1117), Color(0xDB0D1117), Color(0xF20D1117))
                )
            )
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(1.dp).background(sk.glassBorder))
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(58.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { (key, icon, label) ->
                    val selected = current == key
                    val tint by animateColorAsState(
                        if (selected) sk.sky else sk.labelText,
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
                                .width(if (selected) 22.dp else 0.dp)
                                .height(2.5.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (selected) Brush.horizontalGradient(
                                        listOf(com.example.skillsync.theme.Cyan, com.example.skillsync.theme.SkyBlue)
                                    ) else Brush.horizontalGradient(
                                        listOf(Color.Transparent, Color.Transparent)
                                    )
                                )
                        )
                        Spacer(Modifier.height(6.dp))
                        Icon(
                            painterResource(icon),
                            contentDescription = label,
                            tint = tint,
                            modifier = Modifier.size(19.dp),
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            label,
                            fontSize = 9.sp,
                            color = tint,
                            maxLines = 1,
                            letterSpacing = 0.03.em,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

// ── Drill-down ────────────────────────────────────────────────────────────────

data class DrillRow(val primary: String, val secondary: String, val targetEmail: String? = null)
data class Drill(val title: String, val subtitle: String, val rows: List<DrillRow>)

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
            drill.rows.forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(row.primary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.skill.bodyText)
                        if (row.secondary.isNotBlank()) {
                            Text(row.secondary, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.skill.subText)
                        }
                    }
                    if (row.targetEmail != null) {
                        Spacer(Modifier.width(8.dp))
                        var showQuickMsg by remember { mutableStateOf(false) }
                        IconButton(onClick = { showQuickMsg = true }) {
                            Icon(painterResource(R.drawable.ic_mail), "Send Message", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                        if (showQuickMsg) {
                            // We can use a simple quick message dialog right here
                            var msg by remember { mutableStateOf("Hi ${row.primary.split(" ").first()},\n\nPlease resolve your ${row.secondary}.") }
                            AlertDialog(
                                onDismissRequest = { showQuickMsg = false },
                                title = { Text("Message ${row.primary.split(" ").first()}") },
                                text = {
                                    OutlinedTextField(
                                        value = msg,
                                        onValueChange = { msg = it },
                                        modifier = Modifier.fillMaxWidth().height(120.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                },
                                confirmButton = {
                                    Button(onClick = { showQuickMsg = false }) { Text("Send") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showQuickMsg = false }) { Text("Cancel") }
                                },
                                containerColor = MaterialTheme.skill.cardBg
                            )
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
    onLogout: () -> Unit = {},
    onDrill: (Drill) -> Unit,
    onLoadCapability: () -> Unit = {},
    onOpenTeam: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
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
    val attention = remember(ops, deliveryByEmail) { rankByAttention(ops, deliveryByEmail) }

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
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ProfileHeader(
                email = email,
                profile = profile,
                kpis = kpis,
                capKpis = capKpis,
                onOpenProfile = onOpenProfile,
                onOpenNotifications = onOpenNotifications,
            )
        }

        item { Appear(1) { CommandHero(kpis = kpis, capKpis = capKpis) } }

        item { Appear(2) { DashSectionHeader("Critical pulse", "Counted live from RMS") } }

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

        // Needs Attention — promoted directly under the numbers, ahead of the
        // descriptive analytics below. A command center leads with decisions,
        // not charts: this is the one section a manager should act on first,
        // so it no longer sits two-thirds down the page behind five chart
        // cards. A short, ranked preview, not the full roster — the complete
        // list with real search/sort/filter already lives on the Team tab;
        // repeating every TrainerCard here just to also show it on Home was
        // pure duplication, and on a roster of any real size (this product's
        // own reportees dataset runs to 80+) it turned the home screen into
        // an extremely long scroll for zero extra information.
        item {
            Appear(3) {
                DashSectionHeader("Needs you today", "Ranked by urgency across the roster")
            }
        }

        if (ops.isEmpty()) {
            item { EmptyStateCard("No reportees returned. Check your account permissions.") }
        } else {
            if (attention.isEmpty()) {
                item {
                    Appear(3) {
                        EmptyStateCard("No urgent items — the whole team looks healthy right now.")
                    }
                }
            } else {
                itemsIndexed(attention) { i, (t, reason) ->
                    Appear(i + 3) {
                        NeedsYouTodayCard(
                            trainer = t,
                            reason = reason,
                            capability = capMap[t.str("official_email").lowercase()],
                        ) {
                            onTrainerClick(t.str("official_email"), t.str("trainer_name"))
                        }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = onOpenTeam,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("View full team (${ops.size})") }
            }
        }

        // Team Pulse — one header covering readiness/risk/capacity (current
        // state, right now) plus a clearly-separated forecast card
        // (predictive, next month) so it never reads as an unexplained extra
        // card in the middle of the section.
        if (deliveryRows.isNotEmpty() || ops.isNotEmpty()) {
            item { Appear(4) { DashSectionHeader("Team pulse", "Readiness, risk, capacity — and what's trending next") } }
        }
        if (deliveryRows.isNotEmpty()) {
            item { Appear(4) { TeamReadinessSummaryCard(deliveryRows) } }
        }
        if (ops.isNotEmpty()) {
            item { Appear(4) { TeamRiskSummaryCard(ops) } }
            item { Appear(5) { TeamCapacityAlertCard(ops) } }
            // Predictive trend projection — renders nothing when nobody is
            // trending toward overload or bench, so a healthy team sees no
            // extra card at all. The card's own header makes clear this is a
            // forward-looking projection, not another current-state reading
            // like the three cards above it (see TeamCapacityForecastCard).
            item { Appear(5) { TeamCapacityForecastCard(ops) } }
        }

        item { Appear(6) { DashSectionHeader("Team health", "Distribution and trend across the whole team") } }

        item {
            Appear(6) {
                TeamAnalytics(
                    ops = ops,
                    states = states,
                    capKpis = capKpis,
                    capTrainers = capTrainers,
                    capabilityLoading = capabilityLoading,
                )
            }
        }

        item { Appear(7) { TopPerformers(ops, capMap, onTrainerClick) } }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

/**
 * Ranks trainers by how urgently a manager should look at them: feedback risk
 * first (a real incident), then delivery risk, then capacity extremes
 * (overloaded or benched). Returns at most 5 — this is a preview, not the
 * roster; [reason] is the single sentence explaining why each one is here.
 */
private fun rankByAttention(
    ops: List<Map<*, *>>,
    deliveryByEmail: Map<String, Map<*, *>>,
): List<Pair<Map<*, *>, String>> {
    return ops.mapNotNull { t ->
        val email = t.str("official_email").lowercase()
        val delivery = deliveryByEmail[email]
        val feedbackRisk = t.str("feedback_risk")
        val deliveryRisk = delivery?.str("delivery_risk_level").orEmpty()
        val capacity = t.str("capacity_bucket")
        val (score, reason) = when {
            feedbackRisk == "High" -> 100 to "High feedback risk"
            deliveryRisk == "High" -> 90 to "High delivery risk"
            feedbackRisk == "Medium" -> 60 to "Feedback alert"
            capacity == "Stretched" -> 40 to "Stretched — over 85% utilised"
            capacity == "On Bench" -> 30 to "On bench — available now"
            else -> 0 to ""
        }
        if (score == 0) null else Triple(t, score, reason)
    }.sortedByDescending { it.second }.take(5).map { it.first to it.third }
}

@Composable
private fun NeedsYouTodayCard(
    trainer: Map<*, *>,
    reason: String,
    capability: Map<*, *>?,
    onClick: () -> Unit,
) {
    val sk = MaterialTheme.skill
    val name = trainer.str("trainer_name")
    val tint = when {
        reason.contains("risk", ignoreCase = true) -> sk.crit
        reason.contains("Stretched") -> sk.warn
        else -> sk.aqua
    }
    val shape = RoundedCornerShape(Radii.card)
    Box(
        Modifier
            .fillMaxWidth()
            .accentGlass(tint, shape, strong = tint == sk.crit)
            .clickable(onClick = onClick),
    ) {
        // Severity is carried by the stripe first and the colour second, so the
        // triage still reads for a colour-blind manager.
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(Brush.verticalGradient(listOf(tint, tint.copy(alpha = 0.2f))))
        )
        Row(
            Modifier.padding(start = 14.dp, top = 11.dp, end = 12.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(name, capability?.str("photo_url"), 34.dp)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = sk.frost,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    reason,
                    style = MaterialTheme.typography.labelSmall,
                    color = tint,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                )
            }
            Icon(
                painterResource(R.drawable.ic_chevron), null,
                tint = sk.labelText, modifier = Modifier.size(15.dp),
            )
        }
    }
}

/**
 * Section header. The uppercase eyebrow plus a hairline rule that runs to the
 * edge gives the scroll a rhythm the previous title/subtitle pair did not — on
 * a long dashboard the manager needs to feel where one band of information ends.
 */
@Composable
private fun DashSectionHeader(title: String, subtitle: String) {
    val sk = MaterialTheme.skill
    Column(Modifier.padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = sk.ice,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.15.em,
            )
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(sk.ice.copy(alpha = 0.4f), Color.Transparent)
                        )
                    )
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = sk.labelText,
            fontSize = 10.5.sp,
        )
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

    Box(Modifier.fillMaxWidth().glassSurface()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconSlot(tint = sk.sky, size = 26.dp) {
                    Icon(
                        painterResource(R.drawable.ic_award), null,
                        tint = sk.sky, modifier = Modifier.size(14.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "Carrying delivery",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = sk.frost,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "Ranked by utilisation over the last three months",
                style = MaterialTheme.typography.labelSmall, color = sk.labelText,
            )
            Spacer(Modifier.height(12.dp))
            top.forEachIndexed { i, t ->
                val util = t.int("current_utilization")
                val cap = capMap[t.str("official_email").lowercase()]
                val tint = when {
                    util > 85 -> sk.crit
                    util >= 60 -> sk.aqua
                    else -> sk.warn
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
    var selectedFilter by remember { mutableStateOf("All") }
    
    val allActions = data.rows("manager_action_objects").filter { it.str("lifecycle_state") != "closed" }
    val allGapTrainers = capability?.rows("trainers").orEmpty()
        .filter { (it.obj("certification")?.int("gap_count") ?: 0) > 0 }
        
    val actions = when (selectedFilter) {
        "Actions" -> allActions
        "Gaps" -> emptyList()
        else -> allActions
    }
    val gapTrainers = when (selectedFilter) {
        "Actions" -> emptyList()
        "Gaps" -> allGapTrainers
        else -> allGapTrainers
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All", "Actions", "Gaps").forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }
        
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (actions.isEmpty() && gapTrainers.isEmpty()) {
                item { EmptyStateCard("No open manager actions. Everything on the roster is signed off.") }
            }
            itemsIndexed(actions) { i, a -> Appear(i) { AttentionCard(a) } }

            if (gapTrainers.isNotEmpty()) {
                item {
                    DashSectionHeader(
                        "Certification gaps",
                        "Courses being taught without the matching certification",
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
}

@Composable
private fun CertGapActionCard(trainer: Map<*, *>, onClick: () -> Unit) {
    val sk = MaterialTheme.skill
    val cert = trainer.obj("certification")
    val missing = cert?.list("missing").orEmpty()
    Box(
        Modifier
            .fillMaxWidth()
            .accentGlass(sk.warn, RoundedCornerShape(Radii.card))
            .clickable(onClick = onClick),
    ) {
        Row {
            Box(
                Modifier.width(3.dp).fillMaxHeight()
                    .background(Brush.verticalGradient(listOf(sk.warn, sk.warn.copy(alpha = 0.2f))))
            )
            Column(Modifier.padding(start = 13.dp, top = 12.dp, end = 13.dp, bottom = 12.dp)) {
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

/** Shimmer in the real card geometry, so the load reads as the page arriving. */
@Composable
private fun DashboardSkeleton() {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ShimmerBox(height = 172.dp, shape = RoundedCornerShape(Radii.hero), modifier = Modifier.fillMaxWidth())
        repeat(3) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                repeat(2) {
                    ShimmerBox(height = 118.dp, shape = RoundedCornerShape(Radii.kpi), modifier = Modifier.weight(1f))
                }
            }
        }
        ShimmerBox(width = 170.dp, height = 12.dp)
        repeat(2) {
            ShimmerBox(height = 120.dp, shape = RoundedCornerShape(Radii.card), modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DashErrorView(message: String, onRetry: () -> Unit) {
    val sk = MaterialTheme.skill
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconSlot(tint = sk.warn, size = 56.dp) {
            Icon(
                painterResource(R.drawable.ic_alert), null,
                tint = sk.warn, modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "Couldn't load your dashboard",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = sk.frost,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = sk.subText,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(Radii.chip),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
            ),
        ) {
            Text("Try again", style = MaterialTheme.typography.labelLarge)
        }
    }
}

// ── Cards ─────────────────────────────────────────────────────────────────────

/**
 * A single 0–100 health score standing in for utilisation, feedback, delivery
 * risk, readiness and certification gaps — so the roster can be scanned and
 * sorted on one number instead of five separately-coloured badges.
 *
 * Weights: feedback incidents dominate (a real, reported problem) over
 * utilisation extremes (which are often just a scheduling gap, not a risk),
 * with delivery risk, readiness and cert gaps as smaller deductions.
 */
internal fun trainerHealth(
    trainer: Map<*, *>,
    capability: Map<*, *>?,
    delivery: Map<*, *>?,
): Pair<Int, String> {
    var score = 100
    val util = trainer.intOrNull("current_utilization")
    val feedbackRisk = trainer.str("feedback_risk")
    val deliveryRisk = delivery?.str("delivery_risk_level").orEmpty()
    val readiness = capability?.str("readiness_bucket").orEmpty()
    val gaps = capability?.obj("certification")?.int("gap_count") ?: 0

    score -= when (feedbackRisk) {
        "High" -> 35
        "Medium" -> 15
        else -> 0
    }
    if (deliveryRisk == "High") score -= 20
    score -= when {
        util == null -> 5
        util > 92 -> 18
        util > 85 -> 10
        util < 15 -> 12
        else -> 0
    }
    score -= when (readiness) {
        "Needs support" -> 15
        "Developing" -> 6
        else -> 0
    }
    score -= (gaps * 4).coerceAtMost(15)
    score = score.coerceIn(0, 100)

    val bucket = when {
        score >= 80 -> "Healthy"
        score >= 60 -> "Watchlist"
        score >= 40 -> "Needs Attention"
        else -> "High Risk"
    }
    return score to bucket
}

/**
 * The roster card, redesigned around four questions a manager actually asks:
 * what is this trainer doing right now, how healthy are they overall, is
 * there risk, and does anything need my action. Certificates, qubit scores
 * and multi-badge stat walls answer none of those — they moved to the
 * trainer-360 detail screen, which is what the profile deep-dive is for.
 */
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
    val utilRaw = trainer.intOrNull("current_utilization")
    val availableCapacity = utilRaw?.let { (100 - it).coerceIn(0, 100) }
    val upcoming = trainer.int("upcoming_count")

    val status = state?.str("current_status") ?: "unknown"
    val statusLabel = state?.str("status_label") ?: "Unknown"
    val cur = state?.obj("current_batch")
    val nxt = state?.obj("next_batch")

    val recommendedAction = trainer.str("recommended_action")
        .takeIf { it.isNotBlank() && it != "Monitor performance" }

    val (health, healthBucket) = trainerHealth(trainer, capability, delivery)

    val statusColor = when (status) {
        "teaching_now" -> sk.cyan
        "scheduled_today" -> sk.sky
        "preparing" -> sk.ice
        "free" -> sk.aqua
        "blocked" -> sk.crit
        else -> sk.labelText
    }
    val healthColor = when (healthBucket) {
        "Healthy" -> sk.aqua
        "Watchlist" -> sk.sky
        "Needs Attention" -> sk.warn
        else -> sk.crit
    }
    val healthProgress by animateProgressFromZero(health / 100f)
    val barColor by animateColorAsState(healthColor, tween(Motion.NORMAL), label = "bar")

    // Severity edge: whichever reading decides if the manager needs to stop here.
    val edge = if (healthBucket == "High Risk" || healthBucket == "Needs Attention") healthColor else statusColor

    Box(
        Modifier
            .fillMaxWidth()
            .accentGlass(edge, RoundedCornerShape(Radii.card), strong = healthBucket == "High Risk")
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(Brush.verticalGradient(listOf(edge, edge.copy(alpha = 0.2f))))
        )
        Column(Modifier.padding(start = 15.dp, top = 13.dp, end = 13.dp, bottom = 13.dp)) {
            // ── Who, and what are they doing right now ──────────────────────
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Avatar(name, capability?.str("photo_url"), 38.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, color = sk.bodyText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        statusLabel.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.5.sp,
                        letterSpacing = 0.08.em,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(6.dp))
                HealthBadge(health, healthColor)
                Icon(
                    painterResource(R.drawable.ic_chevron), null,
                    tint = sk.subText, modifier = Modifier.size(15.dp).padding(start = 4.dp),
                )
            }

            // The batch the trainer is actually in, and when it ends.
            BatchBanner(cur, nxt, status, statusColor, state?.str("reason").orEmpty())

            Spacer(Modifier.height(10.dp))

            // ── Available capacity and upcoming demand ───────────────────────
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Free", style = MaterialTheme.typography.labelSmall, color = sk.subText, modifier = Modifier.width(28.dp))
                LinearProgressIndicator(
                    progress = { healthProgress },
                    modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = barColor, trackColor = sk.track,
                    gapSize = 0.dp, drawStopIndicator = {},
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    availableCapacity?.let { "$it%" } ?: "—",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold, color = barColor,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "$upcoming upcoming",
                    style = MaterialTheme.typography.labelSmall, color = sk.subText,
                )
            }

            // ── Action, only when one is actually required ────────────────────
            if (recommendedAction != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(7.dp))
                        .background(healthColor.copy(alpha = 0.1f))
                        .padding(horizontal = 9.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_flag), null,
                        tint = healthColor, modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        recommendedAction,
                        style = MaterialTheme.typography.labelSmall,
                        color = healthColor,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Score + bucket, the one figure a manager needs to triage the whole roster. */
@Composable
private fun HealthBadge(score: Int, color: Color) {
    Column(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$score", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
        Text(
            "HEALTH", style = MaterialTheme.typography.labelSmall, color = color,
            fontSize = 7.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.1.em,
        )
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
        "feedback" -> sk.crit
        "allocation" -> sk.sky
        else -> sk.warn
    }
    Box(
        Modifier.fillMaxWidth().accentGlass(catColor, RoundedCornerShape(Radii.card)),
    ) {
        Row {
            Box(
                Modifier.width(3.dp).fillMaxHeight()
                    .background(Brush.verticalGradient(listOf(catColor, catColor.copy(alpha = 0.2f))))
            )
            Column(Modifier.padding(start = 13.dp, top = 12.dp, end = 13.dp, bottom = 12.dp)) {
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

/** Status pill: tinted fill, matching hairline, uppercase micro-label. */
@Composable
internal fun Chip(text: String, tint: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(tint.copy(alpha = 0.16f))
            .border(1.dp, tint.copy(alpha = 0.30f), RoundedCornerShape(20.dp)),
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.05.em,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
