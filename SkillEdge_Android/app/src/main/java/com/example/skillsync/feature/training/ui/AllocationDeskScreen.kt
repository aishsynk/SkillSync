package com.example.skillsync.feature.training.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.skillsync.R
import com.example.skillsync.theme.Figure
import com.example.skillsync.theme.FigureSize
import com.example.skillsync.theme.SectionHeading
import com.example.skillsync.theme.ToneChip
import com.example.skillsync.core.ui.ShimmerBox
import com.example.skillsync.feature.home.SearchField
import com.example.skillsync.theme.SkillCard
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.pressable
import com.example.skillsync.theme.accentGlass
import com.example.skillsync.theme.glassSurface
import com.example.skillsync.theme.skill
import com.example.skillsync.core.ui.*
import androidx.compose.material3.Text
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Plan / Demand & Planning — the unallocated-demand command centre.
 *
 * This page answers "what demand needs planning?" — how many batches are
 * open, which start first, which are blocked and why, which the team can
 * cover in aggregate. It deliberately does NOT answer "who can teach this?":
 * candidate matching, ranking, availability and Grow-the-Team all live in
 * Demand Details (BatchDetailScreen.kt), reached via Open Details. Doing
 * person-level matching here does not scale — a manager with dozens of open
 * batches would be scrolling through candidate cards for each one — and it
 * mixes two different jobs into one screen.
 */

// ── Coverability (aggregate — never a named person) ────────────────────────
//
// relevanceColor/coverageStyle below are also used by BatchDetailScreen.kt
// (Demand Details) for its own candidate-match display — unchanged, kept
// `internal` on purpose. Plan uses its own planCoverageStyle so its labels
// ("Coverable"/"Blocked") can read correctly at the batch level without
// touching what Demand Details already shows.

@Composable
internal fun relevanceColor(relevance: Int): Color {
    val sk = MaterialTheme.skill
    return when {
        relevance >= 75 -> sk.green
        relevance >= 50 -> sk.amber
        relevance > 0 -> sk.red
        else -> sk.subText
    }
}

@Composable
internal fun coverageStyle(coverage: String): Triple<String, Color, Int> {
    val sk = MaterialTheme.skill
    return when (coverage) {
        "Best Match" -> Triple("Best Match", sk.aqua, R.drawable.ic_check)
        "Available with Upskilling" -> Triple("Available with Upskilling", sk.warn, R.drawable.ic_flag)
        else -> Triple("No Coverage", sk.crit, R.drawable.ic_alert)
    }
}

/**
 * One state, one source. Previously the status band (BLOCKED/COVERABLE) and
 * the coverability sentence were computed from two switch statements over
 * `coverage_status` that disagreed on their fallback — a real value like
 * "Available" (distinct from "Best Match") read as unblocked for the badge
 * but fell into the "else -> Blocked" branch for the sentence, producing a
 * card that showed "COVERABLE" and "Blocked · allocation pending" at once.
 * This is the single classifier both the chip and the sentence read from.
 */
private data class PlanState(val label: String, val reason: String, val tint: Color, val icon: Int)

private fun planState(coverage: String, sk: com.example.skillsync.theme.SkillColors): PlanState = when (coverage) {
    "No Coverage" -> PlanState(
        "BLOCKED", "Required capability not currently covered", sk.warn, R.drawable.ic_alert,
    )
    "Best Match" -> PlanState(
        "READY TO ALLOCATE", "Allocation pending", sk.good, R.drawable.ic_check,
    )
    else -> PlanState(
        "NEEDS REVIEW", "Coverage not yet confirmed", sk.sky, R.drawable.ic_flag,
    )
}

private enum class PlanFilter(val label: String) {
    ALL("All"), URGENT("Urgent"), COVERABLE("Coverable"), BLOCKED("Blocked"),
    THIS_WEEK("This Week"), INTERNATIONAL("International"),
}

private enum class PlanSort(val label: String) { START_DATE("Start date"), URGENCY("Urgency"), ACCOUNT("Account"), COURSE("Course") }

private fun daysUntil(iso: String): Int? = try {
    ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(iso)).toInt()
} catch (_: Exception) { null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AllocationDeskContent(
    data: Map<String, Any>,
    newIds: Set<String>,
    onBatchClick: (Map<*, *>) -> Unit,
    capacityPlan: com.example.skillsync.core.network.CapacityPlanResponse? = null,
    capacityPlanLoading: Boolean = false,
) {
    val sk = MaterialTheme.skill
    val batches = data.rows("batches")
    val summary = data.obj("summary")

    var query by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(PlanFilter.ALL) }
    var sort by remember { mutableStateOf(PlanSort.START_DATE) }
    var expandedId by remember { mutableStateOf<String?>(null) }

    // Real, computed facts only — the risk window and coverability read
    // straight off backend fields already on every batch row.
    val enriched = remember(batches) {
        batches.map { b ->
            val days = daysUntil(b.str("start_date"))
            val blocked = b.str("coverage_status") == "No Coverage"
            val urgent = b.bool("at_risk") || (days != null && days in 0..3)
            val thisWeek = days != null && days in 0..6
            Triple(b, days, Triple(blocked, urgent, thisWeek))
        }
    }

    val filtered = remember(enriched, query, activeFilter) {
        enriched.filter { (b, days, flags) ->
            val (blocked, urgent, thisWeek) = flags
            val q = query.trim().lowercase()
            val matchesQuery = q.isBlank() ||
                b.str("course_name").lowercase().contains(q) ||
                b.str("customer").lowercase().contains(q) ||
                b.str("demand_id").contains(q)
            val matchesFilter = when (activeFilter) {
                PlanFilter.ALL -> true
                PlanFilter.URGENT -> urgent
                PlanFilter.COVERABLE -> b.str("coverage_status") != "No Coverage"
                PlanFilter.BLOCKED -> blocked
                PlanFilter.THIS_WEEK -> thisWeek
                PlanFilter.INTERNATIONAL -> b.bool("is_international")
            }
            matchesQuery && matchesFilter
        }
    }

    val sorted = remember(filtered, sort) {
        when (sort) {
            PlanSort.START_DATE -> filtered.sortedBy { it.second ?: Int.MAX_VALUE }
            PlanSort.URGENCY -> filtered.sortedWith(
                compareByDescending<Triple<Map<*, *>, Int?, Triple<Boolean, Boolean, Boolean>>> { it.third.second }
                    .thenBy { it.second ?: Int.MAX_VALUE }
            )
            PlanSort.ACCOUNT -> filtered.sortedBy { it.first.str("customer") }
            PlanSort.COURSE -> filtered.sortedBy { it.first.str("course_name") }
        }
    }

    // ── KPI strip facts — every one a count over a real, already-present field.
    
    val (internationalPriority, otherDemand) = remember(sorted) {
        sorted.partition { (batch, _, _) ->
            val mode = batch.str("delivery_mode").uppercase()
            batch.bool("is_international") && (mode.contains("ILT") || mode.contains("FMAT"))
        }
    }

    val total = summary?.int("total") ?: batches.size
    val urgentCount = enriched.count { it.third.second }
    val coverableCount = batches.count { it.str("coverage_status") != "No Coverage" }
    val blockedCount = batches.count { it.str("coverage_status") == "No Coverage" }
    val thisWeekCount = enriched.count { it.third.third }
    val internationalCount = batches.count { it.bool("is_international") }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { PlanHeader(total = total, newCount = newIds.size) }

        item {
            if (batches.isEmpty() && total == 0) {
                PlanKpiSkeleton()
            } else {
                PlanKpiStrip(
                    unallocated = total, urgent = urgentCount, coverable = coverableCount,
                    blocked = blockedCount, thisWeek = thisWeekCount, international = internationalCount,
                )
            }
        }

        if (batches.isNotEmpty()) {
            item { PlanningFocus(blockedCount, total, batches) }
        }

        item { CapacityPlanningCard(capacityPlan, capacityPlanLoading) }

        item {
            Column {
                Box(Modifier.fillMaxWidth()) {
                    SearchField(query, { query = it }, "Search demand…")
                }
                Spacer(Modifier.height(Space.sm))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlanFilter.entries.forEach { f ->
                        val count = when (f) {
                            PlanFilter.ALL -> total
                            PlanFilter.URGENT -> urgentCount
                            PlanFilter.COVERABLE -> coverableCount
                            PlanFilter.BLOCKED -> blockedCount
                            PlanFilter.THIS_WEEK -> thisWeekCount
                            PlanFilter.INTERNATIONAL -> internationalCount
                        }
                        FilterPill(f.label, count, activeFilter == f) { activeFilter = f }
                    }
                }
                Spacer(Modifier.height(Space.sm))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Sort", style = MaterialTheme.typography.labelSmall, color = sk.labelText)
                    PlanSort.entries.forEach { s ->
                        SortPill(s.label, sort == s) { sort = s }
                    }
                }
            }
        }

        if (sorted.isEmpty()) {
            item {
                SkillSyncEmptyStatePlan(
                    title = if (batches.isEmpty()) "No unallocated demand" else "No batches match this filter",
                    description = if (batches.isEmpty())
                        "All current batches have an allocation, or there is no open demand in this planning horizon."
                    else "Nothing in the current filter. Try All or clear the search.",
                )
            }
        }

        
        if (internationalPriority.isNotEmpty()) {
            item {
                InternationalPriorityZone(
                    items = internationalPriority,
                    newIds = newIds,
                    expandedId = expandedId,
                    onToggleExpand = { id -> expandedId = if (expandedId == id) null else id },
                    onOpenDetails = { onBatchClick(it) }
                )
            }
            item {
                Text(
                    "OTHER DEMAND",
                    style = MaterialTheme.typography.labelSmall,
                    color = sk.labelText,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp, start = 4.dp)
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(sk.cardBorder))
            }
        }

        itemsIndexed(otherDemand, key = { _, t -> t.first.str("demand_id").ifBlank { t.first.str("course_name") } }) { _, (batch, days, flags) ->

            val id = batch.str("demand_id")
            val mode = batch.str("delivery_mode").uppercase()
            val isIltOrFmat = mode.contains("ILT") || mode.contains("FMAT")
            
            if (isIltOrFmat) {
                DeliveryOpportunityCard(
                    b = batch,
                    days = days,
                    blocked = flags.first,
                    urgent = flags.second,
                    isNew = id in newIds,
                    expanded = expandedId == id,
                    onToggleExpand = { expandedId = if (expandedId == id) null else id },
                    onOpenDetails = { onBatchClick(batch) },
                )
            } else {
                PlanBatchCard(
                    b = batch,
                    days = days,
                    blocked = flags.first,
                    urgent = flags.second,
                    isNew = id in newIds,
                    expanded = expandedId == id,
                    onToggleExpand = { expandedId = if (expandedId == id) null else id },
                    onOpenDetails = { onBatchClick(batch) },
                )
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}

// ── Header ───────────────────────────────────────────────────────────────────

@Composable
private fun PlanHeader(total: Int, newCount: Int) {
    val sk = MaterialTheme.skill
    Column {
        Text(
            "Demand & Planning",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = sk.frost,
        )
        Text(
            "Unallocated delivery demand requiring action" +
                if (newCount > 0) " · $newCount new" else "",
            style = MaterialTheme.typography.labelSmall, color = sk.sky,
        )
    }
}

// ── KPI strip ────────────────────────────────────────────────────────────────

@Composable
private fun PlanKpiStrip(unallocated: Int, urgent: Int, coverable: Int, blocked: Int, thisWeek: Int, international: Int) {
    val sk = MaterialTheme.skill
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PlanKpi("UNALLOCATED", unallocated.toString(), sk.frost)
        PlanKpi("URGENT", urgent.toString(), if (urgent > 0) sk.crit else sk.good)
        PlanKpi("COVERABLE", coverable.toString(), sk.sky)
        PlanKpi("BLOCKED", blocked.toString(), if (blocked > 0) sk.warn else sk.good)
        PlanKpi("THIS WEEK", thisWeek.toString(), sk.indigo)
        if (international > 0) PlanKpi("INTERNATIONAL", international.toString(), sk.teal)
    }
}

@Composable
private fun PlanKpi(label: String, value: String, tint: Color) {
    val sk = MaterialTheme.skill
    Column(
        Modifier
            .width(92.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(sk.surface1.copy(alpha = 0.65f))
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tint)
        // Two lines at a smaller size rather than clipping — "UNALLOCATED" and
        // "COVERABLE" both overflowed a single line at this width.
        Text(
            label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = sk.labelText, maxLines = 2,
        )
    }
}

@Composable
private fun PlanKpiSkeleton() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(4) { ShimmerBox(width = 84.dp, height = 62.dp, shape = RoundedCornerShape(4.dp)) }
    }
}

// ── Planning intelligence — one real, derived insight, never generative filler.
@Composable
private fun PlanningFocus(blocked: Int, total: Int, batches: List<Map<*, *>>) {
    val sk = MaterialTheme.skill
    if (total == 0) return
    // The course with the most blocked (uncoverable) batches drives the
    // clearest, single most useful sentence — real counts, no invention.
    val pressureCourse = batches
        .filter { it.str("coverage_status") == "No Coverage" }
        .groupBy { it.str("course_name").ifBlank { "Unspecified course" } }
        .maxByOrNull { it.value.size }

    val headline = when {
        blocked == 0 -> "All $total unallocated batches have at least partial team coverage."
        pressureCourse != null && pressureCourse.value.size > 1 ->
            "${pressureCourse.value.size} of $total batches are blocked, concentrated on ${pressureCourse.key.take(40)}."
        else -> "$blocked of $total batches are blocked by missing capability coverage."
    }
    Row(
        Modifier.fillMaxWidth().accentGlass(if (blocked > 0) sk.warn else sk.good).padding(Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(if (blocked > 0) R.drawable.ic_flag else R.drawable.ic_check),
            null, tint = if (blocked > 0) sk.warn else sk.good, modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(Space.sm))
        Column {
            Text(
                "PLANNING FOCUS", style = MaterialTheme.typography.labelSmall,
                color = sk.labelText, fontWeight = FontWeight.Bold, letterSpacing = 0.08.em,
            )
            Text(headline, style = MaterialTheme.typography.bodyMedium, color = sk.bodyText)
        }
    }
}

// ── Filter / sort pills ──────────────────────────────────────────────────────

@Composable
private fun FilterPill(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val sk = MaterialTheme.skill
    Row(
        Modifier
            .testTag("planFilter_$label")
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) sk.brand.copy(alpha = 0.85f) else sk.surface1)
            .pressable(onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label, style = MaterialTheme.typography.labelMedium,
            color = if (selected) sk.frost else sk.subText, fontWeight = FontWeight.SemiBold,
        )
        if (count > 0) {
            Spacer(Modifier.width(5.dp))
            Text(
                "$count", style = MaterialTheme.typography.labelSmall,
                color = if (selected) sk.frost.copy(alpha = 0.8f) else sk.labelText,
            )
        }
    }
}

@Composable
private fun SortPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val sk = MaterialTheme.skill
    Text(
        label, style = MaterialTheme.typography.labelMedium,
        color = if (selected) sk.sky else sk.subText, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .pressable(onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

// ── Empty state ──────────────────────────────────────────────────────────────

@Composable
private fun SkillSyncEmptyStatePlan(title: String, description: String) {
    val sk = MaterialTheme.skill
    Column(
        Modifier.fillMaxWidth().glassSurface().padding(Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(R.drawable.ic_check), null, tint = sk.sky, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(Space.sm))
        Text(title, style = MaterialTheme.typography.titleSmall, color = sk.bodyText, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            description, style = MaterialTheme.typography.bodySmall, color = sk.subText,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

// ── Batch card — a planning object, not a person list ───────────────────────

@Composable
private fun PlanBatchCard(
    b: Map<*, *>,
    days: Int?,
    blocked: Boolean,
    urgent: Boolean,
    isNew: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onOpenDetails: () -> Unit,
) {
    val sk = MaterialTheme.skill
    val state = planState(b.str("coverage_status"), sk)
    val accent = when {
        urgent -> sk.crit
        else -> state.tint
    }
    val mode = b.str("delivery_mode")
    val international = b.bool("is_international")

    Column(
        Modifier
            .fillMaxWidth()
            .animateContentSize()
            .accentGlass(accent, strong = urgent || blocked)
            .padding(Space.md),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // Course + account first — what the manager scans for.
        Text(
            b.str("course_name").ifBlank { "Course not specified" },
            style = MaterialTheme.typography.titleSmall, color = sk.bodyText,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Text(
            b.str("customer").ifBlank { "Account not specified" },
            style = MaterialTheme.typography.labelSmall, color = sk.labelText, maxLines = 1,
        )
        Text(
            listOfNotNull(
                listOfNotNull(
                    b.str("start_date").takeIf { it.isNotBlank() }?.shortDate(),
                    b.str("end_date").takeIf { it.isNotBlank() }?.shortDate(),
                ).joinToString(" – ").takeIf { it.isNotBlank() },
                mode.takeIf { it.isNotBlank() },
                b.str("location").takeIf { it.isNotBlank() && international },
                b.intOrNull("participants")?.takeIf { it > 0 }?.let { "$it learners" },
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall, color = sk.subText,
        )

        Spacer(Modifier.height(1.dp))

        // ONE primary state, urgency as a secondary time fact on the same
        // line — never two badges that can disagree.
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToneChip(state.label, state.tint, solid = true)
            if (isNew) {
                Spacer(Modifier.width(6.dp))
                ToneChip("NEW", sk.blue, solid = true)
            }
            Spacer(Modifier.weight(1f))
            if (days != null) {
                Text(
                    if (days <= 0) "Starts today" else "Starts in ${days}d",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (urgent) sk.crit else sk.labelText,
                    fontWeight = if (urgent) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(state.icon), null, tint = state.tint, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
            Text(state.reason, style = MaterialTheme.typography.labelSmall, color = sk.subText)
        }

        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.pressable(onOpenDetails)) {
            Text(
                "Open Demand", style = MaterialTheme.typography.labelLarge,
                color = sk.sky, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(4.dp))
            Icon(painterResource(R.drawable.ic_chevron), null, tint = sk.sky, modifier = Modifier.size(14.dp))
            Spacer(Modifier.weight(1f))
            Text(
                if (expanded) "Less" else "More",
                style = MaterialTheme.typography.labelSmall, color = sk.subText,
                modifier = Modifier.pressable(onToggleExpand).padding(4.dp),
            )
        }

        // Reference/priority/risk only — the rest (requirements, remarks,
        // courseware, participants) belongs on Demand Detail, not repeated here.
        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Spacer(Modifier.height(2.dp))
                HorizontalDivider(color = sk.cardBorder)
                Spacer(Modifier.height(2.dp))
                if (b.str("demand_id").isNotBlank()) ExpandedLine("Reference", b.str("demand_id"))
                b.intOrNull("priority_score")?.let { ExpandedLine("Priority score", "$it") }
                if (b.str("assignment_risk").isNotBlank()) ExpandedLine("Risk", b.str("assignment_risk"))
                if (b.str("assignment_level").isNotBlank()) ExpandedLine("Required level", b.str("assignment_level"))
            }
        }
    }
}


@Composable
private fun ExpandedLine(label: String, value: String) {
    val sk = MaterialTheme.skill
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = sk.subText, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelSmall, color = sk.bodyText)
    }
}

// ── Aggregate capacity outlook (unchanged in spirit — no person-level content).

@Composable
private fun CapacityPlanningCard(
    plan: com.example.skillsync.core.network.CapacityPlanResponse?,
    loading: Boolean,
) {
    val sk = MaterialTheme.skill

    val pressured = plan?.weeks?.filter { it.pressure == "high" }.orEmpty()
    val watch = plan?.weeks?.filter { it.pressure == "watch" }.orEmpty()
    val headline = when {
        plan == null && loading -> "Building the outlook from the demand snapshot"
        plan == null -> "Waiting for the demand snapshot"
        pressured.isNotEmpty() ->
            "${pressured.size} week${if (pressured.size == 1) " is" else "s are"} over capacity."
        watch.isNotEmpty() ->
            "${watch.size} week${if (watch.size == 1) " needs" else "s need"} watching."
        else -> "Every week ahead is inside capacity."
    }

    SectionHeading(
        "Eight week outlook",
        headline,
        trailing = if (plan?.ready == true) null else "preparing",
    )

    SkillCard(Modifier.fillMaxWidth()) {
        when {
            loading && plan == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
            plan == null -> Text(
                "The outlook appears once the demand snapshot is ready.",
                style = MaterialTheme.typography.bodySmall, color = sk.subText,
            )
            else -> {
                val s2 = plan.summary
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    Figure("${s2.demand}", "Demand", FigureSize.Small, Modifier.weight(1f), sk.bodyText)
                    Figure(
                        s2.coveragePct?.let { "$it%" } ?: "—", "Covered",
                        FigureSize.Small, Modifier.weight(1f),
                        if ((s2.coveragePct ?: 0) >= 75) sk.good else sk.warn,
                    )
                    Figure(
                        "${s2.uncovered}", "Uncovered", FigureSize.Small, Modifier.weight(1f),
                        if (s2.uncovered == 0) sk.good else sk.crit,
                    )
                }

                val maxDemand = (plan.weeks.maxOfOrNull { it.demand } ?: 1).coerceAtLeast(1)
                Row(
                    Modifier.fillMaxWidth().height(76.dp),
                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    plan.weeks.forEach { week ->
                        val tint = when (week.pressure) {
                            "high" -> sk.crit; "watch" -> sk.warn
                            "healthy" -> sk.good; else -> sk.cardBorder
                        }
                        Column(
                            Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                        ) {
                            if (week.demand > 0) {
                                Text("${week.demand}", style = MaterialTheme.typography.labelSmall, color = tint)
                            }
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height((8 + (48f * week.demand / maxDemand)).dp)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(tint.copy(alpha = 0.75f))
                            )
                            Text(
                                week.weekStart.takeLast(5),
                                style = MaterialTheme.typography.labelSmall,
                                color = sk.labelText, maxLines = 1,
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    listOf("Over capacity" to sk.crit, "Watch" to sk.warn, "Inside capacity" to sk.good)
                        .forEach { (label, tint) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(tint))
                                Spacer(Modifier.width(Space.xs))
                                Text(label, style = MaterialTheme.typography.labelSmall, color = sk.subText)
                            }
                        }
                }

                if (plan.confidence.note.isNotBlank()) {
                    Text(plan.confidence.note, style = MaterialTheme.typography.bodySmall, color = sk.subText)
                }

                // Compact status line, not a full sentence dominating the card:
                // the fact is useful but must not out-weigh the outlook itself.
                plan.confidence.availabilityPct?.let { pct ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Availability evidence",
                            style = MaterialTheme.typography.labelSmall, color = sk.labelText,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (pct == 100) "$pct% verified"
                            else "$pct% verified · rest unconfirmed",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (pct == 100) sk.subText else sk.warn,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}


// ── International Priority ───────────────────────────────────────────────────

@Composable
private fun InternationalPriorityZone(
    items: List<Triple<Map<*, *>, Int?, Triple<Boolean, Boolean, Boolean>>>,
    newIds: Set<String>,
    expandedId: String?,
    onToggleExpand: (String) -> Unit,
    onOpenDetails: (Map<*, *>) -> Unit
) {
    val sk = MaterialTheme.skill
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { 20 }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Brush.horizontalGradient(listOf(sk.brand, sk.azure, sk.cyan)))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(painterResource(R.drawable.ic_globe), null, tint = sk.cyan, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("INTERNATIONAL ILT / FMAT", color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Strategic delivery opportunities", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                    Text("Travel / international readiness required", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                }
                Text("${items.size}", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }

            // Items
            items.forEachIndexed { index, (batch, days, flags) ->
                val id = batch.str("demand_id")
                DeliveryOpportunityCard(
                    b = batch, days = days, blocked = flags.first, urgent = flags.second,
                    isNew = id in newIds, expanded = expandedId == id,
                    onToggleExpand = { onToggleExpand(id) }, onOpenDetails = { onOpenDetails(batch) }
                )
            }
        }
    }
}

@Composable
private fun DeliveryOpportunityCard(
    b: Map<*, *>, days: Int?, blocked: Boolean, urgent: Boolean,
    isNew: Boolean, expanded: Boolean, onToggleExpand: () -> Unit, onOpenDetails: () -> Unit
) {
    val sk = MaterialTheme.skill
    val mode = b.str("delivery_mode").uppercase()
    val pax = b.intOrNull("participants") ?: 0
    val international = b.bool("is_international")
    
    val city = b.str("city")
    val country = b.str("country")
    val loc = b.str("location")
    val location = when {
        city.isNotBlank() && country.isNotBlank() -> "$city, $country"
        loc.isNotBlank() && country.isNotBlank() -> "$loc, $country"
        city.isNotBlank() -> city
        country.isNotBlank() -> country
        else -> "Location not provided"
    }
    
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400))
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(sk.surface1)
                .border(1.dp, sk.azure.copy(alpha=0.5f), RoundedCornerShape(6.dp))
                .clickable { onToggleExpand() }
        ) {
            Row(
                Modifier.fillMaxWidth().background(sk.azure.copy(alpha=0.1f)).padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (international) "INTERNATIONAL $mode" else mode, color = sk.azure, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                if (pax > 0) Text("$pax pax", color = sk.azure, style = MaterialTheme.typography.labelSmall)
            }
            
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ToneChip("PRIORITY", sk.cyan)
                    ToneChip(mode, sk.brand)
                    if (international) ToneChip("GLOBAL OPPORTUNITY", sk.azure)
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_globe), null, tint = sk.subText, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(location, style = MaterialTheme.typography.titleSmall, color = sk.bodyText)
                }
                
                Text(b.str("course_name").ifBlank{"Course TBA"}, style = MaterialTheme.typography.bodyMedium, color = sk.bodyText, maxLines = 2, overflow = TextOverflow.Ellipsis)
                
                Text(
                    listOfNotNull(
                        b.str("start_date").takeIf { it.isNotBlank() }?.shortDate(),
                        b.str("end_date").takeIf { it.isNotBlank() }?.shortDate(),
                    ).joinToString(" – ").takeIf { it.isNotBlank() } ?: "Dates pending",
                    style = MaterialTheme.typography.labelSmall, color = sk.subText
                )

                if (blocked) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(sk.crit.copy(alpha=0.1f)).border(1.dp, sk.crit.copy(alpha=0.3f), RoundedCornerShape(4.dp)).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(painterResource(R.drawable.ic_alert), null, tint = sk.crit, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("NO TRAINER HOLDS THIS COURSE", color = sk.crit, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text("Capability gap detected", color = sk.crit, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            if (expanded) {
                Column(Modifier.fillMaxWidth().padding(12.dp).border(1.dp, sk.cardBorder, RoundedCornerShape(6.dp)).padding(12.dp)) {
                    if (international) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_people), null, tint = sk.subText, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Travel readiness required", style = MaterialTheme.typography.labelSmall, color = sk.subText)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(onClick = onOpenDetails, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = sk.azure)) {
                        Text("View Intelligence & Recommend")
                    }
                }
            }
        }
    }
}
