package com.example.skillsync.ui.batch

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.skillsync.R
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.accentGlass
import com.example.skillsync.theme.glassSurface
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.components.*

/** Relevance -> colour. 75%+ is green, matching the agreed banding. */
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

// ── Delivery-mode priority ───────────────────────────────────────────────────

/**
 * ILT and FMAT together are the priority tier a manager needs to staff first;
 * ILO is deliberately kept below regardless of date. Anything else RMS
 * returns (a mode we haven't seen, or a typo in the source data) defaults to
 * the priority tier rather than being silently buried — an unrecognised mode
 * is a data-quality question, not a reason to demote it.
 */
private fun isDeprioritisedMode(mode: String): Boolean {
    val m = mode.uppercase()
    return m.contains("ILO")
}

private enum class MatchBand(val label: String) {
    ALL("All"), HIGH("75%+ Ready"), MEDIUM("50-74% Partial"), LOW("Under 50%"),
}

private fun matchBandOf(relevance: Int) = when {
    relevance >= 75 -> MatchBand.HIGH
    relevance >= 50 -> MatchBand.MEDIUM
    else -> MatchBand.LOW
}

@Composable
internal fun AllocationDeskContent(
    data: Map<String, Any>,
    newIds: Set<String>,
    onBatchClick: (Map<*, *>) -> Unit,
) {
    val sk = MaterialTheme.skill
    val batches = data.rows("batches")
    val summary = data.obj("summary")

    var query by remember { mutableStateOf("") }
    var matchBand by remember { mutableStateOf(MatchBand.ALL) }
    var selectedModes by remember { mutableStateOf(setOf<String>()) }
    var showFilters by remember { mutableStateOf(false) }
    var priorityExpanded by remember { mutableStateOf(true) }
    var otherExpanded by remember { mutableStateOf(true) }

    // Built from what's actually in the data, not a guessed enum — RMS's real
    // delivery-mode strings have proven inconsistent before (see AI/CONTEXT.md).
    val availableModes = remember(batches) {
        batches.map { it.str("delivery_mode") }.filter { it.isNotBlank() }.distinct().sorted()
    }

    val filtered = remember(batches, query, matchBand, selectedModes) {
        batches.filter { b ->
            val q = query.trim().lowercase()
            val matchesQuery = q.isBlank() ||
                b.str("course_name").lowercase().contains(q) ||
                b.str("customer").lowercase().contains(q) ||
                b.str("delivery_mode").lowercase().contains(q) ||
                b.str("demand_id").contains(q)
            val matchesBand = matchBand == MatchBand.ALL || matchBandOf(b.int("relevance")) == matchBand
            val matchesMode = selectedModes.isEmpty() || b.str("delivery_mode") in selectedModes
            matchesQuery && matchesBand && matchesMode
        }
    }

    // Segregated by priority, each tier sorted by delivery date descending.
    val (priorityBatches, otherBatches) = remember(filtered) {
        val sorted = filtered.sortedByDescending { it.str("start_date") }
        sorted.partition { !isDeprioritisedMode(it.str("delivery_mode")) }
    }

    val activeFilterCount = selectedModes.size + (if (matchBand != MatchBand.ALL) 1 else 0)

    if (showFilters) {
        FilterBottomSheet(
            availableModes = availableModes,
            selectedModes = selectedModes,
            matchBand = matchBand,
            onModesChange = { selectedModes = it },
            onBandChange = { matchBand = it },
            onReset = { selectedModes = emptySet(); matchBand = MatchBand.ALL },
            onDismiss = { showFilters = false },
        )
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column {
                // Coverage first: the manager's real question is not "how many
                // batches are open" but "how much of this can my team actually
                // cover" — which the relevance bands already answer.
                val high = summary?.int("high") ?: 0
                val medium = summary?.int("medium") ?: 0
                val unmatched = summary?.int("unmatched") ?: 0
                val total = summary?.int("total") ?: batches.size
                val partial = (total - high - medium - unmatched).coerceAtLeast(0)

                Box(Modifier.fillMaxWidth().glassSurface()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Coverage by fit",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = sk.frost,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "$total unallocated · ranked against your team's capability",
                            style = MaterialTheme.typography.labelSmall,
                            color = sk.labelText,
                            fontSize = 10.sp,
                        )
                        Spacer(Modifier.height(16.dp))
                        DistributionBar(
                            slices = listOf(
                                Slice("Strong fit", high, sk.aqua),
                                Slice("Partial", medium + partial, sk.sky),
                                Slice("No cover", unmatched, sk.crit),
                            )
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))

                if (newIds.isNotEmpty()) {
                    NewBatchBanner(newIds.size)
                    Spacer(Modifier.height(10.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search course, vendor, mode, ref", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(painterResource(R.drawable.ic_search), null, tint = sk.subText, modifier = Modifier.size(16.dp))
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box {
                        FilledTonalButton(
                            onClick = { showFilters = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (activeFilterCount > 0) sk.teal.copy(alpha = 0.16f) else sk.cardBg,
                            ),
                        ) {
                            Text(
                                if (activeFilterCount > 0) "Filters ($activeFilterCount)" else "Filters",
                                fontSize = 12.sp,
                                color = if (activeFilterCount > 0) sk.teal else sk.subText,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                if (activeFilterCount > 0) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (matchBand != MatchBand.ALL) {
                            ActiveFilterChip(matchBand.label) { matchBand = MatchBand.ALL }
                        }
                        selectedModes.forEach { mode ->
                            ActiveFilterChip(mode) { selectedModes = selectedModes - mode }
                        }
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (batches.isEmpty()) "No unallocated batches right now."
                        else "No batches match this filter.",
                        style = MaterialTheme.typography.bodySmall, color = sk.subText,
                    )
                }
            }
        }

        if (priorityBatches.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                SectionHeader(
                    title = "Priority — ILT + FMAT",
                    count = priorityBatches.size,
                    tint = sk.teal,
                    expanded = priorityExpanded,
                    onToggle = { priorityExpanded = !priorityExpanded },
                )
            }
        }
        if (priorityExpanded) {
            itemsIndexed(priorityBatches, key = { _, b -> "p_" + b.str("demand_id") }) { i, b ->
                Appear(i) {
                    BatchCard(b, isNew = b.str("demand_id") in newIds, isPriority = true) { onBatchClick(b) }
                }
            }
        }

        if (otherBatches.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                SectionHeader(
                    title = "Other Delivery Modes (ILO)",
                    count = otherBatches.size,
                    tint = sk.subText,
                    expanded = otherExpanded,
                    onToggle = { otherExpanded = !otherExpanded },
                )
            }
        }
        if (otherExpanded) {
            itemsIndexed(otherBatches, key = { _, b -> "o_" + b.str("demand_id") }) { i, b ->
                Appear(i) {
                    BatchCard(b, isNew = b.str("demand_id") in newIds, isPriority = false) { onBatchClick(b) }
                }
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}

// ── Filters ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBottomSheet(
    availableModes: List<String>,
    selectedModes: Set<String>,
    matchBand: MatchBand,
    onModesChange: (Set<String>) -> Unit,
    onBandChange: (MatchBand) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sk = MaterialTheme.skill
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = sk.cardBg) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Filter batches", style = MaterialTheme.typography.headlineSmall, color = sk.bodyText, modifier = Modifier.weight(1f))
                TextButton(onClick = onReset) { Text("Reset", color = sk.red, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(16.dp))

            Text("Skill match".uppercase(), style = MaterialTheme.typography.labelSmall, color = sk.subText, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MatchBand.entries.forEach { band ->
                    FilterChip(
                        selected = matchBand == band,
                        onClick = { onBandChange(band) },
                        label = { Text(band.label, fontSize = 11.sp) },
                    )
                }
            }

            if (availableModes.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text("Delivery mode".uppercase(), style = MaterialTheme.typography.labelSmall, color = sk.subText, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    availableModes.forEach { mode ->
                        val checked = mode in selectedModes
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onModesChange(if (checked) selectedModes - mode else selectedModes + mode)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = {
                                onModesChange(if (checked) selectedModes - mode else selectedModes + mode)
                            })
                            Spacer(Modifier.width(4.dp))
                            Text(mode, style = MaterialTheme.typography.bodyMedium, color = sk.bodyText)
                            if (isDeprioritisedMode(mode)) {
                                Spacer(Modifier.width(6.dp))
                                MiniTag("below priority", sk.subText)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Show results") }
        }
    }
}

/**
 * Small tinted label, same visual language as the app-wide `Chip` in
 * ui.main.MainScreen.kt — redeclared locally because BatchDetailScreen.kt
 * (same package) already owns the name `Chip` as a file-private composable,
 * and Kotlin resolves an unqualified same-package name before a wildcard
 * import from a different package.
 */
@Composable
private fun MiniTag(text: String, tint: Color) {
    Surface(color = tint.copy(alpha = 0.14f), shape = RoundedCornerShape(12.dp)) {
        Text(
            text, style = MaterialTheme.typography.labelSmall, color = tint,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun ActiveFilterChip(label: String, onRemove: () -> Unit) {
    val sk = MaterialTheme.skill
    Surface(
        color = sk.teal.copy(alpha = 0.14f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.clickable(onClick = onRemove),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = sk.teal, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            Spacer(Modifier.width(4.dp))
            Text("×", style = MaterialTheme.typography.labelSmall, color = sk.teal, fontWeight = FontWeight.ExtraBold)
        }
    }
}

// ── Section header ───────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, count: Int, tint: Color, expanded: Boolean, onToggle: () -> Unit) {
    val sk = MaterialTheme.skill
    val rotation by animateFloatAsStateCompat(if (expanded) 90f else 0f)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(tint))
        Spacer(Modifier.width(8.dp))
        Text(
            title, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = sk.bodyText, modifier = Modifier.weight(1f),
        )
        Surface(color = tint.copy(alpha = 0.14f), shape = RoundedCornerShape(10.dp)) {
            Text(
                "$count", style = MaterialTheme.typography.labelSmall, color = tint,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        Icon(
            painterResource(R.drawable.ic_chevron), null, tint = sk.subText,
            modifier = Modifier.size(14.dp).rotate(rotation),
        )
    }
}

@Composable
private fun animateFloatAsStateCompat(target: Float) =
    androidx.compose.animation.core.animateFloatAsState(target, tween(Motion.FAST), label = "chevron")

@Composable
private fun NewBatchBanner(count: Int) {
    val sk = MaterialTheme.skill
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(sk.blue.copy(alpha = 0.13f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_inbox), null, tint = sk.blue, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            if (count == 1) "1 new batch since you last checked"
            else "$count new batches since you last checked",
            style = MaterialTheme.typography.titleSmall, color = sk.bodyText,
        )
    }
}

@Composable
private fun SummaryPill(icon: Int, label: String, value: Int, tint: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.skill.cardBg).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Column {
            Text("$value", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = tint)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.skill.subText, fontSize = 9.sp)
        }
    }
}

// ── Batch card ───────────────────────────────────────────────────────────────

@Composable
internal fun BatchCard(b: Map<*, *>, isNew: Boolean, isPriority: Boolean = true, onClick: () -> Unit) {
    val sk = MaterialTheme.skill
    val relevance = b.int("relevance")
    val tint = relevanceColor(relevance)
    val candidates = b.list("candidates")
    val mode = b.str("delivery_mode")

    Box(
        Modifier
            .fillMaxWidth()
            .animateContentSize()
            .accentGlass(tint, RoundedCornerShape(Radii.card), strong = isPriority)
            .clickable(onClick = onClick),
    ) {
        Row {
            // Relevance is the primary scan signal, so it owns the leading edge.
            Box(
                Modifier.width(3.dp).fillMaxHeight()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(tint, tint.copy(alpha = 0.2f))
                        )
                    )
            )
            Column(Modifier.padding(start = 14.dp, top = 13.dp, end = 13.dp, bottom = 13.dp).fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isNew) {
                                Surface(color = sk.blue, shape = RoundedCornerShape(4.dp)) {
                                    Text(
                                        "NEW", style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                            }
                            if (mode.isNotBlank()) {
                                Surface(
                                    color = (if (isPriority) sk.teal else sk.subText).copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(4.dp),
                                ) {
                                    Text(
                                        mode.uppercase(), style = MaterialTheme.typography.labelSmall,
                                        color = if (isPriority) sk.teal else sk.subText,
                                        fontWeight = FontWeight.Bold, fontSize = 9.sp,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            b.str("course_name").ifBlank { "Course not specified" },
                            style = MaterialTheme.typography.titleSmall, color = sk.bodyText,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            listOfNotNull(
                                b.str("start_date").takeIf { it.isNotBlank() }?.shortDate(),
                                b.intOrNull("days")?.let { "${it}d" },
                                b.intOrNull("participants")?.takeIf { it > 0 }?.let { "$it pax" },
                                b.str("customer").takeIf { it.isNotBlank() },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall, color = sk.subText,
                        )
                        val badges = listOfNotNull(
                            b.str("customer_priority").takeIf { it.isNotBlank() }?.let { "Priority: $it" },
                            b.str("revenue_impact").takeIf { it.isNotBlank() }?.let { "Rev: $it" },
                        )
                        if (badges.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                badges.forEach { MiniTag(it, sk.indigo) }
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "$relevance%",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold, color = tint,
                        )
                        Text("match", style = MaterialTheme.typography.labelSmall, color = sk.subText)
                    }
                }

                if (candidates.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = sk.cardBorder)
                    Spacer(Modifier.height(7.dp))
                    candidates.take(3).forEach { c ->
                        // RMS AutoTall parity: a trainer inside their 3-14 day
                        // negative-feedback window won't actually be
                        // auto-allocated, so showing them tinted as a "great
                        // match" would be misleading — this dot/text stays
                        // neutral-red regardless of match score when blocked.
                        val blocked = c.bool("blocked")
                        val dotTint = if (blocked) sk.red else relevanceColor(c.int("match"))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .then(if (blocked) Modifier.background(sk.red.copy(alpha = 0.05f), RoundedCornerShape(6.dp)).padding(4.dp) else Modifier),
                        ) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(dotTint))
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    if (blocked) "Blocked" else c.str("category"),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = dotTint,
                                    modifier = Modifier.weight(0.4f),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        c.str("trainer_name"),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (blocked) sk.subText else sk.bodyText,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                    // Previously only visible after opening the batch
                                    // detail screen — a manager scanning the list
                                    // couldn't tell which candidate was the actual
                                    // Primary pick vs. an Alternate at a glance.
                                    val backupRole = c.str("backup_role")
                                    if (!blocked && backupRole.isNotBlank()) {
                                        Text(
                                            backupRole,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = sk.subText, fontSize = 9.sp,
                                            maxLines = 1,
                                        )
                                    }
                                }
                                if (!blocked) {
                                    Text(
                                        "${c.int("match")}%",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = dotTint,
                                    )
                                }
                            }

                            val missing = c.list("missing_skills").joinToString(", ")
                            when {
                                blocked -> Text(
                                    "🚫 Negative feedback — not auto-allocated until ${c.str("blocked_until").shortDate()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = sk.red,
                                    modifier = Modifier.padding(start = 13.dp, top = 2.dp)
                                )
                                missing.isNotBlank() -> Text(
                                    "⚠ Missing: $missing",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = sk.amber,
                                    modifier = Modifier.padding(start = 13.dp, top = 2.dp)
                                )
                                c.int("match") < 75 -> Text(
                                    "⚠ Upskilling Required",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = sk.amber,
                                    modifier = Modifier.padding(start = 13.dp, top = 2.dp)
                                )
                                c.bool("recent_negative_6mo") -> Text(
                                    "Feedback on file within last 6 months",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = sk.subText,
                                    modifier = Modifier.padding(start = 13.dp, top = 2.dp)
                                )
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "No one on your team maps to this course.",
                        style = MaterialTheme.typography.labelSmall, color = sk.subText,
                    )
                }
            }
        }
    }
}
