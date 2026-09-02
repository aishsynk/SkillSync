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
import com.example.skillsync.theme.NumericStyle
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
import com.example.skillsync.ui.components.obj
import com.example.skillsync.ui.components.pressable
import com.example.skillsync.ui.components.rows
import com.example.skillsync.ui.components.str
import com.example.skillsync.ui.components.strings

/**
 * The trainer (reportee) app — a distinct four-page experience, not the manager
 * shell with things hidden. Today · Demand · Calendar · Practice. Every page is
 * scoped to this one person, and the only writes are: mark my own skill (capped
 * at level 4, above that it becomes a manager request) and message my manager.
 */
@Composable
fun ReporteeHome(
    email: String,
    tab: String,
    onTabChange: (String) -> Unit,
    onOpenPractice: () -> Unit,
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
            bottomBar = { ReporteeDock(tab, onTabChange) },
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    ReporteeTab.DEMAND -> DemandTab(viewModel) { id, name -> skillTarget = id to name }
                    ReporteeTab.CALENDAR -> CalendarTab(viewModel)
                    ReporteeTab.PRACTICE -> PracticeTab(onOpenPractice, onLogout)
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
        MarkSkillDialog(courseName, onDismiss = { skillTarget = null }) { level ->
            viewModel.markSkill(courseId, level) { _, m -> toast(context, m) }
            skillTarget = null
        }
    }
    if (messageOpen) {
        MessageManagerDialog(onDismiss = { messageOpen = false }) { text ->
            viewModel.messageManager(text) { _, m -> toast(context, m) }
            messageOpen = false
        }
    }
}

/* ── Dock ───────────────────────────────────────────────────────────────── */

@Composable
private fun ReporteeDock(current: String, onTabChange: (String) -> Unit) {
    val sk = MaterialTheme.skill
    val items = listOf(
        ReporteeTab.TODAY to "Today",
        ReporteeTab.DEMAND to "Demand",
        ReporteeTab.CALENDAR to "Calendar",
        ReporteeTab.PRACTICE to "Practice",
    )
    Column(
        Modifier.fillMaxWidth().glassSurface(RoundedCornerShape(0.dp)).editorialRule(top = true),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.md).height(58.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { (key, label) ->
                val selected = current == key
                Column(
                    Modifier.weight(1f).pressable { onTabChange(key) },
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
                            .width(if (selected) 16.dp else 0.dp)
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
    onMarkSkill: (String, String) -> Unit,
    onMessageManager: () -> Unit,
) {
    val home by viewModel.home.collectAsState()
    val loading by viewModel.homeLoading.collectAsState()
    val updates by viewModel.updates.collectAsState()
    val sk = MaterialTheme.skill

    if (loading && home == null) { Center { CircularProgressIndicator(color = sk.brand) }; return }
    val h = home ?: emptyMap()
    val util = (h["current_utilization"] as? Number)?.toInt()
    val next = h["next_batch"] as? Map<*, *>
    val requests = h.rows("my_requests")
    val skills = h.rows("my_skills")

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = Space.xl),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = Space.xxl, bottom = Space.xxl),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        item {
            Appear(0) {
                Column {
                    Text(
                        "Hello, ${h.str("name").substringBefore(" ").ifBlank { "there" }}.",
                        style = MaterialTheme.typography.displaySmall, color = sk.bodyText,
                    )
                    Text(roleLabel(h.str("role").ifBlank { "reportee" }).uppercase(),
                        style = MaterialTheme.typography.labelSmall, color = sk.labelText)
                }
            }
        }
        item {
            Appear(1) {
                Column(Modifier.padding(top = Space.xl)) {
                    Figure(util?.let { "$it%" } ?: "—", "My utilisation", size = FigureSize.Hero)
                    Spacer(Modifier.height(Space.lg))
                    Text(
                        if (next != null) "Next up — ${next.str("course")}, ${next.str("start_date")}."
                        else "Nothing on your calendar yet.",
                        style = MaterialTheme.typography.bodyLarge, color = sk.subText,
                    )
                }
            }
        }
        item {
            Appear(2) {
                Button(
                    onClick = onMessageManager,
                    modifier = Modifier.padding(top = Space.xl),
                    colors = ButtonDefaults.buttonColors(containerColor = sk.brand),
                    shape = RoundedCornerShape(Radii.chip),
                ) { Text("Message my manager") }
            }
        }

        if (updates.isNotEmpty()) {
            item { SectionHeader("Updates") }
            items(updates.take(6)) { n ->
                Row(Modifier.fillMaxWidth().padding(vertical = Space.md).editorialRule()) {
                    Column {
                        Text(n.str("title"), style = MaterialTheme.typography.titleMedium, color = sk.bodyText)
                        Text(n.str("message"), style = MaterialTheme.typography.bodySmall, color = sk.subText)
                    }
                }
            }
        }

        if (requests.isNotEmpty()) {
            item { SectionHeader("Skill requests", conclusion = "Waiting on your manager.") }
            items(requests) { r ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = Space.md).editorialRule(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Level ${r.str("requested_level")}", style = NumericStyle.copy(
                        fontSize = MaterialTheme.typography.headlineMedium.fontSize), color = sk.bodyText,
                        modifier = Modifier.width(76.dp))
                    Spacer(Modifier.width(Space.md))
                    Text(r.str("course_name").ifBlank { "Course ${r.str("course_id")}" },
                        style = MaterialTheme.typography.titleMedium, color = sk.bodyText,
                        modifier = Modifier.weight(1f))
                    Text(r.str("status").uppercase(), style = MaterialTheme.typography.labelSmall,
                        color = when (r.str("status")) {
                            "approved" -> sk.aqua; "denied" -> sk.warn; else -> sk.labelText
                        })
                }
            }
        }

        item {
            SectionHeader("My skills", conclusion =
                if (skills.isEmpty()) "Nothing on record yet." else "${skills.size} on your register.")
        }
        items(skills) { s ->
            Row(
                Modifier.fillMaxWidth().pressable { onMarkSkill(s.str("course_id"), s.str("course_name")) }
                    .padding(vertical = Space.lg).editorialRule(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(s.str("course_name"), style = MaterialTheme.typography.titleMedium,
                    color = sk.bodyText, modifier = Modifier.weight(1f))
                Text("UPDATE LEVEL", style = MaterialTheme.typography.labelMedium, color = sk.brand)
            }
        }
    }
}

/* ── Demand ─────────────────────────────────────────────────────────────── */

@Composable
private fun DemandTab(viewModel: ReporteeViewModel, onMarkSkill: (String, String) -> Unit) {
    val rows by viewModel.demand.collectAsState()
    val loading by viewModel.demandLoading.collectAsState()
    val error by viewModel.demandError.collectAsState()
    val sk = MaterialTheme.skill

    when {
        loading -> Center { CircularProgressIndicator(color = sk.brand) }
        error != null -> Center { Text(error!!, color = sk.warn) }
        rows.isEmpty() -> Center {
            Text("No open batches match your current skills.",
                style = MaterialTheme.typography.bodyLarge, color = sk.subText)
        }
        else -> LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = Space.xl),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = Space.xl, bottom = Space.xxl),
        ) {
            item {
                SectionHeader("Open work you can teach", conclusion =
                    "${rows.size} unallocated ${if (rows.size == 1) "batch matches" else "batches match"} your skills.")
            }
            items(rows) { row ->
                Row(
                    Modifier.fillMaxWidth().pressable { onMarkSkill(row.str("course_id"), row.str("course_name")) }
                        .padding(vertical = Space.lg).editorialRule(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${row.str("skill_match_pct")}%", style = NumericStyle.copy(
                        fontSize = MaterialTheme.typography.headlineMedium.fontSize),
                        color = sk.bodyText, modifier = Modifier.width(64.dp))
                    Spacer(Modifier.width(Space.md))
                    Column(Modifier.weight(1f)) {
                        Text(row.str("course_name"), style = MaterialTheme.typography.titleMedium, color = sk.bodyText)
                        Text(listOf(row.str("start_date"), row.str("location"))
                            .filter { it.isNotBlank() }.joinToString("  ·  "),
                            style = MaterialTheme.typography.bodySmall, color = sk.subText)
                    }
                    Text("MARK MY SKILL", style = MaterialTheme.typography.labelSmall, color = sk.brand)
                }
            }
        }
    }
}

/* ── Calendar ───────────────────────────────────────────────────────────── */

@Composable
private fun CalendarTab(viewModel: ReporteeViewModel) {
    val cal by viewModel.calendar.collectAsState()
    val loading by viewModel.calendarLoading.collectAsState()
    val sk = MaterialTheme.skill

    if (loading && cal == null) { Center { CircularProgressIndicator(color = sk.brand) }; return }
    val c = cal ?: emptyMap()
    val current = c.rows("current")
    val upcoming = c.rows("upcoming")
    val offBands = (c["off_bands"] as? Map<*, *>).orEmpty()

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = Space.xl),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = Space.xl, bottom = Space.xxl),
    ) {
        item {
            SectionHeader("My schedule", conclusion = when {
                current.isNotEmpty() -> "You're delivering ${current.first().str("course")} now."
                upcoming.isNotEmpty() -> "${upcoming.size} batch${if (upcoming.size == 1) "" else "es"} ahead."
                else -> "Clear diary."
            })
        }
        if (current.isNotEmpty()) {
            item { Label("DELIVERING NOW") }
            items(current) { AssignmentRow(it, sk.aqua) }
        }
        if (upcoming.isNotEmpty()) {
            item { Label("UPCOMING") }
            items(upcoming) { AssignmentRow(it, sk.brand) }
        }
        if (offBands.isNotEmpty()) {
            item { SectionHeader("Shift bands you're marked off",
                conclusion = "Batches in these bands skip you. Ask your manager to update if wrong.") }
            items(offBands.entries.toList()) { (k, _) ->
                Row(Modifier.fillMaxWidth().padding(vertical = Space.md).editorialRule()) {
                    Text(k.toString().replace("_", " ").uppercase(),
                        style = MaterialTheme.typography.titleSmall, color = sk.warn)
                }
            }
        }
    }
}

@Composable
private fun AssignmentRow(a: Map<*, *>, tone: Color) {
    val sk = MaterialTheme.skill
    Column(Modifier.fillMaxWidth().padding(vertical = Space.md).editorialRule()) {
        Text(a.str("course"), style = MaterialTheme.typography.titleMedium, color = sk.bodyText)
        Text(
            listOf(a.str("start_date"), a.str("end_date").let { if (it.isNotBlank()) "→ $it" else "" },
                a.str("mode"), a.str("location"))
                .filter { it.isNotBlank() }.joinToString("  ·  "),
            style = MaterialTheme.typography.bodySmall, color = sk.subText,
        )
    }
}

@Composable
private fun Label(t: String) = Text(
    t, style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.skill.labelText, fontWeight = FontWeight.Bold,
    modifier = Modifier.padding(top = Space.lg, bottom = Space.xs),
)

/* ── Practice ───────────────────────────────────────────────────────────── */

@Composable
private fun PracticeTab(onOpenPractice: () -> Unit, onLogout: () -> Unit) {
    val sk = MaterialTheme.skill
    Column(
        Modifier.fillMaxSize().padding(horizontal = Space.xl, vertical = Space.xxl),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        SectionHeader("My practice", conclusion = "What learners said, and every session you delivered.")
        Text(
            "Your learner-feedback log and session recordings, in one place.",
            style = MaterialTheme.typography.bodyLarge, color = sk.subText,
        )
        Button(
            onClick = onOpenPractice,
            colors = ButtonDefaults.buttonColors(containerColor = sk.brand),
            shape = RoundedCornerShape(Radii.chip),
        ) { Text("Open my practice record  →") }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onLogout) { Text("Sign out", color = sk.warn) }
    }
}

/* ── Dialogs ────────────────────────────────────────────────────────────── */

@Composable
private fun MarkSkillDialog(courseName: String, onDismiss: () -> Unit, onSubmit: (Int) -> Unit) {
    var level by remember { mutableStateOf(3f) }
    val sk = MaterialTheme.skill
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = sk.cardBg,
        title = { Text("Mark my skill", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(courseName, style = MaterialTheme.typography.bodyMedium, color = sk.bodyText)
                Figure(level.toInt().toString(), "Level", size = FigureSize.Medium)
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
                label = { Text("Your note") }, minLines = 3, modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onSend(text.trim()) }) { Text("Send") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Center(content: @Composable () -> Unit) =
    Box(Modifier.fillMaxSize().padding(Space.xl), contentAlignment = Alignment.Center) { content() }

private fun toast(context: android.content.Context, message: String) =
    android.widget.Toast.makeText(context.applicationContext, message, android.widget.Toast.LENGTH_SHORT).show()

private fun Map<*, *>?.orEmpty(): Map<*, *> = this ?: emptyMap<Any, Any>()
