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
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.SkillCard
import com.example.skillsync.theme.SkillColors
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.ToneChip
import com.example.skillsync.theme.pressable
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.components.LocalNotify

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HrMonthlyReportScreen(
    managerEmail: String,
    onTrainerClick: (email: String, name: String) -> Unit = { _, _ -> },
    onBack: () -> Unit,
    vm: HrMonthlyReportViewModel = viewModel(),
) {
    val sk = MaterialTheme.skill
    val context = LocalContext.current
    val notify = LocalNotify.current

    LaunchedEffect(managerEmail) { vm.init(managerEmail, context) }

    val state by vm.state.collectAsState()
    val displayMonth by vm.displayMonth.collectAsState()
    val canNext by remember { derivedStateOf { vm.canGoNext() } }

    var selectedFilter by rememberSaveable { mutableStateOf("All") }
    var inspectingReportee by remember { mutableStateOf<ReporteeSnapshot?>(null) }

    Scaffold(
        containerColor = sk.pageBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("HR Monthly Report", fontWeight = FontWeight.Bold, color = Color.White)
                        Text(displayMonth, color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_back), "Back", tint = Color.White)
                    }
                },
                actions = {
                    if (state is HrReportState.Success) {
                        val reportData = (state as HrReportState.Success).data
                        IconButton(onClick = {
                            exportHrCsv(context, reportData)
                            notify.success("Exported HR Monthly CSV")
                        }) {
                            Icon(painterResource(R.drawable.ic_copy), "Export CSV", tint = Color.White)
                        }
                        IconButton(onClick = { shareReport(context, reportData) }) {
                            Icon(painterResource(R.drawable.ic_share), "Share", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = sk.heroBg),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Month navigation bar
            MonthNavBar(
                displayMonth = displayMonth,
                canNext = canNext,
                onPrev = { vm.previousMonth() },
                onNext = { vm.nextMonth() },
                sk = sk,
            )

            when (val s = state) {
                is HrReportState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = sk.brand)
                    }
                }

                is HrReportState.Error -> {
                    val isSessionError = s.message.contains("401") ||
                        s.message.contains("session", ignoreCase = true) ||
                        s.message.contains("unauthorized", ignoreCase = true) ||
                        s.message.contains("expired", ignoreCase = true)
                    Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            if (isSessionError) "Session expired" else s.message,
                            color = sk.warn,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (isSessionError) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Please sign in again to continue.",
                                color = sk.subText,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.height(20.dp))
                            Button(
                                onClick = { com.example.skillsync.data.SessionManager.clearSession() },
                                colors = ButtonDefaults.buttonColors(containerColor = sk.brand),
                            ) {
                                Text("Sign in again")
                            }
                        }
                    }
                }

                is HrReportState.Success -> {
                    val data = s.data
                    val allReportees = data.reportees

                    val filteredReportees = remember(allReportees, selectedFilter) {
                        when (selectedFilter) {
                            "Diamond" -> allReportees.filter { it.trainerIndex.tierLevel == 1 }
                            "Platinum" -> allReportees.filter { it.trainerIndex.tierLevel == 2 }
                            "High Performer" -> allReportees.filter { it.trajectory == "High Performer" }
                            "Needs Coaching" -> allReportees.filter { it.trajectory == "Needs Coaching" }
                            "Bench" -> allReportees.filter { it.trajectory == "Bench Upskilling" || it.utilisationPct < 50 }
                            else -> allReportees
                        }
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item { TeamSummaryCard(data.teamSummary, sk) }

                        // Trajectory & Tier Filter Chips
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val filterItems = listOf(
                                    "All" to allReportees.size,
                                    "Diamond" to allReportees.count { it.trainerIndex.tierLevel == 1 },
                                    "Platinum" to allReportees.count { it.trainerIndex.tierLevel == 2 },
                                    "High Performer" to allReportees.count { it.trajectory == "High Performer" },
                                    "Needs Coaching" to allReportees.count { it.trajectory == "Needs Coaching" },
                                )
                                items(filterItems) { (label, count) ->
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

                        item {
                            Text(
                                "Reportees (${filteredReportees.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = sk.bodyText,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                            )
                        }

                        items(filteredReportees, key = { it.email.ifBlank { it.name } }) { rep ->
                            ReporteeSnapshotCard(
                                rep = rep,
                                sk = sk,
                                onTrainerClick = { onTrainerClick(rep.email, rep.name) },
                                onInspectCriteria = { inspectingReportee = rep },
                                onCopy = {
                                    val text = buildReporteeText(rep, data.month)
                                    copyToClipboard(context, text)
                                    notify.success("Copied ${rep.name.substringBefore(" ")}'s summary")
                                },
                            )
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }

    // 20-Criteria Inspector Dialog
    inspectingReportee?.let { rep ->
        TrainerIndexCriteriaDialog(
            rep = rep,
            onDismiss = { inspectingReportee = null },
            sk = sk,
        )
    }
}

@Composable
private fun MonthNavBar(
    displayMonth: String,
    canNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    sk: SkillColors,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(sk.heroBg.copy(alpha = 0.6f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(painterResource(R.drawable.ic_back), "Previous month", tint = Color.White)
        }
        Text(
            displayMonth,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )
        IconButton(onClick = onNext, enabled = canNext) {
            Icon(
                painterResource(R.drawable.ic_forward),
                "Next month",
                tint = if (canNext) Color.White else Color.White.copy(alpha = 0.3f),
            )
        }
    }
}

@Composable
private fun TeamSummaryCard(ts: TeamSummaryData, sk: SkillColors) {
    SkillCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Team Overview", fontWeight = FontWeight.Bold, color = sk.bodyText, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryMetric("Headcount", ts.headcount.toString(), sk, Modifier.weight(1f))
                SummaryMetric("Avg Util", "${ts.avgUtilisation.toInt()}%", sk, Modifier.weight(1f))
                SummaryMetric("Avg HR Score", "${ts.avgHrScore.toInt()}/100", sk, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryMetric("Batches", ts.totalBatches.toString(), sk, Modifier.weight(1f))
                SummaryMetric(
                    "Neg Feedback",
                    ts.totalNegativeFeedback.toString(),
                    sk,
                    Modifier.weight(1f),
                    if (ts.totalNegativeFeedback > 0) sk.warn else sk.good,
                )
                SummaryMetric(
                    "Cert Gaps",
                    ts.certGapCount.toString(),
                    sk,
                    Modifier.weight(1f),
                    if (ts.certGapCount > 0) sk.warn else sk.good,
                )
            }
            if (ts.totalPositiveHr > 0 || ts.totalNegativeHr > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryMetric("HR Positive", ts.totalPositiveHr.toString(), sk, Modifier.weight(1f), sk.good)
                    SummaryMetric("HR Incidents", ts.totalNegativeHr.toString(), sk, Modifier.weight(1f),
                        if (ts.totalNegativeHr > 0) sk.warn else sk.good)
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SummaryMetric(
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
private fun ReporteeSnapshotCard(
    rep: ReporteeSnapshot,
    sk: SkillColors,
    onTrainerClick: () -> Unit,
    onInspectCriteria: () -> Unit,
    onCopy: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val notify = LocalNotify.current

    SkillCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header row
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Score badge
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(hrScoreColor(rep.hrScore, sk).copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        rep.hrScore.toString(),
                        fontWeight = FontWeight.Bold,
                        color = hrScoreColor(rep.hrScore, sk),
                        fontSize = 14.sp,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.clickable { onTrainerClick() },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(rep.name.ifBlank { rep.email }, fontWeight = FontWeight.Bold, color = sk.bodyText, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        ToneChip(
                            rep.trajectory,
                            when (rep.trajectory) {
                                "High Performer" -> sk.good
                                "Needs Coaching" -> sk.crit
                                "Bench Upskilling" -> sk.cyan
                                "In Transition" -> sk.warn
                                else -> sk.sky
                            }
                        )
                        if (rep.trainerIndex.totalScore > 0) {
                            ToneChip(
                                "TI ${rep.trainerIndex.totalScore.toInt()} ${rep.trainerIndex.tierBadge.substringBefore(" ")}",
                                when (rep.trainerIndex.tierLevel) {
                                    1 -> sk.good
                                    2 -> sk.sky
                                    3 -> sk.cyan
                                    4 -> sk.warn
                                    else -> sk.crit
                                }
                            )
                        }
                    }
                    Text("${rep.utilisationPct.toInt()}% util · ${rep.batchCount} batches · Qubits ${rep.avgQubits.toInt()}%", color = sk.subText, fontSize = 12.sp)
                }
                // Flag chip
                rep.flag?.let {
                    ToneChip(it, sk.warn)
                    Spacer(Modifier.width(6.dp))
                }
                IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                    Icon(painterResource(R.drawable.ic_copy), "Copy summary", tint = sk.subText, modifier = Modifier.size(18.dp))
                }
            }

            // Inline KPI row (always visible)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (rep.avgQubits > 0) {
                    MiniChip("Qubits ${rep.avgQubits.toInt()}", sk.brand)
                }
                if (rep.trainerIndex.totalScore > 0) {
                    MiniChip("TI ${rep.trainerIndex.totalScore.toInt()}", sk.amber)
                }
                if (rep.hrPositiveCount > 0) {
                    MiniChip("+${rep.hrPositiveCount} HR", sk.good)
                }
                if (rep.hrNegativeCount > 0) {
                    MiniChip("${rep.hrNegativeCount} incident${if (rep.hrNegativeCount > 1) "s" else ""}", sk.warn)
                }
                if (rep.negativeFeedbackCount > 0) {
                    MiniChip("${rep.negativeFeedbackCount} neg feedback", sk.warn)
                }
                if (rep.certsMissing > 0) {
                    MiniChip("${rep.certsMissing} cert gap${if (rep.certsMissing > 1) "s" else ""}", sk.warn)
                }
            }

            // Expanded structured feedback & details
            AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider(color = sk.cardBorder)

                    // 1. STRENGTH BLOCK
                    if (rep.structuredFeedback.strength.isNotBlank()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = sk.good.copy(alpha = 0.08f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, sk.good.copy(alpha = 0.35f)),
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("🟢 STRENGTH", style = MaterialTheme.typography.labelSmall, color = sk.good, fontWeight = FontWeight.Bold)
                                Text(rep.structuredFeedback.strength, style = MaterialTheme.typography.bodySmall, color = sk.bodyText, lineHeight = 18.sp)
                            }
                        }
                    }

                    // 2. AREA OF IMPROVEMENT BLOCK
                    if (rep.structuredFeedback.areaOfImprovement.isNotBlank()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = sk.warn.copy(alpha = 0.08f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, sk.warn.copy(alpha = 0.35f)),
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("🟠 AREA OF IMPROVEMENT", style = MaterialTheme.typography.labelSmall, color = sk.warn, fontWeight = FontWeight.Bold)
                                Text(rep.structuredFeedback.areaOfImprovement, style = MaterialTheme.typography.bodySmall, color = sk.bodyText, lineHeight = 18.sp)
                            }
                        }
                    }

                    // 3. OTHER FEEDBACK / MANAGER'S VERDICT BLOCK
                    if (rep.structuredFeedback.otherFeedback.isNotBlank()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = sk.cyan.copy(alpha = 0.08f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, sk.cyan.copy(alpha = 0.35f)),
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("🔵 OTHER FEEDBACK / MANAGER'S VERDICT", style = MaterialTheme.typography.labelSmall, color = sk.cyan, fontWeight = FontWeight.Bold)
                                Text(rep.structuredFeedback.otherFeedback, style = MaterialTheme.typography.bodySmall, color = sk.bodyText, lineHeight = 18.sp)
                            }
                        }
                    }

                    // 4. TRAINER INDEX SCORECARD (20 CRITERIA)
                    if (rep.trainerIndex.totalScore > 0) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { onInspectCriteria() },
                            shape = RoundedCornerShape(8.dp),
                            color = sk.surface1.copy(alpha = 0.6f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, sk.cardBorder),
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column {
                                        Text("🏆 HR TRAINER INDEX (TI – 13/08/26)", style = MaterialTheme.typography.labelSmall, color = sk.amber, fontWeight = FontWeight.Bold)
                                        Text("${rep.trainerIndex.tier} (${rep.trainerIndex.totalScore.toInt()} pts)", style = MaterialTheme.typography.bodyMedium, color = sk.bodyText, fontWeight = FontWeight.Bold)
                                    }
                                    ToneChip("Inspect 20 Criteria ↗", sk.amber)
                                }
                                Text(
                                    "Util: ${rep.trainerIndex.utilizationPts.toInt()} · Quality/AI: ${(rep.trainerIndex.qualityPts + rep.trainerIndex.beastAiPts).toInt()} · Certs: ${(rep.trainerIndex.certificationsPts + rep.trainerIndex.instructorPts).toInt()} · Knowledge: ${rep.trainerIndex.knowledgeSharingPts.toInt()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = sk.subText,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }

                    if (rep.topCourses.isNotEmpty()) {
                        Text("Top courses: ${rep.topCourses.joinToString(" · ")}", color = sk.subText, fontSize = 12.sp)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DetailCell("Certs held", rep.certsHeld.toString(), sk, Modifier.weight(1f))
                        DetailCell("Cert gaps", rep.certsMissing.toString(), sk, Modifier.weight(1f),
                            if (rep.certsMissing > 0) sk.warn else null)
                        DetailCell("Avg Qubits", if (rep.avgQubits > 0) "${rep.avgQubits.toInt()}%" else "—", sk, Modifier.weight(1f))
                    }

                    // Quick Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                val text = rep.structuredFeedback.formattedText.ifBlank {
                                    buildReporteeText(rep, "")
                                }
                                copyToClipboard(context, text)
                                notify.success("Copied 3-part feedback for ${rep.name.substringBefore(" ")}")
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
                                    putExtra(Intent.EXTRA_TEXT, rep.structuredFeedback.formattedText)
                                    putExtra(Intent.EXTRA_SUBJECT, "Manager Evaluation — ${rep.name}")
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
    }
}

@Composable
private fun TrainerIndexCriteriaDialog(
    rep: ReporteeSnapshot,
    onDismiss: () -> Unit,
    sk: SkillColors,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Koenig HR Trainer Index", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = sk.bodyText)
                Text("${rep.name} · ${rep.trainerIndex.tier} (${rep.trainerIndex.totalScore.toInt()} pts)", fontSize = 12.sp, color = sk.amber)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(rep.trainerIndex.criteria) { c ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp),
                        color = sk.surface1.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, sk.cardBorder),
                    ) {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("${c.sNo}. ${c.criteria}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = sk.bodyText, modifier = Modifier.weight(1f))
                                Text(
                                    "${if (c.points >= 0) "+" else ""}${c.points.toInt()} pts",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (c.points > 0) sk.good else if (c.points < 0) sk.crit else sk.subText,
                                )
                            }
                            Text("Raw Value: ${c.rawValue} · ${c.remarks}", fontSize = 10.sp, color = sk.subText)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = sk.brand)) {
                Text("Close")
            }
        },
        containerColor = sk.cardBg,
    )
}

@Composable
private fun MiniChip(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DetailCell(label: String, value: String, sk: SkillColors, modifier: Modifier, valueColor: Color? = null) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(sk.surface1.copy(alpha = 0.4f))
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontWeight = FontWeight.SemiBold, color = valueColor ?: sk.bodyText, fontSize = 15.sp)
        Text(label, color = sk.subText, fontSize = 10.sp, textAlign = TextAlign.Center)
    }
}

private fun hrScoreColor(score: Int, sk: SkillColors): Color = when {
    score >= 80 -> sk.good
    score >= 60 -> sk.warn
    else -> sk.crit
}

private fun buildReporteeText(rep: ReporteeSnapshot, month: String): String {
    val sb = StringBuilder()
    val monthPart = if (month.isNotBlank()) " — $month" else ""
    sb.appendLine("HR Evaluation — ${rep.name}$monthPart")
    val tiPart = if (rep.trainerIndex.totalScore > 0) " | TI: ${rep.trainerIndex.totalScore.toInt()} pts (${rep.trainerIndex.tierBadge})" else ""
    sb.appendLine("HR Score: ${rep.hrScore}/100 | Trajectory: ${rep.trajectory}$tiPart")
    sb.appendLine("Utilisation: ${rep.utilisationPct.toInt()}% | Batches: ${rep.batchCount} | Avg Qubits: ${rep.avgQubits.toInt()}%")
    if (rep.hrPositiveCount > 0) sb.appendLine("HR Recognition: ${rep.hrPositiveCount}")
    if (rep.hrNegativeCount > 0) sb.appendLine("HR Incidents: ${rep.hrNegativeCount}")
    if (rep.negativeFeedbackCount > 0) sb.appendLine("Negative Feedback: ${rep.negativeFeedbackCount}")
    if (rep.certsMissing > 0) sb.appendLine("Certification Gaps: ${rep.certsMissing}")
    if (rep.certsHeld > 0) sb.appendLine("Certifications Held: ${rep.certsHeld}")
    if (rep.topCourses.isNotEmpty()) sb.appendLine("Top Courses: ${rep.topCourses.joinToString(", ")}")
    sb.appendLine()
    if (rep.structuredFeedback.formattedText.isNotBlank()) {
        sb.appendLine(rep.structuredFeedback.formattedText)
    }
    return sb.toString().trim()
}

private fun shareReport(context: Context, data: HrReportData) {
    val sb = StringBuilder()
    sb.appendLine("HR Monthly Report — ${data.month}")
    sb.appendLine("Team: ${data.teamSummary.headcount} reportees, Avg HR Score ${data.teamSummary.avgHrScore.toInt()}/100")
    sb.appendLine()
    data.reportees.forEach { rep ->
        sb.appendLine("${rep.name}: ${rep.hrScore}/100 | ${rep.utilisationPct.toInt()}% util | ${rep.trajectory}")
        if (rep.structuredFeedback.strength.isNotBlank()) {
            sb.appendLine("Strength: ${rep.structuredFeedback.strength.take(120)}...")
        }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, sb.toString().trim())
        putExtra(Intent.EXTRA_SUBJECT, "HR Monthly Report ${data.month}")
    }
    context.startActivity(Intent.createChooser(intent, "Share HR Report"))
}

private fun exportHrCsv(context: Context, data: HrReportData) {
    val sb = StringBuilder()
    sb.appendLine("Trainer Name,Email,HR Score,Trajectory,Trainer Index Score,TI Tier,Util%,Batches,Avg Qubits%,HR Pos,HR Neg,Neg Feedback,Cert Gaps")
    data.reportees.forEach { r ->
        sb.appendLine("\"${r.name}\",\"${r.email}\",${r.hrScore},\"${r.trajectory}\",${r.trainerIndex.totalScore},\"${r.trainerIndex.tier}\",${r.utilisationPct.toInt()},${r.batchCount},${r.avgQubits.toInt()},${r.hrPositiveCount},${r.hrNegativeCount},${r.negativeFeedbackCount},${r.certsMissing}")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, sb.toString())
        putExtra(Intent.EXTRA_SUBJECT, "HR_Monthly_Report_${data.month.replace(" ", "_")}.csv")
    }
    context.startActivity(Intent.createChooser(intent, "Export HR Report CSV"))
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("HR Report", text))
}
