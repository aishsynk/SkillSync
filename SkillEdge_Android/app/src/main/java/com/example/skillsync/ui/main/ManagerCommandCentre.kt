package com.example.skillsync.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.skillsync.theme.Figure
import com.example.skillsync.theme.FigureSize
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.SectionHeading
import com.example.skillsync.theme.Severity
import com.example.skillsync.theme.SkillCard
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.ToneChip
import com.example.skillsync.theme.accentGlass
import com.example.skillsync.theme.glassSurface
import com.example.skillsync.theme.heroSurface
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.components.*

/**
 * The manager's briefing.
 *
 * This surface used to render six section titles and fifteen equal-weight
 * panels in one scroll, at type sizes down to 8sp — every reading given the
 * same emphasis, so nothing was emphasised. It now answers five questions in
 * descending order of urgency, and everything that is reference rather than
 * decision sits behind [ExploreSection], collapsed by default.
 *
 * No derivation below has changed: the same rows, filters, drills and captions
 * feed the same figures. Only weight, order and grouping are different.
 */
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
    val utilisation = kpis?.intOrNull("avg_team_utilization")

    val highRisk = ops.filter { it.str("feedback_risk").equals("High", true) }
    val watch = ops.filter { it.str("capacity_bucket") in setOf("Light", "Stretched") && it !in highRisk }
    val needs = ops.filter { it.str("recommended_action").isNotBlank() && it !in highRisk && it !in watch }
    val healthy = ops.filter { it !in highRisk && it !in watch && it !in needs }

    // LazyColumn treats this entire command centre as one item, so it needs one
    // explicit measuring parent; sibling roots would share a single item slot.
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {

        // ── 1 · Are we healthy? ─────────────────────────────────────────────
        BriefingHero(
            readiness = readiness,
            utilisation = utilisation,
            team = ops.size,
            free = available.size,
            overload = overloaded.size,
            demand = demand.size,
            actions = openActions.size,
        )

        // ── 2 · What is on fire? ────────────────────────────────────────────
        val alerts = buildAlerts(highRisk, overloaded, demand, openActions, gaps)
        if (alerts.isNotEmpty()) {
            SectionHeading("Needs you today", alerts.first().headline, trailing = "${alerts.size} open")
            alerts.take(3).forEach { alert ->
                key(alert.headline) {
                    SkillCard(
                        severity = alert.severity,
                        strong = alert.severity == Severity.Critical,
                        padding = Space.md,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(alert.drill?.let { d -> Modifier.clickable { onDrill(d) } } ?: Modifier),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                alert.headline,
                                style = MaterialTheme.typography.titleMedium,
                                color = sk.bodyText,
                                modifier = Modifier.weight(1f),
                            )
                            ToneChip(alert.severity.label, alert.severity.tint())
                        }
                        Text(alert.detail, style = MaterialTheme.typography.bodySmall, color = sk.subText)
                    }
                }
            }
        }

        // ── 3 · What is moving? ─────────────────────────────────────────────
        SectionHeading("Pulse", trailing = if (capabilityLoading) "refreshing" else null)
        PulseGrid(
            listOf(
                MiniKpi("Team strength", ops.size.toString(), "reportees", sk.sky, drill("Team strength", ops)),
                MiniKpi(
                    "Utilisation", utilisation?.let { "$it%" } ?: "—",
                    "${kpis?.intOrNull("utilization_sample") ?: 0} of ${ops.size} measured",
                    utilisationColour(utilisation, sk), drill("Team utilisation", ops, true),
                ),
                MiniKpi(
                    "Unallocated demand", demand.size.toString(), "batches need owners",
                    if (demand.isEmpty()) sk.good else sk.crit, demandDrill(demand),
                ),
                MiniKpi(
                    "Open actions", openActions.size.toString(), "requiring attention",
                    if (openActions.isEmpty()) sk.good else sk.warn, actionDrill(openActions),
                ),
            ),
            onDrill,
        )

        // ── 4 · Where is the slack? ─────────────────────────────────────────
        val balance = when {
            available.isEmpty() && demand.isNotEmpty() ->
                "No verified free trainer against ${demand.size} open ${plural(demand.size, "batch", "batches")}."
            overloaded.isNotEmpty() && available.isNotEmpty() ->
                "${available.size} free while ${overloaded.size} are over 85% — the gap is coverage, not headcount."
            demand.isEmpty() -> "All visible demand is covered."
            else -> "${available.size} free against ${demand.size} unallocated ${plural(demand.size, "batch", "batches")}."
        }
        SectionHeading("Capacity balance", balance)
        SkillCard(Modifier.fillMaxWidth()) {
            DistributionBar(
                listOf(
                    Slice("Healthy", healthy.size, sk.good),
                    Slice("Watchlist", watch.size, sk.warn),
                    Slice("Needs attention", needs.size, sk.warn),
                    Slice("High risk", highRisk.size, sk.crit),
                )
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                listOf("Healthy" to healthy, "Watch" to watch, "Attention" to needs, "Risk" to highRisk)
                    .forEach { (label, rows) ->
                        Figure(
                            value = rows.size.toString(),
                            label = label,
                            size = FigureSize.Small,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onDrill(drill(label, rows)) },
                        )
                    }
            }
        }

        // ── 5 · What is coming? ─────────────────────────────────────────────
        val international = demand.count { it.str("delivery_mode").uppercase() in setOf("FMAT", "ILT") }
        SectionHeading(
            "Demand",
            if (demand.isEmpty()) "Nothing waiting for an owner."
            else "$international of ${demand.size} unallocated ${plural(demand.size, "batch", "batches")} are FMAT or ILT — review those first.",
        )
        SkillCard(Modifier.fillMaxWidth()) {
            BarChart(
                listOf("FMAT", "ILT", "ILO", "Unknown").map { mode ->
                    BarDatum(
                        mode,
                        demand.count { it.str("delivery_mode").ifBlank { "Unknown" }.equals(mode, true) },
                        when (mode) {
                            "FMAT" -> sk.crit; "ILT" -> sk.warn; "ILO" -> sk.sky; else -> sk.subText
                        },
                    )
                },
                height = 92.dp,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
                Figure(active.size.toString(), "Active", FigureSize.Small, Modifier.weight(1f), sk.aqua)
                Figure(upcoming.size.toString(), "Upcoming", FigureSize.Small, Modifier.weight(1f), sk.sky)
                Figure(
                    kpis?.intOrNull("training_days_delivered")?.toString() ?: "—",
                    "Days delivered", FigureSize.Small, Modifier.weight(1f), sk.brand,
                )
            }
        }

        // ── 6 · Everything else ─────────────────────────────────────────────
        ExploreSection(
            kpis = kpis, capKpis = capKpis, capabilityLoading = capabilityLoading,
            ops = ops, upcoming = upcoming, capTrainers = capTrainers, gaps = gaps,
            openActions = openActions, highRisk = highRisk,
            onDrill = onDrill, onTrainerClick = onTrainerClick,
        )
    }
}

private fun plural(n: Int, one: String, many: String) = if (n == 1) one else many

// ── 1 · Briefing hero ───────────────────────────────────────────────────────

/**
 * The only large element on the screen. A ring for the single health reading, a
 * sentence for what it means, and the three supporting counts — so "is my org
 * healthy?" is answered before any scrolling happens.
 */
@Composable
private fun BriefingHero(
    readiness: Int?, utilisation: Int?, team: Int, free: Int,
    overload: Int, demand: Int, actions: Int,
) {
    val sk = MaterialTheme.skill
    Column(
        Modifier
            .fillMaxWidth()
            .heroSurface()
            .padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "TEAM READINESS",
                    style = MaterialTheme.typography.labelSmall,
                    color = sk.ice,
                )
                Text(
                    readiness?.toString() ?: "—",
                    style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
                    color = sk.heroText,
                )
            }
            ReadinessRing(readiness, utilisation, size = 84.dp)
        }
        Text(
            when {
                actions > 0 -> "$actions ${plural(actions, "action needs", "actions need")} attention. $free of $team trainers are verified free, while $demand ${plural(demand, "batch needs", "batches need")} allocation."
                overload > 0 -> "$overload trainers are overloaded. Rebalance upcoming work before assigning $demand open ${plural(demand, "batch", "batches")}."
                else -> "Team operations are stable. $free of $team trainers are free and $demand ${plural(demand, "batch awaits", "batches await")} allocation."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = sk.heroMuted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xl)) {
            Figure(team.toString(), "Strength", FigureSize.Small, tint = sk.heroText)
            Figure(free.toString(), "Free now", FigureSize.Small, tint = sk.heroText)
            Figure(utilisation?.let { "$it%" } ?: "—", "Utilisation", FigureSize.Small, tint = sk.heroText)
        }
    }
}

// ── 2 · Alerts ──────────────────────────────────────────────────────────────

private data class Alert(
    val severity: Severity,
    val headline: String,
    val detail: String,
    val drill: Drill?,
)

/**
 * Triage. Ordered by [Severity.weight] so a feedback incident always outranks
 * an unallocated batch, regardless of the order the API returned rows in.
 */
private fun buildAlerts(
    highRisk: List<Map<*, *>>,
    overloaded: List<Map<*, *>>,
    demand: List<Map<*, *>>,
    openActions: List<Map<String, Any>>,
    gaps: Int,
): List<Alert> = buildList {
    if (highRisk.isNotEmpty()) add(
        Alert(
            Severity.Critical,
            "${highRisk.size} ${plural(highRisk.size, "trainer is", "trainers are")} at high feedback risk",
            highRisk.take(3).joinToString(" · ") { it.str("trainer_name") },
            drill("High feedback risk", highRisk),
        )
    )
    if (overloaded.isNotEmpty()) add(
        Alert(
            Severity.Warning,
            "${overloaded.size} ${plural(overloaded.size, "trainer is", "trainers are")} over 85% utilised",
            "Rebalance before assigning new work.",
            drill("Overloaded", overloaded, true),
        )
    )
    if (demand.isNotEmpty()) add(
        Alert(
            Severity.Warning,
            "${demand.size} ${plural(demand.size, "batch has", "batches have")} no owner",
            demand.take(3).joinToString(" · ") { it.str("course_name").ifBlank { it.str("demand_id") } },
            demandDrill(demand),
        )
    )
    if (gaps > 0) add(
        Alert(
            Severity.Watch,
            "$gaps certification ${plural(gaps, "gap", "gaps")} across the team",
            "Gaps block allocation to accredited demand.",
            null,
        )
    )
    if (openActions.isNotEmpty()) add(
        Alert(
            Severity.Info,
            "${openActions.size} open manager ${plural(openActions.size, "action", "actions")}",
            openActions.take(2).joinToString(" · ") { it.str("title").ifBlank { "Manager action" } },
            actionDrill(openActions),
        )
    )
}.sortedBy { it.severity.weight }

// ── 3 · Pulse ───────────────────────────────────────────────────────────────

private data class MiniKpi(
    val label: String, val value: String, val caption: String,
    val tint: Color, val drill: Drill?,
)

@Composable
private fun PulseGrid(items: List<MiniKpi>, onDrill: (Drill) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        items.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                row.forEach { item -> key(item.label) { PulseTile(item, Modifier.weight(1f), onDrill) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PulseTile(item: MiniKpi, modifier: Modifier, onDrill: (Drill) -> Unit) {
    val sk = MaterialTheme.skill
    val target = item.drill
    Column(
        modifier
            .glassSurface(RoundedCornerShape(Radii.kpi))
            .then(if (target != null) Modifier.clickable { onDrill(target) } else Modifier)
            .padding(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(item.tint, RoundedCornerShape(3.dp)))
            Spacer(Modifier.width(Space.sm))
            Text(
                item.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = sk.labelText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            item.value,
            style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
            color = item.tint,
        )
        Text(
            item.caption,
            style = MaterialTheme.typography.bodySmall,
            color = sk.subText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── 6 · Explore ─────────────────────────────────────────────────────────────

/**
 * Reference material, not decisions: trend, top performers, certification
 * coverage, readiness distribution and the action breakdown. Collapsed by
 * default so the briefing above stays a briefing, expandable for the manager
 * who wants the evidence.
 */
@Composable
private fun ExploreSection(
    kpis: Map<*, *>?, capKpis: Map<*, *>?, capabilityLoading: Boolean,
    ops: List<Map<*, *>>, upcoming: List<Map<*, *>>, capTrainers: List<Map<*, *>>,
    gaps: Int, openActions: List<Map<String, Any>>, highRisk: List<Map<*, *>>,
    onDrill: (Drill) -> Unit, onTrainerClick: (String, String) -> Unit,
) {
    val sk = MaterialTheme.skill
    var open by rememberSaveable { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(Radii.card))
            .clickable { open = !open }
            .padding(Space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Explore the detail",
            style = MaterialTheme.typography.titleMedium,
            color = sk.bodyText,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (open) "Hide" else "Show",
            style = MaterialTheme.typography.labelMedium,
            color = sk.sky,
        )
    }

    AnimatedVisibility(open) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {

            val history = (kpis?.get("utilization_history") as? List<*>)
                ?.mapNotNull { (it as? Number)?.toInt() }.orEmpty()
            SectionHeading("Utilisation", "Measured over the available RMS window")
            SkillCard(Modifier.fillMaxWidth()) {
                TrendChart(
                    points = history.mapIndexed { i, v ->
                        TrendPoint(if (i == history.lastIndex) "Now" else "M${i + 1}", v)
                    },
                    tint = utilisationColour(history.lastOrNull(), sk),
                    height = 76.dp,
                )
            }

            TopPerformersPanel(ops, capTrainers, onTrainerClick)

            val coverage = capKpis?.intOrNull("avg_trainer_coverage_pct") ?: kpis?.intOrNull("cert_coverage_pct")
            val ready = capTrainers.count { it.int("readiness_score") >= 75 }
            val developing = capTrainers.count { it.int("readiness_score") in 50..74 }
            val blocked = (capTrainers.size - ready - developing).coerceAtLeast(0)
            SectionHeading(
                "Certification",
                if (capabilityLoading) "Refreshing capability data" else "$gaps ${plural(gaps, "gap requires", "gaps require")} follow-up",
            )
            SkillCard(Modifier.fillMaxWidth()) {
                val slices = listOf(
                    Slice("Covered", coverage ?: 0, sk.good),
                    Slice("Gap", if (coverage == null) 0 else 100 - coverage, sk.warn),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DonutChart(slices, coverage?.let { "$it%" } ?: "—", "coverage", size = 92.dp)
                    Spacer(Modifier.width(Space.lg))
                    ChartLegend(slices, Modifier.weight(1f))
                }
                BarChart(
                    listOf(
                        BarDatum("Ready", ready, sk.good),
                        BarDatum("Develop", developing, sk.warn),
                        BarDatum("Blocked", blocked, sk.crit),
                    ),
                    height = 92.dp,
                )
            }

            SectionHeading("Upcoming delivery", "Next scheduled commitments")
            SkillCard(Modifier.fillMaxWidth()) {
                if (upcoming.isEmpty()) {
                    Text(
                        "No upcoming deliveries returned by RMS.",
                        style = MaterialTheme.typography.bodySmall, color = sk.subText,
                    )
                } else upcoming.take(5).forEach { b ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onDrill(batchDrill("Upcoming delivery", listOf(b))) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            b.str("start_at").takeLast(5).ifBlank { "TBC" },
                            style = MaterialTheme.typography.labelMedium,
                            color = sk.sky,
                            modifier = Modifier.width(54.dp),
                        )
                        Text(
                            b.str("course_name"),
                            style = MaterialTheme.typography.bodySmall,
                            color = sk.bodyText,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            SectionHeading(
                "Action centre",
                "${openActions.size} open across six management themes",
                trailing = if (highRisk.isEmpty()) null else "${highRisk.size} at risk",
            )
            SkillCard(Modifier.fillMaxWidth()) {
                BarChart(
                    listOf("Certification", "Underutilised", "Overallocated", "Unallocated", "Readiness", "Feedback")
                        .map { category ->
                            BarDatum(
                                category.take(5),
                                openActions.count {
                                    (it.str("category") + " " + it.str("title") + " " + it.str("detail"))
                                        .contains(category, true)
                                },
                                sk.warn,
                            )
                        },
                    height = 100.dp,
                )
                openActions.take(3).forEach { ActionPreview(it) }
                if (openActions.isNotEmpty()) {
                    TextButton(
                        onClick = { onDrill(actionDrill(openActions)) },
                        modifier = Modifier.align(Alignment.End),
                    ) { Text("Review all ${openActions.size}") }
                }
            }
        }
    }
}

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
    SectionHeading("Top performers", "Carrying delivery, ranked by measured utilisation")
    SkillCard(Modifier.fillMaxWidth()) {
        top.forEachIndexed { index, trainer ->
            val util = trainer.int("current_utilization")
            val email = trainer.str("official_email")
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onTrainerClick(email, trainer.str("trainer_name")) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = sk.labelText,
                    modifier = Modifier.width(20.dp),
                )
                Avatar(trainer.str("trainer_name"), capMap[email.lowercase()]?.str("photo_url"), 32.dp)
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        trainer.str("trainer_name"),
                        style = MaterialTheme.typography.titleSmall,
                        color = sk.bodyText,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        trainer.str("capacity_bucket").ifBlank { "Measured delivery load" },
                        style = MaterialTheme.typography.bodySmall,
                        color = sk.subText, maxLines = 1,
                    )
                }
                Text(
                    "$util%",
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    color = utilisationColour(util, sk),
                )
            }
        }
    }
}

@Composable
private fun ActionPreview(a: Map<String, Any>) {
    val sk = MaterialTheme.skill
    val high = a.str("priority").equals("high", true)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(6.dp)
                .background(if (high) sk.crit else sk.warn, RoundedCornerShape(3.dp))
        )
        Spacer(Modifier.width(Space.sm))
        Column(Modifier.weight(1f)) {
            Text(
                a.str("title").ifBlank { "Manager action" },
                style = MaterialTheme.typography.bodyMedium,
                color = sk.bodyText, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                a.str("trainer_name"),
                style = MaterialTheme.typography.bodySmall,
                color = sk.subText, maxLines = 1,
            )
        }
    }
}

private fun drill(title: String, rows: List<Map<*, *>>, util: Boolean = false) = Drill(title, "Tap a trainer for detail", rows.map { DrillRow(it.str("trainer_name").ifBlank { it.str("official_email") }, if (util) "${it.intOrNull("current_utilization")?.let { u -> "$u%" } ?: "not measured"} • ${it.str("capacity_bucket")}" else listOf(it.str("capacity_bucket"), it.str("recommended_action")).filter { s -> s.isNotBlank() }.joinToString(" • "), it.str("official_email").ifBlank { it.str("trainer_email") }) })
private fun batchDrill(title: String, rows: List<Map<*, *>>) = Drill(title, "Delivery schedule", rows.map { DrillRow(it.str("course_name").ifBlank { "Unnamed course" }, listOf(it.str("trainer_name"), it.str("delivery_mode"), it.str("start_at")).filter(String::isNotBlank).joinToString(" • ")) })
private fun demandDrill(rows: List<Map<*, *>>) = Drill("Unallocated demand", "Batches waiting for a suitable owner", rows.map { DrillRow(it.str("course_name").ifBlank { it.str("demand_id") }, listOf(it.str("delivery_mode"), it.str("location"), it.str("start_date")).filter(String::isNotBlank).joinToString(" • ")) })
private fun actionDrill(rows: List<Map<String, Any>>) = Drill("Action centre", "Open decisions requiring manager attention", rows.map { DrillRow(it.str("title").ifBlank { "Manager action" }, listOf(it.str("trainer_name"), it.str("category"), it.str("priority")).filter(String::isNotBlank).joinToString(" • "), it.str("trainer_email").takeIf(String::isNotBlank)) })
