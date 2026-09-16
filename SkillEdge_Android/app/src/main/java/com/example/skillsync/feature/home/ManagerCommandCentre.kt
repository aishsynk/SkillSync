package com.example.skillsync.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.skillsync.R
import com.example.skillsync.core.notification.NotifyEvent
import com.example.skillsync.core.ui.Avatar
import com.example.skillsync.core.ui.intOrNull
import com.example.skillsync.core.ui.str
import com.example.skillsync.feature.communication.engine.CommunicationPlanner
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
    val todayLabel = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date())

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
    // Backend truth is "current" (backend.py _engagement_state), never "active" —
    // this filter previously read "active" and so always matched zero rows.
    val activeBatches = batches.filter { it.str("engagement_state") == "current" }
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
                TopPerformer(
                    t.str("trainer_name").ifBlank { return@mapNotNull null },
                    util, t.str("readiness_bucket"), t.str("trainer_email"),
                    t.str("photo_url").takeIf { it.isNotBlank() },
                )
            }
            .sortedByDescending { it.utilization }
            .take(3)
    }

    val attentionItems = buildList {
        unallocatedDemand.take(3).forEach { b ->
            val cName = b.str("course_name").ifBlank { "Unnamed course" }
            val mode = b.str("delivery_mode").ifBlank { "mode tbc" }
            val demandId = b.str("demand_id")
            // Real, skill-matched candidates from the backend (see
            // backend.py _match_trainers_for_demand) — never an
            // aggregate headcount. planUnallocatedDemand picks the
            // first resolved plan; when multiple candidates are
            // equally eligible this still names one real person
            // rather than broadcasting to the whole team.
            val candidates = (b["matching_trainers"] as? List<*>)
                .orEmpty()
                .filterIsInstance<Map<*, *>>()
                .map { m ->
                    CommunicationPlanner.CandidateTrainer(
                        name = m["name"]?.toString().orEmpty(),
                        email = m["email"]?.toString().orEmpty(),
                        capabilityMatch = m["capability_match"] == true,
                        availability = when (m["availability"]?.toString()) {
                            "AVAILABLE" -> CommunicationPlanner.AvailabilityState.AVAILABLE
                            "COMMITTED" -> CommunicationPlanner.AvailabilityState.COMMITTED
                            else -> CommunicationPlanner.AvailabilityState.UNKNOWN
                        },
                    )
                }
            val plan = CommunicationPlanner.planUnallocatedDemand(
                CommunicationPlanner.DemandFact(demandId = demandId, course = cName, deliveryMode = mode),
                candidates,
            ).firstOrNull()
            add(
                AttentionItem(
                    "$cName needs a trainer", mode, Severity.Critical, demandId = demandId,
                    recipientType = plan?.recipientType ?: "TEAM",
                    recipientName = plan?.recipientName.orEmpty(),
                ),
            )
        }
        if (pendingSkillRequests > 0) {
            add(AttentionItem("$pendingSkillRequests skill request${if (pendingSkillRequests == 1) "" else "s"} pending", "Awaiting your review", Severity.Info))
        }
    }

    val trainerNames = remember(ops) {
        ops.mapNotNull { it.str("trainer_name").takeIf(String::isNotBlank) }.distinct()
    }
    var showTrainerPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        // ════════════════════════════════════════════════════════════════════
        // 1. MANAGER BRIEF HERO — identity, readiness, three headline figures
        //    and the schedule entry, consolidated into one flagship panel.
        // ════════════════════════════════════════════════════════════════════
        Column(Modifier.fillMaxWidth().heroSurface()) {
            Row(
                Modifier.fillMaxWidth().pressable(onOpenProfile).padding(start = Space.lg, end = Space.lg, top = Space.lg, bottom = Space.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(name = name, photoUrl = profile?.str("photo_url")?.takeIf { it.isNotBlank() }, size = 46.dp)
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        "MANAGER BRIEF",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.08.em),
                        color = sk.cyan, fontWeight = FontWeight.Bold,
                    )
                    Text(name, style = MaterialTheme.typography.titleLarge, color = sk.frost, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // The date gets its own line so it is never truncated.
                    Text(todayLabel, style = MaterialTheme.typography.bodySmall, color = sk.ice)
                }
                StatusPill(if (fromCache) "CACHED" else "LIVE", if (fromCache) sk.warn else sk.good)
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
            Row(
                Modifier.fillMaxWidth().pressable(onOpenPriorities).padding(horizontal = Space.lg, vertical = Space.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("TEAM READINESS", style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.1.em), color = sk.ice, fontWeight = FontWeight.Bold)
                    Text(
                        readiness?.toString() ?: "—",
                        style = MaterialTheme.typography.displayMedium,
                        color = sk.frost, fontWeight = FontWeight.Black,
                    )
                    if (readinessTrend != null) {
                        Text(readinessTrend, style = MaterialTheme.typography.labelLarge, color = deltaTone(readinessTrend, sk) ?: sk.ice, fontWeight = FontWeight.SemiBold)
                    }
                }
                HeroRing(value = readiness, modifier = Modifier.size(96.dp))
            }
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = Space.lg),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                HeroStat(
                    "Deployed",
                    activeTrainers?.let { "$it/$teamStrength" } ?: teamStrength.toString(),
                    activeTrainers?.let { it.toFloat() / teamStrength.coerceAtLeast(1) },
                    sk.sky, Modifier.weight(1f),
                )
                HeroStat("Utilised", utilisation?.let { "$it%" } ?: "—", utilisation?.let { it / 100f }, sk.cyan, Modifier.weight(1f))
                HeroStat("To staff", openDemand.toString(), null, if (openDemand > 0) sk.warn else sk.good, Modifier.weight(1f))
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(Space.lg)
                    .clip(RoundedCornerShape(Radii.chip))
                    .background(Color.White.copy(alpha = 0.07f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(Radii.chip))
                    .pressable(onOpenMySchedule)
                    .padding(horizontal = Space.md, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_calendar), contentDescription = null, tint = sk.sky, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Space.sm))
                Column(Modifier.weight(1f)) {
                    Text("Your schedule", style = MaterialTheme.typography.titleSmall, color = sk.frost, fontWeight = FontWeight.SemiBold)
                    Text("Your own deliveries and off-bands", style = MaterialTheme.typography.labelSmall, color = sk.ice)
                }
                Icon(painterResource(R.drawable.ic_chevron), contentDescription = null, tint = sk.ice, modifier = Modifier.size(16.dp))
            }
        }

        // ════════════════════════════════════════════════════════════════════
        // 2. MORNING NOTE — weekday team greeting via Communication Intelligence.
        // ════════════════════════════════════════════════════════════════════
        MorningNoteCard(email = email)

        // ════════════════════════════════════════════════════════════════════
        // 3. NEEDS YOU TODAY — level-1 container, compact priority rows.
        // ════════════════════════════════════════════════════════════════════
        TodayPanel(
            title = "Needs you today",
            icon = R.drawable.ic_flag,
            tint = if (attentionItems.any { it.severity == Severity.Critical }) sk.crit else sk.good,
            badge = attentionItems.size.takeIf { it > 0 }?.toString(),
            contentSpacing = Space.sm,
            emphasis = true,
        ) {
            if (attentionItems.isEmpty()) {
                EmptyNote(R.drawable.ic_check, sk.good, "All clear", "Nothing needs you right now — the queue is clear.")
            } else {
                attentionItems.forEach { item ->
                    AttentionCard(
                        item = item,
                        onOpenDemand = onOpenDemand,
                        onAskAvailability = if (item.demandId.isNotBlank()) {
                            {
                                onOpenCommunication(
                                    item.recipientType, item.recipientName, "AVAILABILITY_REQUEST",
                                    "demand", item.demandId,
                                )
                            }
                        } else null,
                    )
                }
            }
        }

        // ════════════════════════════════════════════════════════════════════
        // 3. PULSE — four KPI widgets, each with a real current-value bar.
        // ════════════════════════════════════════════════════════════════════
        TodayPanel(
            title = "Pulse",
            icon = R.drawable.ic_trend,
            tint = sk.cyan,
            trailing = if (fromCache) "cached" else "live",
            contentSpacing = Space.sm,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                PulseTile(
                    R.drawable.ic_people, "Strength", teamStrength.toString(), Modifier.weight(1f),
                    iconTint = sk.royal,
                    fraction = activeTrainers?.let { it.toFloat() / teamStrength.coerceAtLeast(1) },
                    caption = activeTrainers?.let { "$it active / $teamStrength trainers" },
                    onClick = {
                        onDrill(
                            Drill(
                                "Team strength", "Your roster",
                                ops.map {
                                    DrillRow(
                                        it.str("trainer_name").ifBlank { it.str("official_email") },
                                        listOfNotNull(
                                            it.intOrNull("current_utilization")?.let { u -> "$u% utilised" },
                                            it.str("capacity_bucket").takeIf(String::isNotBlank),
                                        ).joinToString(" · ").ifBlank { "Not yet measured" },
                                        it.str("official_email").takeIf(String::isNotBlank),
                                    )
                                },
                            ),
                        )
                    },
                )
                PulseTile(
                    R.drawable.ic_trend, "Utilisation", utilisation?.let { "$it%" } ?: "—", Modifier.weight(1f),
                    iconTint = sk.cyan,
                    fraction = utilisation?.let { it / 100f },
                    caption = utilisationTrend, captionTint = deltaTone(utilisationTrend, sk),
                    onClick = onOpenCapacityRunway,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                PulseTile(
                    R.drawable.ic_certificate, "Cert coverage", certCoverage?.let { "$it%" } ?: "—", Modifier.weight(1f),
                    iconTint = sk.violet,
                    fraction = certCoverage?.let { it / 100f },
                    // No certification target is defined in the backend, so none is claimed here.
                    caption = certCoverage?.let { "courses covered" },
                    onClick = onOpenPriorities,
                )
                PulseTile(
                    R.drawable.ic_alert, "At risk", atRisk.toString(), Modifier.weight(1f),
                    tint = if (atRisk > 0) sk.crit else sk.good,
                    iconTint = if (atRisk > 0) sk.crit else sk.good,
                    fraction = atRisk.toFloat() / teamStrength.coerceAtLeast(1),
                    caption = "of $teamStrength trainers",
                    onClick = onOpenPriorities,
                )
            }

            // Capacity bands and verified availability sit inside Pulse as
            // analytical sub-rows rather than as two more stand-alone cards.
            if (capacityTotal > 1 || bench + optimal + stretched > 0) {
                HorizontalDivider(color = sk.cardBorder.copy(alpha = 0.5f))
                Column(Modifier.fillMaxWidth().pressable(onOpenCapacityRunway), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SubHeading("Capacity balance", capacityConclusion(bench, optimal, stretched))
                    val total = (bench + optimal + stretched).coerceAtLeast(1)
                    CapacityBar(bench, optimal, stretched)
                    Row(Modifier.fillMaxWidth()) {
                        BandStat("Bench", bench, total, sk.warn, Modifier.weight(1f))
                        BandStat("Optimal", optimal, total, sk.sky, Modifier.weight(1f))
                        BandStat("Stretched", stretched, total, sk.crit, Modifier.weight(1f))
                    }
                }
            }
            // Who is actually free — honest, from verified leave/commitment days.
            if (checkedCount > 0) {
                HorizontalDivider(color = sk.cardBorder.copy(alpha = 0.5f))
                Column(Modifier.fillMaxWidth().pressable(onOpenDelivery), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SubHeading(
                        "Who is actually free",
                        "$onLeaveCount on leave in the next 90 days, $clearCount with nothing booked. From RMS leave and bookings, not utilisation.",
                    )
                    Row(Modifier.fillMaxWidth()) {
                        BandStat("Clear", clearCount, checkedCount, sk.good, Modifier.weight(1f))
                        BandStat("Committed", committedCount, checkedCount, sk.sky, Modifier.weight(1f))
                        BandStat("On leave", onLeaveCount, checkedCount, sk.warn, Modifier.weight(1f))
                    }
                }
            }
        }

        // ════════════════════════════════════════════════════════════════════
        // 5. DEMAND — the intelligence leads; Allocate is a restrained action.
        // ════════════════════════════════════════════════════════════════════
        TodayPanel(
            title = "Demand",
            icon = R.drawable.ic_book,
            tint = if (unallocatedDemand.isNotEmpty()) sk.crit else sk.brand,
            conclusion = "${unallocatedDemand.size} unallocated batch${if (unallocatedDemand.size == 1) "" else "es"}" +
                (if (internationalBatches > 0) ", $internationalBatches international." else ", none international."),
            contentSpacing = 10.dp,
        ) {
            Row(Modifier.fillMaxWidth()) {
                DemandStat("Unallocated", unallocatedDemand.size, sk.crit, Modifier.weight(1f))
                DemandStat("International", internationalBatches, sk.violet, Modifier.weight(1f))
                DemandStat("Active", activeBatches.size, sk.good, Modifier.weight(1f))
                DemandStat("Upcoming", upcomingBatches.size, sk.sky, Modifier.weight(1f))
            }
            val demandTotal = unallocatedDemand.size + activeBatches.size + upcomingBatches.size
            if (demandTotal > 0) {
                // Proportional to the three real counts above; international is a
                // subset of demand, so it is not a separate segment.
                Row(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (unallocatedDemand.isNotEmpty()) Box(Modifier.weight(unallocatedDemand.size.toFloat()).fillMaxHeight().background(sk.crit))
                    if (activeBatches.isNotEmpty()) Box(Modifier.weight(activeBatches.size.toFloat()).fillMaxHeight().background(sk.good))
                    if (upcomingBatches.isNotEmpty()) Box(Modifier.weight(upcomingBatches.size.toFloat()).fillMaxHeight().background(sk.sky))
                }
            }
            if (unallocatedDemand.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(Radii.chip))
                        .background(sk.royal.copy(alpha = 0.16f))
                        .pressable(onOpenDemand)
                        .semantics { contentDescription = "Allocate ${unallocatedDemand.size} open batches" }
                        .padding(horizontal = Space.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_flag), contentDescription = null, tint = sk.sky, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(Space.sm))
                    Text("Allocate", style = MaterialTheme.typography.labelLarge, color = sk.frost, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${unallocatedDemand.size} open", style = MaterialTheme.typography.labelMedium, color = sk.ice)
                    Spacer(Modifier.width(Space.xs))
                    Icon(painterResource(R.drawable.ic_chevron), contentDescription = null, tint = sk.ice, modifier = Modifier.size(16.dp))
                }
            }
        }

        // ════════════════════════════════════════════════════════════════════
        // 6. COMMUNICATE
        // ════════════════════════════════════════════════════════════════════
        TodayPanel(title = "Communicate", icon = R.drawable.ic_mail, tint = sk.indigoDeep) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                CommunicateAction(R.drawable.ic_people, "Team", sk.sky, Modifier.weight(1f)) {
                    onOpenCommunication("TEAM", "", "GENERAL_PROFESSIONAL", "", "")
                }
                CommunicateAction(R.drawable.ic_mail, "Trainer", sk.royal, Modifier.weight(1f)) { showTrainerPicker = true }
                CommunicateAction(R.drawable.ic_calendar, "Weekly", sk.cyan, Modifier.weight(1f), onClick = onOpenWeeklyReport)
                CommunicateAction(R.drawable.ic_calendar, "Monthly", sk.violet, Modifier.weight(1f), onClick = onOpenHrReport)
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

        // ════════════════════════════════════════════════════════════════════
        // 7. DELIVERY OUTLOOK — stats and the real feed in one panel.
        // ════════════════════════════════════════════════════════════════════
        TodayPanel(
            title = "Delivery outlook",
            icon = R.drawable.ic_calendar,
            tint = sk.emerald,
            trailing = "Full calendar →",
            onTrailingClick = onOpenDelivery,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MiniStat("Delivering", activeBatches.size.toString(), sk.good)
                MiniStat("Upcoming", upcomingBatches.size.toString(), sk.sky)
                MiniStat("On leave", onLeaveCount.toString(), sk.warn)
            }
            val feed = (activeBatches.map { it to "Active now" } + upcomingBatches.take(3).map { it to "Upcoming" })
            if (feed.isEmpty()) {
                EmptyNote(R.drawable.ic_calendar, sk.subText, "No deliveries scheduled", "No active or upcoming deliveries right now.")
            } else {
                // Real photo URLs come from capability rows; batches only carry the name.
                val photoByName = remember(capTrainers) {
                    capTrainers.associate { it.str("trainer_name").lowercase() to it.str("photo_url") }
                }
                HorizontalDivider(color = sk.cardBorder.copy(alpha = 0.5f))
                Column {
                    feed.forEachIndexed { i, (b, stateLabel) ->
                        val cName = b.str("course_name").ifBlank { "Unnamed course" }
                        val trainer = b.str("trainer_name").ifBlank { "Unassigned" }
                        val mode = b.str("delivery_mode").ifBlank { "Virtual" }
                        TimelineItem(
                            title = cName,
                            modifier = Modifier.pressable { onBatchClick(b.str("demand_id")) },
                            timestamp = stateLabel,
                            tint = if (stateLabel == "Active now") sk.good else sk.sky,
                            isFirst = i == 0,
                            isLast = i == feed.lastIndex,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (b.str("trainer_name").isNotBlank()) {
                                    Avatar(name = trainer, photoUrl = photoByName[trainer.lowercase()]?.takeIf { it.isNotBlank() }, size = 22.dp)
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text("$trainer · $mode", style = MaterialTheme.typography.bodySmall, color = sk.subText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }

        // ════════════════════════════════════════════════════════════════════
        // 8. CERTIFICATION COVERAGE
        // ════════════════════════════════════════════════════════════════════
        if (certCoverage != null) {
            val certTint = sk.violet
            TodayPanel(title = "Certification coverage", icon = R.drawable.ic_certificate, tint = certTint, onClick = onOpenPriorities, contentSpacing = Space.sm, bodyPadding = Space.md) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("$certCoverage%", style = MaterialTheme.typography.headlineLarge, color = certTint, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(Space.md))
                    Text(
                        "of open-demand courses have a certified trainer",
                        style = MaterialTheme.typography.bodyMedium, color = sk.bodyText, modifier = Modifier.weight(1f),
                    )
                }
                MetricProgress(fraction = certCoverage.coerceIn(0, 100) / 100f, tint = certTint, trackHeight = 10.dp)
            }
        }

        // ════════════════════════════════════════════════════════════════════
        // 9. TOP PERFORMERS — People-style trainer rows.
        // ════════════════════════════════════════════════════════════════════
        if (topPerformers.isNotEmpty()) {
            TodayPanel(
                title = "Top performers",
                icon = R.drawable.ic_award,
                tint = sk.amber,
                conclusion = "Carrying delivery, ranked by measured utilisation.",
                contentSpacing = 0.dp,
            ) {
                topPerformers.forEachIndexed { i, p ->
                    PerformerRow(
                        rank = i + 1, performer = p,
                        onClick = { onTrainerClick(p.trainerEmail.ifBlank { email }, p.name) },
                    )
                    if (i < topPerformers.lastIndex) HorizontalDivider(color = sk.cardBorder)
                }
            }
        }

        // ════════════════════════════════════════════════════════════════════
        // 10. OPERATIONS — domain-grouped action list.
        // ════════════════════════════════════════════════════════════════════
        data class OpTile(val title: String, val subtitle: String, val icon: Int, val onClick: () -> Unit)
        val groups = listOf(
            Triple("Planning", sk.azure, listOf(
                OpTile("This week", "Priorities, ranked", R.drawable.ic_calendar, onOpenPriorities),
                OpTile("Pipeline radar", "Signed demand", R.drawable.ic_search, onOpenPipelineRadar),
                OpTile("Capacity runway", "8-week demand gap", R.drawable.ic_trend, onOpenCapacityRunway),
            )),
            Triple("Delivery", sk.cyan, listOf(
                OpTile("Delivery compliance", "Recording & audit", R.drawable.ic_check, onOpenDeliveryCompliance),
                OpTile("Accounts book", "Client concentration", R.drawable.ic_book, onOpenAccounts),
            )),
            Triple("People", sk.sky, listOf(
                OpTile("HR monthly review", "Trainer index", R.drawable.ic_people, onOpenHrReport),
                OpTile("Skill requests", "From reportees", R.drawable.ic_gap, onOpenSkillRequests),
            )),
            Triple("Automation", sk.violet, listOf(
                OpTile("Team copilot", "Ask about your team", R.drawable.ic_inbox, onOpenCopilot),
                OpTile("Viber automation", "Auto-dispatch queue", R.drawable.ic_share, onOpenViberAutomation),
            )),
        )
        TodayPanel(title = "Operations", icon = R.drawable.ic_home, tint = sk.brand, contentSpacing = Space.md) {
            groups.forEach { (domain, domainTint, tiles) ->
                Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(domainTint))
                        Spacer(Modifier.width(Space.sm))
                        Text(domain.uppercase(), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.1.em), color = domainTint, fontWeight = FontWeight.Bold)
                    }
                    // A 2-column matrix of equal tiles. An odd tile keeps its
                    // half width; no placeholder tile is invented to fill it.
                    tiles.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            pair.forEach { t ->
                                OperationTile(t.title, t.subtitle, t.icon, domainTint, Modifier.weight(1f), t.onClick)
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

// ── Today building blocks ────────────────────────────────────────────────────

/**
 * Every Today section is a titled panel: a coloured icon anchor, a bold
 * uppercase title, an optional conclusion, a hairline, then the content.
 * This replaces the old bare `SectionHeading` label floating above loose
 * cards — the section hierarchy is now carried by the container itself.
 */
@Composable
private fun TodayPanel(
    title: String,
    icon: Int,
    tint: Color,
    modifier: Modifier = Modifier,
    conclusion: String? = null,
    trailing: String? = null,
    onTrailingClick: (() -> Unit)? = null,
    badge: String? = null,
    onClick: (() -> Unit)? = null,
    contentSpacing: Dp = Space.md,
    /** Level 1 (Needs you today): accent rail + tinted stroke. Level 2 (default): hairline only. */
    emphasis: Boolean = false,
    bodyPadding: Dp = Space.md,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sk = MaterialTheme.skill
    val shape = RoundedCornerShape(Radii.card)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF172236), Color(0xFF0F1726))))
            .then(
                if (emphasis) Modifier.border(1.dp, Brush.verticalGradient(listOf(tint.copy(alpha = 0.55f), sk.cardBorder.copy(alpha = 0.6f))), shape)
                else Modifier.border(1.dp, Color.White.copy(alpha = 0.06f), shape)
            )
            .then(if (onClick != null) Modifier.pressable(onClick) else Modifier),
    ) {
        if (emphasis) Box(Modifier.fillMaxWidth().height(3.dp).background(Brush.horizontalGradient(listOf(tint, tint.copy(alpha = 0f)))))
        Row(
            Modifier.fillMaxWidth().padding(start = Space.md, end = Space.md, top = Space.md, bottom = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconSlot(tint = tint, size = 28.dp) {
                Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(Space.sm))
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, letterSpacing = 0.08.em),
                color = sk.frost, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (badge != null) {
                Box(
                    Modifier.clip(CircleShape).background(tint).padding(horizontal = 8.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(badge, style = MaterialTheme.typography.labelMedium, color = sk.navy, fontWeight = FontWeight.Black)
                }
            }
            if (trailing != null) {
                Text(
                    trailing,
                    style = MaterialTheme.typography.labelMedium, color = sk.cyan, fontWeight = FontWeight.SemiBold,
                    modifier = if (onTrailingClick != null) Modifier.pressable(onTrailingClick) else Modifier,
                )
            }
        }
        if (conclusion != null) {
            Text(
                conclusion,
                style = MaterialTheme.typography.bodyMedium, color = sk.bodyText,
                modifier = Modifier.padding(start = Space.md, end = Space.md, bottom = Space.sm),
            )
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
        Column(
            Modifier.fillMaxWidth().padding(bodyPadding),
            verticalArrangement = Arrangement.spacedBy(contentSpacing),
            content = content,
        )
    }
}

@Composable
private fun StatusPill(text: String, tint: Color) {
    Row(
        Modifier.clip(CircleShape).background(tint.copy(alpha = 0.16f)).border(1.dp, tint.copy(alpha = 0.5f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(tint))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = tint, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HeroStat(label: String, value: String, fraction: Float?, tint: Color, modifier: Modifier = Modifier) {
    val sk = MaterialTheme.skill
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(Radii.chip))
            .background(Color.Black.copy(alpha = 0.22f))
            .border(1.dp, tint.copy(alpha = 0.30f), RoundedCornerShape(Radii.chip))
            .padding(horizontal = Space.sm, vertical = Space.sm),
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = sk.frost, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = sk.ice, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        if (fraction != null) {
            MetricProgress(fraction = fraction, tint = tint, trackHeight = 4.dp)
        } else {
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(tint))
        }
    }
}

@Composable
private fun PulseTile(
    icon: Int,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    iconTint: Color? = null,
    fraction: Float? = null,
    caption: String? = null,
    captionTint: Color? = null,
    onClick: () -> Unit = {},
) {
    val sk = MaterialTheme.skill
    val icTint = iconTint ?: tint ?: sk.sky
    Column(
        modifier
            .clip(RoundedCornerShape(Radii.kpi))
            .background(Brush.verticalGradient(listOf(icTint.copy(alpha = 0.14f), icTint.copy(alpha = 0.03f))))
            .pressable(onClick)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconSlot(tint = icTint, size = 26.dp) {
                Icon(painterResource(icon), contentDescription = null, tint = icTint, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(Space.sm))
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = sk.labelText, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Spacer(Modifier.height(Space.sm))
        Text(value, style = MaterialTheme.typography.headlineMedium, color = tint ?: sk.frost, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(6.dp))
        MetricProgress(fraction = fraction ?: 0f, tint = icTint, trackHeight = 5.dp)
        if (caption != null) {
            Spacer(Modifier.height(4.dp))
            Text(caption, style = MaterialTheme.typography.labelSmall, color = captionTint ?: sk.subText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * "Needs you today" card, in the People roster card's visual language: a
 * severity-tinted glass card with a solid left edge, a coloured uppercase
 * severity line, and a full-width tinted action strip for the one thing the
 * manager can do from here. Recipient resolution is unchanged.
 */
@Composable
private fun AttentionCard(
    item: AttentionItem,
    onOpenDemand: () -> Unit,
    onAskAvailability: (() -> Unit)?,
) {
    val sk = MaterialTheme.skill
    val tint = item.severity.tint()
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            // Level 3: tonal row inside the level-1 panel — no second border.
            .clip(RoundedCornerShape(Radii.chip))
            .background(tint.copy(alpha = if (item.severity == Severity.Critical) 0.10f else 0.06f))
            .pressable(onOpenDemand),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(tint))
        Column(Modifier.weight(1f).padding(start = 10.dp, top = 10.dp, end = 8.dp, bottom = if (onAskAvailability != null) 2.dp else 10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                IconSlot(tint = tint, size = 30.dp) {
                    Icon(
                        painterResource(if (item.severity == Severity.Critical) R.drawable.ic_alert else R.drawable.ic_inbox),
                        contentDescription = item.severity.label, tint = tint, modifier = Modifier.size(15.dp),
                    )
                }
                Spacer(Modifier.width(Space.sm))
                Column(Modifier.weight(1f)) {
                    // Long course titles wrap; the action below keeps its own row.
                    Text(item.title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp), color = sk.frost, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${item.severity.label.uppercase()} · ${item.subtitle}",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.04.em),
                        color = tint, fontWeight = FontWeight.Bold, maxLines = 1,
                    )
                    if (onAskAvailability != null) {
                        // A named ask only when the planner resolved a real
                        // individual; otherwise a neutral "Ask availability".
                        // Neither claims anyone is free.
                        val first = item.recipientName.substringBefore(" ")
                        val named = item.recipientType == "INDIVIDUAL" && first.isNotBlank()
                        val askLabel = if (named) "Ask $first" else "Ask availability"
                        // Screen readers get the full intent, not the short visible label.
                        val askDescription = if (named) "Ask $first availability" else "Ask trainer availability"
                        Row(
                            Modifier
                                .height(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .pressable(onAskAvailability)
                                .semantics { contentDescription = askDescription },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(painterResource(R.drawable.ic_calendar), contentDescription = null, tint = sk.sky, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(askLabel, style = MaterialTheme.typography.labelLarge, color = sk.sky, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Spacer(Modifier.width(2.dp))
                            Icon(painterResource(R.drawable.ic_chevron), contentDescription = null, tint = sk.sky, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubHeading(title: String, conclusion: String) {
    val sk = MaterialTheme.skill
    Column {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.06.em), color = sk.frost, fontWeight = FontWeight.SemiBold)
        Text(conclusion, style = MaterialTheme.typography.bodySmall, color = sk.subText)
    }
}

@Composable
private fun DemandStat(label: String, value: Int, tint: Color, modifier: Modifier = Modifier) {
    val sk = MaterialTheme.skill
    Column(modifier) {
        Text(value.toString(), style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp), color = if (value > 0) tint else sk.frost, fontWeight = FontWeight.Bold)
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.sp), color = sk.labelText, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun EmptyNote(icon: Int, tint: Color, title: String, body: String) {
    val sk = MaterialTheme.skill
    Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)).border(1.dp, tint.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = sk.frost, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodySmall, color = sk.subText)
        }
    }
}

@Composable
private fun BandStat(label: String, count: Int, total: Int, tint: Color, modifier: Modifier = Modifier) {
    val sk = MaterialTheme.skill
    val pct = (count * 100f / total.coerceAtLeast(1)).toInt()
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(count.toString(), style = MaterialTheme.typography.headlineSmall, color = tint, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(4.dp))
            Text("$pct%", style = MaterialTheme.typography.labelSmall, color = sk.subText, modifier = Modifier.padding(bottom = 4.dp))
        }
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = sk.labelText, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MiniStat(label: String, value: String, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = tint, fontWeight = FontWeight.Black)
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.skill.labelText, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PerformerRow(rank: Int, performer: TopPerformer, onClick: () -> Unit) {
    val sk = MaterialTheme.skill
    val rankTint = when (rank) { 1 -> sk.amber; 2 -> sk.ice; else -> sk.sky }
    val readyTint = when (performer.readinessBucket.lowercase()) {
        "ready" -> sk.good
        "developing", "almost ready" -> sk.sky
        "" -> sk.subText
        else -> sk.warn
    }
    Row(
        Modifier.fillMaxWidth().pressable(onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Avatar(name = performer.name, photoUrl = performer.photoUrl, size = 44.dp)
            Box(
                Modifier.size(18.dp).clip(CircleShape).background(rankTint).border(2.dp, sk.navy, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("$rank", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = sk.navy, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(performer.name, style = MaterialTheme.typography.titleSmall, color = sk.frost, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (performer.readinessBucket.isNotBlank()) {
                    Spacer(Modifier.width(Space.xs))
                    Text(performer.readinessBucket, style = MaterialTheme.typography.labelSmall, color = sk.subText, maxLines = 1)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetricProgress(fraction = performer.utilization / 100f, tint = readyTint, trackHeight = 6.dp, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(Space.sm))
                Text("${performer.utilization}%", style = MaterialTheme.typography.titleSmall, color = readyTint, fontWeight = FontWeight.Black)
            }
        }
    }
}

/** Operations matrix tile: tonal icon box, title, one supporting line, corner chevron. */
@Composable
private fun OperationTile(title: String, subtitle: String, icon: Int, tint: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val sk = MaterialTheme.skill
    Box(
        modifier
            .height(108.dp)
            .clip(RoundedCornerShape(Radii.chip))
            .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.16f), tint.copy(alpha = 0.03f))))
            .pressable(onClick)
            .semantics { contentDescription = "$title, $subtitle" }
            .padding(12.dp),
    ) {
        Column {
            IconSlot(tint = tint, size = 30.dp) {
                Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = sk.frost, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = sk.subText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(
            painterResource(R.drawable.ic_chevron), contentDescription = null, tint = tint,
            modifier = Modifier.align(Alignment.BottomEnd).size(14.dp),
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
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp)),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (bench > 0) Box(Modifier.weight(bench / total).fillMaxHeight().background(sk.warn))
        if (optimal > 0) Box(Modifier.weight(optimal / total).fillMaxHeight().background(sk.sky))
        if (stretched > 0) Box(Modifier.weight(stretched / total).fillMaxHeight().background(sk.crit))
        if (bench + optimal + stretched == 0) Box(Modifier.weight(1f).fillMaxHeight().background(sk.surface3))
    }
}

private data class AttentionItem(
    val title: String,
    val subtitle: String,
    val severity: Severity,
    /** Real `demand_id` when this item is an unallocated batch — powers "Ask availability". Blank for non-demand items (e.g. skill requests), which get no communication shortcut. */
    val demandId: String = "",
    /**
     * Resolved by [CommunicationPlanner] from real matching_trainers data —
     * "INDIVIDUAL"+a real name when a capability-matched candidate exists,
     * "TEAM" only when none could be identified. Never a blind broadcast by
     * default the way this used to always pass ("TEAM", "").
     */
    val recipientType: String = "TEAM",
    val recipientName: String = "",
)

private data class TopPerformer(
    val name: String,
    val utilization: Int,
    val readinessBucket: String,
    /** Real capability-row email — powers the Trainer360 drill-down. Never the manager's own email. */
    val trainerEmail: String,
    /** Real RMS profile photo URL when the capability row carries one; null falls back to circular initials. */
    val photoUrl: String? = null,
)

@Composable
private fun CommunicateAction(icon: Int, label: String, tint: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val sk = MaterialTheme.skill
    Column(
        modifier
            .clip(RoundedCornerShape(Radii.chip))
            .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.18f), tint.copy(alpha = 0.04f))))
            .pressable(onClick)
            .semantics { contentDescription = "Communicate: $label" }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconSlot(tint = tint, size = 30.dp) {
            Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.height(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = sk.frost, fontWeight = FontWeight.Bold)
    }
}

// Stub to satisfy MainScreen
fun managerBriefFromPayload(data: Map<String, Any>?): String = ""
