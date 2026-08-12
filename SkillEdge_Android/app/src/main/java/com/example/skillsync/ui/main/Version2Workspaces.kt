package com.example.skillsync.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.TextStyle
import java.util.Locale

import com.example.skillsync.theme.glassSurface
import com.example.skillsync.theme.accentGlass
import androidx.compose.material3.Surface
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.components.rows
import com.example.skillsync.ui.components.str

private data class CommandResult(
    val kind: String,
    val title: String,
    val detail: String,
    val trainerEmail: String = "",
    val demandId: String = "",
)

@Composable
internal fun PeopleWorkspaceSwitch(selected: String, onSelect: (String) -> Unit) {
    WorkspaceSelector(selected, listOf("PORTFOLIO" to "Team", "CAPABILITY" to "Capability"), onSelect)
}

@Composable
internal fun TodayWorkspaceSwitch(selected: String, onSelect: (String) -> Unit) {
    WorkspaceSelector(selected, listOf("BRIEF" to "Briefing", "QUEUE" to "Action queue"), onSelect)
}

@Composable
private fun WorkspaceSelector(
    selected: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    val sk = MaterialTheme.skill
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .background(sk.surface1, RoundedCornerShape(12.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (key, label) ->
            TextButton(
                onClick = { onSelect(key) },
                modifier = Modifier.weight(1f)
                    .background(if (selected == key) sk.surface3 else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(9.dp)),
            ) {
                Text(
                    label,
                    color = if (selected == key) sk.frost else sk.subText,
                    fontWeight = if (selected == key) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
internal fun UniversalCommandSearch(
    dashboard: Map<String, Any>,
    capability: Map<String, Any>?,
    allocation: Map<String, Any>?,
    actions: List<Map<String, Any>>,
    onTrainer: (String, String) -> Unit,
    onDemand: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val needle = query.trim().lowercase()
    val results = remember(needle, dashboard, capability, allocation, actions) {
        if (needle.length < 2) emptyList() else buildList {
            dashboard.rows("trainer_operations_df").forEach { row ->
                val name = row.str("trainer_name")
                val email = row.str("official_email")
                val detail = listOf(row.str("designation"), row.str("capacity_bucket"))
                    .filter { it.isNotBlank() }.joinToString(" · ")
                if ("$name $email $detail".lowercase().contains(needle)) {
                    add(CommandResult("TRAINER", name.ifBlank { email }, detail, trainerEmail = email))
                }
            }
            capability?.rows("courses").orEmpty().forEach { row ->
                val course = row.str("course_name").ifBlank { row.str("course") }
                val detail = listOf(row.str("vendor"), row.str("coverage"))
                    .filter { it.isNotBlank() }.joinToString(" · ")
                if ("$course $detail".lowercase().contains(needle)) {
                    add(CommandResult("COURSE", course, detail))
                }
            }
            allocation?.rows("batches").orEmpty().forEach { row ->
                val course = row.str("course_name")
                val detail = listOf(row.str("delivery_mode"), row.str("location"), row.str("customer"))
                    .filter { it.isNotBlank() }.joinToString(" · ")
                if ("$course $detail".lowercase().contains(needle)) {
                    add(CommandResult("DEMAND", course, detail, demandId = row.str("demand_id")))
                }
            }
            actions.forEach { row ->
                val title = row.str("title")
                val detail = listOf(row.str("trainer_name"), row.str("category"), row.str("priority"))
                    .filter { it.isNotBlank() }.joinToString(" · ")
                if ("$title $detail".lowercase().contains(needle)) {
                    add(CommandResult("ACTION", title, detail, trainerEmail = row.str("trainer_email")))
                }
            }
        }.take(60)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text("Find anything", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.skill.bodyText)
        Text("Trainers, capabilities, demand and actions in one command surface", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.skill.subText)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Try “available Azure”, “FMAT” or a trainer name") },
        )
        Spacer(Modifier.height(10.dp))
        when {
            needle.length < 2 -> Text("Enter at least two characters.", color = MaterialTheme.skill.subText)
            results.isEmpty() -> Text("No matching people, courses, demand or actions.", color = MaterialTheme.skill.subText)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results) { result ->
                    Column(
                        Modifier.fillMaxWidth()
                            .glassSurface(RoundedCornerShape(14.dp))
                            .clickable(enabled = result.trainerEmail.isNotBlank() || result.demandId.isNotBlank()) {
                                if (result.demandId.isNotBlank()) onDemand(result.demandId)
                                else if (result.trainerEmail.isNotBlank()) onTrainer(result.trainerEmail, result.title)
                            }
                            .padding(12.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(result.title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.skill.bodyText, modifier = Modifier.weight(1f))
                            Text(result.kind, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        if (result.detail.isNotBlank()) Text(result.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.skill.subText)
                    }
                }
            }
        }
    }
}

@Composable
internal fun DeliveryOperationsWorkspace(
    dashboard: Map<String, Any>,
    onTrainer: (String, String) -> Unit,
) {
    val sk = MaterialTheme.skill
    val assignments = dashboard.rows("batch_engagement_df")
    
    // Parse dates and sort chronologically
    val parsedAssignments = remember(assignments) {
        assignments.mapNotNull { row ->
            val startAt = row.str("start_at")
            val dateStr = if (startAt.length >= 10) startAt.substring(0, 10) else ""
            val date = try {
                if (dateStr.isNotBlank()) LocalDate.parse(dateStr) else null
            } catch (e: DateTimeParseException) { null }
            if (date != null) Pair(date, row) else null
        }.sortedBy { it.first }
    }

    // Group by Date
    val grouped = parsedAssignments.groupBy { it.first }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            Text("Delivery Operations", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.skill.bodyText)
            Text("Calendar overview of all previous, current, and future assignments", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.skill.subText)
            Spacer(Modifier.height(16.dp))
        }

        if (grouped.isEmpty()) {
            item { Text("No assignments found.", color = MaterialTheme.skill.subText, style = MaterialTheme.typography.bodySmall) }
        }

        grouped.forEach { (date, rows) ->
            // Determine state for color coding (if any is live, mark day as live, else future/past)
            val states = rows.map { it.second.str("engagement_state") }
            val lineColor = when {
                states.contains("current") -> sk.teal
                states.contains("upcoming") -> sk.sky
                else -> sk.subText.copy(alpha = 0.3f)
            }
            
            val dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            val dayOfMonth = date.dayOfMonth.toString()
            val month = date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())

            item {
                Row(Modifier.fillMaxWidth()) {
                    // Date Column (Timeline)
                    Column(
                        Modifier
                            .width(60.dp)
                            .drawBehind {
                                // Draw vertical timeline line
                                drawLine(
                                    color = lineColor,
                                    start = Offset(size.width - 16.dp.toPx(), 0f),
                                    end = Offset(size.width - 16.dp.toPx(), size.height),
                                    strokeWidth = 2.dp.toPx()
                                )
                                // Draw node circle
                                drawCircle(
                                    color = lineColor,
                                    radius = 4.dp.toPx(),
                                    center = Offset(size.width - 16.dp.toPx(), 24.dp.toPx())
                                )
                            }
                            .padding(top = 14.dp, end = 24.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(dayOfWeek.uppercase(), style = MaterialTheme.typography.labelSmall, color = if (states.contains("current")) sk.teal else sk.subText, fontWeight = FontWeight.Bold)
                        Text(dayOfMonth, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.skill.bodyText, fontWeight = FontWeight.Black)
                        Text(month.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.skill.subText)
                    }

                    // Assignments Column
                    Column(
                        Modifier.weight(1f).padding(top = 10.dp, bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rows.forEach { (_, row) ->
                            val trainer = row.str("trainer_name")
                            val email = row.str("trainer_email")
                            val state = row.str("engagement_state")
                            val cardBg = when (state) {
                                "current" -> sk.teal
                                "upcoming" -> sk.sky
                                else -> sk.subText
                            }
                            Column(
                                Modifier.fillMaxWidth()
                                    .accentGlass(cardBg, RoundedCornerShape(14.dp))
                                    .clickable(enabled = email.isNotBlank()) { onTrainer(email, trainer) }
                                    .padding(12.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text(row.str("course_name").ifBlank { "Course not supplied" }, fontWeight = FontWeight.SemiBold, color = MaterialTheme.skill.bodyText, modifier = Modifier.weight(1f))
                                    Surface(color = cardBg.copy(alpha = 0.14f), shape = RoundedCornerShape(4.dp)) {
                                        Text(state.uppercase(), style = MaterialTheme.typography.labelSmall, color = cardBg, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(trainer.ifBlank { "Trainer not supplied" }, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                Text(listOf(row.str("delivery_mode"), row.str("location")).filter { it.isNotBlank() }.joinToString(" · "), color = MaterialTheme.skill.subText, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
