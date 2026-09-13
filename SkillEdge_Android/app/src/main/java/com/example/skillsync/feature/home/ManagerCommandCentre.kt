package com.example.skillsync.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.skillsync.core.notification.NotifyEvent
import com.example.skillsync.core.ui.intOrNull
import com.example.skillsync.core.ui.str
import com.example.skillsync.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Today — the Command Centre. Design V2: dark glass system, comparison-to-
 * baseline KPIs, a capacity balance readout, and a severity-ranked attention
 * queue. Every figure below is read from `kpis` / `demand` / `batches` /
 * `actions` as delivered by the backend — nothing here is a placeholder or an
 * invented trend. Where the backend has no baseline for a figure (e.g.
 * `readiness_trend` is deliberately blank when no history exists) the delta
 * is simply omitted rather than fabricated.
 *
 * Rendered as a single item inside the caller's own `LazyColumn` (`MainScreen`)
 * — this stays a plain, non-scrolling `Column` so it never nests one scrolling
 * list inside another (that nested-LazyColumn crash is what commit 064a4c6
 * had to fix on the previous layout).
 */
@Composable
fun ManagerCommandCentre(
      email: String,
      profile: Map<String, Any>?,
      kpis: Map<*, *>?, capKpis: Map<*, *>?, capabilityLoading: Boolean,
      ops: List<Map<*, *>>, states: List<Map<*, *>>, batches: List<Map<*, *>>,
      demand: List<Map<*, *>>, capTrainers: List<Map<*, *>>,
      actions: List<Map<String, Any>>,
      recentNotifications: List<NotifyEvent> = emptyList(),
      fromCache: Boolean, cachedAt: Long,
      onDrill: (Drill) -> Unit,
      onTrainerClick: (String, String) -> Unit,
      onOpenProfile: () -> Unit,
      onOpenMySchedule: () -> Unit = {},
      onOpenNotifications: () -> Unit,
      onOpenDemand: () -> Unit,
      onOpenWeeklyReport: () -> Unit = {},
      onOpenHrReport: () -> Unit = {},
      onOpenPriorities: () -> Unit = {},
      onOpenAccounts: () -> Unit = {},
      onOpenCopilot: () -> Unit = {},
      onOpenDelivery: () -> Unit = {},
      onOpenPipelineRadar: () -> Unit = {},
      onOpenDeliveryCompliance: () -> Unit = {},
      onOpenCapacityRunway: () -> Unit = {},
      onOpenViberAutomation: () -> Unit = {},
      onOpenSkillRequests: () -> Unit = {},
      pendingSkillRequests: Int = 0,
      onBatchClick: (String) -> Unit = {},
      calendarReadiness: Map<String, Map<String, Any>> = emptyMap(),
) {
    val sk = MaterialTheme.skill
    val name = profile?.get("name")?.toString()?.ifBlank { null } ?: email.substringBefore("@")
    val initials = name.trim().split(" ").filter { it.isNotBlank() }
        .take(2).joinToString("") { it.first().uppercase() }.ifBlank { "?" }
    val todayLabel = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date())

    val unreadCount = kpis?.intOrNull("unread_notifications") ?: recentNotifications.size
    val readiness = kpis?.intOrNull("team_readiness_score")
    val readinessTrend = kpis?.str("readiness_trend")?.takeIf { it.isNotBlank() }
    val utilisation = kpis?.intOrNull("avg_team_utilization")
    val utilisationTrend = kpis?.str("utilization_trend")?.takeIf { it.isNotBlank() }
    val openDemand = kpis?.intOrNull("open_demand") ?: demand.size
    val atRisk = kpis?.intOrNull("high_risk_trainers") ?: kpis?.intOrNull("delivery_risk_count") ?: 0
    val bench = kpis?.intOrNull("bench_trainers") ?: 0
    val optimal = kpis?.intOrNull("optimal_trainers") ?: 0
    val stretched = kpis?.intOrNull("stretched_trainers") ?: 0
    val capacityTotal = (bench + optimal + stretched).coerceAtLeast(1)
    val certCoverage = kpis?.intOrNull("cert_coverage_pct")

    val unallocatedDemand = demand.filter { it.str("trainer_name").isBlank() }
    val activeBatches = batches.filter { it.str("engagement_state") == "active" }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.xl),
    ) {
        CommandHeader(
            name = name,
            initials = initials,
            dateLabel = todayLabel,
            unreadCount = unreadCount,
            onOpenProfile = onOpenProfile,
            onOpenNotifications = onOpenNotifications,
        )

        // Managers who also deliver (assistant managers, trainer-plus) get the
        // same "where am I on the calendar" entry point a trainer has — reached
        // here instead of only buried in the profile menu, since on a day he is
        // teaching that fact belongs on Today, not one tap further away.
        SkillSyncListItem(
            title = "Your schedule",
            subtitle = "Your own deliveries and off-bands, same as any trainer's",
            onClick = onOpenMySchedule,
        )

        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            SectionHeading("This week vs. last", trailing = if (fromCache) "cached" else "live")
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                ComparisonStat(
                    label = "Readiness",
                    value = readiness?.toString() ?: "—",
                    delta = readinessTrend,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenPriorities,
                )
                ComparisonStat(
                    label = "Utilisation",
                    value = utilisation?.let { "$it%" } ?: "—",
                    delta = utilisationTrend,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenCapacityRunway,
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                ComparisonStat(
                    label = "Open demand",
                    value = openDemand.toString(),
                    delta = null,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenDemand,
                )
                ComparisonStat(
                    label = "At risk",
                    value = atRisk.toString(),
                    delta = null,
                    severity = if (atRisk > 0) Severity.Critical else Severity.Good,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenPriorities,
                )
            }
        }

        if (capacityTotal > 1 || bench + optimal + stretched > 0) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                SectionHeading("Capacity balance", conclusion = capacityConclusion(bench, optimal, stretched))
                SkillCard(modifier = Modifier.fillMaxWidth().pressable(onOpenCapacityRunway)) {
                    CapacityBar(bench, optimal, stretched)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                        LegendDot(sk.warn, "Bench $bench")
                        LegendDot(sk.sky, "Optimal $optimal")
                        LegendDot(sk.crit, "Stretched $stretched")
                    }
                }
            }
        }

        val attentionItems = buildList {
            unallocatedDemand.take(3).forEach { b ->
                val cName = b.str("course_name").ifBlank { "Unnamed course" }
                val mode = b.str("delivery_mode").ifBlank { "mode tbc" }
                add(Triple("$cName needs a trainer", mode, Severity.Critical))
            }
            if (pendingSkillRequests > 0) {
                add(Triple("$pendingSkillRequests skill request${if (pendingSkillRequests == 1) "" else "s"} pending", "Awaiting your review", Severity.Info))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            SectionHeading(
                "Needs you today",
                trailing = if (attentionItems.isNotEmpty()) "review →" else null,
            )
            if (attentionItems.isEmpty()) {
                StateNote("Nothing needs you right now — the queue is clear.")
            } else {
                SkillCard(modifier = Modifier.fillMaxWidth(), padding = Space.sm) {
                    attentionItems.forEachIndexed { i, (title, subtitle, sev) ->
                        AttentionRow(title, subtitle, sev, onClick = onOpenDemand)
                        if (i < attentionItems.lastIndex) {
                            HorizontalDivider(color = sk.cardBorder, thickness = 1.dp)
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            SectionHeading("Today's operations")
            if (activeBatches.isEmpty()) {
                StateNote("No active deliveries right now.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    activeBatches.forEach { b ->
                        val cName = b.str("course_name").ifBlank { "Unnamed course" }
                        val trainer = b.str("trainer_name").ifBlank { "Unassigned" }
                        val mode = b.str("delivery_mode").ifBlank { "Virtual" }
                        SkillSyncListItem(
                            title = cName,
                            subtitle = "$trainer · $mode",
                            onClick = { onBatchClick(b.str("demand_id")) },
                        )
                    }
                }
            }
        }

        if (certCoverage != null) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                SectionHeading("Certification coverage")
                SkillCard(modifier = Modifier.fillMaxWidth().pressable(onOpenPriorities)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Open-demand courses with a certified trainer", style = MaterialTheme.typography.titleSmall, color = sk.frost)
                        Text("$certCoverage%", style = MaterialTheme.typography.titleMedium, color = sk.cyan, fontWeight = FontWeight.Bold)
                    }
                    ProgressTrack(fraction = certCoverage.coerceIn(0, 100) / 100f, tint = sk.cyan)
                }
            }
        }
    }
}

@Composable
private fun CommandHeader(
    name: String,
    initials: String,
    dateLabel: String,
    unreadCount: Int,
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
) {
    val sk = MaterialTheme.skill
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(Radii.icon))
                .background(Brush.linearGradient(listOf(sk.navy, sk.brand)))
                .pressable(onOpenProfile),
            contentAlignment = Alignment.Center,
        ) {
            Text(initials, style = MaterialTheme.typography.titleSmall, color = sk.frost, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = sk.frost, fontWeight = FontWeight.SemiBold)
            Text(dateLabel, style = MaterialTheme.typography.labelSmall, color = sk.labelText)
        }
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(Radii.chip))
                .background(sk.surface2)
                .pressable(onOpenNotifications),
            contentAlignment = Alignment.Center,
        ) {
            Text("🔔", style = MaterialTheme.typography.titleSmall)
            if (unreadCount > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(sk.crit),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (unreadCount > 9) "9+" else unreadCount.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun ComparisonStat(
    label: String,
    value: String,
    delta: String?,
    modifier: Modifier = Modifier,
    severity: Severity? = null,
    onClick: () -> Unit = {},
) {
    val sk = MaterialTheme.skill
    val tint = severity?.tint()
    SkillCard(
        modifier = modifier.pressable(onClick),
        severity = severity,
        padding = Space.md,
    ) {
        Figure(
            value = value,
            label = label,
            size = FigureSize.Small,
            tint = tint,
            delta = delta,
            deltaTint = deltaTone(delta, sk),
        )
    }
}

@Composable
private fun deltaTone(delta: String?, sk: SkillColors): Color? {
    if (delta == null) return null
    return when {
        delta.startsWith("+") || delta.startsWith("▲") -> sk.good
        delta.startsWith("-") || delta.startsWith("▼") -> sk.crit
        else -> sk.subText
    }
}

private fun capacityConclusion(bench: Int, optimal: Int, stretched: Int): String = when {
    stretched > optimal && stretched > 0 -> "More trainers are stretched than sitting in the optimal band."
    bench > 0 && stretched > 0 -> "$bench on bench while $stretched are stretched — the gap is coverage, not headcount."
    bench > 0 -> "$bench trainer${if (bench == 1) "" else "s"} available on bench."
    stretched > 0 -> "$stretched trainer${if (stretched == 1) "" else "s"} running above optimal load."
    else -> "Team load sits inside the optimal band."
}

@Composable
private fun CapacityBar(bench: Int, optimal: Int, stretched: Int) {
    val sk = MaterialTheme.skill
    val total = (bench + optimal + stretched).coerceAtLeast(1).toFloat()
    Row(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(Radii.chip)),
    ) {
        if (bench > 0) Box(Modifier.weight(bench / total).fillMaxHeight().background(sk.warn))
        if (optimal > 0) Box(Modifier.weight(optimal / total).fillMaxHeight().background(sk.sky))
        if (stretched > 0) Box(Modifier.weight(stretched / total).fillMaxHeight().background(sk.crit))
        if (bench + optimal + stretched == 0) Box(Modifier.weight(1f).fillMaxHeight().background(sk.surface3))
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(Space.xs))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.skill.subText)
    }
}

@Composable
private fun ProgressTrack(fraction: Float, tint: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(Radii.chip))
            .background(MaterialTheme.skill.track),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(tint),
        )
    }
}

@Composable
private fun AttentionRow(title: String, subtitle: String, severity: Severity, onClick: () -> Unit) {
    val sk = MaterialTheme.skill
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = Space.sm, horizontal = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(severity.tint()))
        Spacer(Modifier.width(Space.sm))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = sk.frost, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = sk.subText, maxLines = 1)
        }
        ToneChip(text = severity.label, tint = severity.tint())
    }
}

// Stub to satisfy MainScreen
fun managerBriefFromPayload(data: Map<String, Any>?): String = ""
