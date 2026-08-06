package com.example.skillsync.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey

// ── Brand colours (mirrors web frontend) ──────────────────────────────────────
private val Teal     = Color(0xFF00ACAC)
private val Blue     = Color(0xFF348FE2)
private val Green    = Color(0xFF90CA4B)
private val Amber    = Color(0xFFF59C1A)
private val Red      = Color(0xFFFF5B57)
private val Indigo   = Color(0xFF6610F2)
private val DarkBg   = Color(0xFF333F4A)
private val PageBg   = Color(0xFFF2F5F8)
private val CardBg   = Color.White
private val SubText  = Color(0xFF8A97A0)
private val BodyText = Color(0xFF3A4552)

// ── Screen entry point ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    email: String,
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel(),
) {
    LaunchedEffect(email) { viewModel.loadData(email) }
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("SkillSync", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        Text("Manager Command Dashboard", fontSize = 10.sp, color = Color.White.copy(alpha = 0.75f))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Teal,
                    titleContentColor = Color.White,
                ),
            )
        },
    ) { pv ->
        Box(modifier = modifier.fillMaxSize().padding(pv).background(PageBg)) {
            when (state) {
                is DashboardState.Loading -> DashLoadingView()
                is DashboardState.Error   -> DashErrorView((state as DashboardState.Error).message)
                is DashboardState.Success -> DashboardContent((state as DashboardState.Success).intelligenceData)
            }
        }
    }
}

@Composable
private fun DashLoadingView() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = Teal, strokeWidth = 3.dp)
        Spacer(Modifier.height(16.dp))
        Text("Loading manager intelligence…", style = MaterialTheme.typography.bodyMedium, color = SubText)
    }
}

@Composable
private fun DashErrorView(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("⚠", fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text(message, color = Red, style = MaterialTheme.typography.bodyMedium)
    }
}

// ── Dashboard content ─────────────────────────────────────────────────────────

@Composable
fun DashboardContent(data: Map<String, Any>) {
    val trainerOps    = data.listOf("trainer_operations_df")
    val trainerStates = data.listOf("trainer_current_state_df")
    val demands       = data.listOf("unallocated_demand_df")
    val allActions    = data.listOf("manager_action_objects")
    val openActions   = allActions.filter { it.str("lifecycle_state") != "closed" }
    val decisions     = data.listOf("trainer_decision_objects")
    val manager       = data["manager"] as? Map<*, *>

    val stateMap  = trainerStates.associateBy { it.str("trainer_email").lowercase() }
    val live      = trainerStates.count { it.str("current_status") == "teaching_now" }
    val preparing = trainerStates.count { it.str("current_status") in setOf("scheduled_today", "preparing") }
    val unknown   = trainerStates.count { it.str("current_status") == "unknown" }
    val blocked   = decisions.count { it.str("assignment_status") == "blocked" }
    val negFbCount= trainerOps.count { it.int("negative_count") > 0 }
    val utilVals  = trainerOps.mapNotNull { t ->
        val v = t["current_utilization"]
        when (v) {
            is Number -> v.toDouble().takeIf { it.isFinite() && it > 0 }
            is String -> v.toDoubleOrNull()?.takeIf { it > 0 }
            else      -> null
        }
    }
    val avgUtil   = if (utilVals.isNotEmpty()) utilVals.average().toInt() else 0
    val underUtil = utilVals.count { it < 60 }
    val overUtil  = utilVals.count { it > 85 }
    val known     = trainerStates.count { it.str("current_status") != "unknown" }
    val knownPct  = if (trainerOps.isNotEmpty()) (known * 100 / trainerOps.size) else 0

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(PageBg),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {

        // ── 1. Team Deployment header card ───────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkBg),
                shape = RoundedCornerShape(6.dp),
                elevation = CardDefaults.cardElevation(2.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "TEAM DEPLOYMENT",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFADB5BD),
                        letterSpacing = 0.1.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${trainerOps.size}",
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            " reportees",
                            fontSize = 15.sp,
                            color = Color(0xFFADB5BD),
                            modifier = Modifier.padding(start = 6.dp, top = 10.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        Chip("$knownPct% verified", Teal)
                    }
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f), thickness = 1.dp)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DeployStat("Delivering", live, Teal, trainerOps.size, Modifier.weight(1f))
                        DeployStat("Upcoming",   preparing, Blue, trainerOps.size, Modifier.weight(1f))
                        DeployStat("Unknown",    unknown, Color(0xFF7C8791), trainerOps.size, Modifier.weight(1f))
                    }
                }
            }
        }

        // ── 2. Capacity + Control ────────────────────────────────────────
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = DarkBg),
                    shape = RoundedCornerShape(6.dp),
                    elevation = CardDefaults.cardElevation(2.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("CAPACITY SIGNAL", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFADB5BD), letterSpacing = 0.1.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("$avgUtil", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("%", fontSize = 14.sp, color = Color(0xFFADB5BD), modifier = Modifier.padding(bottom = 5.dp, start = 2.dp))
                        }
                        Text("avg utilization · ${utilVals.size} trainers", fontSize = 9.sp, color = SubText, modifier = Modifier.padding(bottom = 10.dp))
                        MiniStat("< 60% util", underUtil.toString(), Green)
                        MiniStat("> 85% util", overUtil.toString(), Red)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = DarkBg),
                    shape = RoundedCornerShape(6.dp),
                    elevation = CardDefaults.cardElevation(2.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("MANAGER CONTROL", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFADB5BD), letterSpacing = 0.1.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("${openActions.size}", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("open decisions", fontSize = 9.sp, color = SubText, modifier = Modifier.padding(bottom = 10.dp))
                        MiniStat("Blocked",      blocked.toString(), Red)
                        MiniStat("Neg feedback", negFbCount.toString(), Amber)
                        MiniStat("Demand",       demands.size.toString(), Blue)
                    }
                }
            }
        }

        // ── 3. Team roster ───────────────────────────────────────────────
        item {
            SectionHeader("Team — right now  (${trainerOps.size})")
        }

        if (trainerOps.isEmpty()) {
            item { EmptyCard("No reportees returned. Check your account permissions.") }
        } else {
            items(trainerOps) { trainer ->
                val trEmail = trainer.str("official_email").lowercase()
                TrainerCard(trainer = trainer, state = stateMap[trEmail])
            }
        }

        // ── 4. Attention queue ───────────────────────────────────────────
        if (openActions.isNotEmpty()) {
            item { SectionHeader("Manager Attention  (${openActions.size})") }
            items(openActions.take(6)) { action ->
                AttentionCard(action)
            }
        }

        // ── 5. Unallocated demand ────────────────────────────────────────
        if (demands.isNotEmpty()) {
            item { SectionHeader("Unallocated Sales Demand  (${demands.size})") }
            items(demands.take(8)) { demand ->
                DemandCard(demand)
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}

// ── Trainer card ─────────────────────────────────────────────────────────────

@Composable
private fun TrainerCard(trainer: Map<*, *>, state: Map<*, *>?) {
    val name      = trainer.str("trainer_name")
    val desig     = trainer.str("designation").ifBlank { trainer.str("direct_or_indirect") }
    val util      = trainer.int("current_utilization")
    val fbRisk    = trainer.str("feedback_risk")
    val negCount  = trainer.int("negative_count")
    val readiness = trainer.str("readiness_bucket")
    val actionLbl = trainer.str("recommended_action")

    val status      = state?.str("current_status") ?: "unknown"
    val statusLabel = state?.str("status_label") ?: "Unknown"
    val confidence  = state?.int("confidence") ?: 0
    val curBatch    = state?.get("current_batch") as? Map<*, *>
    val nxtBatch    = state?.get("next_batch") as? Map<*, *>
    val curCourse   = curBatch?.str("course_name")?.takeIf { it.isNotBlank() }
    val curMode     = curBatch?.str("delivery_mode")?.takeIf { it.isNotBlank() }
    val nxtCourse   = nxtBatch?.str("course_name")?.takeIf { it.isNotBlank() }
    val nxtDate     = nxtBatch?.str("start_at")?.takeIf { it.isNotBlank() }

    val statusColor = when (status) {
        "teaching_now"    -> Teal
        "scheduled_today" -> Blue
        "preparing"       -> Indigo
        "free"            -> Green
        "blocked"         -> Red
        else              -> SubText
    }
    val fbColor = when (fbRisk.lowercase()) {
        "high"   -> Red
        "medium" -> Amber
        else     -> Green
    }
    val utilColor = when {
        util > 85 -> Red
        util > 60 -> Teal
        util > 30 -> Amber
        else      -> SubText
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(6.dp),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(12.dp)) {

            // Name + designation + status badge
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(Teal.copy(alpha = 0.1f), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        name.split(" ").take(2).joinToString("") { it.take(1).uppercase() },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Teal,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = BodyText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (desig.isNotBlank()) {
                        Text(desig, fontSize = 10.sp, color = SubText, maxLines = 1)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Chip(statusLabel, statusColor)
            }

            Spacer(Modifier.height(10.dp))

            // Utilization bar
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Util", fontSize = 9.sp, color = SubText, modifier = Modifier.width(26.dp))
                LinearProgressIndicator(
                    progress = { (util / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)),
                    color = utilColor,
                    trackColor = Color(0xFFE8EBEE),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "$util%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = utilColor,
                )
                if (confidence > 0) {
                    Text("  $confidence% conf.", fontSize = 8.5.sp, color = SubText)
                }
            }

            // Current delivery
            if (curCourse != null) {
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Text("→ ", fontSize = 9.sp, color = Teal, fontWeight = FontWeight.Bold)
                    Column {
                        Text(curCourse, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = BodyText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!curMode.isNullOrBlank()) {
                            Text(curMode, fontSize = 9.sp, color = SubText)
                        }
                    }
                }
            }

            // Next batch
            if (nxtCourse != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        append("↗ Next: ")
                        append(nxtCourse)
                        if (!nxtDate.isNullOrBlank()) append("  ($nxtDate)")
                    },
                    fontSize = 10.sp,
                    color = Color(0xFF5A6472),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(9.dp))
            HorizontalDivider(color = Color(0xFFF0F2F4), thickness = 1.dp)
            Spacer(Modifier.height(7.dp))

            // Feedback risk + readiness + action hint
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Chip(
                    text = "FB: $fbRisk${if (negCount > 0) " ($negCount)" else ""}",
                    tint = fbColor,
                )
                Spacer(Modifier.width(5.dp))
                Chip(readiness, SubText)
                Spacer(Modifier.weight(1f))
                Text(
                    actionLbl,
                    fontSize = 9.sp,
                    color = SubText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 160.dp),
                )
            }
        }
    }
}

// ── Attention card ────────────────────────────────────────────────────────────

@Composable
private fun AttentionCard(action: Map<*, *>) {
    val title    = action.str("title").ifBlank { "Manager action required" }
    val trainer  = action.str("trainer_name")
    val category = action.str("category").ifBlank { "Action" }
    val priority = action.str("priority")
    val catColor = when (category.lowercase()) {
        "feedback"   -> Red
        "allocation" -> Blue
        else         -> Amber
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(6.dp),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(Modifier.padding(0.dp)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(catColor))
            Column(Modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Chip(category, catColor)
                    if (priority.equals("high", ignoreCase = true)) {
                        Spacer(Modifier.width(5.dp))
                        Chip("HIGH", Red)
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = BodyText, maxLines = 2)
                if (trainer.isNotBlank()) {
                    Text(trainer, fontSize = 10.sp, color = SubText)
                }
            }
        }
    }
}

// ── Demand card ───────────────────────────────────────────────────────────────

@Composable
private fun DemandCard(demand: Map<*, *>) {
    val course   = demand.str("course_name").ifBlank { "Course not specified" }
    val date     = demand.str("start_date")
    val mode     = demand.str("delivery_mode")
    val customer = demand.str("customer")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(6.dp),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).background(Blue.copy(alpha = 0.1f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("!", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Blue)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(course, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = BodyText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(date, mode, customer).filter { it.isNotBlank() }.joinToString(" · "),
                    fontSize = 10.sp, color = SubText, maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
            Chip("Match", Blue)
        }
    }
}

// ── Reusable small components ─────────────────────────────────────────────────

@Composable
private fun Chip(text: String, tint: Color) {
    Surface(
        color = tint.copy(alpha = 0.13f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = tint,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun DeployStat(label: String, count: Int, color: Color, total: Int, modifier: Modifier) {
    val frac = if (total > 0) (count.toFloat() / total).coerceIn(0f, 1f) else 0f
    Column(modifier) {
        Text(label, fontSize = 9.sp, color = Color(0xFFADB5BD))
        Text("$count", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        LinearProgressIndicator(
            progress = { frac },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(3.dp).clip(RoundedCornerShape(2.dp)),
            color = color,
            trackColor = Color.White.copy(alpha = 0.1f),
        )
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(color, RoundedCornerShape(50)))
            Spacer(Modifier.width(5.dp))
            Text(label, fontSize = 9.sp, color = Color(0xFFADB5BD))
        }
        Text(value, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        color = BodyText,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun EmptyCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
            Text(message, fontSize = 12.sp, color = SubText)
        }
    }
}

// ── Legacy KpiCard (kept for any callers outside this file) ───────────────────

@Composable
fun KpiCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Extension helpers ─────────────────────────────────────────────────────────

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any>.listOf(key: String): List<Map<*, *>> =
    (this[key] as? List<*>)?.filterIsInstance<Map<*, *>>() ?: emptyList()

private fun Map<*, *>.str(key: String): String {
    val v = this[key] ?: return ""
    return if (v is String) v.trim() else v.toString().trim()
}

private fun Map<*, *>.int(key: String): Int {
    val v = this[key] ?: return 0
    return when (v) {
        is Int    -> v
        is Double -> v.toInt()
        is Float  -> v.toInt()
        is Long   -> v.toInt()
        is String -> v.toDoubleOrNull()?.toInt() ?: 0
        else      -> 0
    }
}

private fun Map<*, *>.intVal(key: String): Int = int(key)
