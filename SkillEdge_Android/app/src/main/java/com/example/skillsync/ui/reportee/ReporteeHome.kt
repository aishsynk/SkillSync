package com.example.skillsync.ui.reportee

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.skillsync.ReporteeTab
import com.example.skillsync.theme.AuroraBackground
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.SkillCard
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.auth.roleLabel
import com.example.skillsync.ui.components.list
import com.example.skillsync.ui.components.rows
import com.example.skillsync.ui.components.str

/**
 * The whole app a trainer (reportee) sees. Three tabs, nothing from the manager
 * consoles. The only writes a trainer can make: mark their own skill (capped at
 * level 4 by the backend) and message their own manager.
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

    // Skill-mark dialog state, shared by Today and Demand.
    var skillTarget by remember { mutableStateOf<Pair<String, String>?>(null) } // courseId to courseName
    var messageOpen by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground()
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.skill.cardBg) {
                    NavItem(tab, ReporteeTab.TODAY, "Today", onTabChange)
                    NavItem(tab, ReporteeTab.DEMAND, "Demand", onTabChange)
                    NavItem(tab, ReporteeTab.UPDATES, "Updates", onTabChange)
                }
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

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavItem(
    current: String, key: String, label: String, onTabChange: (String) -> Unit,
) = NavigationBarItem(
    selected = current == key,
    onClick = { onTabChange(key) },
    icon = { Text(label.take(1), fontWeight = FontWeight.Bold) },
    label = { Text(label) },
)

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

    LazyColumn(
        Modifier.fillMaxSize().padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        item {
            Text(
                "Hi ${h.str("name").ifBlank { "there" }}",
                style = MaterialTheme.typography.headlineSmall, color = sk.bodyText,
            )
            Text(roleLabel(h.str("role").ifBlank { "reportee" }),
                style = MaterialTheme.typography.labelMedium, color = sk.subText)
        }

        item {
            SkillCard(Modifier.fillMaxWidth()) {
                Text("MY WEEK", style = MaterialTheme.typography.labelSmall,
                    color = sk.labelText, fontWeight = FontWeight.Bold)
                val util = h["current_utilization"]
                Text(
                    if (util != null) "Utilisation ${(util as? Number)?.toInt() ?: util}%"
                    else "Utilisation not available",
                    style = MaterialTheme.typography.titleMedium, color = sk.bodyText,
                )
                val next = h["next_batch"] as? Map<*, *>
                Text(
                    if (next != null) "Next: ${next.str("course")} · ${next.str("start_date")}"
                    else "No upcoming batch assigned",
                    style = MaterialTheme.typography.bodySmall, color = sk.subText,
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                Button(onClick = onMessageManager) { Text("Message my manager") }
            }
        }

        val requests = h.rows("my_requests")
        if (requests.isNotEmpty()) {
            item {
                Text("MY SKILL REQUESTS", style = MaterialTheme.typography.labelSmall,
                    color = sk.labelText, fontWeight = FontWeight.Bold)
            }
            items(requests) { r ->
                SkillCard(Modifier.fillMaxWidth()) {
                    Text(
                        "Level ${r.str("requested_level")} · " +
                            r.str("course_name").ifBlank { "course ${r.str("course_id")}" },
                        style = MaterialTheme.typography.bodyMedium, color = sk.bodyText,
                    )
                    Text(r.str("status").uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = when (r.str("status")) {
                            "approved" -> sk.brand; "denied" -> sk.warn; else -> sk.subText
                        })
                }
            }
        }

        val skills = h.rows("my_skills")
        item {
            Text("MY SKILLS", style = MaterialTheme.typography.labelSmall,
                color = sk.labelText, fontWeight = FontWeight.Bold)
        }
        if (skills.isEmpty()) {
            item { Text("No skills on record yet.", color = sk.subText,
                style = MaterialTheme.typography.bodySmall) }
        }
        items(skills) { s ->
            SkillCard(Modifier.fillMaxWidth()) {
                Text(s.str("course_name"), style = MaterialTheme.typography.bodyMedium, color = sk.bodyText)
                TextButton(onClick = { onMarkSkill(s.str("course_id"), s.str("course_name")) }) {
                    Text("Update my level")
                }
            }
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
            Text("No open batches match your current skills.", color = sk.subText)
        }
        else -> LazyColumn(
            Modifier.fillMaxSize().padding(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            item {
                Text("OPEN BATCHES YOU CAN TEACH", style = MaterialTheme.typography.labelSmall,
                    color = sk.labelText, fontWeight = FontWeight.Bold)
            }
            items(rows) { row ->
                SkillCard(Modifier.fillMaxWidth()) {
                    Text(row.str("course_name"), style = MaterialTheme.typography.titleSmall, color = sk.bodyText)
                    Text(
                        listOf(row.str("start_date"), row.str("location"),
                            "match ${row.str("skill_match_pct")}%")
                            .filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = sk.subText,
                    )
                    TextButton(onClick = { onMarkSkill(row.str("course_id"), row.str("course_name")) }) {
                        Text("Mark my skill")
                    }
                }
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
        Modifier.fillMaxSize().padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        if (updates.isEmpty()) item { Text("Nothing new.", color = sk.subText) }
        items(updates) { n ->
            SkillCard(Modifier.fillMaxWidth()) {
                Text(n.str("title"), style = MaterialTheme.typography.titleSmall, color = sk.bodyText)
                Text(n.str("message"), style = MaterialTheme.typography.bodySmall, color = sk.subText)
            }
        }
        item {
            TextButton(onClick = onLogout) { Text("Sign out", color = sk.warn) }
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
        title = { Text("Mark my skill") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(courseName, color = sk.bodyText)
                Text("Level ${level.toInt()}", color = sk.bodyText,
                    style = MaterialTheme.typography.titleMedium)
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Message my manager") },
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
