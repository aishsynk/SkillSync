package com.example.skillsync

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.auth.LoginScreen
import com.example.skillsync.ui.batch.AllocationState
import com.example.skillsync.ui.batch.AllocationViewModel
import com.example.skillsync.ui.batch.BatchDetailScreen
import com.example.skillsync.ui.components.list
import com.example.skillsync.ui.components.rows
import com.example.skillsync.ui.components.str
import com.example.skillsync.ui.components.Motion
import com.example.skillsync.ui.main.DashboardState
import com.example.skillsync.ui.main.MainScreen
import com.example.skillsync.ui.main.MainScreenViewModel
import com.example.skillsync.ui.trainer.Trainer360Screen

@Composable
fun MainNavigation() {
    // SessionManager.loginState is nullable: null = init() not yet called (app
    // cold start, prefs not read). We start with Login only if we KNOW the user
    // is not logged in (i.e. state is false), never while it is still null.
    var current by remember {
        mutableStateOf<NavKey>(
            when {
                com.example.skillsync.data.SessionManager.isLoggedIn() ->
                    Main(com.example.skillsync.data.SessionManager.getEmail()!!)
                else -> Login
            }
        )
    }

    // Shared so a batch opened from the desk keeps its data and mark-skill state.
    val allocationViewModel: AllocationViewModel = viewModel()
    // Hoisted out of MainScreen so a skill write can invalidate the capability
    // cache that the dashboard and Courses tab read from.
    val mainViewModel: MainScreenViewModel = viewModel()
    val notificationDestination by com.example.skillsync.util.NotificationDestinationStore.pending.collectAsState()

    // loginState is Boolean? — null means init() has not run yet (cold start).
    // Only redirect to Login when we get a DEFINITIVE false, so an app wake-up
    // that reads prefs asynchronously does not flash the login screen.
    val isLoggedIn by com.example.skillsync.data.SessionManager.loginState.collectAsState()

    LaunchedEffect(isLoggedIn) {
        // isLoggedIn == null  → unknown, do nothing and wait for init() to settle
        // isLoggedIn == false → definitively logged out, go to Login
        // isLoggedIn == true  → logged in, restore to Main if we somehow ended up on Login
        when (isLoggedIn) {
            false -> if (current !is Login) current = Login
            true  -> if (current is Login) {
                val email = com.example.skillsync.data.SessionManager.getEmail()
                if (!email.isNullOrBlank()) current = Main(email)
            }
            null  -> Unit // still initialising, hold position
        }
    }

    LaunchedEffect(notificationDestination) {
        val target = notificationDestination ?: return@LaunchedEffect
        val email = com.example.skillsync.data.SessionManager.getEmail()
        if (!email.isNullOrBlank()) {
            current = when (target.type) {
                "demand" -> if (target.id.isNotBlank()) BatchDetail(email, target.id) else Main(email, HomeTab.DEMAND)
                "demand_list" -> Main(email, HomeTab.DEMAND)
                "trainer" -> if (target.id.isNotBlank()) Trainer360(email, target.id, target.label) else Main(email, HomeTab.TEAM)
                "trainer_list" -> Main(email, HomeTab.TEAM)
                "priorities" -> Priorities(email)
                "actions" -> Main(email, HomeTab.ACTIONS)
                else -> Main(email, HomeTab.DASHBOARD)
            }
        }
        com.example.skillsync.util.NotificationDestinationStore.consumed()
    }

    // Hardware/gesture back returns from a pushed detail screen to the shell.
    BackHandler(enabled = current is Trainer360 || current is BatchDetail || current is WeeklyReport || current is Copilot || current is HrReport || current is Priorities || current is CapacityRunway) {
        current = when (val c = current) {
            is Trainer360 -> Main(c.email, HomeTab.TEAM)
            is BatchDetail -> Main(c.email, HomeTab.DEMAND)
            is WeeklyReport -> Main(c.email, HomeTab.DASHBOARD)
            is Copilot -> Main(c.email, HomeTab.DASHBOARD)
            is HrReport -> Main(c.email, HomeTab.TEAM)
            is Priorities -> Main(c.email, HomeTab.DASHBOARD)
            is CapacityRunway -> Priorities(c.email)
            else -> c
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(current, lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (current is Main) {
                    val email = (current as Main).email
                    mainViewModel.adoptBackgroundSync(email)
                    allocationViewModel.adoptBackgroundSync(email, context)
                    com.example.skillsync.data.sync.SyncScheduler.enqueueImmediate(context)
                    mainViewModel.startPolling(email, context)
                }
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                mainViewModel.stopPolling()
            }
        }
        lifecycle.addObserver(observer)
        try {
            kotlinx.coroutines.awaitCancellation()
        } finally {
            lifecycle.removeObserver(observer)
        }
    }

    AnimatedContent(
        targetState = current,
        transitionSpec = {
            val from = initialState
            val to = targetState
            when {
                // Drilling into a trainer: slide in from the right.
                to is Trainer360 ->
                    (slideInHorizontally(tween(Motion.NORMAL, easing = Motion.Emphasized)) { it } +
                        fadeIn(tween(Motion.NORMAL)))
                        .togetherWith(fadeOut(tween(Motion.FAST)))
                // Coming back out: slide away to the right.
                from is Trainer360 ->
                    fadeIn(tween(Motion.NORMAL))
                        .togetherWith(
                            slideOutHorizontally(tween(Motion.NORMAL, easing = Motion.Emphasized)) { it } +
                                fadeOut(tween(Motion.NORMAL))
                        )
                // Switching tabs inside the shell: cross-fade only.
                from is Main && to is Main ->
                    fadeIn(tween(Motion.FAST)).togetherWith(fadeOut(tween(Motion.FAST)))
                // Login -> dashboard.
                else ->
                    (slideInVertically(tween(Motion.SLOW, easing = Motion.Emphasized)) { it / 5 } +
                        fadeIn(tween(Motion.SLOW)))
                        .togetherWith(
                            fadeOut(tween(Motion.NORMAL)) +
                                scaleOut(tween(Motion.NORMAL), targetScale = 0.94f)
                        )
            }
        },
        label = "screen",
    ) { screen ->
        when (screen) {
            is Login -> LoginScreen(
                onLoginSuccess = { email -> current = Main(email) },
            )

            is Main -> MainScreen(
                email = screen.email,
                tab = screen.tab,
                onTabChange = { tab -> current = Main(screen.email, tab) },
                onTrainerClick = { trainerEmail, trainerName ->
                    current = Trainer360(screen.email, trainerEmail, trainerName)
                },
                onBatchClick = { demandId -> current = BatchDetail(screen.email, demandId) },
                onOpenWeeklyReport = { current = WeeklyReport(screen.email) },
                onOpenHrReport = { current = HrReport(screen.email) },
                onOpenPriorities = { current = Priorities(screen.email) },
                onOpenCopilot = { current = Copilot(screen.email) },
                onLogout = { current = Login },
                modifier = Modifier,
                viewModel = mainViewModel,
                allocationViewModel = allocationViewModel,
            )

            is Copilot -> {
                val dash by mainViewModel.uiState.collectAsState()
                val capability by mainViewModel.capability.collectAsState()
                val agentActions by mainViewModel.teamActions.collectAsState()
                val alloc by allocationViewModel.state.collectAsState()
                val payload = (dash as? com.example.skillsync.ui.main.DashboardState.Success)?.intelligenceData
                if (payload == null) {
                    LaunchedEffect(screen.email) { mainViewModel.loadData(screen.email, context) }
                    androidx.compose.material3.CircularProgressIndicator()
                } else {
                    // The desk is what tells the agent who ranks for which
                    // batch, so it is loaded here rather than only on Demand.
                    LaunchedEffect(screen.email) { allocationViewModel.load(screen.email, context) }
                    val facts = com.example.skillsync.ai.FactBuilder.build(
                        dashboard = payload,
                        capability = capability,
                        allocation = (alloc as? AllocationState.Success)?.data,
                        actions = agentActions.map { it.asMap() },
                    )
                    com.example.skillsync.ui.ai.CopilotScreen(
                        team = facts,
                        onTrainerClick = { te, tn -> current = Trainer360(screen.email, te, tn) },
                        onBack = { current = Main(screen.email, HomeTab.DASHBOARD) },
                    )
                }
            }

            is WeeklyReport -> {
                val dash by mainViewModel.uiState.collectAsState()
                val capability by mainViewModel.capability.collectAsState()
                val weeklyActions by mainViewModel.teamActions.collectAsState()
                val payload = (dash as? com.example.skillsync.ui.main.DashboardState.Success)?.intelligenceData
                com.example.skillsync.ui.report.WeeklyReportScreen(
                    managerEmail = screen.email,
                    data = payload ?: emptyMap(),
                    capability = capability,
                    actions = weeklyActions.map { it.asMap() },
                    onTrainerClick = { email, name -> current = Trainer360(screen.email, email, name) },
                    onBack = { current = Main(screen.email, HomeTab.DASHBOARD) },
                )
            }

            is HrReport -> com.example.skillsync.ui.report.HrMonthlyReportScreen(
                managerEmail = screen.email,
                onTrainerClick = { email, name -> current = Trainer360(screen.email, email, name) },
                onBack = { current = Main(screen.email, HomeTab.TEAM) },
            )

            is Priorities -> com.example.skillsync.ui.report.PrioritiesScreen(
                managerEmail = screen.email,
                onOpenDemand = { demandId -> current = BatchDetail(screen.email, demandId) },
                onOpenTrainer = { email, name -> current = Trainer360(screen.email, email, name) },
                onOpenActions = { current = Main(screen.email, HomeTab.ACTIONS) },
                onOpenRunway = { current = CapacityRunway(screen.email) },
                onBack = { current = Main(screen.email, HomeTab.DASHBOARD) },
            )

            is CapacityRunway -> com.example.skillsync.ui.report.CapacityRunwayScreen(
                managerEmail = screen.email,
                onOpenTrainer = { email, name -> current = Trainer360(screen.email, email, name) },
                onBack = { current = Priorities(screen.email) },
            )

            is Trainer360 -> Trainer360Screen(
                trainerEmail = screen.trainerEmail,
                trainerName = screen.trainerName,
                managerEmail = screen.email,
                onBack = { current = Main(screen.email, HomeTab.TEAM) },
            )

            is BatchDetail -> {
                val allocState by allocationViewModel.state.collectAsState()
                val dashState by mainViewModel.uiState.collectAsState()
                val markState by allocationViewModel.mark.collectAsState()
                val demandContext by allocationViewModel.demandContext.collectAsState()
                val demandContextLoading by allocationViewModel.demandContextLoading.collectAsState()
                val demandContextError by allocationViewModel.demandContextError.collectAsState()
                val gatedCandidates by allocationViewModel.gatedCandidates.collectAsState()
                val gatedLoading by allocationViewModel.gatedCandidatesLoading.collectAsState()
                val gatedUnverified by allocationViewModel.gatedCandidatesUnverified.collectAsState()
                val data = (allocState as? AllocationState.Success)?.data
                val dashData = (dashState as? DashboardState.Success)?.intelligenceData

                val rawTarget = screen.demandId.removePrefix("DEM-").trim()
                val batch = data?.rows("batches")
                    ?.firstOrNull { 
                        it.str("demand_id") == screen.demandId || 
                        it.str("demand_id") == rawTarget ||
                        it.str("course_name").equals(screen.demandId, ignoreCase = true) ||
                        it.str("course_id") == screen.demandId ||
                        it.str("course_id") == rawTarget
                    }
                    ?: dashData?.rows("unallocated_demand_df")
                        ?.firstOrNull { 
                            it.str("demand_id") == screen.demandId || 
                            it.str("demand_id") == rawTarget ||
                            it.str("course_name").equals(screen.demandId, ignoreCase = true) ||
                            it.str("course_id") == screen.demandId ||
                            it.str("course_id") == rawTarget
                        }
                    ?: if (screen.demandId.isBlank() || screen.demandId == "demand_list" || screen.demandId == "DEM-open") {
                        data?.rows("batches")?.firstOrNull() ?: dashData?.rows("unallocated_demand_df")?.firstOrNull()
                    } else null

                if (batch == null) {
                    // A notification may launch directly into detail before the
                    // allocation cache is hydrated. Trigger fresh network fetches.
                    LaunchedEffect(screen.email, screen.demandId) {
                        allocationViewModel.refresh(screen.email, context)
                        mainViewModel.loadData(screen.email, context)
                    }
                    Box(
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.skill.cardBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.padding(24.dp),
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.skill.aqua)
                            Text(
                                "Loading unallocated batch details...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.skill.subText,
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { current = Main(screen.email, HomeTab.DEMAND) },
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text("Back to Demand Desk", color = MaterialTheme.skill.brand)
                            }
                        }
                    }
                } else {
                    LaunchedEffect(screen.demandId, batch.str("course_name")) {
                        allocationViewModel.loadDemandContext(screen.email, screen.demandId, batch.str("course_name"))
                        // Full gated evaluation for this one batch: the board
                        // overlay cannot check leave or client exclusions.
                        allocationViewModel.loadGatedCandidates(
                            manager = screen.email,
                            course = batch.str("course_name"),
                            start = batch.str("start_date"),
                            end = batch.str("end_date").ifBlank { batch.str("start_date") },
                            country = batch.str("location"),
                            customer = batch.str("customer"),
                            deliveryMode = batch.str("delivery_mode"),
                            international = batch.str("is_international").equals("true", true),
                        )
                    }
                    // Candidates are this manager's reportees, which is exactly the
                    // set they may mark a skill for.
                    val reportees = (data?.rows("batches") ?: dashData?.rows("unallocated_demand_df") ?: emptyList())
                        .flatMap { it.list("candidates") }
                        .map { it.str("trainer_name") to it.str("trainer_email") }
                        .filter { it.second.isNotBlank() }
                        .distinctBy { it.second }
                        .sortedBy { it.first }

                    BatchDetailScreen(
                        batch = batch,
                        managerEmail = screen.email,
                        reportees = reportees,
                        markState = markState,
                        operationalContext = demandContext,
                        operationalContextLoading = demandContextLoading,
                        operationalContextError = demandContextError,
                        gatedCandidates = gatedCandidates,
                        gatedCandidatesLoading = gatedLoading,
                        gatedCandidatesUnverified = gatedUnverified,
                        onMarkSkill = { courseId, trainerEmail, level, date, who ->
                            allocationViewModel.markSkill(
                                context, courseId, trainerEmail, level, date, who,
                                onSaved = { 
                                    mainViewModel.refreshCapability(screen.email, context)
                                    allocationViewModel.refresh(screen.email, context)
                                },
                            )
                        },
                        onClearMark = { allocationViewModel.clearMark() },
                        onBack = { current = Main(screen.email, HomeTab.DEMAND) },
                    )
                }
            }
        }
    }
}
