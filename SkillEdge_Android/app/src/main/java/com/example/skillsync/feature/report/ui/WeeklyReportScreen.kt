package com.example.skillsync.ui.report

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.example.skillsync.R
import com.example.skillsync.theme.AuroraBackground
import com.example.skillsync.theme.Figure
import com.example.skillsync.theme.FigureSize
import com.example.skillsync.theme.Layout
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.SectionHeading
import com.example.skillsync.theme.Severity
import com.example.skillsync.theme.SkillCard
import com.example.skillsync.theme.SkillColors
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.ToneChip
import com.example.skillsync.theme.pressable
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.components.*

/**
 * Enterprise Weekly Delivery & Operations Intelligence Screen.
 * Driven strictly by live RMS API return data and verified KPI statistics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyReportScreen(
    managerEmail: String = "",
    data: Map<String, Any> = emptyMap(),
    capability: Map<String, Any>? = null,
    actions: List<Map<String, Any>> = emptyList(),
    onTrainerClick: (email: String, name: String) -> Unit = { _, _ -> },
    onBack: () -> Unit,
    vm: WeeklyReportViewModel = viewModel(),
) {
    val sk = MaterialTheme.skill
    val context = LocalContext.current
    val notify = LocalNotify.current

    LaunchedEffect(managerEmail) {
        if (managerEmail.isNotBlank()) {
            vm.init(managerEmail, context)
        }
    }

    val state by vm.state.collectAsState()
    val displayWeek by vm.displayWeek.collectAsState()
    val canNext by remember { derivedStateOf { vm.canGoNext() } }

    var style by rememberSaveable { mutableStateOf(MessageStyle.TEAMS) }
    // Screen-level message cadence: false = "This week" (Monday plan), true = "Weekend" (Friday wrap-up).
    var weekendSelected by rememberSaveable { mutableStateOf(false) }
    var teamUserMessage by rememberSaveable { mutableStateOf("") }
    var teamMyMessage by rememberSaveable { mutableStateOf("") }
    var teamRewritten by rememberSaveable { mutableStateOf("") }
    var teamRewriting by remember { mutableStateOf(false) }
    val teamScope = rememberCoroutineScope()
    var selectedFilter by rememberSaveable { mutableStateOf("All") }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Weekly Delivery Report", style = MaterialTheme.typography.titleLarge, color = sk.bodyText, fontWeight = FontWeight.Bold)
                            Text(
                                displayWeek,
                                style = MaterialTheme.typography.labelSmall,
                                color = sk.sky,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_back), "Back", tint = sk.ice)
                        }
                    },
                    actions = {
                        if (state is WeeklyReportState.Success) {
                            val repData = (state as WeeklyReportState.Success).data
                            IconButton(onClick = {
                                copyToClipboard(context, "Weekly Team Digest", repData.teamDigest)
                                notify.success("Copied Weekly Broadcast Digest")
                            }) {
                                Icon(painterResource(R.drawable.ic_copy), "Copy Digest", tint = sk.ice)
                            }
                            IconButton(onClick = {
                                shareWeeklyDigest(context, repData)
                            }) {
                                Icon(painterResource(R.drawable.ic_share), "Share Digest", tint = sk.ice)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
        ) { pv ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(pv),
            ) {
                // Interactive Week Navigation Bar
                WeekNavBar(
                    displayWeek = displayWeek,
                    canNext = canNext,
                    onPrev = { vm.previousWeek() },
                    onNext = { vm.nextWeek() },
                    onReset = { vm.resetToCurrentWeek() },
                    sk = sk,
                )

                when (val s = state) {
                    is WeeklyReportState.Loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = sk.brand)
                        }
                    }

                    is WeeklyReportState.Error -> {
                        // Fallback to local dashboard data if live API fails
                        if (data.isNotEmpty()) {
                            FallbackWeeklyContent(
                                data = data,
                                capability = capability,
                                actions = actions,
                                style = style,
                                onStyleChange = { style = it },
                                note = teamMyMessage,
                                onNoteChange = { teamMyMessage = it },
                                onTrainerClick = onTrainerClick,
                                context = context,
                                notify = notify,
                                sk = sk,
                            )
                        } else {
                            Column(
                                Modifier.fillMaxSize().padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text("Could not load weekly report", color = sk.warn, style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(8.dp))
                                Text(s.message, color = sk.subText, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                                Spacer(Modifier.height(16.dp))
                                Button(onClick = { vm.reload() }, colors = ButtonDefaults.buttonColors(containerColor = sk.brand)) {
                                    Text("Retry")
                                }
                            }
                        }
                    }

                    is WeeklyReportState.Success -> {
                        val repData = s.data
                        val allReportees = repData.reportees

                        val filteredReportees = remember(allReportees, selectedFilter) {
                            when (selectedFilter) {
                                "Delivering" -> allReportees.filter { it.batchCount > 0 }
                                "On Bench" -> allReportees.filter { it.capacityBucket.equals("On Bench", true) }
                                "At Risk" -> allReportees.filter { it.feedbackRisk.equals("High", true) }
                                "Cert Gaps" -> allReportees.filter { it.certGaps > 0 }
                                else -> allReportees
                            }
                        }

                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = Layout.gutter, end = Layout.gutter,
                                top = Space.sm, bottom = Space.xxl,
                            ),
                            verticalArrangement = Arrangement.spacedBy(Layout.section),
                        ) {
                            // 1. Executive Team Overview Card
                            item {
                                WeeklyTeamOverviewCard(repData.teamSummary, sk)
                            }

                            // 1b. Headline message block — the point of this screen.
                            item {
                                TeamMessageCard(
                                    title = "Message to the team",
                                    message = if (weekendSelected) repData.teamDigestWeekend else repData.teamDigest,
                                    weekendSelected = weekendSelected,
                                    onCadenceChange = { weekendSelected = it },
                                    primaryLabel = "This week",
                                    endLabel = "Weekend",
                                    copyLabel = "Copy for Teams",
                                    onCopy = { text ->
                                        copyToClipboard(context, "Team Digest", text)
                                        notify.success("Copied team message")
                                    },
                                    sk = sk,
                                )
                            }

                            // 2. Quick Action Bar (Copy / Share / Format Switcher)
                            item {
                                SkillCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                "FORMAT & EXPORT",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = sk.labelText,
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                MessageStyle.entries.forEach { option ->
                                                    val selected = style == option
                                                    Text(
                                                        if (option == MessageStyle.TEAMS) "Teams" else "Plain",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (selected) sk.frost else sk.subText,
                                                        modifier = Modifier
                                                            .background(
                                                                if (selected) sk.brand.copy(alpha = 0.85f) else sk.glass,
                                                                RoundedCornerShape(Radii.chip),
                                                            )
                                                            .pressable { style = option }
                                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                                    )
                                                }
                                            }
                                        }

                                        OutlinedTextField(
                                            value = teamUserMessage,
                                            onValueChange = { teamUserMessage = it; teamRewritten = "" },
                                            label = { Text("User Message [User Message: …]") },
                                            placeholder = { Text("Paste their message — Hinglish or informal is fine") },
                                            shape = RoundedCornerShape(Radii.chip),
                                            modifier = Modifier.fillMaxWidth(),
                                            minLines = 2,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = sk.brand,
                                                unfocusedBorderColor = sk.glassBorder,
                                                focusedTextColor = sk.bodyText,
                                                unfocusedTextColor = sk.bodyText,
                                                cursorColor = sk.brand,
                                            ),
                                        )
                                        OutlinedTextField(
                                            value = teamMyMessage,
                                            onValueChange = { teamMyMessage = it; teamRewritten = "" },
                                            label = { Text("My Message [My Message: …]") },
                                            placeholder = { Text("Your intent in your own words — at least one is required") },
                                            shape = RoundedCornerShape(Radii.chip),
                                            modifier = Modifier.fillMaxWidth(),
                                            minLines = 2,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = sk.brand,
                                                unfocusedBorderColor = sk.glassBorder,
                                                focusedTextColor = sk.bodyText,
                                                unfocusedTextColor = sk.bodyText,
                                                cursorColor = sk.brand,
                                            ),
                                        )
                                        // Inline rewrite preview
                                        if (teamRewritten.isNotBlank()) {
                                            SelectionContainer {
                                                Text(
                                                    teamRewritten,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = sk.bodyText,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(sk.surface1, RoundedCornerShape(Radii.chip))
                                                        .padding(10.dp),
                                                )
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            FilledTonalButton(
                                                onClick = {
                                                    teamRewriting = true
                                                    teamScope.launch {
                                                        try {
                                                            val resp = com.example.skillsync.data.api.RetrofitClient.instance.composeMessage(
                                                                manager = managerEmail,
                                                                cadence = if (weekendSelected) "weekend" else "weekly",
                                                                target = "",
                                                                myMessage = teamMyMessage,
                                                            )
                                                            teamRewritten = resp.message
                                                            notify.success("Message composed")
                                                        } catch (_: Exception) {
                                                            teamRewritten = (if (weekendSelected) repData.teamDigestWeekend else repData.teamDigest).ifBlank {
                                                                MessageRewriter.compose(
                                                                    userMessage = "", myMessage = teamMyMessage,
                                                                    style = style, isTeam = true,
                                                                )
                                                            }
                                                            notify.success("Composed locally (offline)")
                                                        } finally { teamRewriting = false }
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                enabled = !teamRewriting,
                                                shape = RoundedCornerShape(Radii.chip),
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = sk.brand.copy(alpha = 0.85f),
                                                    contentColor = Color.White,
                                                ),
                                            ) {
                                                if (teamRewriting) CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                                                else Text(if (teamRewritten.isBlank()) "Rewrite for Teams" else "Rewrite Again", style = MaterialTheme.typography.labelMedium)
                                            }
                                            FilledTonalButton(
                                                onClick = {
                                                    val source = teamRewritten.ifBlank {
                                                        if (teamUserMessage.isBlank() && teamMyMessage.isBlank())
                                                            (if (weekendSelected) repData.teamDigestWeekend else repData.teamDigest)
                                                        else MessageRewriter.compose(teamUserMessage, teamMyMessage, style, isTeam = true)
                                                    }
                                                    copyToClipboard(context, "Team Digest", source)
                                                    notify.success("Copied broadcast message")
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(Radii.chip),
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = sk.brand.copy(alpha = 0.25f),
                                                    contentColor = sk.ice,
                                                ),
                                            ) {
                                                Text("Copy Broadcast", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(
                                                onClick = {
                                                    exportWeeklyCsv(context, repData)
                                                    notify.success("Exported Weekly CSV")
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(Radii.chip),
                                            ) {
                                                Text("Export CSV", style = MaterialTheme.typography.labelMedium, color = sk.sky)
                                            }
                                            if (teamUserMessage.isNotBlank() || teamMyMessage.isNotBlank()) {
                                                TextButton(onClick = { teamUserMessage = ""; teamMyMessage = ""; teamRewritten = "" }, modifier = Modifier.weight(1f)) {
                                                    Text("Clear", color = sk.subText)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 3. Filter Lens Bar
                            item {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val filterList = listOf(
                                        "All" to allReportees.size,
                                        "Delivering" to allReportees.count { it.batchCount > 0 },
                                        "On Bench" to allReportees.count { it.capacityBucket.equals("On Bench", true) },
                                        "At Risk" to allReportees.count { it.feedbackRisk.equals("High", true) },
                                        "Cert Gaps" to allReportees.count { it.certGaps > 0 },
                                    )
                                    items(filterList) { (label, count) ->
                                        val active = selectedFilter == label
                                        Surface(
                                            onClick = { selectedFilter = label },
                                            shape = RoundedCornerShape(Radii.chip),
                                            color = if (active) sk.brand.copy(alpha = 0.85f) else sk.surface1,
                                        ) {
                                            Text(
                                                "$label ($count)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (active) Color.White else sk.subText,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            )
                                        }
                                    }
                                }
                            }

                            // 4. Reportees List
                            item {
                                SectionHeading(
                                    "Reportees (${filteredReportees.size})",
                                    "Real KPI stats, active delivery schedules, and standpoint guidance.",
                                )
                            }

                            items(filteredReportees, key = { it.email.ifBlank { it.name } }) { rep ->
                                WeeklyReporteeLiveCard(
                                    rep = rep,
                                    style = style,
                                    weekendSelected = weekendSelected,
                                    onCadenceChange = { weekendSelected = it },
                                    managerEmail = managerEmail,
                                    onTrainerClick = onTrainerClick,
                                    context = context,
                                    notify = notify,
                                    sk = sk,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekNavBar(
    displayWeek: String,
    canNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onReset: () -> Unit,
    sk: SkillColors,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Layout.gutter, vertical = Space.xs),
        shape = RoundedCornerShape(Radii.card),
        color = sk.surface1.copy(alpha = 0.70f),
        border = androidx.compose.foundation.BorderStroke(1.dp, sk.cardBorder),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.sm, vertical = Space.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrev, modifier = Modifier.size(36.dp)) {
                Icon(painterResource(R.drawable.ic_back), "Previous week", tint = sk.ice, modifier = Modifier.size(18.dp))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    displayWeek,
                    color = sk.bodyText,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall,
                )
                Surface(
                    onClick = onReset,
                    shape = RoundedCornerShape(Radii.chip),
                    color = sk.brand.copy(alpha = 0.22f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, sk.brand.copy(alpha = 0.40f)),
                ) {
                    Text(
                        "Today",
                        color = sk.cyan,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            IconButton(onClick = onNext, enabled = canNext, modifier = Modifier.size(36.dp)) {
                Icon(
                    painterResource(R.drawable.ic_forward),
                    "Next week",
                    tint = if (canNext) sk.ice else sk.subText.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun WeeklyTeamOverviewCard(ts: WeeklyTeamSummary, sk: SkillColors) {
    SkillCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Weekly Operations Pulse", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = sk.bodyText)
                ToneChip("${ts.headcount} Trainers", tint = sk.sky)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WeeklyMetric("Delivering", ts.deliveringCount.toString(), sk, Modifier.weight(1f), sk.good)
                WeeklyMetric("On Bench", ts.benchCount.toString(), sk, Modifier.weight(1f), if (ts.benchCount > 0) sk.cyan else sk.good)
                WeeklyMetric("Total Pax", ts.totalParticipants.toString(), sk, Modifier.weight(1f), sk.sky)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WeeklyMetric("Batches", ts.totalBatches.toString(), sk, Modifier.weight(1f), sk.frost)
                WeeklyMetric(
                    "At Risk",
                    ts.atRiskCount.toString(),
                    sk,
                    Modifier.weight(1f),
                    if (ts.atRiskCount > 0) sk.crit else sk.good,
                )
                WeeklyMetric(
                    "Cert Gaps",
                    ts.totalCertGaps.toString(),
                    sk,
                    Modifier.weight(1f),
                    if (ts.totalCertGaps > 0) sk.warn else sk.good,
                )
            }
        }
    }
}

@Composable
private fun WeeklyMetric(
    label: String,
    value: String,
    sk: SkillColors,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Radii.kpi))
            .background(sk.surface1.copy(alpha = 0.65f))
            .border(1.dp, sk.glassBorder.copy(alpha = 0.50f), RoundedCornerShape(Radii.kpi))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            value,
            fontWeight = FontWeight.Bold,
            color = valueColor ?: sk.bodyText,
            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
        )
        Text(
            label.uppercase(),
            color = sk.labelText,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun WeeklyReporteeLiveCard(
    rep: WeeklyReporteeData,
    style: MessageStyle,
    weekendSelected: Boolean,
    onCadenceChange: (Boolean) -> Unit,
    managerEmail: String,
    onTrainerClick: (email: String, name: String) -> Unit,
    context: Context,
    notify: NotifyState,
    sk: SkillColors,
) {
    var expanded by rememberSaveable(rep.email) { mutableStateOf(false) }
    var userMessage by rememberSaveable(rep.email) { mutableStateOf("") }
    var myMessage by rememberSaveable(rep.email) { mutableStateOf("") }
    var rewritten by rememberSaveable(rep.email) { mutableStateOf("") }
    var rewriting by remember { mutableStateOf(false) }
    val cardScope = rememberCoroutineScope()

    val signal = remember(rep) {
        ReporteeSignals(
            name = rep.name,
            utilisation = rep.currentUtilization,
            capacityBucket = rep.capacityBucket,
            certGaps = rep.certGaps,
            certGapCourses = rep.certGapCourses,
            feedbackRisk = rep.feedbackRisk,
            readiness = rep.avgQubits,
            currentCourse = rep.currentBatch?.course.orEmpty(),
            nextCourse = "",
            openActions = if (rep.feedbackRisk == "High") 1 else 0,
            learnerRating = rep.learnerRating,
            learnerRatingCount = rep.learnerRatingCount,
            learnerRecentDate = rep.learnerFeedback?.let { (it["recent_date"] as? String) } ?: "",
            hrNegativeCount = rep.hrNegativeCount,
            negativeFeedbackCount = rep.negativeFeedbackCount,
        )
    }

    // The selected cadence variant is the headline. When the backend has not
    // supplied one, fall back to the evidence-only note that is always genuine.
    val variantMessage = remember(rep, weekendSelected, signal, style) {
        val v = if (weekendSelected) rep.messageWeekend else rep.messageWeekly
        v.ifBlank { rep.standpointNote.ifBlank { composeManagerStandpointNote(signal, style) } }
    }
    // The rewritten text takes precedence when present; otherwise the variant.
    val activeText = rewritten.ifBlank { variantMessage }

    val severity = when {
        rep.feedbackRisk.equals("High", true) -> Severity.Critical
        rep.certGaps > 0 -> Severity.Warning
        rep.capacityBucket.equals("Stretched", true) -> Severity.Warning
        rep.capacityBucket.equals("On Bench", true) -> Severity.Watch
        else -> Severity.Good
    }

    SkillCard(Modifier.fillMaxWidth(), severity = severity) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header Row
            Row(
                Modifier.fillMaxWidth().clickable { onTrainerClick(rep.email, rep.name) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(rep.name.ifBlank { rep.email }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = sk.bodyText)
                        ToneChip(
                            rep.capacityBucket,
                            when (rep.capacityBucket) {
                                "Delivering" -> sk.good
                                "Stretched" -> sk.warn
                                "On Bench" -> sk.cyan
                                else -> sk.sky
                            }
                        )
                    }
                    if (rep.currentBatch != null) {
                        Text(
                            "Delivering: ${rep.currentBatch.course} (${rep.currentBatch.participants} pax · ${rep.currentBatch.mode})",
                            color = sk.sky,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Text(
                            "${rep.currentUtilization ?: 0}% util · Qubits ${rep.avgQubits}%",
                            color = sk.subText,
                            fontSize = 12.sp,
                        )
                    }
                }

                Text(
                    if (expanded) "Hide" else "Preview",
                    style = MaterialTheme.typography.labelMedium,
                    color = sk.sky,
                    modifier = Modifier.pressable { expanded = !expanded }.padding(4.dp),
                )
            }

            // Quick status pills row
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (rep.avgQubits > 0) {
                    MiniWeeklyChip("Qubits ${rep.avgQubits}%", sk.brand)
                }
                if (rep.feedbackRisk == "High") {
                    MiniWeeklyChip("Feedback Flag", sk.crit)
                }
                if (rep.certGaps > 0) {
                    MiniWeeklyChip("${rep.certGaps} cert gap", sk.warn)
                }
                if (rep.currentUtilization != null) {
                    MiniWeeklyChip("${rep.currentUtilization}% load", sk.sky)
                }
            }

            // ── Headline message block (always visible) ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(sk.surface1, RoundedCornerShape(Radii.chip))
                    .padding(Space.md),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Message for ${rep.name.substringBefore(" ").ifBlank { "reportee" }}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = sk.bodyText,
                    )
                    CadenceSegmentToggle(
                        weekendSelected = weekendSelected,
                        onChange = onCadenceChange,
                        primaryLabel = "This week",
                        endLabel = "Weekend",
                        sk = sk,
                    )
                }
                SelectionContainer {
                    Text(
                        activeText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = sk.bodyText,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            copyToClipboard(context, rep.name, activeText)
                            notify.success("Copied ${rep.name.substringBefore(" ")}'s message")
                        },
                        shape = RoundedCornerShape(Radii.chip),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = sk.brand.copy(alpha = 0.85f),
                            contentColor = Color.White,
                        ),
                    ) {
                        Text(
                            if (style == MessageStyle.TEAMS) "Copy for Teams" else "Copy for Viber",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val outboxItem = com.example.skillsync.data.cache.ViberOutboxItem(
                                id = "viber_weekly_${rep.email}_${System.currentTimeMillis()}",
                                category = com.example.skillsync.data.cache.ViberOutboxItem.CAT_WEEKLY,
                                recipientName = rep.name,
                                recipientEmail = rep.email,
                                courseName = rep.currentBatch?.course ?: "Weekly Delivery Standpoint",
                                messageText = activeText,
                            )
                            com.example.skillsync.data.cache.ViberOutboxStore.enqueue(managerEmail, listOf(outboxItem))
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                com.example.skillsync.util.ViberDispatcher.dispatchBatch(context, managerEmail, listOf(outboxItem))
                            }
                            notify.success("Auto-sending ${rep.name.substringBefore(" ")}'s message to Viber...")
                        },
                        shape = RoundedCornerShape(Radii.chip),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66818CF8)),
                    ) {
                        Text("🚀 Auto-Send", style = MaterialTheme.typography.labelMedium, color = Color(0xFF818CF8))
                    }
                }
            }

            // Expanded view
            if (expanded) {
                // ── Rewrite studio: [User Message] + [My Message] → house-style Teams message ──
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = userMessage,
                        onValueChange = { userMessage = it; rewritten = "" },
                        label = { Text("User Message [User Message: …]") },
                        placeholder = { Text("Paste their Hinglish/informal message") },
                        shape = RoundedCornerShape(Radii.chip),
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = sk.brand,
                            unfocusedBorderColor = sk.glassBorder,
                            focusedTextColor = sk.bodyText,
                            unfocusedTextColor = sk.bodyText,
                            cursorColor = sk.brand,
                        ),
                    )
                    OutlinedTextField(
                        value = myMessage,
                        onValueChange = { myMessage = it; rewritten = "" },
                        label = { Text("My Message [My Message: …]") },
                        placeholder = { Text("Your intent — at least one required") },
                        shape = RoundedCornerShape(Radii.chip),
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = sk.brand,
                            unfocusedBorderColor = sk.glassBorder,
                            focusedTextColor = sk.bodyText,
                            unfocusedTextColor = sk.bodyText,
                            cursorColor = sk.brand,
                        ),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        FilledTonalButton(
                            onClick = {
                                rewriting = true
                                cardScope.launch {
                                    try {
                                        val resp = com.example.skillsync.data.api.RetrofitClient.instance.composeMessage(
                                            manager = managerEmail,
                                            cadence = if (weekendSelected) "weekend" else "weekly",
                                            target = rep.email,
                                            myMessage = myMessage,
                                        )
                                        rewritten = resp.message
                                        notify.success("Message composed")
                                    } catch (_: Exception) {
                                        rewritten = rep.standpointNote.ifBlank {
                                            MessageRewriter.compose(
                                                userMessage = "", myMessage = myMessage, style = style,
                                                targetName = rep.name, isTeam = false,
                                                evidence = MessageRewriter.EvidenceContext(
                                                    certGapCourses = rep.certGapCourses,
                                                    learnerRating = rep.learnerRating,
                                                    learnerRatingCount = rep.learnerRatingCount,
                                                    utilisation = rep.currentUtilization,
                                                ),
                                            )
                                        }
                                        notify.success("Composed locally (offline)")
                                    } finally { rewriting = false }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !rewriting,
                            shape = RoundedCornerShape(Radii.chip),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = sk.brand.copy(alpha = 0.85f), contentColor = Color.White),
                        ) {
                            if (rewriting) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                            else Text(if (rewritten.isBlank()) "Rewrite for Teams" else "Rewrite Again", style = MaterialTheme.typography.labelMedium)
                        }
                        if (userMessage.isNotBlank() || myMessage.isNotBlank()) {
                            TextButton(onClick = { userMessage = ""; myMessage = ""; rewritten = "" }, modifier = Modifier.weight(1f)) {
                                Text("Clear", color = sk.subText)
                            }
                        }
                    }
                    if (rewritten.isNotBlank()) {
                        Text("Preview is genuine and on top of evidence — copy or send below.", style = MaterialTheme.typography.labelSmall, color = sk.subText)
                    }
                }
            }

            // Action Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                FilledTonalButton(
                    onClick = {
                        copyToClipboard(context, rep.name, activeText)
                        notify.success("Copied ${rep.name.substringBefore(" ")}'s message")
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(Radii.chip),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = sk.brand.copy(alpha = 0.20f),
                        contentColor = sk.ice,
                    ),
                ) { Text(if (rewritten.isBlank()) "Copy Note" else "Copy Rewritten", style = MaterialTheme.typography.labelMedium) }

                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, activeText)
                        }
                        context.startActivity(Intent.createChooser(intent, "Send Weekly Update"))
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(Radii.chip),
                ) { Text("Send", style = MaterialTheme.typography.labelMedium, color = sk.sky) }
            }
        }
    }
}

/** Small two-segment toggle used near every message block. */
@Composable
internal fun CadenceSegmentToggle(
    weekendSelected: Boolean,
    onChange: (Boolean) -> Unit,
    primaryLabel: String,
    endLabel: String,
    sk: SkillColors,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(false to primaryLabel, true to endLabel).forEach { (isEnd, label) ->
            val selected = weekendSelected == isEnd
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) sk.frost else sk.subText,
                modifier = Modifier
                    .background(
                        if (selected) sk.brand.copy(alpha = 0.85f) else sk.glass,
                        RoundedCornerShape(Radii.chip),
                    )
                    .pressable { onChange(isEnd) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/** Prominent, titled team-message headline card with cadence toggle and copy button. */
@Composable
private fun TeamMessageCard(
    title: String,
    message: String,
    weekendSelected: Boolean,
    onCadenceChange: (Boolean) -> Unit,
    primaryLabel: String,
    endLabel: String,
    copyLabel: String,
    onCopy: (String) -> Unit,
    sk: SkillColors,
) {
    SkillCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = sk.bodyText)
                CadenceSegmentToggle(weekendSelected, onCadenceChange, primaryLabel, endLabel, sk)
            }
            SelectionContainer {
                Text(
                    message.ifBlank { "No message from RMS for this period yet." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = sk.bodyText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(sk.surface1, RoundedCornerShape(Radii.chip))
                        .padding(Space.md),
                )
            }
            FilledTonalButton(
                onClick = { onCopy(message) },
                shape = RoundedCornerShape(Radii.chip),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = sk.brand.copy(alpha = 0.85f),
                    contentColor = Color.White,
                ),
            ) {
                Text(copyLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun MiniWeeklyChip(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun FallbackWeeklyContent(
    data: Map<String, Any>,
    capability: Map<String, Any>?,
    actions: List<Map<String, Any>>,
    style: MessageStyle,
    onStyleChange: (MessageStyle) -> Unit,
    note: String,
    onNoteChange: (String) -> Unit,
    onTrainerClick: (email: String, name: String) -> Unit,
    context: Context,
    notify: NotifyState,
    sk: SkillColors,
) {
    val ops = data.rows("trainer_operations_df")
    val states = data.rows("trainer_current_state_df").associateBy { it.str("trainer_email").lowercase() }
    val capMap = (capability?.rows("trainers") ?: emptyList()).associateBy { it.str("trainer_email").lowercase() }
    val kpis = data.obj("manager_kpis")
    val demand = data.rows("unallocated_demand_df")

    val teamSignals = remember(ops, states, kpis, demand, capMap) {
        TeamSignals(
            strength = ops.size,
            deployed = states.values.count { it.str("current_status") != "free" },
            free = states.values.count { it.str("current_status") == "free" },
            utilisation = kpis?.intOrNull("avg_team_utilization"),
            atRisk = ops.count { it.str("feedback_risk").equals("High", true) },
            certGaps = capMap.values.sumOf { it.obj("certification")?.int("gap_count") ?: 0 },
            unallocated = demand.size,
            international = demand.count { it.str("delivery_mode").uppercase() in setOf("FMAT", "ILT") },
        )
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            WeeklyTeamOverviewCard(
                WeeklyTeamSummary(
                    headcount = teamSignals.strength,
                    deliveringCount = teamSignals.deployed,
                    benchCount = teamSignals.free,
                    atRiskCount = teamSignals.atRisk,
                    totalCertGaps = teamSignals.certGaps,
                    unallocatedDemand = teamSignals.unallocated,
                ),
                sk,
            )
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun shareWeeklyDigest(context: Context, data: WeeklyReportData) {
    val sb = StringBuilder()
    sb.appendLine("Weekly Delivery Report — ${data.weekLabel}")
    sb.appendLine("Delivering: ${data.teamSummary.deliveringCount} | Bench: ${data.teamSummary.benchCount} | Pax: ${data.teamSummary.totalParticipants}")
    sb.appendLine()
    sb.appendLine(data.teamDigest)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, sb.toString().trim())
        putExtra(Intent.EXTRA_SUBJECT, "Weekly Delivery Report ${data.weekLabel}")
    }
    context.startActivity(Intent.createChooser(intent, "Share Weekly Report"))
}

private fun exportWeeklyCsv(context: Context, data: WeeklyReportData) {
    val sb = StringBuilder()
    sb.appendLine("Trainer Name,Email,Capacity Bucket,Status Headline,Current Batch,Pax,Util%,Qubits%,Feedback Risk,Cert Gaps")
    data.reportees.forEach { r ->
        val batch = r.currentBatch?.course?.replace(",", ";") ?: "None"
        sb.appendLine("\"${r.name}\",\"${r.email}\",\"${r.capacityBucket}\",\"${r.statusHeadline.replace("\"", "'")}\",\"$batch\",${r.totalPax},${r.currentUtilization ?: 0},${r.avgQubits},\"${r.feedbackRisk}\",${r.certGaps}")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, sb.toString())
        putExtra(Intent.EXTRA_SUBJECT, "Weekly_Delivery_Report_${data.weekStart}.csv")
    }
    context.startActivity(Intent.createChooser(intent, "Export Weekly CSV"))
}
