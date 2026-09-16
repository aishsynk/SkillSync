package com.example.skillsync.feature.home

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.example.skillsync.core.data.CopilotRepository
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
import kotlinx.coroutines.launch
import com.example.skillsync.theme.accentGlass
import com.example.skillsync.theme.glassSurface
import com.example.skillsync.theme.skill
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.Space
import com.example.skillsync.core.ui.intOrNull
import com.example.skillsync.core.ui.rows
import com.example.skillsync.core.ui.str
import androidx.compose.material3.Text

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

/**
 * D1 promoted this pattern into the shared `theme.SegmentedSelector` — this
 * wrapper keeps `TodayWorkspaceSwitch`/`PeopleWorkspaceSwitch`'s existing call
 * sites and screen padding unchanged, delegating the actual segmented control
 * to the one shared implementation rather than keeping a second copy of it.
 */
@Composable
private fun WorkspaceSelector(
    selected: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    com.example.skillsync.theme.SegmentedSelector(
        options = options,
        selected = selected,
        onSelect = onSelect,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
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
    managerEmail: String = "",
) {
    val sk = MaterialTheme.skill
    val copilotRepository = remember { CopilotRepository() }
    var query by remember { mutableStateOf("") }
    var selectedScope by remember { mutableStateOf("ALL") }
    val needle = query.trim().lowercase()

    // Question-answering: any query that reads as a question routes to the team
    // Copilot (RMS-grounded), and its answer sits above the entity matches.
    val scope = rememberCoroutineScope()
    var answer by remember { mutableStateOf<Map<String, Any>?>(null) }
    var answering by remember { mutableStateOf(false) }
    var answeredFor by remember { mutableStateOf("") }
    val looksLikeQuestion = query.trim().let { q ->
        q.endsWith("?") || q.split(" ").firstOrNull()?.lowercase() in setOf(
            "who", "what", "when", "where", "which", "why", "how", "can", "is", "are", "does", "do", "should",
        )
    }
    fun ask() {
        val q = query.trim()
        if (q.length < 4 || answering || managerEmail.isBlank()) return
        answering = true; answeredFor = q
        scope.launch {
            try {
                answer = copilotRepository.askTeam(
                    mapOf("manager" to managerEmail, "question" to q),
                )
            } catch (_: Exception) {
                answer = mapOf("answer" to "Could not reach the Copilot. Try again in a moment.", "confidence" to "")
            } finally {
                answering = false
            }
        }
    }

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
                Text("Search & Ask", style = MaterialTheme.typography.titleMedium, color = sk.bodyText, fontWeight = FontWeight.Bold)
                Text("Find a trainer, course, demand or action — or ask a question and the team Copilot answers from RMS", style = MaterialTheme.typography.bodySmall, color = sk.subText)
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
            placeholder = { Text("Search a name… or ask “who can take AI-103 in October?”", color = sk.subText) },
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

        // ── Ask the Copilot ─────────────────────────────────────────────────
        if (looksLikeQuestion && query.trim().length >= 4) {
            Surface(
                onClick = { ask() },
                shape = RoundedCornerShape(10.dp),
                color = sk.cyan.copy(alpha = 0.14f),
                border = androidx.compose.foundation.BorderStroke(1.dp, sk.cyan.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (answering) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = sk.cyan, strokeWidth = 2.dp)
                    } else {
                        Icon(painterResource(R.drawable.ic_alert), null, tint = sk.cyan, modifier = Modifier.size(16.dp))
                    }
                    Text(
                        if (answering) "Asking the team Copilot…" else "Ask the team Copilot: “${query.trim()}”",
                        style = MaterialTheme.typography.labelMedium, color = sk.cyan, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        answer?.takeIf { answeredFor.isNotBlank() }?.let { a ->
            val conf = (a["confidence"] as? String).orEmpty()
            @Suppress("UNCHECKED_CAST")
            val people = (a["data"] as? List<Map<String, Any>>).orEmpty()
            Column(
                Modifier.fillMaxWidth().glassSurface(RoundedCornerShape(12.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("COPILOT", style = MaterialTheme.typography.labelSmall, color = sk.cyan, fontWeight = FontWeight.Bold)
                    if (conf.isNotBlank()) Text("· $conf confidence", style = MaterialTheme.typography.labelSmall, color = sk.subText)
                }
                Text((a["answer"] as? String).orEmpty(), style = MaterialTheme.typography.bodyMedium, color = sk.bodyText)
                people.forEach { p ->
                    val nm = (p["name"] as? String).orEmpty().trim()
                    val em = (p["email"] as? String).orEmpty()
                    val nt = (p["note"] as? String).orEmpty()
                    if (nm.isNotBlank()) Row(
                        Modifier.fillMaxWidth().clickable(enabled = em.isNotBlank()) { onTrainer(em, nm) }
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(nm, style = MaterialTheme.typography.bodySmall, color = sk.sky, fontWeight = FontWeight.SemiBold)
                        if (nt.isNotBlank()) Text(nt, style = MaterialTheme.typography.labelSmall, color = sk.subText)
                    }
                }
                (a["evidence"] as? String)?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = sk.labelText)
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
    readiness: Map<String, Map<String, Any>> = emptyMap(),
    onOpenWeeklyReport: () -> Unit = {},
    onTrainer: (String, String) -> Unit = { _, _ -> },
) {
    val sk = MaterialTheme.skill
    val assignments = dashboard.rows("batch_engagement_df")

    // Hoisted so the summary strip below can report counts for the exact
    // month/day the calendar is currently showing, instead of a second,
    // independently-scoped tally.
    var currentYearMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }

    val allEvents = remember(assignments, readiness) { buildCalendarEvents(assignments, readiness) }
    val monthEvents = remember(allEvents, currentYearMonth) {
        allEvents.filter { ev ->
            !ev.endDate.isBefore(currentYearMonth.atDay(1)) && !ev.startDate.isAfter(currentYearMonth.atEndOfMonth())
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
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

        // Compact operational summary — real counts for the month the calendar
        // is showing, never a repeat of the delivery cards themselves.
        item {
            OperationsSummaryStrip(
                monthLabel = currentYearMonth.format(DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)),
                events = monthEvents,
            )
        }

        // Complete premium Month/Week/Day calendar + category filters + agenda.
        item {
            TeamCalendarScreen(
                batches = assignments,
                readiness = readiness,
                modifier = Modifier.fillMaxWidth(),
                yearMonth = currentYearMonth,
                onYearMonthChange = { currentYearMonth = it },
                selectedDate = selectedDate,
                onSelectedDateChange = { selectedDate = it },
                onTrainerClick = onTrainer,
            )
        }
    }
}

/**
 * Compact stat row — the single source of "how much is going on this month,"
 * so the top of the page never has to repeat the same delivery cards the
 * calendar and agenda already show in detail.
 */
@Composable
private fun OperationsSummaryStrip(monthLabel: String, events: List<CalendarEventItem>) {
    val sk = MaterialTheme.skill
    val deliveries = events.count { it.category == EventCategory.DELIVERY }
    val leaves = events.count { it.category == EventCategory.LEAVE }
    val mocks = events.count { it.category == EventCategory.MOCK }
    val webinars = events.count { it.category == EventCategory.WEBINAR }
    val trainersActive = events.mapNotNull { it.trainerEmail.takeIf(String::isNotBlank) }.distinct().size

    Column(
        Modifier.fillMaxWidth().glassSurface(RoundedCornerShape(Radii.card)).padding(horizontal = Space.md, vertical = Space.sm),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "$monthLabel at a glance",
            style = MaterialTheme.typography.labelSmall,
            color = sk.labelText,
            fontWeight = FontWeight.Bold,
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SummaryStat("Events", events.size.toString(), sk.frost)
            SummaryStat("Delivery/Batch", deliveries.toString(), EventCategory.DELIVERY.color)
            SummaryStat("Leaves", leaves.toString(), EventCategory.LEAVE.color)
            SummaryStat("Mocks", mocks.toString(), EventCategory.MOCK.color)
            SummaryStat("Webinars", webinars.toString(), EventCategory.WEBINAR.color)
            SummaryStat("Trainers Active", trainersActive.toString(), sk.cyan)
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, tint: Color) {
    val sk = MaterialTheme.skill
    Column(horizontalAlignment = Alignment.Start) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = tint, fontWeight = FontWeight.Black)
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = sk.subText, fontWeight = FontWeight.SemiBold)
    }
}
