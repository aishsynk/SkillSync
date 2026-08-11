package com.example.skillsync.ui.report

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.skillsync.R
import com.example.skillsync.theme.AuroraBackground
import com.example.skillsync.theme.Figure
import com.example.skillsync.theme.FigureSize
import com.example.skillsync.theme.Layout
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.SectionHeading
import com.example.skillsync.theme.Severity
import com.example.skillsync.theme.SkillCard
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.ToneChip
import com.example.skillsync.theme.glassSurface
import com.example.skillsync.theme.pressable
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.components.*

/**
 * The weekly report.
 *
 * Deliberately not a document. A manager does not want to read a page of prose
 * on a phone on a Monday morning; they want the two or three facts that changed
 * and a message they can paste into Teams or Viber without editing. So each
 * card is a short summary and a ready message, and the primary action is Copy.
 *
 * One message for the team, then one per reportee, each composed from that
 * person's own week by [composeReporteeMessage] — which is why the benched
 * trainer, the stretched one and the one with a feedback flag do not receive
 * the same note.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyReportScreen(
    data: Map<String, Any>,
    capability: Map<String, Any>?,
    actions: List<Map<String, Any>> = emptyList(),
    onBack: () -> Unit,
) {
    val sk = MaterialTheme.skill
    val context = LocalContext.current
    val notify = LocalNotify.current

    var style by rememberSaveable { mutableStateOf(MessageStyle.TEAMS) }

    val ops = data.rows("trainer_operations_df")
    val states = data.rows("trainer_current_state_df").associateBy { it.str("trainer_email").lowercase() }
    val capMap = (capability?.rows("trainers") ?: emptyList())
        .associateBy { it.str("trainer_email").lowercase() }
    val kpis = data.obj("manager_kpis")
    val demand = data.rows("unallocated_demand_df")
    val actionsByTrainer = actions
        .filter { it.str("lifecycle_state").ifBlank { "open" } !in setOf("closed", "resolved") }
        .groupBy { it.str("trainer_email").lowercase() }

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

    val reportees = remember(ops, states, capMap, actionsByTrainer) {
        ops.map { t ->
            val key = t.str("official_email").lowercase()
            val state = states[key]
            val cap = capMap[key]
            ReporteeSignals(
                name = t.str("trainer_name").ifBlank { t.str("official_email") },
                utilisation = t.intOrNull("current_utilization"),
                capacityBucket = t.str("capacity_bucket"),
                certGaps = cap?.obj("certification")?.int("gap_count") ?: 0,
                feedbackRisk = t.str("feedback_risk"),
                readiness = cap?.intOrNull("readiness_score"),
                currentCourse = state?.obj("current_batch")?.str("course_name").orEmpty(),
                nextCourse = state?.obj("next_batch")?.str("course_name").orEmpty(),
                openActions = actionsByTrainer[key]?.size ?: 0,
            )
        }.sortedBy { severityOf(it).weight }
    }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Weekly report", style = MaterialTheme.typography.titleLarge, color = sk.bodyText)
                            Text(
                                weekReference(java.time.LocalDate.now()),
                                style = MaterialTheme.typography.labelSmall,
                                color = sk.labelText,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_back), "Back", tint = sk.ice)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
        ) { pv ->
            LazyColumn(
                Modifier.fillMaxSize().padding(pv),
                contentPadding = PaddingValues(
                    start = Layout.gutter, end = Layout.gutter,
                    top = Space.sm, bottom = Space.xxl,
                ),
                verticalArrangement = Arrangement.spacedBy(Layout.section),
            ) {
                item {
                    // Teams renders bold and italics on paste; Viber shows the
                    // markers as literal characters, so the manager picks.
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "PASTE FORMAT",
                            style = MaterialTheme.typography.labelSmall,
                            color = sk.labelText,
                            modifier = Modifier.weight(1f),
                        )
                        MessageStyle.entries.forEach { option ->
                            val selected = style == option
                            Text(
                                if (option == MessageStyle.TEAMS) "Teams" else "Plain",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) sk.frost else sk.subText,
                                modifier = Modifier
                                    .background(
                                        if (selected) sk.brand.copy(alpha = 0.85f) else sk.glass,
                                        RoundedCornerShape(Radii.chip),
                                    )
                                    .pressable { style = option }
                                    .padding(horizontal = Space.md, vertical = Space.sm),
                            )
                        }
                    }
                }

                item {
                    SectionHeading(
                        "Team",
                        "${teamSignals.strength} people, ${teamSignals.deployed} deployed, ${teamSignals.unallocated} unallocated",
                    )
                }
                item {
                    Appear(0) {
                        MessageCard(
                            title = "Message to the team",
                            severity = when {
                                teamSignals.atRisk > 0 -> Severity.Critical
                                teamSignals.unallocated > 0 -> Severity.Warning
                                else -> Severity.Good
                            },
                            summary = {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(Space.md),
                                ) {
                                    Figure(
                                        teamSignals.utilisation?.let { "$it%" } ?: "—",
                                        "Utilisation", FigureSize.Small, Modifier.weight(1f),
                                    )
                                    Figure(
                                        teamSignals.atRisk.toString(), "At risk",
                                        FigureSize.Small, Modifier.weight(1f),
                                        if (teamSignals.atRisk == 0) sk.good else sk.crit,
                                    )
                                    Figure(
                                        teamSignals.certGaps.toString(), "Cert gaps",
                                        FigureSize.Small, Modifier.weight(1f),
                                        if (teamSignals.certGaps == 0) sk.good else sk.warn,
                                    )
                                    Figure(
                                        teamSignals.unallocated.toString(), "Open demand",
                                        FigureSize.Small, Modifier.weight(1f),
                                        if (teamSignals.unallocated == 0) sk.good else sk.crit,
                                    )
                                }
                            },
                            message = composeTeamMessage(teamSignals, style),
                            context = context,
                            notify = notify,
                        )
                    }
                }

                item {
                    SectionHeading(
                        "Reportees",
                        "One message each, written from their own week. Most urgent first.",
                        trailing = "${reportees.size}",
                    )
                }

                items(reportees, key = { it.name }) { person ->
                    MessageCard(
                        title = person.name,
                        severity = severityOf(person),
                        summary = {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Space.md),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ToneChip(headlineFor(person), severityOf(person).tint())
                                Spacer(Modifier.weight(1f))
                                person.utilisation?.let {
                                    Text(
                                        "$it%",
                                        style = MaterialTheme.typography.titleMedium
                                            .copy(fontFeatureSettings = "tnum"),
                                        color = sk.bodyText,
                                    )
                                }
                            }
                        },
                        message = composeReporteeMessage(person, style),
                        context = context,
                        notify = notify,
                    )
                }
            }
        }
    }
}

/**
 * A summary you can read in two seconds and a message you can send without
 * editing. Collapsed to the summary by default so the page stays scannable.
 */
@Composable
private fun MessageCard(
    title: String,
    severity: Severity,
    summary: @Composable () -> Unit,
    message: String,
    context: Context,
    notify: NotifyState,
) {
    val sk = MaterialTheme.skill
    var expanded by rememberSaveable(title) { mutableStateOf(false) }

    SkillCard(Modifier.fillMaxWidth(), severity = severity) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = sk.bodyText,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (expanded) "Hide" else "Preview",
                style = MaterialTheme.typography.labelMedium,
                color = sk.sky,
                modifier = Modifier.pressable { expanded = !expanded },
            )
        }
        summary()

        if (expanded) {
            // Selectable, so the manager can also grab a single line by hand.
            SelectionContainer {
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = sk.bodyText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(sk.surface1, RoundedCornerShape(Radii.chip))
                        .padding(Space.md),
                )
            }
            Text(
                "${message.length} of $MESSAGE_LIMIT characters",
                style = MaterialTheme.typography.labelSmall,
                color = sk.labelText,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            FilledTonalButton(
                onClick = {
                    copyToClipboard(context, title, message)
                    notify.success("Copied", "Paste it straight into Teams or Viber.")
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(Radii.chip),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = sk.brand.copy(alpha = 0.20f),
                    contentColor = sk.ice,
                ),
            ) { Text("Copy", style = MaterialTheme.typography.labelLarge) }

            OutlinedButton(
                onClick = { shareMessage(context, message) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(Radii.chip),
            ) { Text("Send", style = MaterialTheme.typography.labelLarge, color = sk.sky) }
        }
    }
}

// ── Reading the person's week ───────────────────────────────────────────────

internal fun severityOf(p: ReporteeSignals): Severity = when {
    p.feedbackRisk.equals("High", true) -> Severity.Critical
    p.certGaps > 0 -> Severity.Warning
    p.capacityBucket.equals("Stretched", true) -> Severity.Warning
    p.capacityBucket.equals("On Bench", true) -> Severity.Watch
    p.openActions > 0 -> Severity.Info
    else -> Severity.Good
}

internal fun headlineFor(p: ReporteeSignals): String = when {
    p.feedbackRisk.equals("High", true) -> "Feedback flag"
    p.certGaps > 0 -> "${p.certGaps} cert gap"
    p.capacityBucket.equals("Stretched", true) -> "Stretched"
    p.capacityBucket.equals("On Bench", true) -> "Available"
    p.openActions > 0 -> "${p.openActions} open"
    else -> "On track"
}

// ── Sending ─────────────────────────────────────────────────────────────────

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

/**
 * Hands the text to the system share sheet rather than targeting Teams or
 * Viber directly: the manager picks whichever they actually use, and the app
 * does not have to guess at package names that may not be installed.
 */
private fun shareMessage(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Send weekly update"))
}
