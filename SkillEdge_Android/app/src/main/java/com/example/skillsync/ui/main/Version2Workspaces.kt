package com.example.skillsync.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.skillsync.R
import com.example.skillsync.theme.accentGlass
import com.example.skillsync.theme.glassSurface
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.components.intOrNull
import com.example.skillsync.ui.components.rows
import com.example.skillsync.ui.components.str

private data class CommandResult(
    val kind: String,
    val title: String,
    val detail: String,
    val trainerEmail: String = "",
    val demandId: String = "",
    val badge: String = "",
    val badgeColor: Color = Color.Unspecified,
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
                    .background(if (selected == key) sk.surface3 else Color.Transparent, RoundedCornerShape(9.dp)),
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

/**
 * Universal Command Search — Instant discovery across Trainers, Courses,
 * Unallocated Demand, and Manager Actions with quick filter chips and scope tabs.
 */
@Composable
internal fun UniversalCommandSearch(
    dashboard: Map<String, Any>,
    capability: Map<String, Any>?,
    allocation: Map<String, Any>?,
    actions: List<Map<String, Any>>,
    onTrainer: (String, String) -> Unit,
    onDemand: (String) -> Unit,
) {
    val sk = MaterialTheme.skill
    var query by remember { mutableStateOf("") }
    var selectedScope by remember { mutableStateOf("ALL") }
    val needle = query.trim().lowercase()

    val quickPrompts = listOf(
        "🔥 High Risk" to "high",
        "🏖️ On Bench" to "bench",
        "⚡ FMAT" to "fmat",
        "⚠️ Gap" to "gap",
        "🌐 Azure" to "azure",
        "📜 AWS" to "aws",
    )

    val allResults = remember(needle, dashboard, capability, allocation, actions) {
        if (needle.length < 2) emptyList() else buildList {
            // Trainers
            dashboard.rows("trainer_operations_df").forEach { row ->
                val name = row.str("trainer_name")
                val email = row.str("official_email")
                val designation = row.str("designation")
                val capacity = row.str("capacity_bucket")
                val risk = row.str("feedback_risk")
                val detail = listOf(designation, capacity, if (risk.isNotBlank()) "$risk risk" else "").filter { it.isNotBlank() }.joinToString(" · ")
                if ("$name $email $detail $risk $capacity".lowercase().contains(needle)) {
                    val color = when {
                        risk.equals("High", true) -> sk.crit
                        capacity.contains("Bench", true) -> sk.cyan
                        else -> sk.teal
                    }
                    add(CommandResult("TRAINER", name.ifBlank { email }, detail, trainerEmail = email, badge = "Trainer", badgeColor = color))
                }
            }
            // Courses
            capability?.rows("courses").orEmpty().forEach { row ->
                val course = row.str("course_name").ifBlank { row.str("course") }
                val vendor = row.str("vendor")
                val coverage = row.str("coverage")
                val certified = row.str("certified_count")
                val detail = listOf(vendor, coverage, if (certified.isNotBlank()) "$certified certified" else "").filter { it.isNotBlank() }.joinToString(" · ")
                if ("$course $detail $vendor".lowercase().contains(needle)) {
                    add(CommandResult("COURSE", course, detail, badge = "Course", badgeColor = sk.sky))
                }
            }
            // Demand batches
            allocation?.rows("batches").orEmpty().forEach { row ->
                val course = row.str("course_name")
                val mode = row.str("delivery_mode")
                val loc = row.str("location")
                val cust = row.str("customer")
                val detail = listOf(mode, loc, cust).filter { it.isNotBlank() }.joinToString(" · ")
                if ("$course $detail $mode $cust".lowercase().contains(needle)) {
                    add(CommandResult("DEMAND", course, detail, demandId = row.str("demand_id"), badge = "Demand", badgeColor = sk.amber))
                }
            }
            // Actions
            actions.forEach { row ->
                val title = row.str("title")
                val trainer = row.str("trainer_name")
                val cat = row.str("category")
                val prio = row.str("priority")
                val detail = listOf(trainer, cat, prio).filter { it.isNotBlank() }.joinToString(" · ")
                if ("$title $detail $cat $prio".lowercase().contains(needle)) {
                    val color = if (prio.equals("critical", true) || prio.equals("high", true)) sk.crit else sk.sky
                    add(CommandResult("ACTION", title, detail, trainerEmail = row.str("trainer_email"), badge = "Action", badgeColor = color))
                }
            }
        }.take(80)
    }

    val filteredResults = remember(allResults, selectedScope) {
        if (selectedScope == "ALL") allResults
        else allResults.filter { it.kind == selectedScope }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Universal Command Search", style = MaterialTheme.typography.titleMedium, color = sk.bodyText, fontWeight = FontWeight.Bold)
                Text("Search across trainers, capability, demand and action queue", style = MaterialTheme.typography.bodySmall, color = sk.subText)
            }
            if (filteredResults.isNotEmpty()) {
                Surface(color = sk.cardBg, shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, sk.cardBorder)) {
                    Text("${filteredResults.size} matches", style = MaterialTheme.typography.labelSmall, color = sk.cyan, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Try “available Azure”, “FMAT” or a trainer name", color = sk.subText) },
            shape = RoundedCornerShape(12.dp),
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), null, tint = sk.cyan, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { query = "" }) {
                        Text("✕", color = sk.subText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
        )

        // Quick Suggestion Chips
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            quickPrompts.forEach { (label, term) ->
                Surface(
                    onClick = { query = term },
                    shape = RoundedCornerShape(8.dp),
                    color = if (query.contains(term, true)) sk.cyan.copy(alpha = 0.2f) else sk.cardBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (query.contains(term, true)) sk.cyan else sk.cardBorder),
                ) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = if (query.contains(term, true)) sk.cyan else sk.labelText, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }

        // Scope Filter Tabs
        if (allResults.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("ALL" to "All (${allResults.size})", "TRAINER" to "Trainers", "COURSE" to "Courses", "DEMAND" to "Demand", "ACTION" to "Actions").forEach { (scope, label) ->
                    val selected = selectedScope == scope
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary else sk.cardBg)
                            .clickable { selectedScope = scope }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) Color.White else sk.subText,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
        }

        // Results List
        when {
            needle.length < 2 -> {
                Box(Modifier.fillMaxSize().padding(top = 40.dp), contentAlignment = Alignment.TopCenter) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Type to explore the command surface", style = MaterialTheme.typography.bodyMedium, color = sk.subText)
                        Text("Instant unified search across 100% of your organization", style = MaterialTheme.typography.labelSmall, color = sk.labelText)
                    }
                }
            }
            filteredResults.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(top = 40.dp), contentAlignment = Alignment.TopCenter) {
                    Text("No results matching “$query” in this scope.", style = MaterialTheme.typography.bodyMedium, color = sk.subText)
                }
            }
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(filteredResults) { result ->
                    Row(
                        Modifier.fillMaxWidth()
                            .glassSurface(RoundedCornerShape(12.dp))
                            .clickable(enabled = result.trainerEmail.isNotBlank() || result.demandId.isNotBlank()) {
                                if (result.demandId.isNotBlank()) onDemand(result.demandId)
                                else if (result.trainerEmail.isNotBlank()) onTrainer(result.trainerEmail, result.title)
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Category Icon
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(result.badgeColor.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                when (result.kind) {
                                    "TRAINER" -> "👤"
                                    "COURSE" -> "📚"
                                    "DEMAND" -> "💼"
                                    else -> "⚡"
                                },
                                fontSize = 16.sp,
                            )
                        }

                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(result.title, fontWeight = FontWeight.SemiBold, color = sk.bodyText, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                if (result.badge.isNotBlank()) {
                                    Surface(color = result.badgeColor.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                                        Text(result.badge.uppercase(), style = MaterialTheme.typography.labelSmall, color = result.badgeColor, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                    }
                                }
                            }
                            if (result.detail.isNotBlank()) {
                                Text(result.detail, style = MaterialTheme.typography.bodySmall, color = sk.subText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Delivery Operations Workspace — Fully features the interactive Outlook / Bootstrap 5
 * Month Calendar grid, live active delivery indicators, date inspection, and timeline feed.
 */
@Composable
internal fun DeliveryOperationsWorkspace(
    dashboard: Map<String, Any>,
    onOpenWeeklyReport: () -> Unit = {},
    onTrainer: (String, String) -> Unit = { _, _ -> },
) {
    val sk = MaterialTheme.skill
    val assignments = dashboard.rows("batch_engagement_df")

    val currentBatches = assignments.filter { it.str("engagement_state") == "current" }
    val liveCount = assignments.count { it.str("engagement_state") == "current" }
    val upcomingCount = assignments.count { it.str("engagement_state") == "upcoming" }
    val totalPax = assignments.sumOf { it.intOrNull("participants") ?: 0 }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Delivery Operations",
                    style = MaterialTheme.typography.titleMedium,
                    color = sk.bodyText,
                    fontWeight = FontWeight.Bold,
                )
                Surface(
                    onClick = onOpenWeeklyReport,
                    shape = RoundedCornerShape(10.dp),
                    color = sk.brand.copy(alpha = 0.20f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, sk.brand.copy(alpha = 0.50f)),
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_calendar), "Weekly Report", tint = sk.cyan, modifier = Modifier.size(14.dp))
                        Text("Weekly Report ↗", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = sk.cyan)
                    }
                }
            }
        }

        // Top KPI Banner
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = sk.cardBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, sk.cardBorder),
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(sk.good))
                            Text("DELIVERING", style = MaterialTheme.typography.labelSmall, color = sk.good, fontWeight = FontWeight.Bold)
                        }
                        Text("$liveCount live", style = MaterialTheme.typography.titleMedium, color = sk.bodyText, fontWeight = FontWeight.Black)
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = sk.cardBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, sk.cardBorder),
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("UPCOMING", style = MaterialTheme.typography.labelSmall, color = sk.sky, fontWeight = FontWeight.Bold)
                        Text("$upcomingCount batches", style = MaterialTheme.typography.titleMedium, color = sk.bodyText, fontWeight = FontWeight.Black)
                    }
                }

                if (totalPax > 0) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = sk.cardBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, sk.cardBorder),
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("TOTAL PAX", style = MaterialTheme.typography.labelSmall, color = sk.cyan, fontWeight = FontWeight.Bold)
                            Text("$totalPax learners", style = MaterialTheme.typography.titleMedium, color = sk.bodyText, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }

        // Active Deliveries (if any currently running)
        if (currentBatches.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("ACTIVE DELIVERIES", style = MaterialTheme.typography.labelSmall, color = sk.labelText, fontWeight = FontWeight.Bold)
                    Surface(color = sk.good.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                        Text("CURRENT", style = MaterialTheme.typography.labelSmall, color = sk.good, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
            items(currentBatches) { batch ->
                val course = batch.str("course_name")
                val trainer = batch.str("trainer_name")
                val trainerEmail = batch.str("trainer_email")
                val loc = batch.str("location")
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (trainerEmail.isNotBlank()) onTrainer(trainerEmail, trainer)
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = sk.cardBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, sk.cardBorder),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(course, style = MaterialTheme.typography.bodyMedium, color = sk.bodyText, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Surface(color = sk.good.copy(alpha = 0.18f), shape = RoundedCornerShape(4.dp)) {
                                Text("LIVE", style = MaterialTheme.typography.labelSmall, color = sk.good, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                            }
                        }
                        Text(listOf(trainer, loc).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = sk.subText)
                    }
                }
            }
        }

        // Complete Outlook Month Calendar & Timeline view
        item {
            TeamCalendarScreen(
                batches = assignments,
                demand = dashboard.rows("unallocated_demand_df"),
                modifier = Modifier.fillMaxWidth(),
                onTrainerClick = onTrainer,
            )
        }
    }
}
