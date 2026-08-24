package com.example.skillsync.ui.report

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
            vm.init(managerEmail)
        }
    }

    val state by vm.state.collectAsState()
    val displayWeek by vm.displayWeek.collectAsState()
    val canNext by remember { derivedStateOf { vm.canGoNext() } }

    var style by rememberSaveable { mutableStateOf(MessageStyle.TEAMS) }
    var note by rememberSaveable { mutableStateOf("") }
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
                                note = note,
                                onNoteChange = { note = it },
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
                                            value = note,
                                            onValueChange = { note = it },
                                            label = { Text("Manager Note (Optional)") },
                                            placeholder = { Text("Add custom priorities for your team") },
                                            shape = RoundedCornerShape(Radii.chip),
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = sk.brand,
                                                unfocusedBorderColor = sk.glassBorder,
                                                focusedTextColor = sk.bodyText,
                                                unfocusedTextColor = sk.bodyText,
                                                cursorColor = sk.brand,
                                            ),
                                        )

                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            FilledTonalButton(
                                                onClick = {
                                                    val fullBroadcast = if (note.isNotBlank()) "${formatManagerNote(note)}\n\n${repData.teamDigest}" else repData.teamDigest
                                                    copyToClipboard(context, "Team Digest", fullBroadcast)
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
                                    managerNote = note,
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(sk.heroBg.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(painterResource(R.drawable.ic_back), "Previous week", tint = Color.White)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                displayWeek,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Surface(
                onClick = onReset,
                shape = RoundedCornerShape(12.dp),
                color = sk.brand.copy(alpha = 0.35f),
            ) {
                Text(
                    "Today",
                    color = sk.cyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        IconButton(onClick = onNext, enabled = canNext) {
            Icon(
                painterResource(R.drawable.ic_forward),
                "Next week",
                tint = if (canNext) Color.White else Color.White.copy(alpha = 0.3f),
            )
        }
    }
}

@Composable
private fun WeeklyTeamOverviewCard(ts: WeeklyTeamSummary, sk: SkillColors) {
    SkillCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Weekly Operations Pulse", fontWeight = FontWeight.Bold, color = sk.bodyText, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WeeklyMetric("Delivering", ts.deliveringCount.toString(), sk, Modifier.weight(1f), sk.good)
                WeeklyMetric("On Bench", ts.benchCount.toString(), sk, Modifier.weight(1f), if (ts.benchCount > 0) sk.cyan else sk.good)
                WeeklyMetric("Total Pax", ts.totalParticipants.toString(), sk, Modifier.weight(1f), sk.sky)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WeeklyMetric("Batches", ts.totalBatches.toString(), sk, Modifier.weight(1f))
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
            .clip(RoundedCornerShape(8.dp))
            .background(sk.surface1.copy(alpha = 0.5f))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontWeight = FontWeight.Bold, color = valueColor ?: sk.bodyText, fontSize = 17.sp)
        Text(label, color = sk.subText, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun WeeklyReporteeLiveCard(
    rep: WeeklyReporteeData,
    style: MessageStyle,
    managerNote: String,
    onTrainerClick: (email: String, name: String) -> Unit,
    context: Context,
    notify: NotifyState,
    sk: SkillColors,
) {
    var expanded by rememberSaveable(rep.email) { mutableStateOf(false) }
    var showStandpoint by rememberSaveable(rep.email) { mutableStateOf(true) }

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
        )
    }

    val weeklyMsg = remember(signal, style, managerNote) {
        composeReporteeMessage(signal, style, managerNote = managerNote)
    }

    val activeText = if (showStandpoint) rep.standpointNote.ifBlank { composeManagerStandpointNote(signal, style) } else weeklyMsg

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

            // Expanded view
            if (expanded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        onClick = { showStandpoint = true },
                        shape = RoundedCornerShape(Radii.chip),
                        color = if (showStandpoint) sk.cyan.copy(alpha = 0.85f) else sk.surface1,
                    ) {
                        Text(
                            "Manager Standpoint",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (showStandpoint) Color.Black else sk.subText,
                            modifier = Modifier.padding(horizontal = Space.md, vertical = 6.dp),
                        )
                    }
                    Surface(
                        onClick = { showStandpoint = false },
                        shape = RoundedCornerShape(Radii.chip),
                        color = if (!showStandpoint) sk.brand.copy(alpha = 0.85f) else sk.surface1,
                    ) {
                        Text(
                            "Weekly Message",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (!showStandpoint) sk.frost else sk.subText,
                            modifier = Modifier.padding(horizontal = Space.md, vertical = 6.dp),
                        )
                    }
                }

                SelectionContainer {
                    Text(
                        activeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = sk.bodyText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(sk.surface1, RoundedCornerShape(Radii.chip))
                            .padding(Space.md),
                    )
                }
            }

            // Action Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                FilledTonalButton(
                    onClick = {
                        copyToClipboard(context, rep.name, activeText)
                        notify.success("Copied ${rep.name.substringBefore(" ")}'s standpoint")
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(Radii.chip),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = sk.brand.copy(alpha = 0.20f),
                        contentColor = sk.ice,
                    ),
                ) { Text("Copy Note", style = MaterialTheme.typography.labelMedium) }

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
