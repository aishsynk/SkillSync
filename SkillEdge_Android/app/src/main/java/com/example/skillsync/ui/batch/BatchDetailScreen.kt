package com.example.skillsync.ui.batch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.skillsync.R
import com.example.skillsync.theme.AuroraBackground
import com.example.skillsync.theme.IconSlot
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.StatusBarIcons
import com.example.skillsync.theme.accentGlass
import com.example.skillsync.theme.glassSurface
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.components.*
import com.example.skillsync.ui.main.CourseCurriculumSheet

/**
 * Everything known about one unallocated batch, plus the four actions a manager
 * takes from here: read the outline, claim it themselves, claim it for a
 * reportee, or message the team about it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchDetailScreen(
    batch: Map<*, *>,
    managerEmail: String,
    reportees: List<Pair<String, String>>,   // name to email
    markState: MarkState,
    operationalContext: com.example.skillsync.data.api.DemandContextResponse? = null,
    operationalContextLoading: Boolean = false,
    operationalContextError: String? = null,
    gatedCandidates: com.example.skillsync.data.api.AllocationCandidatesResponse? = null,
    gatedCandidatesLoading: Boolean = false,
    gatedCandidatesUnverified: String? = null,
    onMarkSkill: (courseId: String, trainerEmail: String, level: Int, date: String, who: String) -> Unit,
    onClearMark: () -> Unit,
    onBack: () -> Unit,
) {
    val sk = MaterialTheme.skill
    val context = LocalContext.current
    StatusBarIcons(lightIcons = true)

    var showMine by remember { mutableStateOf(false) }
    var showReportee by remember { mutableStateOf(false) }
    var showCurriculumSheet by remember { mutableStateOf(false) }
    var showNetworkSheet by remember { mutableStateOf(false) }
    var shareTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showMessagePreview by remember { mutableStateOf(false) }

    val courseId = batch.str("course_id")
    val courseName = batch.str("course_name")
    val relevance = batch.int("relevance")
    val candidates = batch.list("candidates")

    val shareBatch = remember(batch) {
        BatchShare.Batch(
            courseName = courseName,
            startDate = batch.str("start_date").longDate(),
            endDate = batch.str("end_date").longDate(),
            sessionTime = batch.str("session_time"),
            days = batch.intOrNull("days"),
            deliveryMode = batch.str("delivery_mode"),
            language = batch.str("language"),
            participants = batch.intOrNull("participants")?.toString().orEmpty(),
            location = batch.str("location"),
            vendor = batch.str("customer"),
            reference = batch.str("demand_id"),
            tocUrl = batch.str("toc_url"),
        )
    }

    fun messageFor(target: Pair<String, String>?): String =
        BatchShare.composeMessage(shareBatch, recipient = target?.first ?: "Team")

    fun htmlFor(target: Pair<String, String>?): String =
        BatchShare.htmlMessage(shareBatch, recipient = target?.first ?: "Team")

    // Confirm or explain the RMS write, then reset so the dialog can reopen.
    // Material's default snackbar was the only surface in the app that did not
    // use the design tokens, and it gave a confirmed write, an unconfirmed one
    // and an outright failure the same neutral styling.
    val notify = com.example.skillsync.ui.components.LocalNotify.current
    LaunchedEffect(markState) {
        when (markState) {
            is MarkState.Done -> {
                notify.success("Skill saved to RMS", markState.message)
                onClearMark()
            }
            is MarkState.Unconfirmed -> {
                notify.warn("Saved, but not confirmed", markState.message)
                onClearMark()
            }
            is MarkState.Failed -> {
                notify.error("Not saved", markState.message)
                onClearMark()
            }
            else -> Unit
        }
    }

    val (coverageLabel, coverageTint, coverageIcon) = coverageStyle(batch.str("coverage_status"))
    val risk = batch.str("assignment_risk")
    val riskTint = when (risk) { "High" -> sk.crit; "Medium" -> sk.warn; else -> sk.aqua }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Demand detail", fontWeight = FontWeight.SemiBold, color = sk.frost)
                            Text(
                                "Ref ${batch.str("demand_id")}", color = sk.labelText,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_back), "Back", tint = sk.frost)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
        ) { pv ->
            Column(
                Modifier.fillMaxSize().padding(pv).verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Headline: course, coverage, and the three business-priority
                // figures a manager needs before reading anything else — no
                // separate cards, one glass block.
                Box(Modifier.fillMaxWidth().glassSurface()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                courseName.ifBlank { "Course not specified" },
                                style = MaterialTheme.typography.titleLarge, color = sk.frost,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(10.dp))
                            IconSlot(tint = coverageTint, size = 34.dp) {
                                Icon(painterResource(coverageIcon), null, tint = coverageTint, modifier = Modifier.size(17.dp))
                            }
                        }
                        Text(
                            coverageLabel, style = MaterialTheme.typography.labelSmall,
                            color = coverageTint, fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (batch.bool("is_priority")) { Chip("★ Priority", sk.teal); Spacer(Modifier.width(6.dp)) }
                            if (batch.str("tentative").equals("Yes", true)) { Chip("Tentative", sk.amber); Spacer(Modifier.width(6.dp)) }
                            if (batch.str("third_party").equals("Yes", true)) { Chip("Third party", sk.indigo) }
                        }
                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = sk.cardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            DetailStat("Opportunity", batch.str("revenue_potential").ifBlank { "—" }, sk.indigo)
                            DetailStat("Priority", "${batch.intOrNull("priority_score") ?: 0}", sk.teal)
                            DetailStat("Risk", risk.ifBlank { "—" }, riskTint)
                            DetailStat("Coverage", "$relevance%", relevanceColor(relevance))
                        }
                    }
                }

                // The full eligibility check — leave, client exclusions,
                // confirmed bookings, skill floor and visa. The demand board
                // cannot afford these per-trainer calls, so this is where the
                // non-overridable gates are actually applied.
                Spacer(Modifier.height(14.dp))
                GatedCandidatesSection(
                    response = gatedCandidates,
                    loading = gatedCandidatesLoading,
                    unverified = gatedCandidatesUnverified,
                )

                Box(Modifier.fillMaxWidth().glassSurface()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Operational verification", fontWeight = FontWeight.SemiBold, color = sk.frost)
                            val label = when {
                                operationalContextLoading -> "Checking"
                                operationalContext?.confidence == "verified" -> "Verified"
                                else -> "Partial"
                            }
                            Text(label, style = MaterialTheme.typography.labelSmall, color = if (label == "Verified") sk.aqua else sk.amber)
                        }
                        when {
                            operationalContextLoading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                            operationalContext != null -> {
                                val course = operationalContext.course
                                val confirmations = operationalContext.salesConfirmations
                                Text(
                                    if (course.verified) "RMS course status: ${course.status.ifBlank { "Verified" }}" else "Course status could not be verified",
                                    style = MaterialTheme.typography.bodySmall, color = sk.bodyText,
                                )
                                Text(
                                    if (confirmations.verified) "${confirmations.count} sales confirmation${if (confirmations.count == 1) "" else "s"} linked" else "Sales confirmation link could not be verified",
                                    style = MaterialTheme.typography.bodySmall, color = sk.subText,
                                )
                            }
                            operationalContextError != null -> Text(operationalContextError, style = MaterialTheme.typography.bodySmall, color = sk.warn)
                        }
                    }
                }

                // Start -> End on a single row, then everything else the
                // manager needs before deciding, all in one dense block.
                Box(Modifier.fillMaxWidth().glassSurface()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("START", style = MaterialTheme.typography.labelSmall, color = sk.ice, fontWeight = FontWeight.Bold)
                                Text(batch.str("start_date").takeIf { it.isNotBlank() }?.shortDate() ?: "—", style = MaterialTheme.typography.titleSmall, color = sk.frost, fontWeight = FontWeight.SemiBold)
                            }
                            Icon(painterResource(R.drawable.ic_chevron), null, tint = sk.subText, modifier = Modifier.size(16.dp))
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text("END", style = MaterialTheme.typography.labelSmall, color = sk.ice, fontWeight = FontWeight.Bold)
                                Text(batch.str("end_date").takeIf { it.isNotBlank() }?.shortDate() ?: "—", style = MaterialTheme.typography.titleSmall, color = sk.frost, fontWeight = FontWeight.SemiBold)
                            }
                            batch.intOrNull("days")?.let {
                                Spacer(Modifier.width(14.dp))
                                Chip("${it}d", sk.sky)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = sk.cardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                        Spacer(Modifier.height(10.dp))
                        // Mode through Remarks, two per row. Short facts pair
                        // up; anything long (remarks, a wordy location) takes a
                        // full row so it is not truncated to fit a column.
                        FactGrid(
                            listOf(
                                "Mode" to batch.str("delivery_mode"),
                                "Vendor" to batch.str("customer"),
                                "Participants" to (batch.intOrNull("participants")?.toString() ?: ""),
                                "Daily time" to batch.str("session_time"),
                                "Language" to batch.str("language"),
                                "Location" to batch.str("location"),
                                "Courseware" to batch.str("courseware"),
                                "Allocation for" to batch.str("allocation_for"),
                                "Course id" to courseId,
                                "Student card" to batch.str("scid"),
                                "Remarks" to batch.str("remarks"),
                            )
                        )
                        // The raw `schedule` blob is deliberately not rendered.
                        // RMS repeats the same window once per delivery day
                        // ("24 Aug / 09:00-17:00 / 25 Aug / 09:00-17:00 / ..."),
                        // so printing it restated the dates already shown above
                        // and the daily time already shown in the grid. The
                        // window is extracted once, server-side, as
                        // `session_time`.
                    }
                }

                // Official Courseware & Versioning Access
                val contentPdf = operationalContext?.course?.contentUrl.orEmpty()
                val activeVer = operationalContext?.course?.latestVersion.orEmpty()
                if (contentPdf.isNotBlank() || activeVer.isNotBlank()) {
                    Box(Modifier.fillMaxWidth().glassSurface()) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                if (activeVer.isNotBlank()) {
                                    Text("🏷️ Active RMS Version: $activeVer", style = MaterialTheme.typography.titleSmall, color = sk.aqua, fontWeight = FontWeight.Bold)
                                }
                                if (contentPdf.isNotBlank()) {
                                    Text("Official Koenig slide deck available", style = MaterialTheme.typography.bodySmall, color = sk.subText)
                                }
                            }
                            if (contentPdf.isNotBlank()) {
                                Button(
                                    onClick = {
                                        try {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(contentPdf))
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = sk.cyan),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                ) {
                                    Text("Open Slides PDF ↗", style = MaterialTheme.typography.labelSmall, color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Recommended allocation
                Box(Modifier.fillMaxWidth().glassSurface()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Recommended allocation", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold, color = sk.frost,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Ranked by skill fit, readiness, availability and language — includes you",
                            style = MaterialTheme.typography.labelSmall, color = sk.labelText,
                        )
                        Spacer(Modifier.height(10.dp))
                        if (candidates.isEmpty()) {
                            Text(
                                "No one on your team maps to this course.",
                                style = MaterialTheme.typography.bodySmall, color = sk.subText,
                            )
                        } else {
                            candidates.forEachIndexed { i, c ->
                                if (i > 0) {
                                    Spacer(Modifier.height(2.dp))
                                    HorizontalDivider(color = sk.cardBorder.copy(alpha = 0.4f), thickness = 0.5.dp)
                                    Spacer(Modifier.height(2.dp))
                                }
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val isDnc = c.bool("dnc_flag")
                                    val isClientReq = c.bool("client_requested")
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            c.str("trainer_name"),
                                            style = MaterialTheme.typography.titleSmall, color = sk.frost,
                                        )
                                        Text(
                                            "via ${c.str("via_course")}",
                                            style = MaterialTheme.typography.labelSmall, color = sk.subText,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                        val coverage = c.str("coverage")

                                        Spacer(Modifier.height(2.dp))
                                        if (isDnc) {
                                            Text(
                                                "🚫 Client DNC Blocked",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = sk.crit,
                                            )
                                        } else if (isClientReq) {
                                            Text(
                                                "⭐ Client Requested Trainer",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = sk.amber,
                                            )
                                        } else if (coverage.isNotBlank()) {
                                            Text(
                                                listOfNotNull(
                                                    coverage,
                                                    c.str("backup_role").takeIf { it.isNotBlank() },
                                                    c.intOrNull("utilization")?.let { "${it}% utilised" },
                                                    if (!c.bool("speaks_english")) "non-English" else null,
                                                ).joinToString(" · "),
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = relevanceColor(c.int("match")),
                                            )
                                        }
                                    }
                                    Chip("${c.int("match")}%", if (isDnc) sk.crit else relevanceColor(c.int("match")))
                                    // Addresses the message to this trainer by name rather
                                    // than sending an unaddressed team broadcast.
                                    TextButton(onClick = {
                                        shareTarget = c.str("trainer_name") to c.str("trainer_email")
                                        showMessagePreview = true
                                    }) { Text("Message", color = sk.sky) }
                                }
                            }
                        }
                    }
                }

                // Actions — compact row with primary tools
                ActionBar(
                    actions = listOf(
                        ActionItem("Curriculum", R.drawable.ic_book, sk.blue) { showCurriculumSheet = true },
                        ActionItem("My skill", R.drawable.ic_check, sk.teal) { showMine = true },
                        ActionItem("Reportee", R.drawable.ic_people, sk.indigo) { showReportee = true },
                        ActionItem("Message", R.drawable.ic_mail, sk.green) {
                            shareTarget = null
                            showMessagePreview = true
                        },
                    ),
                )

                if (courseName.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { showNetworkSheet = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Search Wider Trainer Network 🌐", style = MaterialTheme.typography.labelMedium, color = sk.cyan, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showCurriculumSheet && courseName.isNotBlank()) {
        CourseCurriculumSheet(
            courseName = courseName,
            courseId = courseId,
            onDismiss = { showCurriculumSheet = false },
        )
    }

    if (showNetworkSheet && courseName.isNotBlank()) {
        NetworkStaffingSheet(
            courseName = courseName,
            onDismiss = { showNetworkSheet = false },
        )
    }

    if (showMessagePreview) {
        MessagePreviewDialog(
            message = messageFor(shareTarget),
            recipient = shareTarget?.first,
            onDismiss = { showMessagePreview = false },
            onCopy = { text ->
                // Only pass the HTML variant when the text is untouched; once it
                // has been edited the two would disagree and the rich paste would
                // silently drop the manager's changes.
                val html = if (text == messageFor(shareTarget)) htmlFor(shareTarget) else null
                BatchShare.copyMessage(context, text, html)
                showMessagePreview = false
            },
            onShare = { text ->
                BatchShare.shareAnywhere(context, text)
                showMessagePreview = false
            },
        )
    }

    if (showMine) {
        MarkSkillDialog(
            title = "Mark my skill",
            subtitle = courseName,
            people = null,
            working = markState is MarkState.Working,
            onDismiss = { showMine = false },
            onConfirm = { _, level, date ->
                onMarkSkill(courseId, managerEmail, level, date, "you")
                showMine = false
            },
        )
    }

    if (showReportee) {
        MarkSkillDialog(
            title = "Mark reportee's skill",
            subtitle = courseName,
            people = reportees,
            working = markState is MarkState.Working,
            onDismiss = { showReportee = false },
            onConfirm = { who, level, date ->
                val email = who?.second.orEmpty()
                if (email.isNotBlank()) {
                    onMarkSkill(courseId, email, level, date, who?.first ?: email)
                }
                showReportee = false
            },
        )
    }
}

// ── Pieces ────────────────────────────────────────────────────────────────────

/**
 * Shows the exact text before it leaves the app, and lets the manager edit it.
 *
 * Copy is the primary action, not a Viber deep link. `viber://forward?text=`
 * carries the body inside a URI and Viber truncates it at roughly a hundred
 * characters, so complete messages arrived cut off mid sentence with their
 * meaning lost. The clipboard has no such limit.
 */
@Composable
private fun MessagePreviewDialog(
    message: String,
    recipient: String?,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
) {
    val sk = MaterialTheme.skill
    var text by remember(message) { mutableStateOf(message) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { onCopy(text) }, shape = RoundedCornerShape(10.dp)) {
                Icon(painterResource(R.drawable.ic_check), null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(7.dp))
                Text("Copy", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = { onShare(text) }) { Text("Share") }
            }
        },
        title = {
            Column {
                Text(
                    if (recipient != null) "Message $recipient" else "Message the team",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    "${text.length} of 1000 characters · paste into Viber or Teams",
                    style = MaterialTheme.typography.labelSmall, color = sk.subText,
                )
            }
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = MaterialTheme.typography.bodySmall,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 360.dp),
            )
        },
    )
}

@Composable
private fun DetailStat(label: String, value: String, tint: Color) {
    Column {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = tint)
        Text(
            label.uppercase(), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.skill.labelText,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Facts laid out two per row, so the detail page reads at a glance instead of
 * as one long column the manager has to scroll.
 *
 * Blank values are dropped before pairing, so an absent field closes the gap
 * rather than leaving a hole in the grid. A value long enough to be truncated
 * in half-width takes a full row instead.
 */
@Composable
private fun FactGrid(facts: List<Pair<String, String>>) {
    val present = facts.filter { it.second.isNotBlank() && it.second != "—" }
    if (present.isEmpty()) return

    // Greedy pairing: walk the list and pair two short facts, or emit one long
    // fact on its own row.
    val rows = remember(present) {
        val out = mutableListOf<List<Pair<String, String>>>()
        var i = 0
        while (i < present.size) {
            val a = present[i]
            val aLong = a.second.length > 28
            val b = present.getOrNull(i + 1)
            if (!aLong && b != null && b.second.length <= 28) {
                out.add(listOf(a, b)); i += 2
            } else {
                out.add(listOf(a)); i += 1
            }
        }
        out
    }

    Column(Modifier.fillMaxWidth()) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                row.forEach { (label, value) ->
                    Column(Modifier.weight(1f).padding(end = 10.dp)) {
                        Text(
                            label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.skill.ice,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            value,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.skill.frost,
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Skips itself when the value is blank, so the block never shows empty rows. */
@Composable
private fun Fact(label: String, value: String) {
    if (value.isBlank() || value == "—") return
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.skill.labelText, modifier = Modifier.width(104.dp),
        )
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.skill.frost)
    }
}

@Composable
private fun Chip(text: String, tint: Color) {
    Surface(color = tint.copy(alpha = 0.16f), shape = RoundedCornerShape(10.dp)) {
        Text(
            text, style = MaterialTheme.typography.labelSmall, color = tint,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

internal data class ActionItem(
    val label: String,
    val icon: Int,
    val tint: Color,
    val onClick: () -> Unit,
)

/**
 * Four actions on one row, each a tinted glyph over a short label.
 *
 * Replaces four stacked full-width buttons that ran to roughly 220dp — a third
 * of a phone screen spent on four words, which pushed the batch facts the
 * manager came to read below the fold. Each cell still fills the row height, so
 * the touch targets stay comfortably above the 48dp minimum.
 */
@Composable
private fun ActionBar(actions: List<ActionItem>) {
    val sk = MaterialTheme.skill
    Box(Modifier.fillMaxWidth().glassSurface(RoundedCornerShape(14.dp))) {
        Row(Modifier.fillMaxWidth().height(74.dp), verticalAlignment = Alignment.CenterVertically) {
            actions.forEach { a ->
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = a.onClick),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(11.dp))
                            .background(a.tint.copy(alpha = 0.13f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(a.icon), null,
                            tint = a.tint, modifier = Modifier.size(17.dp),
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        a.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = sk.labelText, maxLines = 1,
                    )
                }
            }
        }
    }
}
