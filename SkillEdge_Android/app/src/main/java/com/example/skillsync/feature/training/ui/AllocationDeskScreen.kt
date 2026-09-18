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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.style.TextAlign
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

private data class RegionalData(
    val country: String,
    val flag: String,
    val total: Int,
    val matched: Int,
    val isInternational: Boolean
)

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
    
    val (intl, natl, ilo) = remember(sorted) {
        val i = mutableListOf<Triple<Map<*, *>, Int?, Triple<Boolean, Boolean, Boolean>>>()
        val n = mutableListOf<Triple<Map<*, *>, Int?, Triple<Boolean, Boolean, Boolean>>>()
        val r = mutableListOf<Triple<Map<*, *>, Int?, Triple<Boolean, Boolean, Boolean>>>()
        sorted.forEach { item ->
            val mode = item.first.str("delivery_mode").uppercase()
            if (mode.contains("ILT") || mode.contains("FMAT")) {
                if (item.first.bool("is_international")) i.add(item) else n.add(item)
            } else r.add(item)
        }
        Triple(i, n, r)
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
            PlanKpiStrip(
                    unallocated = total, urgent = urgentCount, coverable = coverableCount,
                    blocked = blockedCount, thisWeek = thisWeekCount, international = internationalCount,
                    capacityPlan = capacityPlan
                )
        }

        if (batches.isNotEmpty()) {
            item { PlanningFocus(blockedCount, total, batches) }
        }

        item { CapacityPlanningCard(capacityPlan, capacityPlanLoading) }

        if (batches.isNotEmpty()) {
            item { AllocationFunnelCard(batches, summary) }
        }

        item { RegionalCoverageCard(batches) }

        item { TopExposureCard(batches) }

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

        
        
        if (intl.isNotEmpty()) {
            item { SectionHeader("INTERNATIONAL DELIVERY", "Cross-border delivery", intl.size, sk.cyan) }
            items(intl, key = { it.first.str("demand_id") }) { (batch, _, flags) ->
                val (urgent, blocked, thisWeek) = flags
                PlanBatchCard(batch, urgent, blocked, thisWeek, newIds.contains(batch.str("demand_id")), expandedId == batch.str("demand_id"), { expandedId = if (expandedId == batch.str("demand_id")) null else batch.str("demand_id") }, { onBatchClick(batch) }, "international")
            }
        }
        if (natl.isNotEmpty()) {
            item { SectionHeader("NATIONAL DELIVERY", "India-based ILT/FMAT", natl.size, sk.teal) }
            items(natl, key = { it.first.str("demand_id") }) { (batch, _, flags) ->
                val (urgent, blocked, thisWeek) = flags
                PlanBatchCard(batch, urgent, blocked, thisWeek, newIds.contains(batch.str("demand_id")), expandedId == batch.str("demand_id"), { expandedId = if (expandedId == batch.str("demand_id")) null else batch.str("demand_id") }, { onBatchClick(batch) }, "national")
            }
        }
        if (ilo.isNotEmpty()) {
            item { SectionHeader("ILO / REMOTE", "Virtual delivery", ilo.size, sk.indigo) }
            items(ilo, key = { it.first.str("demand_id") }) { (batch, _, flags) ->
                val (urgent, blocked, thisWeek) = flags
                PlanBatchCard(batch, urgent, blocked, thisWeek, newIds.contains(batch.str("demand_id")), expandedId == batch.str("demand_id"), { expandedId = if (expandedId == batch.str("demand_id")) null else batch.str("demand_id") }, { onBatchClick(batch) }, "ilo")
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
private fun PlanKpiStrip(
    unallocated: Int, urgent: Int, coverable: Int, blocked: Int, thisWeek: Int, international: Int,
    capacityPlan: com.example.skillsync.core.network.CapacityPlanResponse? = null,
) {
    val sk = MaterialTheme.skill

    // Compute deltas vs previous week from capacity plan
    val (unallocatedDelta, urgentDelta, coverableDelta) = computeKpiDeltas(capacityPlan)

    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PlanKpi("UNALLOCATED", unallocated.toString(), sk.frost, delta = unallocatedDelta)
        PlanKpi("URGENT", urgent.toString(), if (urgent > 0) sk.crit else sk.good, delta = urgentDelta)
        PlanKpi("COVERABLE", coverable.toString(), sk.sky, delta = coverableDelta)
        PlanKpi("BLOCKED", blocked.toString(), if (blocked > 0) sk.warn else sk.good, delta = null)
        PlanKpi("THIS WEEK", thisWeek.toString(), sk.indigo, delta = null)
        PlanKpi("INTERNATIONAL", international.toString(), sk.teal, delta = null)
    }
}

private fun computeKpiDeltas(
    capacityPlan: com.example.skillsync.core.network.CapacityPlanResponse?
): Triple<Int?, Int?, Int?> /* unallocated, urgent, coverable */ {
    val weeks = capacityPlan?.weeks ?: return Triple(null, null, null)
    if (weeks.size < 2) return Triple(null, null, null)

    val currentWeek = weeks.first()
    val previousWeek = weeks[1]

    // Map capacity plan fields to KPI deltas
    // unallocated ~ demand, urgent ~ priority, coverable ~ strongCoverage, blocked ~ uncovered
    val unallocatedDelta = currentWeek.demand - previousWeek.demand
    val urgentDelta = currentWeek.priority - previousWeek.priority
    val coverableDelta = currentWeek.strongCoverage - previousWeek.strongCoverage
    // blocked and thisWeek don't have direct mapping, return null
    return Triple(unallocatedDelta, urgentDelta, coverableDelta)
}

@Composable
private fun PlanKpi(
    label: String, value: String, tint: Color, delta: Int? = null
) {
    val sk = MaterialTheme.skill
    Column(
        Modifier
            .width(92.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(sk.surface1.copy(alpha = 0.65f))
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Column {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tint)
            delta?.let { d ->
                val isPositive = d > 0
                val deltaColor = if (isPositive) sk.good else sk.crit
                val deltaSign = if (isPositive) "+" else ""
                Text(
                    "$deltaSign$d vs last week",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = deltaColor,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
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

// ── Allocation Funnel: open demand → matched → scheduled → delivered ───────
@Composable
private fun AllocationFunnelCard(
    batches: List<Map<*, *>>,
    summary: Map<*, *>?
) {
    val sk = MaterialTheme.skill
    val total = summary?.int("total") ?: batches.size
    val matched = batches.count { it.str("coverage_status") != "No Coverage" }
    val scheduled = batches.count { it.str("allocation_status") == "scheduled" }
    val delivered = batches.count { it.str("allocation_status") == "delivered" }
    val openDemand = total - matched

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = sk.surface1),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("ALLOCATION FUNNEL", style = MaterialTheme.typography.labelSmall, color = sk.labelText, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FunnelStage("OPEN DEMAND", openDemand, sk.frost)
                FunnelArrow(sk.labelText)
                FunnelStage("COVERED", matched, sk.sky)
                FunnelArrow(sk.labelText)
                FunnelStage("SCHEDULED", scheduled, sk.indigo)
                FunnelArrow(sk.labelText)
                FunnelStage("DELIVERED", delivered, sk.good)
            }
            Spacer(Modifier.height(8.dp))
            // Conversion rates
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ConversionRate("Open → Covered", total, matched)
                ConversionRate("Covered → Scheduled", matched, scheduled)
                ConversionRate("Scheduled → Delivered", scheduled, delivered)
            }
        }
    }
}

@Composable
private fun FunnelStage(label: String, count: Int, color: Color) {
    val sk = MaterialTheme.skill
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = sk.labelText)
        Spacer(Modifier.height(4.dp))
        Text(count.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun FunnelArrow(color: Color) {
    Text("→", style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
private fun ConversionRate(label: String, from: Int, to: Int) {
    val sk = MaterialTheme.skill
    val rate = if (from > 0) (to * 100 / from) else 0
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = sk.labelText, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text("$rate%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = sk.bodyText)
    }
}

// ── Regional Coverage Breakdown ────────────────────────────────────────────
@Composable
private fun RegionalCoverageCard(batches: List<Map<*, *>>) {
    val sk = MaterialTheme.skill

    // Group batches by country/region
    val regionalData = batches
        .groupBy { it.str("country").ifBlank { "Unknown" } }
        .map { (country, countryBatches) ->
            val total = countryBatches.size
            val matched = countryBatches.count { it.str("coverage_status") != "No Coverage" }
            val international = countryBatches.any { it.bool("is_international") }
            val flag = when (country) {
                "India" -> "🇮🇳"
                "United Kingdom", "UK" -> "🇬🇧"
                "United States", "USA" -> "🇺🇸"
                "Germany" -> "🇩🇪"
                "Singapore" -> "🇸🇬"
                "UAE" -> "🇦🇪"
                else -> "🌍"
            }
            RegionalData(country, flag, total, matched, international)
        }
        .sortedByDescending { it.total } // sort by total batches desc

    if (regionalData.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = sk.surface1),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("REGIONAL COVERAGE", style = MaterialTheme.typography.labelSmall, color = sk.labelText, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                regionalData.forEach { data ->
                    val coveragePct = if (data.total > 0) (data.matched * 100 / data.total) else 0
                    val color = when {
                        coveragePct >= 80 -> sk.good
                        coveragePct >= 60 -> sk.warn
                        else -> sk.crit
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // Country with flag
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(data.flag, style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.Start) {
                                Text(data.country, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = sk.bodyText)
                                if (data.isInternational) {
                                    Text("International", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = sk.amber)
                                }
                            }
                        }

                        // Coverage bar
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("${data.matched}/${data.total} covered", style = MaterialTheme.typography.labelSmall, color = sk.labelText)
                                Text("$coveragePct%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
                            }
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { (data.matched / data.total.toFloat()).coerceIn(0f, 1f) },
                                color = color,
                                trackColor = sk.surface1,
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
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
private fun SectionHeader(title: String, subtitle: String, count: Int, color: Color) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.skill.subText)
        }
        Text(count.toString(), style = MaterialTheme.typography.titleMedium, color = color)
    }
}

@Composable
@androidx.annotation.VisibleForTesting
internal fun PlanBatchCard(
    b: Map<*, *>, urgent: Boolean, blocked: Boolean, thisWeek: Boolean, isNew: Boolean, expanded: Boolean, onToggleExpand: () -> Unit, onClick: () -> Unit, categoryTheme: String = "ilo"
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
        loc.isNotBlank() -> loc
        else -> if (mode.contains("ILO") || mode.contains("VIRTUAL")) "Remote / Virtual" else "Location not provided"
    }
    val accentBase = when (categoryTheme) { "international" -> sk.azure; "national" -> sk.teal; else -> sk.indigo }
    val accentLight = when (categoryTheme) { "international" -> sk.cyan; "national" -> sk.emerald; else -> sk.violet }

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), colors = CardDefaults.cardColors(containerColor = sk.surface1), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, if (isNew) accentBase else sk.cardBorder)) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().background(accentBase.copy(alpha = 0.15f)).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_globe), null, tint = accentLight, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(when(categoryTheme) { "international" -> "INTERNATIONAL $mode"; "national" -> "INDIA NATIONAL $mode"; else -> "ILO / REMOTE" }, color = accentLight, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                if (pax > 0) Text("$pax pax", color = accentLight, style = MaterialTheme.typography.labelSmall)
            }
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_globe), null, tint = sk.subText, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(location, style = MaterialTheme.typography.titleMedium, color = sk.bodyText)
                }
                if (categoryTheme == "international") {
                    Spacer(Modifier.height(4.dp))
                    Text("Travels for ${b.str("start_date").shortDate()}", color = sk.subText, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    if (urgent) ToneChip("PRIORITY", sk.cyan)
                    ToneChip(mode, sk.brand)
                    if (international) ToneChip("GLOBAL OPPORTUNITY", sk.azure)
                }
                Spacer(Modifier.height(12.dp))
                Text(b.str("course_name").ifBlank{"Course TBA"}, style = MaterialTheme.typography.bodyLarge, color = sk.bodyText, fontWeight = FontWeight.Bold)
                if (b.str("customer").isNotBlank()) Text(b.str("customer"), style = MaterialTheme.typography.bodyMedium, color = sk.subText)
                Spacer(Modifier.height(12.dp))
                if (categoryTheme == "international") {
                    Box(Modifier.fillMaxWidth().background(sk.surface1, RoundedCornerShape(8.dp)).padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_globe), null, tint = sk.azure, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("INTERNATIONAL $mode OPPORTUNITY", color = sk.azure, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text(location, color = sk.bodyText, style = MaterialTheme.typography.labelMedium)
                                Text("TRAVEL REQUIRED · Visa and schedule readiness require manager review", color = sk.subText, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                if (blocked) {
                    Box(Modifier.fillMaxWidth().background(sk.red.copy(alpha=0.1f), RoundedCornerShape(8.dp)).border(1.dp, sk.red.copy(alpha=0.3f), RoundedCornerShape(8.dp)).padding(12.dp)) {
                        Row {
                            Icon(painterResource(R.drawable.ic_alert), null, tint = sk.red, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("NO TRAINER HOLDS THIS COURSE", color = sk.red, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Text("Nobody in RMS is skilled on it. This needs hiring or training, not reallocation.", color = sk.red.copy(alpha=0.8f), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                val candidates = b.list("candidates")
                if (candidates.isNotEmpty()) {
                    Text("RECOMMENDED TRAINERS", style = MaterialTheme.typography.labelSmall, color = sk.labelText, fontWeight = FontWeight.Bold)
                    Text("Client exclusions and leave are checked when you open this batch.", style = MaterialTheme.typography.labelSmall, color = sk.subText)
                    Spacer(Modifier.height(8.dp))
                    candidates.filter { it.str("trainer_name").isNotBlank() }.take(3).forEach { c ->
                        val isBlocked = c.bool("blocked")
                        val displayCoverage = if (isBlocked) "Blocked" else c.str("coverage").ifBlank { "Not Assessed" }
                        val coverageColor = if (isBlocked) sk.red else when (displayCoverage) {
                            "Best Match", "Good Match" -> sk.green
                            "Available with Upskilling" -> sk.amber
                            "No Coverage", "Not Assessed" -> sk.warn
                            else -> sk.subText
                        }
                        // Explicitly check if match is absent. Do not fabricate 0.
                        val matchVal = c.intOrNull("match")
                        val matchColor = if (isBlocked) sk.red else (matchVal?.let { relevanceColor(it) } ?: sk.subText)

                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(matchColor))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(c.str("trainer_name"), style = MaterialTheme.typography.labelMedium, color = if (isBlocked) sk.subText else sk.bodyText, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                                    val backupRole = c.str("backup_role")
                                    val suitability = c.intOrNull("suitability_score")
                                    val roleText = listOfNotNull(
                                        backupRole.ifBlank { null },
                                        suitability?.let { "$it suitability" }
                                    ).joinToString(" · ")
                                    if (roleText.isNotBlank()) {
                                        Text(roleText, style = MaterialTheme.typography.labelSmall, color = sk.subText)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    val availabilityStatus = c.str("availability_status")
                                    Box(Modifier.background(sk.surface1, RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                        Text(
                                            when (availabilityStatus) {
                                                "available" -> "Available for these dates"
                                                "conflict" -> "Schedule conflict"
                                                else -> "Availability unverified"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = when (availabilityStatus) {
                                                "available" -> sk.green
                                                "conflict" -> sk.red
                                                else -> sk.warn
                                            }
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    c.obj("suitability_components")?.let { parts ->
                                        val metrics = listOfNotNull(
                                            parts.intOrNull("skill")?.let { "Skill $it" },
                                            parts.intOrNull("readiness")?.let { "Ready $it" },
                                            parts.intOrNull("availability")?.let { "Avail $it" },
                                            parts.intOrNull("certification")?.let { "Cert $it" },
                                            parts.intOrNull("language")?.let { "Lang $it" }
                                        )
                                        if (metrics.isNotEmpty()) {
                                            Text(
                                                metrics.joinToString(" · "),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = sk.subText, maxLines = 1,
                                            )
                                        }
                                    }
                                }
                                Text(
                                    displayCoverage,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = coverageColor,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                Button(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = sk.surface1, contentColor = sk.cyan), shape = RoundedCornerShape(24.dp)) {
                    Text("Search Wider Trainer Network", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(8.dp))
                    Icon(painterResource(R.drawable.ic_globe), null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ── Ranked list with trailing stat: highest-exposure courses (Design V2) ──
// Aggregates only fields already present on every batch row — the trailing
// stat is the open-batch count with a blocked sub-count, so the list genuinely
// answers "which courses are consuming the team's attention" without inventing
// any score or metric. A certification "renewal campaign" card was deliberately
// NOT added: this screen's only capacity data is demand coverage (`coverage_pct`
// on CapacityPlanResponse), and presenting that as certification status would
// be misleading fabrication.
@Composable
private fun TopExposureCard(batches: List<Map<*, *>>) {
    val sk = MaterialTheme.skill
    val rows = remember(batches) {
        batches
            .groupBy { it.str("course_name").ifBlank { "Unspecified course" } }
            .map { (course, courseBatches) ->
                val open = courseBatches.size
                val blocked = courseBatches.count { it.str("coverage_status") == "No Coverage" }
                Triple(course, open, blocked)
            }
            .sortedWith(compareByDescending<Triple<String, Int, Int>> { it.second }.thenByDescending { it.third })
            .take(5)
    }
    if (rows.size < 2) return

    val maxOpen = rows.maxOf { it.second }.coerceAtLeast(1)

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = sk.surface1),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("TOP EXPOSURE COURSES", style = MaterialTheme.typography.labelSmall, color = sk.labelText, fontWeight = FontWeight.Bold)
            Text("Where unallocated demand is concentrated", style = MaterialTheme.typography.labelSmall, color = sk.subText)
            Spacer(Modifier.height(12.dp))
            rows.forEach { (course, open, blocked) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(course, style = MaterialTheme.typography.bodyMedium, color = sk.bodyText, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { (open / maxOpen.toFloat()).coerceIn(0f, 1f) },
                            color = if (blocked > 0) sk.warn else sk.sky,
                            trackColor = sk.surface1,
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("$open", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = sk.frost)
                        Text(if (blocked > 0) "$blocked blocked" else "all covered", style = MaterialTheme.typography.labelSmall, color = if (blocked > 0) sk.warn else sk.subText)
                    }
                }
                Spacer(Modifier.height(12.dp))
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


