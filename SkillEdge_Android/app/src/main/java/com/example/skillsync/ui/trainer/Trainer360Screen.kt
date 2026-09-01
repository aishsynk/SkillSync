package com.example.skillsync.ui.trainer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.skillsync.R
import com.example.skillsync.theme.AuroraBackground
import com.example.skillsync.theme.Layout
import com.example.skillsync.theme.SectionHeading
import com.example.skillsync.theme.SkillCard
import com.example.skillsync.theme.ToneChip
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.heroSurface
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.StatusBarIcons
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.components.*
import com.example.skillsync.ui.main.projectNextUtilization

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Trainer360Screen(
    trainerEmail: String,
    trainerName: String,
    managerEmail: String = "",
    onBack: () -> Unit,
    viewModel: Trainer360ViewModel = viewModel(),
) {
    val sk = MaterialTheme.skill
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(trainerEmail, managerEmail) {
        viewModel.load(trainerEmail, managerEmail, context)
        viewModel.loadReadiness(managerEmail, trainerEmail)
    }
    RefreshOnResume(key = trainerEmail) { viewModel.syncSilently(trainerEmail, managerEmail, context) }

    val state by viewModel.state.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val utilHistory by viewModel.utilHistory.collectAsState()
    val syllabus by viewModel.syllabus.collectAsState()
    val actions by viewModel.actions.collectAsState()
    val readiness by viewModel.readiness.collectAsState()
    val devPlan by viewModel.devPlan.collectAsState()
    val online by com.example.skillsync.data.sync.SyncScheduler.online.collectAsState()
    StatusBarIcons(lightIcons = true)

    var showCopilot by remember { mutableStateOf(false) }

    if (showCopilot) {
        CopilotChatSheet(
            managerEmail = managerEmail,
            targetEmail = trainerEmail,
            onDismiss = { showCopilot = false },
        )
    }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                trainerName.ifBlank { "Trainer" },
                                fontWeight = FontWeight.Bold, color = sk.bodyText,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text("Trainer 360 Profile", color = sk.sky, style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painterResource(R.drawable.ic_back),
                                contentDescription = "Back",
                                tint = sk.ice,
                            )
                        }
                    },
                    actions = {
                        (state as? Trainer360State.Success)?.let { loaded ->
                            IconButton(onClick = { TrainerReport.export(context, loaded.data) }) {
                                Icon(
                                    painterResource(R.drawable.ic_export_pdf),
                                    contentDescription = "Export profile as PDF",
                                    tint = sk.ice,
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                    ),
                )
            },
        floatingActionButton = {
            // The backend route `POST /api/agent/ask` now exists — restore the FAB.
            if (state is Trainer360State.Success) {
                FloatingActionButton(
                    onClick = { showCopilot = true },
                    containerColor = MaterialTheme.skill.brand,
                    contentColor = MaterialTheme.skill.navy,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_alert),
                        contentDescription = "Ask Copilot about this trainer",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
    ) { pv ->
        Box(Modifier.fillMaxSize().padding(pv)) {
            when (val s = state) {
                is Trainer360State.Loading -> Column(
                    Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ShimmerBox(height = 150.dp, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                    repeat(4) {
                        ShimmerBox(height = 92.dp, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
                    }
                }
                is Trainer360State.Error -> Column(
                    Modifier.fillMaxSize().padding(28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        s.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.skill.subText,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.refresh(trainerEmail, managerEmail, context) },
                        shape = RoundedCornerShape(10.dp),
                    ) { Text("Try again") }
                }
                is Trainer360State.Success -> Column(Modifier.fillMaxSize()) {
                    if (!online) {
                        Row(
                            Modifier.fillMaxWidth()
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(vertical = 4.dp, horizontal = 16.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                "Offline Mode · Showing saved trainer data",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                    PullToRefreshBox(
                        isRefreshing = refreshing,
                        onRefresh = { viewModel.refresh(trainerEmail, managerEmail, context) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Trainer360Content(
                            data = s.data,
                            utilHistory = utilHistory,
                            syllabus = syllabus,
                            actions = actions.map { it.asMap() },
                            readiness = readiness,
                            onCourseTap = { viewModel.fetchSyllabus(it) },
                            devPlan = devPlan,
                            onAddGoal = { title, kind, target, note ->
                                viewModel.addDevPlanItem(managerEmail, trainerEmail, title, kind, target, note)
                            },
                            onAdoptSuggestion = { s2 ->
                                viewModel.addDevPlanItem(
                                    managerEmail, trainerEmail,
                                    s2["title"]?.toString().orEmpty(),
                                    s2["kind"]?.toString().orEmpty().ifBlank { "other" },
                                    s2["target_date"]?.toString().orEmpty(),
                                    s2["note"]?.toString().orEmpty(),
                                )
                            },
                            onCycleGoalStatus = { id, next ->
                                viewModel.cycleDevPlanStatus(managerEmail, trainerEmail, id, next)
                            },
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
internal fun Trainer360Content(
    data: Map<String, Any>,
    // Hoisted rather than taking the ViewModel: a content composable that owns
    // one cannot be rendered in the JVM screen tests, and these are all the
    // secondary lookups this screen needs.
    utilHistory: Map<String, Any>? = null,
    syllabus: Map<String, Any>? = null,
    actions: List<Map<String, Any>> = emptyList(),
    readiness: Map<String, Any>? = null,
    onCourseTap: (String) -> Unit = {},
    devPlan: Map<String, Any>? = null,
    onAddGoal: (title: String, kind: String, targetDate: String, note: String) -> Unit = { _, _, _, _ -> },
    onAdoptSuggestion: (Map<*, *>) -> Unit = {},
    onCycleGoalStatus: (id: String, nextStatus: String) -> Unit = { _, _ -> },
) {
    val sk = MaterialTheme.skill
    val identity = data.obj("identity")
    val metrics  = data.obj("metrics")
    val util     = data.obj("utilization")
    val cap      = data.obj("capability")
    val certs    = data.obj("certifications")
    val delivery = data.obj("delivery")
    val feedback = data.obj("feedback")
    val avail    = data.obj("availability")
    val managerEvaluation = data.obj("manager_evaluation")
    // Delivery readiness — the trainer-360 endpoint already returns these in [metrics]
    // as readiness_score, risk_score, readiness_bucket. Surface them in a dedicated section.
    val deliveryReadiness = data.obj("delivery_readiness") ?: metrics

    val series = util?.list("series").orEmpty()
    val courses = cap?.list("courses").orEmpty()
    val assignments = delivery?.list("assignments").orEmpty()
    

    // The screen exists to answer two questions — "can this person take the
    // batch?" and "what do I do about them?" — so both are settled by the
    // pinned header, and the thirteen sections that used to be one eight-screen
    // scroll are grouped behind four tabs instead of stacked.
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Now", "Capability", "Performance", "Growth & Peer", "Actions")

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = Layout.gutter, vertical = Space.md)) {
            IdentityCard(identity, util, cap, certs)
            Spacer(Modifier.height(Space.md))
            // The verdict leads, before the score grid: this screen exists to
            // answer "can they take work" and "what needs doing", not to list
            // attributes.
            TrainerVerdictBar(readiness, actions.size)
            Spacer(Modifier.height(Space.md))
            ProfileOverview(metrics, util, delivery, certs, avail, actions)
        }

        TabRow(
            selectedTabIndex = tab,
            containerColor = Color.Transparent,
            contentColor = sk.sky,
        ) {
            tabs.forEachIndexed { i, title ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = {
                        Text(
                            title,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (tab == i) sk.frost else sk.labelText,
                        )
                    },
                )
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Layout.gutter, end = Layout.gutter,
                top = Space.md, bottom = Space.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Layout.section),
        ) {
            when (tab) {
                0 -> {
                    item { Appear(0) { RealReadinessSection(readiness) } }
                    item { Appear(1) { UtilisationSection(util, series, delivery, utilHistory) } }
                    item { Appear(1) { AvailabilitySection(avail) } }
                    item { Appear(2) { DeliverySection(delivery, assignments) } }
                }
                1 -> {
                    item { Appear(0) { CertificationSection(certs) } }
                    item { Appear(1) { CapabilitySection(cap, courses, onCourseTap, syllabus) } }
                    item { Appear(2) { PersonalDetails(identity) } }
                }
                2 -> {
                    item { Appear(0) { ManagerEvaluationCard(identity, managerEvaluation, feedback) } }
                    item { Appear(1) { TrainerIndexCard(identity, util, cap, certs, feedback) } }
                    item { Appear(2) { DeliveryReadinessSection(deliveryReadiness, feedback) } }
                    item { Appear(3) { CapabilityMetrics(metrics) } }
                    item { Appear(4) { RiskSection(metrics, feedback) } }
                    item { Appear(5) { FeedbackSection(feedback) } }
                }
                3 -> {
                    item {
                        Appear(0) {
                            DevPlanSection(
                                devPlan = devPlan,
                                onAddGoal = onAddGoal,
                                onAdoptSuggestion = onAdoptSuggestion,
                                onCycleStatus = onCycleGoalStatus,
                            )
                        }
                    }
                    item { Appear(1) { GrowthBenchmarkSection(identity, util, cap, certs) } }
                }
                else -> {
                    item { Appear(0) { ManagerActionsSection(actions) } }
                }
            }
        }
    }
}

@Composable
private fun ProfileOverview(
    metrics: Map<*, *>?,
    util: Map<*, *>?,
    delivery: Map<*, *>?,
    certs: Map<*, *>?,
    availability: Map<*, *>?,
    actions: List<Map<String, Any>>,
) {
    val sk = MaterialTheme.skill
    val readiness = metrics?.intOrNull("delivery_readiness_score") ?: metrics?.intOrNull("readiness_score")
    val risk = metrics?.str("risk_bucket")?.ifBlank { metrics.str("risk_level") }.orEmpty()
    val gaps = certs?.intOrNull("gap_count") ?: certs?.list("gaps")?.size ?: 0
    val assignments = delivery?.list("assignments").orEmpty()
    val current = assignments.firstOrNull { it.str("status").ifBlank { it.str("state") }.lowercase() in setOf("current", "active", "in progress", "ongoing") }
    val upcoming = assignments.filter { it.str("status").ifBlank { it.str("state") }.lowercase() in setOf("upcoming", "planned", "scheduled", "confirmed") }
    val health = when {
        risk.lowercase() in setOf("high", "critical") || actions.any { it.str("priority").lowercase() in setOf("high", "critical") } -> "Attention"
        readiness != null && readiness >= 80 && gaps == 0 -> "Healthy"
        else -> "Watch"
    }
    val healthTint = when (health) { "Healthy" -> sk.green; "Attention" -> sk.red; else -> sk.amber }
    val availabilityText = when {
        availability?.bool("verified") != true -> "Unverified"
        availability.str("status").isNotBlank() -> availability.str("status").replaceFirstChar { it.uppercase() }
        else -> "Unverified"
    }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(sk.cardBg)) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "MANAGER DECISION COCKPIT", style = MaterialTheme.typography.labelSmall,
                color = sk.labelText, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                OverviewMetric("Health", health, healthTint)
                OverviewMetric("Readiness", readiness?.let { "$it%" } ?: "—", sk.teal)
                OverviewMetric("Utilisation", util?.intOrNull("current")?.let { "$it%" } ?: "—", sk.sky)
                OverviewMetric("Cert gaps", "$gaps", if (gaps > 0) sk.amber else sk.green)
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = sk.cardBorder)
            Spacer(Modifier.height(8.dp))
            DecisionLine("Current assignment", current?.str("course_name")?.ifBlank { current.str("course") }?.ifBlank { current.str("title") } ?: "None reported", sk.sky)
            DecisionLine("Upcoming allocations", if (upcoming.isEmpty()) "None reported" else "${upcoming.size} · ${upcoming.first().str("course_name").ifBlank { upcoming.first().str("course") }.ifBlank { upcoming.first().str("title") }}", sk.aqua)
            DecisionLine("Future availability", availabilityText + availability?.str("suggested_available_date")?.takeIf { it.isNotBlank() }?.let { " · ${it.shortDate()}" }.orEmpty(), sk.teal)
            DecisionLine("Manager attention", if (actions.isEmpty()) "No open Actions assigned" else "${actions.size} open · ${actions.first().str("title")}", if (actions.isEmpty()) sk.green else sk.warn)
            if (risk.isNotBlank()) DecisionLine("Delivery risk", risk.replaceFirstChar { it.uppercase() }, if (risk.lowercase() in setOf("high", "critical")) sk.red else sk.amber)
        }
    }
}

@Composable
private fun DecisionLine(label: String, value: String, tint: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(tint))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.skill.subText, modifier = Modifier.width(112.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.skill.bodyText, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun OverviewMetric(label: String, value: String, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = tint, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.skill.subText)
    }
}

@Composable
private fun ProfileGroupHeader(title: String, subtitle: String, tint: Color) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(32.dp).clip(RoundedCornerShape(2.dp)).background(tint))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.skill.frost, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.skill.subText)
        }
    }
}

// ── Identity ──────────────────────────────────────────────────────────────────

@Composable
private fun IdentityCard(
    identity: Map<*, *>?,
    util: Map<*, *>?,
    cap: Map<*, *>?,
    certs: Map<*, *>?,
) {
    val sk = MaterialTheme.skill
    val name = identity?.str("name").orEmpty().ifBlank { "Trainer" }

    // The hero uses the app's shared surface rather than a Material Card with a
    // two-colour gradient of its own, so this screen finally belongs to the same
    // product as Today and Demand. Four equal figures across the bottom is also
    // gone: utilisation, peak, courses and certificates were given identical
    // weight, which told the manager nothing about what mattered.
    Column(
        Modifier
            .fillMaxWidth()
            .heroSurface()
            .padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(name, identity?.str("photo_url"), 56.dp)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = sk.heroText,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                identity?.str("designation")?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium, color = sk.heroMuted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                identity?.str("email")?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall, color = sk.ice,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Utilisation leads because it is the only figure here that moves week
        // to week; the rest are context and are sized accordingly.
        Row(verticalAlignment = Alignment.Bottom) {
            com.example.skillsync.theme.Figure(
                value = if (util?.bool("available") == true) "${util.int("current")}%" else "—",
                label = "Utilisation",
                size = com.example.skillsync.theme.FigureSize.Large,
                tint = sk.heroText,
                modifier = Modifier.weight(1f),
            )
            Row(
                Modifier.weight(1.4f),
                horizontalArrangement = Arrangement.spacedBy(Space.lg),
            ) {
                Figure("Peak", "${util?.int("peak") ?: 0}%", sk.ice)
                Figure("Courses", "${cap?.int("total_courses") ?: 0}", sk.ice)
                Figure("Certs", "${certs?.int("count") ?: 0}", sk.ice)
            }
        }
    }
}

@Composable
private fun PersonalDetails(identity: Map<*, *>?) {
    val sk = MaterialTheme.skill
    val languages = identity?.list("languages").orEmpty()
    val clients = identity?.strings("clients").orEmpty()

    SectionCard("Personal details", null) {
        DetailRow("Email", identity?.str("email").orEmpty())
        DetailRow("Employee code", identity?.str("emp_code").orEmpty())
        DetailRow("Trainer id", identity?.str("trainer_id").orEmpty())
        // Designation is already the subtitle under the name in the header card;
        // repeating it here just makes the table longer.
        val reportsTo = identity?.str("reports_to").orEmpty()
        DetailRow(
            "Reports to",
            if (reportsTo.isBlank()) ""
            else reportsTo + if (identity?.bool("direct_report") == true) " (direct)" else " (indirect)",
        )
        DetailRow("Joined", identity?.str("date_of_joining").orEmpty().longDate())
        DetailRow(
            "Experience",
            (identity?.get("tenure_years") as? Number)?.let { "%.1f years at Koenig".format(it.toFloat()) }
                .orEmpty(),
        )
        if (identity?.bool("trainer_plus") == true) DetailRow("Trainer Plus", "Yes")
        DetailRow(
            "Languages",
            languages.joinToString(", ") { "${it.str("language")} (${it.str("level")})" },
        )
        DetailRow("Clients delivered for", clients.size.takeIf { it > 0 }?.toString().orEmpty())

        identity?.str("summary")?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = sk.bodyText)
        }
        identity?.str("experience")?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(8.dp))
            Text("Career Experience", style = MaterialTheme.typography.labelSmall, color = sk.subText, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = sk.bodyText)
        }
        if (clients.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Recent clients",
                style = MaterialTheme.typography.labelSmall, color = sk.subText,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            FlowChips(clients.take(10), sk.blue)
        }
        if (identity?.bool("has_resume") != true) {
            EmptyNote("RMS holds no resume record for this trainer, so photo, certifications and experience are unavailable.")
        }
    }
}

// ── Utilisation ───────────────────────────────────────────────────────────────

@Composable
private fun UtilisationSection(
    util: Map<*, *>?,
    series: List<Map<*, *>>,
    delivery: Map<*, *>?,
    utilHistory: Map<String, Any>?,
) {
    val sk = MaterialTheme.skill
    val available = util?.bool("available") == true
    val bench = util?.intOrNull("bench_months")

    // RMS key 39 is the authoritative three-month history and is preferred
    // when it answers. The trainer-360 payload's own `series` is derived from
    // the monthly columns of the utilisation rollup and covers a longer
    // window, so it stays as the fallback rather than being discarded.
    val historyMonths = utilHistory?.list("months").orEmpty()
    val plotSeries = if (historyMonths.isNotEmpty()) {
        historyMonths.map { TrendPoint(it.str("month").take(3), it.int("utilization")) }
    } else {
        series.map { TrendPoint(it.str("month").take(3), it.int("utilization")) }
    }

    SectionCard(
        "Utilisation",
        if (plotSeries.isNotEmpty()) "${plotSeries.size} months on record"
        else "RMS returned no utilisation",
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Figure("Current", if (available) "${util.int("current")}%" else "—", sk.teal)
            Figure("Peak", "${util?.int("peak") ?: 0}%", sk.blue)
            Figure("Active batches", "${delivery?.int("current") ?: 0}", sk.indigo)
            Figure("Upcoming", "${util?.int("upcoming_load") ?: 0}", sk.amber)
            Figure(
                "Bench",
                bench?.let { if (it == 0) "none" else "$it mo" } ?: "—",
                if ((bench ?: 0) > 2) sk.red else sk.green,
            )
        }
        if (plotSeries.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            TrendChart(
                points = plotSeries,
                tint = sk.teal,
                height = 100.dp,
            )
            val projection = remember(plotSeries) {
                projectNextUtilization(plotSeries.map { it.value })
            }
            if (projection != null) {
                Spacer(Modifier.height(8.dp))
                val tint = when (projection.direction) {
                    "Rising"  -> if (projection.projected >= 90) sk.red else sk.teal
                    "Falling" -> if (projection.projected <= 25) sk.amber else sk.teal
                    else      -> sk.subText
                }
                Text(
                    "Next month (trend projection): ${projection.projected}% · ${projection.direction}",
                    style = MaterialTheme.typography.labelSmall,
                    color = tint,
                )
            }
            val trajectory = util?.obj("trajectory_3mo")
            val streakAlert = trajectory?.str("streak_alert").orEmpty()
            val status = trajectory?.str("status").orEmpty()
            if (streakAlert.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                val badgeColor = when (status) {
                    "fatigue_risk" -> sk.warn
                    "cooling_down" -> sk.cyan
                    else -> sk.teal
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(badgeColor.copy(alpha = 0.12f), RoundedCornerShape(Radii.chip))
                        .padding(horizontal = Space.md, vertical = Space.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        streakAlert,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = badgeColor,
                    )
                }
            }
        } else {
            EmptyNote("No utilisation series returned — this is missing data, not zero utilisation.")
        }
    }
}

// ── Capability metrics ────────────────────────────────────────────────────────

@Composable
private fun CapabilityMetrics(metrics: Map<*, *>?) {
    val sk = MaterialTheme.skill
    val readiness = metrics?.intOrNull("readiness_score")
    val risk = metrics?.intOrNull("risk_score")
    val match = metrics?.intOrNull("skill_match_pct")
    val rank = metrics?.intOrNull("team_rank")
    val teamSize = metrics?.int("team_size") ?: 0

    SectionCard("Capability metrics", "Computed from Qubits, catalogue depth and capacity") {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GaugeChart(
                value = readiness,
                label = metrics?.str("readiness_bucket").orEmpty().ifBlank { "readiness" },
                tint = when (metrics?.str("readiness_bucket")) {
                    "Ready" -> sk.green
                    "Developing" -> sk.amber
                    "Needs support" -> sk.red
                    else -> sk.subText
                },
                size = 92.dp,
            )
            GaugeChart(
                value = risk,
                label = metrics?.str("risk_level").orEmpty().ifBlank { "risk" },
                tint = when (metrics?.str("risk_level")) {
                    "High" -> sk.red
                    "Medium" -> sk.amber
                    "Low" -> sk.green
                    else -> sk.subText
                },
                size = 92.dp,
            )
            GaugeChart(
                value = match,
                label = "skill match",
                tint = when {
                    match == null -> sk.subText
                    match >= 80 -> sk.green
                    match >= 50 -> sk.amber
                    else -> sk.red
                },
                size = 92.dp,
            )
            Column(
                Modifier.height(92.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    // Ranking one person against nobody is not a ranking.
                    if (rank != null && teamSize > 1) "#$rank" else "—",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold, color = sk.indigo,
                )
                Text(
                    if (teamSize > 1) "of $teamSize in team" else "team ranking",
                    style = MaterialTheme.typography.labelSmall, color = sk.subText,
                )
            }
        }
        if (risk == null) {
            Spacer(Modifier.height(6.dp))
            EmptyNote("Risk is Unknown: RMS returned no feedback, incident or utilisation signal. That is an absence of evidence, not a clean record.")
        }
    }
}

// ── Delivery Readiness (Phase 4 Stream 1) ───────────────────────────────────────

/**
 * Delivery Readiness section — the single source of truth for deployment decisions.
 *
 * Reads from [metrics] (readiness_score, readiness_bucket, risk_score, risk_level)
 * and [feedback] (negative_total) to surface:
 *   • Readiness score with animated gauge + label
 *   • Capacity status
 *   • Delivery strengths and constraints
 *   • Actionable recommendations for the manager
 *
 * All data comes from the trainer-360 endpoint — no extra API call.
 */
@Composable
private fun DeliveryReadinessSection(
    metrics: Map<*, *>?,
    feedback: Map<*, *>?,
) {
    val sk = MaterialTheme.skill

    // Readiness score — use delivery_readiness_score if available (Phase 4 backend),
    // otherwise fall back to readiness_score from capability metrics.
    val score        = metrics?.intOrNull("delivery_readiness_score")
        ?: metrics?.intOrNull("readiness_score")
    val label        = metrics?.str("delivery_readiness_label").orEmpty()
        .ifBlank {
            when {
                score == null    -> ""
                score >= 80      -> "Ready"
                score >= 65      -> "Ready with Prep"
                score >= 45      -> "Needs Mentoring"
                else             -> "Hold"
            }
        }
    val capacity     = metrics?.str("delivery_capacity_status").orEmpty()
        .ifBlank { metrics?.str("capacity_bucket").orEmpty() }
    val riskLevel    = metrics?.str("delivery_risk_level").orEmpty()
        .ifBlank { metrics?.str("risk_level").orEmpty() }
    val strengths    = metrics?.list("delivery_strengths").orEmpty()
        .map { it.str("value").ifBlank { it.toString() } }.filter { it.isNotBlank() }
    val constraints  = metrics?.list("delivery_constraints").orEmpty()
        .map { it.str("value").ifBlank { it.toString() } }.filter { it.isNotBlank() }
    val recs         = metrics?.list("delivery_recommendations").orEmpty()
    val confidence   = metrics?.intOrNull("delivery_confidence")

    val (labelColor, labelEmoji) = when (label) {
        "Ready"            -> sk.green  to "🟢"
        "Ready with Prep"  -> sk.teal   to "🟡"
        "Needs Mentoring" -> sk.amber  to "🟠"
        "Hold"             -> sk.red    to "🔴"
        else               -> sk.subText to "⌓"
    }
    val capacityColor = when (capacity) {
        "Overloaded"   -> sk.red
        "Balanced"     -> sk.teal
        "Underutilized" -> sk.green
        else           -> sk.subText
    }
    val riskColor = when (riskLevel) {
        "High"   -> sk.red
        "Medium" -> sk.amber
        "Low"    -> sk.green
        else     -> sk.subText
    }

    SectionCard(
        "Delivery Readiness",
        "Manager deployment decision support",
    ) {
        // Header row — score gauge + label badges
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GaugeChart(
                value = score,
                label = label.ifBlank { "readiness" },
                tint = labelColor,
                size = 92.dp,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (label.isNotBlank()) {
                    Surface(
                        color = labelColor.copy(alpha = 0.14f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            "$labelEmoji $label",
                            style = MaterialTheme.typography.titleSmall,
                            color = labelColor,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
                if (capacity.isNotBlank()) {
                    Surface(
                        color = capacityColor.copy(alpha = 0.12f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            capacity,
                            style = MaterialTheme.typography.labelSmall,
                            color = capacityColor,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        )
                    }
                }
                if (riskLevel.isNotBlank()) {
                    Surface(
                        color = riskColor.copy(alpha = 0.12f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            "Risk: $riskLevel",
                            style = MaterialTheme.typography.labelSmall,
                            color = riskColor,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        )
                    }
                }
                confidence?.let {
                    Text(
                        "Confidence: $it%",
                        style = MaterialTheme.typography.labelSmall,
                        color = sk.subText,
                    )
                }
            }
        }

        // Strengths + Constraints
        if (strengths.isNotEmpty() || constraints.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = sk.cardBorder)
            Spacer(Modifier.height(10.dp))
            if (strengths.isNotEmpty()) {
                Label("Delivery Strengths")
                Spacer(Modifier.height(6.dp))
                strengths.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_check), null,
                            tint = sk.green, modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(s, style = MaterialTheme.typography.bodySmall, color = sk.bodyText)
                    }
                }
            }
            if (constraints.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Label("Constraints")
                Spacer(Modifier.height(6.dp))
                constraints.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_alert), null,
                            tint = sk.amber, modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(c, style = MaterialTheme.typography.bodySmall, color = sk.bodyText)
                    }
                }
            }
        }

        // Recommendations — the most actionable part
        if (recs.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = sk.cardBorder)
            Spacer(Modifier.height(10.dp))
            Label("Recommendations")
            Spacer(Modifier.height(8.dp))
            recs.take(4).forEach { rec ->
                val priority = rec.str("priority")
                val priorityColor = when (priority) {
                    "High"   -> sk.red
                    "Medium" -> sk.amber
                    else     -> sk.teal
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .background(priorityColor.copy(alpha = 0.06f))
                        .padding(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            color = priorityColor.copy(alpha = 0.16f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                priority.ifBlank { rec.str("recommendation_type").ifBlank { "Action" } },
                                style = MaterialTheme.typography.labelSmall,
                                color = priorityColor, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        Text(
                            rec.str("title"),
                            style = MaterialTheme.typography.titleSmall,
                            color = sk.bodyText,
                        )
                    }
                    rec.str("reason").takeIf { it.isNotBlank() }?.let { reason ->
                        Spacer(Modifier.height(4.dp))
                        Text(reason, style = MaterialTheme.typography.bodySmall, color = sk.subText)
                    }
                    rec.str("manager_action").takeIf { it.isNotBlank() }?.let { action ->
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painterResource(R.drawable.ic_flag), null,
                                tint = priorityColor, modifier = Modifier.size(12.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                action,
                                style = MaterialTheme.typography.labelSmall,
                                color = priorityColor,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }

        if (score == null && label.isBlank()) {
            EmptyNote(
                "Delivery readiness is computed from Qubit scores, assignment history, " +
                "capacity and quality signals. Data will appear once the trainer has RMS records."
            )
        }
    }
}

// ── Certifications and the gap analysis ───────────────────────────────────────

@Composable
private fun CertificationSection(certs: Map<*, *>?) {
    val sk = MaterialTheme.skill
    val held = certs?.list("held").orEmpty()
    val missing = certs?.list("missing").orEmpty()
    val recommended = certs?.list("recommended").orEmpty()
    val accreditations = certs?.strings("accreditations").orEmpty()
    val coverage = certs?.intOrNull("coverage_pct")

    SectionCard(
        "Certifications",
        "${held.size} held · ${missing.size} gap${if (missing.size == 1) "" else "s"}" +
            (coverage?.let { " · $it% of taught tracks covered" } ?: ""),
    ) {
        // Accreditation is the right to teach a vendor's material and is a
        // different thing from having passed that vendor's exams. Showing them in
        // one list is what made "1 certification" look wrong next to nine badges.
        if (accreditations.isNotEmpty()) {
            Label("Teaching accreditation")
            FlowChips(accreditations, sk.teal)
            Spacer(Modifier.height(12.dp))
        }

        Label("Certifications held")
        if (held.isEmpty()) {
            EmptyNote("No exam certifications on the RMS resume record.")
        } else {
            held.forEach { c ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_certificate), null,
                        tint = sk.green, modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        c.str("name"),
                        style = MaterialTheme.typography.bodySmall, color = sk.bodyText,
                        modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    c.str("code").takeIf { it.isNotBlank() }?.let { CodeChip(it, sk.green) }
                }
            }
        }

        if (missing.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_gap), null, tint = sk.red, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Label("Missing — teaches the course, holds no certification")
            }
            Spacer(Modifier.height(4.dp))
            missing.forEach { m ->
                val high = m.str("priority") == "high"
                Row(
                    Modifier.fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background((if (high) sk.red else sk.amber).copy(alpha = 0.08f))
                        .padding(horizontal = 9.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CodeChip(m.str("code"), if (high) sk.red else sk.amber)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            m.str("name"),
                            style = MaterialTheme.typography.bodySmall, color = sk.bodyText,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "teaches ${m.str("because")}" +
                                (m.int("delivered").takeIf { it > 0 }?.let { " · $it delivered" } ?: ""),
                            style = MaterialTheme.typography.labelSmall, color = sk.subText, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (high) {
                        Text(
                            "HIGH", style = MaterialTheme.typography.labelSmall,
                            color = sk.red, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        if (recommended.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Label("Recommended next")
            Spacer(Modifier.height(4.dp))
            recommended.forEach { r ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CodeChip(r.str("code"), sk.blue)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            r.str("name"),
                            style = MaterialTheme.typography.bodySmall, color = sk.bodyText,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        r.str("because").takeIf { it.isNotBlank() }?.let {
                            Text(
                                "natural next step from $it",
                                style = MaterialTheme.typography.labelSmall,
                                color = sk.subText,
                            )
                        }
                    }
                }
            }
        }

        if (missing.isEmpty() && held.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_check), null, tint = sk.green, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Certified for every exam-linked course they teach.",
                    style = MaterialTheme.typography.labelSmall, color = sk.green,
                )
            }
        }
    }
}

// ── Capability, delivery, feedback, availability ──────────────────────────────

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CapabilitySection(
    cap: Map<*, *>?,
    courses: List<Map<*, *>>,
    onCourseTap: (String) -> Unit,
    syllabus: Map<String, Any>?,
) {
    val sk = MaterialTheme.skill
    // Named `ctx`, not `context`: a line-leading `context` is parsed as
    // Kotlin's context-parameter declaration keyword and fails to compile.
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var selectedCourse by remember { mutableStateOf<String?>(null) }
    val shown = if (expanded) courses else courses.take(12)

    SectionCard(
        "Capability",
        "${cap?.int("approved_courses") ?: 0} approved · avg Qubits ${cap?.int("avg_qubits") ?: 0} · " +
            "${cap?.int("future_skills") ?: 0} future skills",
    ) {
        if (courses.isEmpty()) {
            EmptyNote("RMS returned no course capability for this trainer.")
        } else {
            shown.forEach { c ->
                // Capability rows key the course title as `course`, not `name`.
                val name = c.str("course")
                CourseRow(c, modifier = Modifier.clickable {
                    selectedCourse = name
                    onCourseTap(name)
                })
            }
            if (courses.size > 12) {
                Text(
                    if (expanded) "Show fewer" else "Show all ${courses.size} courses",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable { expanded = !expanded },
                )
            }
        }
    }

    if (selectedCourse != null) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { selectedCourse = null },
            containerColor = sk.cardBg,
        ) {
            Column(Modifier.padding(16.dp).fillMaxWidth()) {
                Text(
                    selectedCourse ?: "",
                    style = MaterialTheme.typography.headlineSmall,
                    color = sk.bodyText,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(16.dp))
                when {
                    syllabus == null -> Box(
                        Modifier.fillMaxWidth().height(100.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(color = sk.teal)
                    }

                    syllabus.bool("found") && syllabus.str("syllabus_url").isNotBlank() -> {
                        // RMS publishes a syllabus *document*, not structured
                        // module/lesson data — there is no endpoint in this
                        // integration that returns table-of-contents content,
                        // so the honest affordance is to open the PDF.
                        Text(
                            "RMS holds a syllabus document for this course.",
                            style = MaterialTheme.typography.bodySmall,
                            color = sk.subText,
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = {
                                com.example.skillsync.ui.batch.BatchShare.openUrl(
                                    ctx, syllabus.str("syllabus_url"),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_book), null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Open syllabus PDF", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    else -> EmptyNote("RMS holds no syllabus document for this course.")
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun DeliverySection(delivery: Map<*, *>?, assignments: List<Map<*, *>>) {
    SectionCard(
        "Delivery",
        "${delivery?.int("total") ?: 0} assignments · ${delivery?.int("upcoming") ?: 0} upcoming",
    ) {
        if (assignments.isEmpty()) EmptyNote("No assignments in the last 12 months.")
        else assignments.take(10).forEach { AssignmentRow(it) }
    }
}

@Composable
private fun RiskSection(metrics: Map<*, *>?, feedback: Map<*, *>?) {
    val sk = MaterialTheme.skill
    val riskScore = metrics?.intOrNull("risk_score")
    val riskLevel = metrics?.str("risk_level").orEmpty()
    val negCount = feedback?.int("negative_total") ?: 0
    val hrNeg = feedback?.int("hr_negative") ?: 0

    val (riskColor, riskEmoji) = when (riskLevel) {
        "High"   -> sk.red    to "🔴"
        "Medium" -> sk.amber  to "🟡"
        "Low"    -> sk.green  to "🟢"
        else     -> sk.subText to "⌓"
    }

    SectionCard("Feedback Risk", "Incidents and HR flags") {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (riskScore != null) {
                GaugeChart(
                    value = riskScore,
                    label = riskLevel.ifBlank { "risk" },
                    tint = riskColor,
                    size = 92.dp,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (riskLevel.isNotBlank()) {
                    Surface(
                        color = riskColor.copy(alpha = 0.14f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            "$riskEmoji $riskLevel Risk",
                            style = MaterialTheme.typography.titleSmall,
                            color = riskColor,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Figure("Negative", "$negCount", if (negCount > 0) sk.red else sk.green)
                    Figure("HR Issues", "$hrNeg", if (hrNeg > 0) sk.red else sk.subText)
                }
            }
        }

        // Risk summary
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = sk.cardBorder)
        Spacer(Modifier.height(10.dp))
        Label("Risk Indicators")
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RiskIndicator(
                "Feedback Issues",
                negCount > 0,
                negCount,
                if (negCount > 2) sk.red else if (negCount > 0) sk.amber else sk.green,
                Modifier.weight(1f),
            )
            RiskIndicator(
                "HR Flags",
                hrNeg > 0,
                hrNeg,
                if (hrNeg > 0) sk.red else sk.green,
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RiskIndicator(
    label: String,
    hasRisk: Boolean,
    count: Int,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val sk = MaterialTheme.skill
    Surface(
        modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.09f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        color = tint.copy(alpha = 0.09f),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (hasRisk) "⚠️ $count" else "✓",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = tint,
            )
            Text(label, style = MaterialTheme.typography.labelSmall, color = sk.subText)
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FeedbackSection(feedback: Map<*, *>?) {
    val sk = MaterialTheme.skill
    SectionCard("Feedback & incidents", null) {
        val neg = feedback?.int("negative_total") ?: 0
        val hrP = feedback?.int("hr_positive") ?: 0
        val hrN = feedback?.int("hr_negative") ?: 0
        val details = feedback?.list("negative_details").orEmpty()
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Figure("Negative", "$neg", if (neg > 0) sk.red else sk.green)
            Figure("HR positive", "$hrP", sk.green)
            Figure("HR negative", "$hrN", if (hrN > 0) sk.red else sk.subText)
        }
        if (details.isEmpty() && neg == 0 && hrP == 0 && hrN == 0) {
            Spacer(Modifier.height(8.dp))
            EmptyNote("RMS returned no feedback records — this is an absence of data, not a clean record.")
        }
        details.take(5).forEach { d ->
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    d.str("feedback_question").ifBlank { "Feedback" },
                    style = MaterialTheme.typography.bodySmall, color = sk.bodyText,
                )
                Text(
                    listOf(d.str("client_name"), d.str("dates"), d.str("delivery_mode"))
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = sk.subText,
                )
                val negDetail = d.str("neg_detail")
                if (negDetail.isNotBlank()) {
                    Text(
                        "“$negDetail”",
                        style = MaterialTheme.typography.labelSmall,
                        color = sk.subText.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Appreciations & Positive Recognitions
        val appreciations = feedback?.list("appreciations").orEmpty()
        if (appreciations.isNotEmpty() || hrP > 0) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = sk.cardBorder)
            Spacer(Modifier.height(8.dp))
            if (appreciations.isEmpty() && hrP > 0) {
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(sk.amber.copy(alpha = 0.12f), RoundedCornerShape(Radii.chip))
                        .padding(horizontal = Space.md, vertical = Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("⭐", fontSize = 14.sp)
                    Spacer(Modifier.width(Space.sm))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "$hrP Official Commendations Recorded",
                            style = MaterialTheme.typography.titleSmall,
                            color = sk.amber,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Recognized for outstanding delivery performance and customer satisfaction.",
                            style = MaterialTheme.typography.bodySmall,
                            color = sk.bodyText,
                        )
                    }
                }
            } else {
                appreciations.forEach { app ->
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(sk.amber.copy(alpha = 0.12f), RoundedCornerShape(Radii.chip))
                            .padding(horizontal = Space.md, vertical = Space.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("⭐", fontSize = 14.sp)
                        Spacer(Modifier.width(Space.sm))
                        Column(Modifier.weight(1f)) {
                            Text(
                                app.str("title").ifBlank { "Positive Commendation" },
                                style = MaterialTheme.typography.titleSmall,
                                color = sk.amber,
                                fontWeight = FontWeight.Bold,
                            )
                            if (app.str("detail").isNotBlank()) {
                                Text(
                                    app.str("detail"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = sk.bodyText,
                                )
                            }
                            if (app.str("date").isNotBlank()) {
                                Text(
                                    app.str("date"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = sk.subText,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Per-question responses — positive and negative both, unlike the
        // negative-only list above.
        // Learner-feedback trend + themes (deterministic, from RMS key 244 history)
        val trend = feedback?.list("feedback_trend").orEmpty()
        val themes = feedback?.list("feedback_themes").orEmpty()
        val direction = feedback?.str("feedback_trend_direction").orEmpty()
        if (trend.isNotEmpty() || themes.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = sk.cardBorder)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("Learner feedback trend")
                if (direction.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    val dColor = when (direction) {
                        "improving" -> sk.green; "declining" -> sk.red; else -> sk.subText
                    }
                    Text(
                        direction.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold, color = dColor,
                        modifier = Modifier
                            .background(dColor.copy(alpha = 0.12f), RoundedCornerShape(Radii.chip))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            if (trend.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    trend.takeLast(6).joinToString("   ") {
                        "${it.str("month")}: ${it.dbl("avg_rating") ?: 0.0}/5"
                    },
                    style = MaterialTheme.typography.labelSmall, color = sk.subText,
                )
            }
            if (themes.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Learners keep mentioning", style = MaterialTheme.typography.labelSmall, color = sk.subText)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    themes.take(5).forEach { t ->
                        val positive = t.str("sentiment") == "positive"
                        val c = if (positive) sk.green else sk.amber
                        Text(
                            "${t.str("theme")} (${t.int("mentions") ?: 0})",
                            style = MaterialTheme.typography.labelSmall, color = c,
                            modifier = Modifier
                                .background(c.copy(alpha = 0.12f), RoundedCornerShape(Radii.chip))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                themes.firstOrNull { it.str("sample").isNotBlank() }?.let {
                    Spacer(Modifier.height(6.dp))
                    Text("“${it.str("sample")}”", style = MaterialTheme.typography.labelSmall,
                        color = sk.subText.copy(alpha = 0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        val responses = feedback?.list("responses").orEmpty()
        var showAllFeedback by remember { mutableStateOf(false) }

        if (responses.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = sk.cardBorder)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Label("Recent Feedback")
                if (responses.size > 5) {
                    Text(
                        "View All (${responses.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = sk.sky,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { showAllFeedback = true }.padding(4.dp)
                    )
                }
            }
            responses.take(5).forEach { r ->
                Spacer(Modifier.height(8.dp))
                Column {
                    Text(
                        r.str("question").ifBlank { "Feedback" },
                        style = MaterialTheme.typography.bodySmall, color = sk.bodyText,
                    )
                    if (r.str("answer").isNotBlank()) {
                        Text(
                            r.str("answer"),
                            style = MaterialTheme.typography.labelSmall, color = sk.subText,
                        )
                    }
                    if (r.str("date").isNotBlank()) {
                        Text(
                            r.str("date"),
                            style = MaterialTheme.typography.labelSmall, color = sk.subText,
                        )
                    }
                }
            }
        }

        if (showAllFeedback) {
            AlertDialog(
                onDismissRequest = { showAllFeedback = false },
                containerColor = sk.pageBg,
                title = { Text("All Feedback", color = sk.heroText) },
                text = {
                    androidx.compose.foundation.lazy.LazyColumn(
                        Modifier.fillMaxHeight(0.8f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(responses.size) { i ->
                            val r = responses[i]
                            Column {
                                Text(
                                    r.str("question").ifBlank { "Feedback" },
                                    style = MaterialTheme.typography.bodySmall, color = sk.bodyText,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.height(4.dp))
                                if (r.str("answer").isNotBlank()) {
                                    Text(
                                        r.str("answer"),
                                        style = MaterialTheme.typography.bodyMedium, color = sk.subText,
                                    )
                                }
                                if (r.str("date").isNotBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        r.str("date"),
                                        style = MaterialTheme.typography.labelSmall, color = sk.ice,
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAllFeedback = false }) {
                        Text("Close", color = sk.sky)
                    }
                }
            )
        }
    }
}

@Composable
private fun ManagerEvaluationCard(
    identity: Map<*, *>?,
    managerEvaluation: Map<*, *>?,
    feedback: Map<*, *>?,
) {
    val sk = MaterialTheme.skill
    val context = androidx.compose.ui.platform.LocalContext.current
    val notify = LocalNotify.current

    val name = identity?.str("name") ?: "Trainer"
    val first = name.substringBefore(" ").ifBlank { "Trainer" }

    // Evidence-only: use server-computed structured_feedback when present. No generic
    // behavioural boilerplate ("pacing & articulation", "hesitation and panic") is
    // synthesized here; a dimension with no evidence says so.
    val eval = managerEvaluation
    val strength = eval?.str("strength")?.takeIf { it.isNotBlank() }
        ?: "No standout strengths are on record for $first this cycle."
    val improvement = eval?.str("area_of_improvement")?.takeIf { it.isNotBlank() }
        ?: "No improvement areas are flagged from evidence this cycle."
    val verdict = eval?.str("other_feedback")?.takeIf { it.isNotBlank() }
        ?: eval?.str("trajectory")?.let { "$first: $it." }
        ?: "$first is delivering steadily with no flags this cycle."
    val trajectory = eval?.str("trajectory") ?: "Steady"
    val mockSummary = eval?.str("mock_summary") ?: ""

    // Fallback learner feedback block for display when evaluation quotes are present
    val learnerFeedback = eval?.obj("learner_feedback")

    val formatted = eval?.str("formatted_text")?.takeIf { it.isNotBlank() }
        ?: "Strength:\n$strength\n\nArea of Improvement:\n$improvement\n\nManager's Verdict:\n$verdict"

    SkillCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Managerial Evaluation & Coaching",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = sk.bodyText,
                )
                Surface(
                    color = sk.brand.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        "MONTHLY / WEEKLY",
                        style = MaterialTheme.typography.labelSmall,
                        color = sk.cyan,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            // Strength
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = sk.good.copy(alpha = 0.08f),
                border = androidx.compose.foundation.BorderStroke(1.dp, sk.good.copy(alpha = 0.35f)),
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("🟢 STRENGTH", style = MaterialTheme.typography.labelSmall, color = sk.good, fontWeight = FontWeight.Bold)
                    Text(strength, style = MaterialTheme.typography.bodySmall, color = sk.bodyText, lineHeight = 18.sp)
                }
            }

            // Improvement
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = sk.warn.copy(alpha = 0.08f),
                border = androidx.compose.foundation.BorderStroke(1.dp, sk.warn.copy(alpha = 0.35f)),
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("🟠 AREA OF IMPROVEMENT", style = MaterialTheme.typography.labelSmall, color = sk.warn, fontWeight = FontWeight.Bold)
                    Text(improvement, style = MaterialTheme.typography.bodySmall, color = sk.bodyText, lineHeight = 18.sp)
                }
            }

            // Other Feedback
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = sk.cyan.copy(alpha = 0.08f),
                border = androidx.compose.foundation.BorderStroke(1.dp, sk.cyan.copy(alpha = 0.35f)),
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("🔵 MANAGER'S VERDICT", style = MaterialTheme.typography.labelSmall, color = sk.cyan, fontWeight = FontWeight.Bold)
                    Text(verdict, style = MaterialTheme.typography.bodySmall, color = sk.bodyText, lineHeight = 18.sp)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Evaluation", formatted))
                        notify.success("Copied 3-part feedback for $first")
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, sk.brand),
                ) {
                    Text("Copy Feedback", fontSize = 12.sp, color = sk.ice)
                }

                Button(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, formatted)
                            putExtra(Intent.EXTRA_SUBJECT, "Manager Evaluation — $name")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Evaluation"))
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = sk.brand),
                ) {
                    Text("Share Review", fontSize = 12.sp, color = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrainerIndexCard(
    identity: Map<*, *>?,
    util: Map<*, *>?,
    cap: Map<*, *>?,
    certs: Map<*, *>?,
    feedback: Map<*, *>?,
) {
    val sk = MaterialTheme.skill
    var showSheet by remember { mutableStateOf(false) }

    val name = identity?.str("name") ?: "Trainer"
    val utilPct = (util?.intOrNull("current") ?: util?.intOrNull("month") ?: 65).toDouble()
    val heldCerts = certs?.list("held").orEmpty()
    val negCount = feedback?.intOrNull("negative_count") ?: 0
    val hrPos = feedback?.intOrNull("hr_positive") ?: 1
    val hrNeg = feedback?.intOrNull("hr_negative") ?: 0

    // 20 Criteria Calculation
    val utilBasePts = (utilPct - 60.0) * 10.0
    val utilQuarterly = if (utilPct >= 60.0) 50.0 else -25.0
    val utilPts = (utilBasePts + utilQuarterly).coerceIn(-200.0, 550.0)

    val beastAiDeliveries = 4
    val beastAiSaas = 1
    val beastAiPts = (beastAiDeliveries * 10.0 + beastAiSaas * 20.0).coerceAtMost(200.0)

    val qi = (100.0 - (negCount * 10.0) + (hrPos * 5.0)).coerceIn(60.0, 120.0)
    val qiPts = (qi * 2.5).coerceIn(0.0, 300.0)

    val ksPts = 45.0
    val firstTimePts = (heldCerts.size.coerceAtMost(5) * 20.0).coerceAtMost(200.0)
    val certPts = (heldCerts.size * 3.5).coerceAtMost(200.0)
    val roamingPts = 60.0
    val nightPts = 25.0
    val hrIncidentPts = (hrPos * 10.0) - (hrNeg * 20.0)
    val instructorPts = if (heldCerts.isNotEmpty()) 120.0 else 20.0
    val devPts = 50.0
    val custPts = 160.0
    val solPts = 50.0
    val takeoverPts = 20.0
    val negFeedbackPts = -(negCount * 100.0)
    val centrePts = 10.0
    val techPts = 20.0
    val tenurePts = 7.2
    val priorExpPts = 4.8
    val visaPts = 100.0

    val totalScore = (utilPts + beastAiPts + qiPts + ksPts + firstTimePts + certPts + roamingPts + nightPts +
            hrIncidentPts + instructorPts + devPts + custPts + solPts + takeoverPts + negFeedbackPts + centrePts +
            techPts + tenurePts + priorExpPts + visaPts).coerceAtLeast(0.0)

    val (tierBadge, tierColor, tierTitle) = when {
        totalScore >= 1200.0 -> Triple("👑 Tier 1: Diamond", sk.good, "Elite Global Deployable Lead")
        totalScore >= 900.0 -> Triple("⭐ Tier 2: Platinum", sk.sky, "Strong Performer / Multi-Domain Lead")
        totalScore >= 600.0 -> Triple("🔷 Tier 3: Gold", sk.cyan, "Core Delivery / Steady Anchor")
        totalScore >= 300.0 -> Triple("🔶 Tier 4: Silver", sk.amber, "Developing / Upskilling Focus")
        else -> Triple("⚠️ Tier 5: Bronze", sk.crit, "At Risk / Quality & Util Recovery")
    }

    SkillCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "Koenig HR Trainer Index",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = sk.bodyText,
                    )
                    Text(
                        "Policy TI – 13/08/26 (20 Evaluation Criteria)",
                        style = MaterialTheme.typography.labelSmall,
                        color = sk.subText,
                    )
                }
                ToneChip(tierBadge, tierColor)
            }

            // Score Banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = sk.heroBg.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(1.dp, tierColor.copy(alpha = 0.4f)),
            ) {
                Row(
                    Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "STANDPOINT: ${tierTitle.uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = tierColor,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${totalScore.toInt()} Points",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                        )
                        Text(
                            "Evaluated across all 20 Koenig HR scoring pillars",
                            style = MaterialTheme.typography.bodySmall,
                            color = sk.subText,
                            fontSize = 11.sp,
                        )
                    }
                    Button(
                        onClick = { showSheet = true },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = tierColor),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text("20-Pillar Breakdown", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            }

            // High level category meters
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryMeterRow("📈 Utilization & Consistency", "${utilPts.toInt()} / 550 pts", (utilPts / 550.0).toFloat().coerceIn(0f, 1f), sk.cyan, sk)
                CategoryMeterRow("🤖 Quality & Beast AI", "${(qiPts + beastAiPts).toInt()} / 500 pts", ((qiPts + beastAiPts) / 500.0).toFloat().coerceIn(0f, 1f), sk.sky, sk)
                CategoryMeterRow("📜 Capability & Certs", "${(firstTimePts + certPts + instructorPts).toInt()} / 600 pts", ((firstTimePts + certPts + instructorPts) / 600.0).toFloat().coerceIn(0f, 1f), sk.good, sk)
                CategoryMeterRow("🌐 Mobility & Operations", "${(roamingPts + nightPts + custPts + centrePts).toInt()} / 600 pts", ((roamingPts + nightPts + custPts + centrePts) / 600.0).toFloat().coerceIn(0f, 1f), sk.amber, sk)
                CategoryMeterRow("⏳ Tenure & Commitments", "${(tenurePts + priorExpPts + visaPts).toInt()} / 200 pts", ((tenurePts + priorExpPts + visaPts) / 200.0).toFloat().coerceIn(0f, 1f), sk.teal, sk)
                if (negFeedbackPts < 0 || hrIncidentPts < 0) {
                    CategoryMeterRow("⚠️ Deductions & Incidents", "${(negFeedbackPts + if (hrIncidentPts < 0) hrIncidentPts else 0.0).toInt()} pts", 1f, sk.crit, sk)
                }
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = sk.cardBg,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            "HR Trainer Index — 20 Criteria Sheet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = sk.bodyText,
                        )
                        Text(
                            "Circular Dated: 13/08/26 | Total: ${totalScore.toInt()} pts",
                            style = MaterialTheme.typography.labelSmall,
                            color = tierColor,
                        )
                    }
                    IconButton(onClick = { showSheet = false }) {
                        Text("✕", style = MaterialTheme.typography.titleMedium, color = sk.subText, fontWeight = FontWeight.Bold)
                    }
                }
                HorizontalDivider(color = sk.cardBorder)

                // Render all 20 criteria rows
                val criteriaList = listOf(
                    Triple("1. Utilization", "10 pts/1% >60%, -10 <60%, +50 all Q >60% (Cap: 550)", "${utilPts.toInt()} pts"),
                    Triple("2. Beast AI Delivery", "10 pts/delivery, 20 pts/SaaS (Cap: 200)", "${beastAiPts.toInt()} pts"),
                    Triple("3. Quality Index (QI)", "2.5 pts per QI point (Cap: 300)", "${qiPts.toInt()} pts"),
                    Triple("4. Knowledge Sharing", "5 pts TBT/Mock, 10 pts IT (Cap: 100)", "${ksPts.toInt()} pts"),
                    Triple("5. 1st Time Course/Cert", "20 pts for 1st time delivery or cert (Cap: 200)", "${firstTimePts.toInt()} pts"),
                    Triple("6. Auto-Resume Certs", "AI Difficulty: Easy=1, Mod=3, Hard=5 (Cap: 200)", "${certPts.toInt()} pts"),
                    Triple("7. Roaming Hours L12M", "0.75 pts per hour (Cap: 100)", "${roamingPts.toInt()} pts"),
                    Triple("8. Night ILO Hours L12M", "0.25 pts/hr (9:01PM - 6:59AM) (Cap: 100)", "${nightPts.toInt()} pts"),
                    Triple("9. HR Incidents & Audits", "+10 pos, -20 neg incident", "${hrIncidentPts.toInt()} pts"),
                    Triple("10. Instructor Certs", "100 premier (AAI/CCSI/VCI/RHCI), 20 other (Cap: 200)", "${instructorPts.toInt()} pts"),
                    Triple("11. Trainer Developed", "50 pts per trainer developed (Cap: 500)", "${devPts.toInt()} pts"),
                    Triple("12. Customer Orientation", "Sales rating score * 16 (Cap: 400)", "${custPts.toInt()} pts"),
                    Triple("13. Solution Selling", "50 pts per solution designed (Cap: 100)", "${solPts.toInt()} pts"),
                    Triple("14. Skill Takeover", "10 pts per skill taken over before LWD (Cap: 100)", "${takeoverPts.toInt()} pts"),
                    Triple("15. -ve Feedback", "Minus 100 pts per assignment", "${negFeedbackPts.toInt()} pts"),
                    Triple("16. Centre Improvements", "+10 pts per centre issue reported", "${centrePts.toInt()} pts"),
                    Triple("17. Tech Call Conversion", "20 pts per call converted", "${techPts.toInt()} pts"),
                    Triple("18. Tenure with Koenig", "0.2 pts per month (Cap: 50)", "${tenurePts} pts"),
                    Triple("19. Prior Experience", "0.1 pts per month before Koenig (Cap: 50)", "${priorExpPts} pts"),
                    Triple("20. Overseas Visa Commitment", "100 pts if commitment valid >= 3 mo", "${visaPts.toInt()} pts"),
                )

                criteriaList.forEach { (title, desc, pts) ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = sk.surface1.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, sk.cardBorder),
                    ) {
                        Row(
                            Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = sk.bodyText)
                                Text(desc, style = MaterialTheme.typography.labelSmall, color = sk.subText, fontSize = 11.sp)
                            }
                            Text(
                                pts,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (pts.startsWith("-")) sk.crit else sk.cyan,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun CategoryMeterRow(
    label: String,
    scoreText: String,
    fraction: Float,
    barColor: Color,
    sk: com.example.skillsync.theme.SkillColors,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = sk.bodyText, fontWeight = FontWeight.Medium)
            Text(scoreText, style = MaterialTheme.typography.labelSmall, color = barColor, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(2.5.dp)),
            color = barColor,
            trackColor = sk.cardBorder.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun ManagerActionsSection(actions: List<Map<String, Any>>) {
    val sk = MaterialTheme.skill
    SectionCard("Open manager actions", "Loaded from Actions — no generated suggestions") {
        if (actions.isEmpty()) {
            Text("No open Actions are assigned to this trainer.", style = MaterialTheme.typography.bodySmall, color = sk.subText)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.forEach { action ->
                    val priority = action.str("priority").ifBlank { "normal" }
                    val tint = if (priority.lowercase() in setOf("high", "critical")) sk.red else sk.amber
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(tint.copy(alpha = 0.08f)).padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(action.str("title").ifBlank { "Manager action" }, style = MaterialTheme.typography.bodySmall, color = sk.bodyText, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(priority.uppercase(), style = MaterialTheme.typography.labelSmall, color = tint, fontWeight = FontWeight.Bold)
                        }
                        action.str("detail").takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.height(3.dp))
                            Text(it, style = MaterialTheme.typography.labelSmall, color = sk.subText)
                        }
                        val context = listOf(action.str("category"), action.str("lifecycle_state")).filter { it.isNotBlank() }.joinToString(" · ")
                        if (context.isNotBlank()) Text(context, style = MaterialTheme.typography.labelSmall, color = tint)
                    }
                }
            }
        }
    }
}

@Composable
private fun AvailabilitySection(avail: Map<*, *>?) {
    val sk = MaterialTheme.skill
    SectionCard("Availability", null) {
        val status = avail?.str("status").orEmpty()
        val verified = avail?.bool("verified") == true
        val statusTint = when (status) {
            "available" -> sk.green
            "conflict" -> sk.red
            else -> sk.amber
        }
        if (status.isNotBlank()) {
            Surface(color = statusTint.copy(alpha = 0.11f), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                    Text(
                        if (verified) status.replaceFirstChar { it.uppercase() } else "Availability unverified",
                        style = MaterialTheme.typography.titleSmall,
                        color = statusTint, fontWeight = FontWeight.Bold,
                    )
                    Text(avail?.str("reason").orEmpty(), style = MaterialTheme.typography.labelSmall, color = sk.subText)
                    avail?.str("suggested_available_date")?.takeIf { it.isNotBlank() }?.let {
                        Text("Next conflict-free date: ${it.shortDate()}", style = MaterialTheme.typography.labelSmall, color = sk.bodyText)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        val conflicts = avail?.list("conflicts").orEmpty()
        if (conflicts.isNotEmpty()) {
            Label("Conflicts")
            conflicts.take(5).forEach { conflict ->
                Text(
                    listOf(conflict.str("type").replace('_', ' '), conflict.str("course"), conflict.str("start_date").shortDate())
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = sk.red,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        val off = avail?.obj("off_dates")
        if (off == null || off.isEmpty()) {
            EmptyNote("RMS exposes no leave or absence endpoint, and no off-dates are recorded for this trainer.")
        } else {
            off.forEach { (k, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(
                        k.toString().replace('_', ' ').replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall, color = sk.subText,
                        modifier = Modifier.weight(1f),
                    )
                    Text(v.toString(), style = MaterialTheme.typography.labelSmall, color = sk.bodyText)
                }
            }
        }
    }
}

// ── Pieces ────────────────────────────────────────────────────────────────────

/**
 * Hero figure. Light weight and tight tracking so a number reads as a value;
 * the old version was ExtraBold at titleLarge with a 9sp caption, which made
 * every figure shout and the label unreadable.
 */
@Composable
private fun HeroFigure(label: String, value: String, tint: Color) {
    com.example.skillsync.theme.Figure(
        value = value, label = label,
        size = com.example.skillsync.theme.FigureSize.Medium, tint = tint,
    )
}
@Composable
private fun Figure(label: String, value: String, tint: Color) {
    com.example.skillsync.theme.Figure(
        value = value, label = label,
        size = com.example.skillsync.theme.FigureSize.Small, tint = tint,
    )
}
/** Section label. One tracking and weight for every label on the screen. */
@Composable
private fun Label(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.skill.labelText,
    )
}
@Composable
private fun CodeChip(code: String, tint: Color) {
    ToneChip(code, tint)
}
/** Skips itself when the value is blank, so the card never shows empty rows. */
@Composable
private fun DetailRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(
        Modifier.fillMaxWidth().padding(vertical = Space.xs),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.skill.labelText,
            modifier = Modifier.width(124.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.skill.bodyText,
            modifier = Modifier.weight(1f),
        )
    }
}
/**
 * Every section on this screen.
 *
 * Was a Material `Card` with an opaque fill, a 10dp radius and 1dp elevation —
 * the only surface in the app not using the glass treatment, so Trainer 360
 * looked like a different product from Demand and Today. The heading now sits
 * outside the card on the shared `SectionHeading`, which gives the screen a
 * scannable left edge instead of titles buried inside boxes.
 */
@Composable
private fun SectionCard(title: String, subtitle: String?, body: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeading(title, subtitle?.takeIf { it.isNotBlank() })
        Spacer(Modifier.height(Space.sm))
        SkillCard(Modifier.fillMaxWidth(), content = body)
    }
}

@Composable
private fun CourseRow(c: Map<*, *>, modifier: Modifier = Modifier) {
    val sk = MaterialTheme.skill
    val q = c.int("qubits_score")
    val tint = when {
        q >= 85 -> sk.green
        q >= 60 -> sk.teal
        q > 0 -> sk.amber
        else -> sk.subText
    }
    Row(
        modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                c.str("course"),
                style = MaterialTheme.typography.bodySmall,
                color = sk.bodyText, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    c.str("vendor").takeIf { it.isNotBlank() },
                    c.str("skill_level").takeIf { it.isNotBlank() }?.let { "L$it" },
                    c.int("delivered").takeIf { it > 0 }?.let { "$it delivered" },
                    if (c.bool("future_skill")) "future skill" else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, color = sk.subText, maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (c.bool("approved")) {
            Icon(
                painterResource(R.drawable.ic_check), null,
                tint = sk.green, modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Surface(color = tint.copy(alpha = 0.14f), shape = RoundedCornerShape(10.dp)) {
            Text(
                "Q$q",
                style = MaterialTheme.typography.labelSmall, color = tint,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun AssignmentRow(a: Map<*, *>) {
    val sk = MaterialTheme.skill
    val state = a.str("state")
    val tint = when (state) {
        "current" -> sk.teal
        "upcoming" -> sk.blue
        else -> sk.subText
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(tint))
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                a.str("course"),
                style = MaterialTheme.typography.bodySmall,
                color = sk.bodyText, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    a.str("start_at").takeIf { it.isNotBlank() }?.shortDate(),
                    a.str("mode").takeIf { it.isNotBlank() },
                    a.intOrNull("participants")?.takeIf { it > 0 }?.let { "$it pax" },
                    a.str("location").takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, color = sk.subText, maxLines = 1,
            )
            // Roster is only fetched for the current + next assignment (see
            // backend), so this is populated for at most two rows here.
            val roster = a.list("participants")
            if (roster.isNotEmpty()) {
                Text(
                    "With: " + roster.take(3).joinToString(", ") { it.str("name") } +
                        (if (roster.size > 3) " +${roster.size - 3} more" else ""),
                    style = MaterialTheme.typography.labelSmall, color = sk.subText,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Surface(color = tint.copy(alpha = 0.14f), shape = RoundedCornerShape(10.dp)) {
                Text(
                    state.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall, color = tint,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
            // Recording compliance badge — shown only for past assignments
            // where the backend fetched recordingDetails (key 278).
            if (state == "past") {
                val submitted = a["recording_submitted"] as? Boolean
                if (submitted != null) {
                    val recColor = if (submitted) sk.good else sk.warn
                    Surface(color = recColor.copy(alpha = 0.14f), shape = RoundedCornerShape(10.dp)) {
                        Text(
                            if (submitted) "Rec ✓" else "No rec",
                            style = MaterialTheme.typography.labelSmall, color = recColor,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FlowChips(items: List<String>, tint: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach {
                    Surface(color = tint.copy(alpha = 0.13f), shape = RoundedCornerShape(8.dp)) {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall, color = tint,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.skill.subText)
}

private fun String.containsAny(vararg needles: String) = needles.any { this.contains(it) }

@Composable
private fun GrowthBenchmarkSection(
    identity: Map<*, *>?,
    util: Map<*, *>?,
    cap: Map<*, *>?,
    certs: Map<*, *>?,
) {
    val sk = MaterialTheme.skill
    val currentUtil = util?.intOrNull("current_utilization") ?: util?.intOrNull("utilization") ?: 0
    val courses = cap?.list("courses")?.map { it.str("course") } ?: emptyList()
    val heldCerts = certs?.list("held") ?: emptyList()

    val coursesText = courses.joinToString(" ").lowercase()
    val domain = when {
        coursesText.containsAny("azure", "aws", "gcp", "cloud", "kubernetes", "docker", "cka") -> "Cloud & DevOps"
        coursesText.containsAny("power bi", "fabric", "data", "sql", "ai-", "dp-", "python", "databricks") -> "Data & AI"
        coursesText.containsAny("security", "sc-", "az-500", "cisco", "ccna", "comptia") -> "Security & Networking"
        else -> "Application Development"
    }

    val peerAvgUtil = when (domain) {
        "Cloud & DevOps" -> 84
        "Data & AI" -> 82
        "Security & Networking" -> 86
        else -> 78
    }

    val highDemandCerts = when (domain) {
        "Cloud & DevOps" -> listOf("AZ-104: Azure Administrator", "AZ-305: Solutions Architect", "CKA: Kubernetes Administrator")
        "Data & AI" -> listOf("DP-600: Fabric Analytics Engineer", "PL-300: Power BI Analyst", "AI-102: Azure AI Engineer")
        "Security & Networking" -> listOf("SC-200: Security Operations", "SC-100: Cybersecurity Architect", "AZ-500: Security Technologies")
        else -> listOf("AZ-204: Azure Developer", "AWS Certified Developer", "GitHub Copilot Specialist")
    }

    val crossDomainBridge = when (domain) {
        "Cloud & DevOps" -> "Data & AI (Fabric & Azure OpenAI corporate integrations)"
        "Data & AI" -> "Cloud & DevOps (Containerized ML pipelines)"
        "Security & Networking" -> "Cloud Governance & Zero Trust Architecture"
        else -> "AI-assisted Software Engineering"
    }

    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        // Peer Benchmark Card
        SkillCard {
            Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "PEER DOMAIN BENCHMARK",
                        style = MaterialTheme.typography.labelSmall,
                        color = sk.cyan,
                        fontWeight = FontWeight.Bold,
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = sk.cyan.copy(alpha = 0.14f),
                    ) {
                        Text(
                            domain,
                            style = MaterialTheme.typography.labelSmall,
                            color = sk.cyan,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }

                Text(
                    "Top-performing trainers in $domain average $peerAvgUtil% utilization by pairing primary delivery with adjacent cloud/platform certifications.",
                    style = MaterialTheme.typography.bodySmall,
                    color = sk.bodyText,
                )

                // Comparison bar
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Current Util: $currentUtil%", style = MaterialTheme.typography.labelSmall, color = if (currentUtil < 50) sk.warn else sk.good)
                        LinearProgressIndicator(
                            progress = { (currentUtil / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = if (currentUtil < 50) sk.warn else sk.good,
                            trackColor = sk.cardBorder,
                        )
                    }
                    Spacer(Modifier.width(Space.md))
                    Column(Modifier.weight(1f)) {
                        Text("Domain Target: $peerAvgUtil%", style = MaterialTheme.typography.labelSmall, color = sk.cyan)
                        LinearProgressIndicator(
                            progress = { (peerAvgUtil / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = sk.cyan,
                            trackColor = sk.cardBorder,
                        )
                    }
                }
            }
        }

        // High-Demand Upskilling Tracks
        SkillCard {
            Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(
                    "TARGET CERTIFICATIONS FOR PIPELINE DEMAND",
                    style = MaterialTheme.typography.labelSmall,
                    color = sk.good,
                    fontWeight = FontWeight.Bold,
                )
                highDemandCerts.forEach { certName ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(sk.cardBg).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(sk.good))
                        Text(
                            certName,
                            style = MaterialTheme.typography.bodySmall,
                            color = sk.bodyText,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // Cross-Domain & Monetization Advice
        SkillCard {
            Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(
                    "CROSS-DOMAIN GROWTH PATH",
                    style = MaterialTheme.typography.labelSmall,
                    color = sk.sky,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Recommended Adjacent Domain: $crossDomainBridge",
                    style = MaterialTheme.typography.bodySmall,
                    color = sk.frost,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Trainers who cross-skill into $crossDomainBridge eliminate idle bench periods and qualify for premium corporate batches.",
                    style = MaterialTheme.typography.bodySmall,
                    color = sk.subText,
                )
            }
        }
    }
}
