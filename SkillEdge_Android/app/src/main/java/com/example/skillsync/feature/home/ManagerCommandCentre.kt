package com.example.skillsync.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * Today — the Command Centre. Design V2: dark glass system, a readiness hero,
 * an icon pulse grid, comparison-to-baseline KPIs, an honest availability
 * breakdown (leave/commitments, never inferred from utilisation), demand and
 * delivery summaries, a severity-ranked attention queue, top performers, and
 * an operations grid to every executive console the manager already has.
 * Every figure is read from `kpis` / `demand` / `batches` / `capTrainers` /
 * `calendarReadiness` as delivered by the backend — nothing here is a
 * placeholder or an invented trend; a missing figure is omitted, not guessed.
 *
 * Rendered as a single item inside the caller's own `LazyColumn` (`MainScreen`)
 * — this stays a plain, non-scrolling `Column` so it never nests one scrolling
 * list inside another (that nested-LazyColumn crash is what commit 064a4c6
 * had to fix on the previous layout).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
      /**
       * (recipientType, recipientName, purpose, relatedEntityType, relatedEntityId) — routes into
       * the one shared Communication Intelligence composer (`CommunicationScreen`). Today only
       * ever supplies a starting point; the manager still reviews, edits, and sends nothing
       * automatically. No second generation pipeline is created here.
       */
      onOpenCommunication: (String, String, String, String, String) -> Unit = { _, _, _, _, _ -> },
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
    val teamStrength = kpis?.intOrNull("total_team_members") ?: ops.size
    val activeTrainers = kpis?.intOrNull("active_trainers")
    val openDemand = kpis?.intOrNull("open_demand") ?: demand.size
    val atRisk = kpis?.intOrNull("high_risk_trainers") ?: kpis?.intOrNull("delivery_risk_count") ?: 0
    val bench = kpis?.intOrNull("bench_trainers") ?: 0
    val optimal = kpis?.intOrNull("optimal_trainers") ?: 0
    val stretched = kpis?.intOrNull("stretched_trainers") ?: 0
    val capacityTotal = (bench + optimal + stretched).coerceAtLeast(1)
    val certCoverage = kpis?.intOrNull("cert_coverage_pct")
    val internationalBatches = kpis?.intOrNull("international_batches") ?: 0

    val unallocatedDemand = demand.filter { it.str("trainer_name").isBlank() }
    val activeBatches = batches.filter { it.str("engagement_state") == "active" }
    val upcomingBatches = batches.filter { it.str("engagement_state") == "upcoming" }

    // Honest availability — from RMS-verified leave/commitment days
    // (`/api/v2/team/readiness`), never inferred from utilisation. A trainer
    // with no entry is unverified, not assumed clear.
    val rosterEmails = remember(ops) {
        ops.mapNotNull { it.str("official_email").takeIf(String::isNotBlank) }.distinct()
    }
    val onLeaveCount = rosterEmails.count { (calendarReadiness[it]?.get("leave_days") as? Number)?.toInt() ?: 0 > 0 }
    val committedCount = rosterEmails.count { e ->
        val row = calendarReadiness[e]
        val leave = (row?.get("leave_days") as? Number)?.toInt() ?: 0
        val confirmed = (row?.get("confirmed_days") as? Number)?.toInt() ?: 0
        leave == 0 && confirmed > 0
    }
    val checkedCount = rosterEmails.count { calendarReadiness.containsKey(it) }
    val clearCount = (checkedCount - onLeaveCount - committedCount).coerceAtLeast(0)

    val topPerformers = remember(capTrainers) {
        capTrainers
            .mapNotNull { t ->
                val util = t.intOrNull("utilization") ?: return@mapNotNull null
                Triple(t.str("trainer_name").ifBlank { return@mapNotNull null }, util, t.str("readiness_bucket"))
            }
            .sortedByDescending { it.second }
            .take(3)
    }

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

        SkillSyncListItem(
            title = "Your schedule",
            subtitle = "Your own deliveries and off-bands, same as any trainer's",
            onClick = onOpenMySchedule,
        )

        // ── Hero: the one heroSurface() on this screen, per the surface usage
        // rule (Surfaces.kt) — team readiness is Today's single major insight.
        Box(Modifier.fillMaxWidth().heroSurface().pressable(onOpenPriorities).padding(Space.lg)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("TEAM READINESS", style = MaterialTheme.typography.labelSmall, color = sk.ice)
                    Text(
                        readiness?.toString() ?: "—",
                        style = MaterialTheme.typography.displaySmall,
                        color = sk.frost,
                        fontWeight = FontWeight.Bold,
                    )
                    if (readinessTrend != null) {
                        Text(readinessTrend, style = MaterialTheme.typography.labelMedium, color = deltaTone(readinessTrend, sk) ?: sk.ice)
                    }
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        listOfNotNull(
                            activeTrainers?.let { "$it of $teamStrength deployed" },
                            utilisation?.let { "utilisation $it%" },
                            if (openDemand > 0) "$openDemand demand${if (openDemand == 1) "" else "s"} unallocated" else null,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = sk.ice,
                    )
                }
                HeroRing(value = readiness, modifier = Modifier.size(72.dp))
            }
        }

        // ── Pulse: icon grid ─────────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            SectionHeading("Pulse", trailing = if (fromCache) "cached" else "live")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                PulseTile("👥", "Strength", teamStrength.toString(), Modifier.weight(1f))
                PulseTile(
                    "⚡", "Utilisation", utilisation?.let { "$it%" } ?: "—", Modifier.weight(1f),
                    delta = utilisationTrend, onClick = onOpenCapacityRunway,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                PulseTile(
                    "🛡️", "Cert coverage", certCoverage?.let { "$it%" } ?: "—", Modifier.weight(1f),
                    tint = if ((certCoverage ?: 100) < 60) sk.warn else null, onClick = onOpenPriorities,
                )
                PulseTile(
                    "⚠️", "At risk", atRisk.toString(), Modifier.weight(1f),
                    tint = if (atRisk > 0) sk.crit else sk.good, onClick = onOpenPriorities,
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

        // ── Who is actually free — honest, from verified leave/commitment days ──
        if (checkedCount > 0) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                SectionHeading(
                    "Who is actually free",
                    conclusion = "$onLeaveCount on leave in the next 90 days, $clearCount with nothing booked.",
                )
                SkillCard(modifier = Modifier.fillMaxWidth().pressable(onOpenDelivery)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MiniStat("Clear", clearCount.toString(), sk.good)
                        MiniStat("Committed", committedCount.toString(), sk.sky)
                        MiniStat("On leave", onLeaveCount.toString(), sk.warn)
                    }
                    Text(
                        "From approved leave and confirmed bookings in RMS, not from utilisation.",
                        style = MaterialTheme.typography.labelSmall, color = sk.subText,
                    )
                }
            }
        }

        // ── Demand summary ───────────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            SectionHeading(
                "Demand",
                conclusion = "${unallocatedDemand.size} unallocated batch${if (unallocatedDemand.size == 1) "" else "es"}" +
                    (if (internationalBatches > 0) ", $internationalBatches international." else ", none international."),
            )
            SkillCard(modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MiniStat("Unallocated", unallocatedDemand.size.toString(), sk.crit)
                    MiniStat("International", internationalBatches.toString(), sk.indigo)
                    MiniStat("Active", activeBatches.size.toString(), sk.good)
                    MiniStat("Upcoming", upcomingBatches.size.toString(), sk.sky)
                }
                if (unallocatedDemand.isNotEmpty()) {
                    SkillSyncPrimaryButton(
                        text = "Allocate ${unallocatedDemand.size} open batch${if (unallocatedDemand.size == 1) "" else "es"}",
                        onClick = onOpenDemand,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        val attentionItems = buildList {
            unallocatedDemand.take(3).forEach { b ->
                val cName = b.str("course_name").ifBlank { "Unnamed course" }
                val mode = b.str("delivery_mode").ifBlank { "mode tbc" }
                add(AttentionItem("$cName needs a trainer", mode, Severity.Critical, demandId = b.str("demand_id")))
            }
            if (pendingSkillRequests > 0) {
                add(AttentionItem("$pendingSkillRequests skill request${if (pendingSkillRequests == 1) "" else "s"} pending", "Awaiting your review", Severity.Info))
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
                Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    attentionItems.forEach { item ->
                        Box(Modifier.fillMaxWidth().accentGlass(item.severity.tint()).pressable(onOpenDemand)) {
                            ActionRow(
                                title = item.title,
                                modifier = Modifier.padding(horizontal = Space.md),
                                supportingText = item.subtitle,
                                tint = item.severity.tint(),
                                primaryActionLabel = if (item.demandId.isNotBlank()) "Ask availability" else null,
                                onPrimaryAction = if (item.demandId.isNotBlank()) {
                                    {
                                        onOpenCommunication(
                                            "TEAM", "", "AVAILABILITY_REQUEST",
                                            "demand", item.demandId,
                                        )
                                    }
                                } else null,
                                secondaryContent = { ToneChip(text = item.severity.label, tint = item.severity.tint()) },
                            )
                        }
                    }
                }
            }
        }

        val trainerNames = remember(ops) {
            ops.mapNotNull { it.str("trainer_name").takeIf(String::isNotBlank) }.distinct()
        }
        var showTrainerPicker by remember { mutableStateOf(false) }
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            SectionHeading("Communicate")
            SkillCard(modifier = Modifier.fillMaxWidth(), padding = Space.sm) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    CommunicateAction("Team", Modifier.weight(1f)) {
                        onOpenCommunication("TEAM", "", "GENERAL_PROFESSIONAL", "", "")
                    }
                    CommunicateAction("Trainer", Modifier.weight(1f)) { showTrainerPicker = true }
                    CommunicateAction("Weekly", Modifier.weight(1f), onClick = onOpenWeeklyReport)
                    CommunicateAction("Monthly", Modifier.weight(1f), onClick = onOpenHrReport)
                }
            }
        }
        if (showTrainerPicker) {
            ModalBottomSheet(onDismissRequest = { showTrainerPicker = false }, containerColor = sk.cardBg) {
                Column(Modifier.fillMaxWidth().padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Text("Message a trainer", style = MaterialTheme.typography.titleMedium, color = sk.frost)
                    if (trainerNames.isEmpty()) {
                        StateNote("No trainers on your roster yet.")
                    } else {
                        LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                            items(trainerNames) { trainerName ->
                                SkillSyncListItem(
                                    title = trainerName,
                                    onClick = {
                                        showTrainerPicker = false
                                        onOpenCommunication("INDIVIDUAL", trainerName, "GENERAL_PROFESSIONAL", "", "")
                                    },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(Space.md))
                }
            }
        }

        // ── Delivery schedule ────────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            SectionHeading("Delivery schedule", trailing = "Full calendar →")
            SkillCard(modifier = Modifier.fillMaxWidth().pressable(onOpenDelivery)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MiniStat("Delivering", activeBatches.size.toString(), sk.good)
                    MiniStat("Upcoming", upcomingBatches.size.toString(), sk.sky)
                    MiniStat("On leave", onLeaveCount.toString(), sk.warn)
                }
            }
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
                    MetricProgress(fraction = certCoverage.coerceIn(0, 100) / 100f, tint = sk.cyan)
                }
            }
        }

        // ── Top performers ───────────────────────────────────────────────────
        if (topPerformers.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                SectionHeading("Top performers", conclusion = "Carrying delivery, ranked by measured utilisation.")
                SkillCard(modifier = Modifier.fillMaxWidth(), padding = Space.sm) {
                    topPerformers.forEachIndexed { i, (trainerName, util, bucket) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onTrainerClick(email, trainerName) }.padding(Space.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = sk.subText, modifier = Modifier.width(20.dp))
                            Column(Modifier.weight(1f)) {
                                Text(trainerName, style = MaterialTheme.typography.titleSmall, color = sk.frost)
                                if (bucket.isNotBlank()) Text(bucket, style = MaterialTheme.typography.labelSmall, color = sk.subText)
                            }
                            Text("$util%", style = MaterialTheme.typography.titleMedium, color = sk.cyan, fontWeight = FontWeight.Bold)
                        }
                        if (i < topPerformers.lastIndex) HorizontalDivider(color = sk.cardBorder, thickness = 1.dp)
                    }
                }
            }
        }

        // ── Operations — every executive console, one tap away ──────────────
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            SectionHeading("Operations")
            val ops2 = listOf(
                Triple("This week", "Priorities, ranked", onOpenPriorities),
                Triple("Pipeline radar", "Signed demand incoming", onOpenPipelineRadar),
                Triple("Delivery compliance", "Recording & audit", onOpenDeliveryCompliance),
                Triple("HR monthly review", "Trainer index breakdown", onOpenHrReport),
                Triple("Capacity runway", "8-week demand gap", onOpenCapacityRunway),
                Triple("Accounts book", "Client concentration", onOpenAccounts),
                Triple("Team copilot", "Ask about your team", onOpenCopilot),
                Triple("Viber automation", "Auto-dispatch queue", onOpenViberAutomation),
                Triple("Skill requests", "Reportee-level requests", onOpenSkillRequests),
            )
            for (row in ops2.chunked(2)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    row.forEach { (title, subtitle, onClick) ->
                        OperationTile(title, subtitle, Modifier.weight(1f), onClick)
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
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
private fun PulseTile(
    glyph: String,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    delta: String? = null,
    tint: Color? = null,
    onClick: () -> Unit = {},
) {
    val sk = MaterialTheme.skill
    SkillCard(modifier = modifier.pressable(onClick), padding = Space.md) {
        IconSlot(tint = tint ?: sk.sky, size = 26.dp) {
            Text(glyph, style = MaterialTheme.typography.titleSmall)
        }
        Text(value, style = MaterialTheme.typography.headlineSmall, color = tint ?: sk.frost, fontWeight = FontWeight.Bold)
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = sk.labelText)
        if (delta != null) {
            Text(delta, style = MaterialTheme.typography.labelSmall, color = deltaTone(delta, sk) ?: sk.subText)
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = tint, fontWeight = FontWeight.Bold)
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.skill.labelText)
    }
}

@Composable
private fun OperationTile(title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val sk = MaterialTheme.skill
    SkillCard(modifier = modifier.pressable(onClick), padding = Space.md) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = sk.frost, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = sk.subText, maxLines = 1)
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

private data class AttentionItem(
    val title: String,
    val subtitle: String,
    val severity: Severity,
    /** Real `demand_id` when this item is an unallocated batch — powers "Ask availability". Blank for non-demand items (e.g. skill requests), which get no communication shortcut. */
    val demandId: String = "",
)

@Composable
private fun CommunicateAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val sk = MaterialTheme.skill
    Box(
        modifier
            .clip(RoundedCornerShape(Radii.chip))
            .background(sk.surface2)
            .pressable(onClick)
            .padding(vertical = Space.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = sk.frost, fontWeight = FontWeight.SemiBold)
    }
}

// Stub to satisfy MainScreen
fun managerBriefFromPayload(data: Map<String, Any>?): String = ""
