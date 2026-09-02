package com.example.skillsync.ui.reportee

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.skillsync.ReporteeTab
import com.example.skillsync.theme.AuroraBackground
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.editorialRule
import com.example.skillsync.theme.glassSurface
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.auth.roleLabel
import com.example.skillsync.ui.components.Appear
import com.example.skillsync.ui.components.Figure
import com.example.skillsync.ui.components.FigureSize
import com.example.skillsync.ui.components.SectionHeader
import com.example.skillsync.ui.components.pressable
import com.example.skillsync.ui.components.rows
import com.example.skillsync.ui.components.str

/**
 * The trainer (reportee) app — Today / Demand / Updates. Same editorial voice as
 * the manager app, one tier simpler. Nothing from the manager consoles.
 */
@Composable
fun ReporteeHome(
    email: String,
    tab: String,
    onTabChange: (String) -> Unit,
    onLogout: () -> Unit,
    viewModel: ReporteeViewModel = viewModel(),
) {
    LaunchedEffect(email) { viewModel.load() }
    val context = LocalContext.current

    var skillTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var messageOpen by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground()
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                ReporteeDock(tab, onTabChange)
            },
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    ReporteeTab.DEMAND -> DemandTab(viewModel) { id, name -> skillTarget = id to name }
                    ReporteeTab.UPDATES -> UpdatesTab(viewModel, onLogout)
                    else -> TodayTab(
                        viewModel = viewModel,
                        onMarkSkill = { id, name -> skillTarget = id to name },
                        onMessageManager = { messageOpen = true },
                    )
                }
            }
        }
    }

    skillTarget?.let { (courseId, courseName) ->
        MarkSkillDialog(
            courseName = courseName,
            onDismiss = { skillTarget = null },
            onSubmit = { level ->
                viewModel.markSkill(courseId, level) { _, m -> toast(context, m) }
                skillTarget = null
            },
        )
    }
    if (messageOpen) {
        MessageManagerDialog(
            onDismiss = { messageOpen = false },
            onSend = { text ->
                viewModel.messageManager(text) { _, m -> toast(context, m) }
                messageOpen = false
            },
        )
    }
}

/* ── Dock ───────────────────────────────────────────────────────────────── */

@Composable
private fun ReporteeDock(current: String, onTabChange: (String) -> Unit) {
    val sk = MaterialTheme.skill
    val items = listOf(
        ReporteeTab.TODAY to "Today",
        ReporteeTab.DEMAND to "Demand",
        ReporteeTab.UPDATES to "Updates",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(0.dp))
            .editorialRule(top = true),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.lg)
                .height(58.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { (key, label) ->
                val selected = current == key
                Column(
                    Modifier
                        .weight(1f)
                        .pressable { onTabChange(key) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) sk.bodyText else sk.labelText,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .width(if (selected) 18.dp else 0.dp)
                            .height(2.dp)
                            .then(if (selected) Modifier.glassSurface(RoundedCornerShape(1.dp)) else Modifier),
                    )
                }
            }
        }
    }
}

/* ── Today ──────────────────────────────────────────────────────────────── */

@Composable
private fun TodayTab(
    viewModel: ReporteeViewModel,
    onMarkSkill: (courseId: String, courseName: String) -> Unit,
    onMessageManager: () -> Unit,
) {
    val home by viewModel.home.collectAsState()
    val loading by viewModel.homeLoading.collectAsState()
    val sk = MaterialTheme.skill

    if (loading && home == null) {
        Center { CircularProgressIndicator(color = sk.brand) }
        return
    }
    val h = home ?: emptyMap()
    val util = (h["current_utilization"] as? Number)?.toInt()
    val next = h["next_batch"] as? Map<*, *>
    val requests = h.rows("my_requests")
    val skills = h.rows("my_skills")

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = Space.xl),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = Space.xxl, bottom = Space.xxl),
    ) {
        item {
            Appear(0) {
                Column {
                    Text(
                        "Hello, ${h.str("name").substringBefore(" ").ifBlank { "there" }}.",
                        style = MaterialTheme.typography.displaySmall,
                        color = sk.bodyText,
                    )
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        roleLabel(h.str("role").ifBlank { "reportee" }).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = sk.labelText,
                    )
                }
            }
        }

        item {
            Appear(1) {
                Column(Modifier.padding(top = Space.xl)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.xxl)) {
                        Figure(
                            value = util?.let { "$it%" } ?: "—",
                            label = "My utilisation",
                            size = FigureSize.Hero,
                        )
                    }
                    Spacer(Modifier.height(Space.lg))
                    Text(
                        if (next != null)
                            "Next up — ${next.str("course")}, ${next.str("start_date")}."
                        else "Nothing on your calendar yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = sk.subText,
                    )
                }
            }
        }

        item {
            Appear(2) {
                Row(
                    Modifier.fillMaxWidth().padding(top = Space.xl),
                    horizontalArrangement = Arrangement.spacedBy(Space.md),
                ) {
                    Button(
                        onClick = onMessageManager,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = sk.brand),
                        shape = RoundedCornerShape(Radii.chip),
                    ) { Text("Message my manager") }
                }
            }
        }

        if (requests.isNotEmpty()) {
            item { SectionHeader("Skill requests", conclusion = "Waiting on your manager.") }
            items(requests) { r ->
                EditorialRow(
                    lead = "Level ${r.str("requested_level")}",
                    body = r.str("course_name").ifBlank { "Course ${r.str("course_id")}" },
                    trailing = r.str("status").uppercase(),
                    trailingTone = when (r.str("status")) {
                        "approved" -> sk.aqua; "denied" -> sk.warn; else -> sk.labelText
                    },
                )
            }
        }

        item {
            SectionHeader(
                "My skills",
                conclusion = if (skills.isEmpty()) "Nothing on record yet."
                else "${skills.size} on your register.",
            )
        }
        items(skills) { s ->
            EditorialRow(
                lead = null,
                body = s.str("course_name"),
                trailing = "Update level",
                trailingTone = sk.brand,
                onClick = { onMarkSkill(s.str("course_id"), s.str("course_name")) },
            )
        }
    }
}

/* ── Demand ─────────────────────────────────────────────────────────────── */

@Composable
private fun DemandTab(
    viewModel: ReporteeViewModel,
    onMarkSkill: (courseId: String, courseName: String) -> Unit,
) {
    val rows by viewModel.demand.collectAsState()
    val loading by viewModel.demandLoading.collectAsState()
    val error by viewModel.demandError.collectAsState()
    val sk = MaterialTheme.skill

    when {
        loading -> Center { CircularProgressIndicator(color = sk.brand) }
        error != null -> Center { Text(error!!, color = sk.warn) }
        rows.isEmpty() -> Center {
            Text(
                "No open batches match your current skills.",
                style = MaterialTheme.typography.bodyLarge,
                color = sk.subText,
            )
        }
        else -> LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = Space.xl),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = Space.xl, bottom = Space.xxl),
        ) {
            item {
                SectionHeader(
                    "Open work you can teach",
                    conclusion = "${rows.size} unallocated ${if (rows.size == 1) "batch matches" else "batches match"} your skills.",
                )
            }
            items(rows) { row ->
                EditorialRow(
                    lead = "${row.str("skill_match_pct")}%",
                    body = row.str("course_name"),
                    sub = listOf(row.str("start_date"), row.str("location"))
                        .filter { it.isNotBlank() }.joinToString("  ·  "),
                    trailing = "Mark my skill",
                    trailingTone = sk.brand,
                    onClick = { onMarkSkill(row.str("course_id"), row.str("course_name")) },
                )
            }
        }
    }
}

/* ── Updates ────────────────────────────────────────────────────────────── */

@Composable
private fun UpdatesTab(viewModel: ReporteeViewModel, onLogout: () -> Unit) {
    val updates by viewModel.updates.collectAsState()
    val sk = MaterialTheme.skill
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = Space.xl),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = Space.xl, bottom = Space.xxl),
    ) {
        item { SectionHeader("Updates", conclusion = if (updates.isEmpty()) "Nothing new." else null) }
        items(updates) { n ->
            EditorialRow(lead = null, body = n.str("title"), sub = n.str("message"))
        }
        item {
            Spacer(Modifier.height(Space.xl))
            TextButton(onClick = onLogout) { Text("Sign out", color = sk.warn) }
        }
    }
}

/* ── Row primitive local to the trainer app ─────────────────────────────── */

@Composable
private fun EditorialRow(
    lead: String?,
    body: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    trailing: String? = null,
    trailingTone: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val sk = MaterialTheme.skill
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressable(onClick = onClick) else Modifier)
            .padding(vertical = Space.lg)
            .editorialRule(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (lead != null) {
            Text(
                lead,
                style = com.example.skillsync.theme.NumericStyle.copy(
                    fontSize = MaterialTheme.typography.headlineMedium.fontSize,
                ),
                color = sk.bodyText,
                modifier = Modifier.width(64.dp),
            )
            Spacer(Modifier.width(Space.md))
        }
        Column(Modifier.weight(1f)) {
            Text(body, style = MaterialTheme.typography.titleMedium, color = sk.bodyText)
            if (sub != null && sub.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(sub, style = MaterialTheme.typography.bodySmall, color = sk.subText)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(Space.md))
            Text(trailing, style = MaterialTheme.typography.labelMedium, color = trailingTone ?: sk.labelText)
        }
    }
}

/* ── Dialogs ────────────────────────────────────────────────────────────── */

@Composable
private fun MarkSkillDialog(
    courseName: String,
    onDismiss: () -> Unit,
    onSubmit: (level: Int) -> Unit,
) {
    var level by remember { mutableStateOf(3f) }
    val sk = MaterialTheme.skill
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = sk.cardBg,
        title = { Text("Mark my skill", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(courseName, style = MaterialTheme.typography.bodyMedium, color = sk.bodyText)
                Figure(value = level.toInt().toString(), label = "Level", size = FigureSize.Medium)
                Slider(value = level, onValueChange = { level = it }, valueRange = 1f..10f, steps = 8)
                Text(
                    if (level.toInt() <= 4) "Saved to your record immediately."
                    else "Levels above 4 go to your manager for approval.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (level.toInt() <= 4) sk.subText else sk.warn,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(level.toInt()) }) { Text("Submit") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MessageManagerDialog(onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val sk = MaterialTheme.skill
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = sk.cardBg,
        title = { Text("Message my manager", style = MaterialTheme.typography.headlineSmall) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it.take(1000) },
                label = { Text("Your note") }, minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onSend(text.trim()) }) { Text("Send") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Center(content: @Composable () -> Unit) =
    Box(Modifier.fillMaxSize().padding(Space.xl), contentAlignment = Alignment.Center) { content() }

private fun toast(context: android.content.Context, message: String) =
    android.widget.Toast.makeText(context.applicationContext, message, android.widget.Toast.LENGTH_SHORT).show()
