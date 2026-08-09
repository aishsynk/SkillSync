package com.example.skillsync.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.glassSurface
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.components.*

/** A compact decision surface. Every block either explains risk or opens evidence. */
@Composable
fun ManagerCommandCentre(
    kpis: Map<*, *>?, capKpis: Map<*, *>?, capabilityLoading: Boolean,
    ops: List<Map<*, *>>, states: List<Map<*, *>>, batches: List<Map<*, *>>,
    demand: List<Map<*, *>>, capTrainers: List<Map<*, *>>,
    actions: List<Map<String, Any>>, onDrill: (Drill) -> Unit,
    onTrainerClick: (String, String) -> Unit,
) {
    val sk = MaterialTheme.skill
    val openActions = actions.filter { it.str("lifecycle_state").ifBlank { "open" } !in setOf("closed", "resolved") }
    val available = states.filter { it.str("current_status") == "free" }
    val overloaded = ops.filter { it.intOrNull("current_utilization")?.let { u -> u > 85 } == true }
    val active = batches.filter { it.str("engagement_state") == "current" }
    val upcoming = batches.filter { it.str("engagement_state") == "upcoming" }
    val gaps = capTrainers.sumOf { it.obj("certification")?.int("gap_count") ?: 0 }
    val readiness = capKpis?.intOrNull("team_readiness_score") ?: kpis?.intOrNull("team_readiness_score")

    // LazyColumn treats this entire command centre as one item. It therefore
    // needs one explicit measuring parent; emitting sibling roots here causes
    // every section to occupy the same item slot and visually overlap.
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {

    SectionTitle("1  EXECUTIVE SUMMARY", "What needs a decision now")
    ExecutiveNarrative(ops.size, available.size, overloaded.size, demand.size, openActions.size, readiness)
    CompactKpiGrid(
        listOf(
            MiniKpi("Team strength", ops.size.toString(), "reportees", sk.good, drill("Team strength", ops)),
            MiniKpi("Available capacity", available.size.toString(), if (available.isEmpty()) "none verified free" else "verified free now", if (available.isEmpty()) sk.subText else sk.good,
                Drill("Available capacity", "Assignment-derived availability", available.map { DrillRow(it.str("trainer_name").ifBlank { it.str("trainer_email") }, it.str("reason"), it.str("trainer_email")) })),
            MiniKpi("Utilisation", kpis?.intOrNull("avg_team_utilization")?.let { "$it%" } ?: "—", "${kpis?.intOrNull("utilization_sample") ?: 0}/${ops.size} measured", utilisationColour(kpis?.intOrNull("avg_team_utilization"), sk), drill("Team utilisation", ops, true)),
            MiniKpi("Active deliveries", active.size.toString(), "running now", if (active.isEmpty()) sk.subText else sk.good, batchDrill("Active deliveries", active)),
            MiniKpi("Unallocated demand", demand.size.toString(), "batches need owners", if (demand.isEmpty()) sk.good else sk.crit, demandDrill(demand)),
            MiniKpi("Actions", openActions.size.toString(), "requiring attention", if (openActions.isEmpty()) sk.good else sk.crit, actionDrill(openActions)),
        ), onDrill
    )

    val utilizationHistory = (kpis?.get("utilization_history") as? List<*>)
        ?.mapNotNull { (it as? Number)?.toInt() }.orEmpty()
    DecisionPanel("Utilisation trend", "Measured team utilisation over the available RMS window") {
        TrendChart(
            points = utilizationHistory.mapIndexed { index, value ->
                TrendPoint(if (index == utilizationHistory.lastIndex) "Now" else "M${index + 1}", value)
            },
            tint = utilisationColour(utilizationHistory.lastOrNull(), sk),
            height = 76.dp,
        )
    }

    SectionTitle("2  TEAM HEALTH & CAPACITY", "Who is healthy, stretched or at risk")
    val highRisk = ops.filter { it.str("feedback_risk").equals("High", true) }
    val watch = ops.filter { it.str("capacity_bucket") in setOf("Light", "Stretched") && it !in highRisk }
    val needs = ops.filter { it.str("recommended_action").isNotBlank() && it !in highRisk && it !in watch }
    val healthy = ops.filter { it !in highRisk && it !in watch && it !in needs }
    val healthSlices = listOf(
        Slice("Healthy", healthy.size, sk.good), Slice("Watchlist", watch.size, sk.warn),
        Slice("Needs attention", needs.size, sk.warn), Slice("High risk", highRisk.size, sk.crit),
    )
    DecisionPanel("Team health & risk matrix", "Tap a status to inspect the trainers") {
        DistributionBar(healthSlices)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Healthy" to healthy, "Watch" to watch, "Attention" to needs, "Risk" to highRisk).forEach { (label, rows) ->
                TextButton(onClick = { onDrill(drill(label, rows)) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(2.dp)) {
                    Text("$label\n${rows.size}", fontSize = 9.sp, maxLines = 2)
                }
            }
        }
    }
    DecisionPanel("Capacity vs demand", "Verified free trainers compared with unallocated batches") {
        BarChart(listOf(BarDatum("Free", available.size, sk.aqua), BarDatum("Demand", demand.size, sk.warn), BarDatum("Overload", overloaded.size, sk.crit)), height = 92.dp)
    }
    TopPerformersPanel(ops, capTrainers, onTrainerClick)

    SectionTitle("3  DEMAND INTELLIGENCE", "What future work is waiting and where")
    val modeCounts = listOf("FMAT", "ILT", "ILO", "Unknown").map { mode ->
        val count = demand.count { it.str("delivery_mode").ifBlank { "Unknown" }.equals(mode, true) }
        BarDatum(mode, count, when (mode) { "FMAT" -> sk.crit; "ILT" -> sk.warn; "ILO" -> sk.sky; else -> sk.subText })
    }
    DecisionPanel("Demand coverage heatmap", "Priority modes and ownership pressure") {
        BarChart(modeCounts, height = 96.dp)
        InsightLine(if (demand.isEmpty()) "All visible demand is covered." else "${demand.size} batches remain unallocated; FMAT and ILT are reviewed first.", if (demand.isEmpty()) sk.good else sk.warn)
    }

    SectionTitle("4  DELIVERY OPERATIONS", "What is running and what starts next")
    CompactKpiGrid(listOf(
        MiniKpi("Active", active.size.toString(), "live deliveries", sk.aqua, batchDrill("Active deliveries", active)),
        MiniKpi("Upcoming", upcoming.size.toString(), "scheduled next", sk.sky, batchDrill("Upcoming deliveries", upcoming)),
        MiniKpi("Delivered days", kpis?.intOrNull("training_days_delivered")?.toString() ?: "—", kpis?.str("training_days_window_label").orEmpty(), sk.brand, null),
        MiniKpi("Delivery risk", highRisk.size.toString(), "trainer restrictions", if (highRisk.isEmpty()) sk.good else sk.crit, drill("Delivery risk", highRisk)),
    ), onDrill)
    UpcomingCalendar(upcoming.take(5), onDrill)

    SectionTitle("5  CERTIFICATION & READINESS", "Coverage that can block future delivery")
    val coverage = capKpis?.intOrNull("avg_trainer_coverage_pct") ?: kpis?.intOrNull("cert_coverage_pct")
    val ready = capTrainers.count { it.int("readiness_score") >= 75 }
    val developing = capTrainers.count { it.int("readiness_score") in 50..74 }
    val blocked = capTrainers.size - ready - developing
    DecisionPanel("Certification coverage", if (capabilityLoading) "Refreshing real capability data" else "$gaps certification gaps require follow-up") {
        val slices = listOf(Slice("Covered", coverage ?: 0, sk.good), Slice("Gap", if (coverage == null) 0 else 100 - coverage, sk.warn))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(slices, coverage?.let { "$it%" } ?: "—", "coverage", size = 92.dp)
            Spacer(Modifier.width(18.dp)); ChartLegend(slices, Modifier.weight(1f))
        }
    }
    DecisionPanel("Team readiness distribution", "Readiness is based on verified capability evidence") {
        BarChart(listOf(BarDatum("Ready", ready, sk.good), BarDatum("Develop", developing, sk.warn), BarDatum("Blocked", blocked.coerceAtLeast(0), sk.crit)), height = 92.dp)
    }

    SectionTitle("6  ACTION CENTRE", "The manager's working queue")
    val categories = listOf("Certification", "Underutilised", "Overallocated", "Unallocated", "Readiness", "Feedback")
    val actionBars = categories.map { category ->
        BarDatum(category.take(5), openActions.count { (it.str("category") + " " + it.str("title") + " " + it.str("detail")).contains(category, true) }, sk.warn)
    }
    DecisionPanel("Actions requiring attention", "${openActions.size} open across six management themes") {
        BarChart(actionBars, height = 100.dp)
        Spacer(Modifier.height(8.dp))
        openActions.take(3).forEach { ActionPreview(it) }
        if (openActions.isEmpty()) InsightLine("No open manager actions.", sk.good)
        else TextButton(onClick = { onDrill(actionDrill(openActions)) }, modifier = Modifier.align(Alignment.End)) { Text("Review all ${openActions.size}") }
    }
    }
}

private data class MiniKpi(val label: String, val value: String, val caption: String, val tint: Color, val drill: Drill?)

@Composable private fun SectionTitle(title: String, subtitle: String) { Column(Modifier.padding(top = 5.dp, bottom = 2.dp)) { Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.skill.ice); Text(subtitle, fontSize = 11.sp, color = MaterialTheme.skill.subText) } }

@Composable private fun ExecutiveNarrative(team: Int, free: Int, overload: Int, demand: Int, actions: Int, readiness: Int?) {
    val sk = MaterialTheme.skill
    DecisionPanel("Manager brief", "Live executive summary") {
        Text(when {
            actions > 0 -> "$actions actions need attention. $free of $team trainers are verified free, while $demand batches need allocation."
            overload > 0 -> "$overload trainers are overloaded. Rebalance upcoming work before assigning $demand open batches."
            else -> "Team operations are stable. $free of $team trainers are free and $demand batches await allocation."
        }, style = MaterialTheme.typography.bodyMedium, color = sk.bodyText, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp)); InsightLine("Readiness ${readiness?.let { "$it%" } ?: "not verified"} • $overload overloaded", if (overload > 0) sk.crit else sk.good)
    }
}

@Composable private fun CompactKpiGrid(items: List<MiniKpi>, onDrill: (Drill) -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(7.dp)) { items.chunked(2).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) { row.forEach { item -> key(item.label) { CompactKpi(item, Modifier.weight(1f), onDrill) } }; if (row.size == 1) Spacer(Modifier.weight(1f)) } } } }

@Composable private fun CompactKpi(item: MiniKpi, modifier: Modifier, onDrill: (Drill) -> Unit) { val sk = MaterialTheme.skill; val target = item.drill; Column(modifier.height(78.dp).glassSurface(RoundedCornerShape(12.dp)).then(if (target != null) Modifier.clickable(onClick = { onDrill(target) }) else Modifier).padding(horizontal = 11.dp, vertical = 9.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(7.dp).background(item.tint, RoundedCornerShape(3.dp))); Spacer(Modifier.width(6.dp)); Text(item.label.uppercase(), fontSize = 8.sp, fontWeight = FontWeight.Bold, color = sk.labelText, maxLines = 1) }; Spacer(Modifier.height(3.dp)); Text(item.value, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = item.tint); Text(item.caption, fontSize = 9.sp, color = sk.subText, maxLines = 1, overflow = TextOverflow.Ellipsis) } }

private fun utilisationColour(value: Int?, sk: com.example.skillsync.theme.SkillColors): Color = when {
    value == null -> sk.subText
    value > 85 -> sk.crit
    value >= 55 -> sk.good
    value >= 30 -> sk.warn
    else -> sk.sky
}

@Composable
private fun TopPerformersPanel(
    ops: List<Map<*, *>>,
    capTrainers: List<Map<*, *>>,
    onTrainerClick: (String, String) -> Unit,
) {
    val sk = MaterialTheme.skill
    val capMap = remember(capTrainers) { capTrainers.associateBy { it.str("trainer_email").lowercase() } }
    val top = remember(ops) {
        ops.filter { (it.intOrNull("current_utilization") ?: 0) > 0 }
            .sortedByDescending { it.int("current_utilization") }
            .take(5)
    }
    if (top.isEmpty()) return
    DecisionPanel("Top performers", "Carrying delivery · ranked by measured utilisation") {
        top.forEachIndexed { index, trainer ->
            val util = trainer.int("current_utilization")
            val email = trainer.str("official_email")
            Row(
                Modifier.fillMaxWidth()
                    .clickable { onTrainerClick(email, trainer.str("trainer_name")) }
                    .padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${index + 1}", color = sk.subText, fontSize = 10.sp, modifier = Modifier.width(18.dp))
                Avatar(trainer.str("trainer_name"), capMap[email.lowercase()]?.str("photo_url"), 28.dp)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(trainer.str("trainer_name"), color = sk.bodyText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(trainer.str("capacity_bucket").ifBlank { "Measured delivery load" }, color = sk.subText, fontSize = 8.5.sp, maxLines = 1)
                }
                Text("$util%", color = utilisationColour(util, sk), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
        }
    }
}

@Composable private fun DecisionPanel(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) { val sk = MaterialTheme.skill; Column(Modifier.fillMaxWidth().glassSurface(RoundedCornerShape(14.dp)).padding(13.dp)) { Text(title, fontWeight = FontWeight.Bold, color = sk.bodyText, fontSize = 13.sp); Text(subtitle, color = sk.subText, fontSize = 9.5.sp); Spacer(Modifier.height(10.dp)); content() } }
@Composable private fun InsightLine(text: String, tint: Color) { Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(6.dp).background(tint, RoundedCornerShape(3.dp))); Spacer(Modifier.width(6.dp)); Text(text, fontSize = 9.5.sp, color = MaterialTheme.skill.subText) } }
@Composable private fun ActionPreview(a: Map<String, Any>) { Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) { Text(a.str("priority").ifBlank { "open" }.uppercase(), fontSize = 8.sp, fontWeight = FontWeight.Bold, color = if (a.str("priority").equals("high", true)) MaterialTheme.skill.crit else MaterialTheme.skill.warn, modifier = Modifier.width(42.dp)); Column(Modifier.weight(1f)) { Text(a.str("title").ifBlank { "Manager action" }, fontSize = 10.sp, color = MaterialTheme.skill.bodyText, maxLines = 1); Text(a.str("trainer_name"), fontSize = 8.5.sp, color = MaterialTheme.skill.subText, maxLines = 1) } } }
@Composable private fun UpcomingCalendar(rows: List<Map<*, *>>, onDrill: (Drill) -> Unit) { DecisionPanel("Upcoming delivery calendar", "Next scheduled commitments") { if (rows.isEmpty()) InsightLine("No upcoming deliveries returned by RMS.", MaterialTheme.skill.good) else rows.forEach { b -> Row(Modifier.fillMaxWidth().clickable { onDrill(batchDrill("Upcoming delivery", listOf(b))) }.padding(vertical = 5.dp)) { Text(b.str("start_at").takeLast(5).ifBlank { "TBC" }, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.skill.sky, modifier = Modifier.width(46.dp)); Text(b.str("course_name"), fontSize = 9.5.sp, color = MaterialTheme.skill.bodyText, maxLines = 1, overflow = TextOverflow.Ellipsis) } } } }

private fun drill(title: String, rows: List<Map<*, *>>, util: Boolean = false) = Drill(title, "Tap a trainer for detail", rows.map { DrillRow(it.str("trainer_name").ifBlank { it.str("official_email") }, if (util) "${it.intOrNull("current_utilization")?.let { u -> "$u%" } ?: "not measured"} • ${it.str("capacity_bucket")}" else listOf(it.str("capacity_bucket"), it.str("recommended_action")).filter { s -> s.isNotBlank() }.joinToString(" • "), it.str("official_email").ifBlank { it.str("trainer_email") }) })
private fun batchDrill(title: String, rows: List<Map<*, *>>) = Drill(title, "Delivery schedule", rows.map { DrillRow(it.str("course_name").ifBlank { "Unnamed course" }, listOf(it.str("trainer_name"), it.str("delivery_mode"), it.str("start_at")).filter(String::isNotBlank).joinToString(" • ")) })
private fun demandDrill(rows: List<Map<*, *>>) = Drill("Unallocated demand", "Batches waiting for a suitable owner", rows.map { DrillRow(it.str("course_name").ifBlank { it.str("demand_id") }, listOf(it.str("delivery_mode"), it.str("location"), it.str("start_date")).filter(String::isNotBlank).joinToString(" • ")) })
private fun actionDrill(rows: List<Map<String, Any>>) = Drill("Action centre", "Open decisions requiring manager attention", rows.map { DrillRow(it.str("title").ifBlank { "Manager action" }, listOf(it.str("trainer_name"), it.str("category"), it.str("priority")).filter(String::isNotBlank).joinToString(" • "), it.str("trainer_email").takeIf(String::isNotBlank)) })
