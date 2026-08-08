package com.example.skillsync.ui.batch

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.ui.unit.em
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

/** Coverage tri-state -> (label, colour, glyph). Backend already returns the label. */
@Composable
internal fun coverageStyle(coverage: String): Triple<String, Color, Int> {
    val sk = MaterialTheme.skill
    return when (coverage) {
        "Best Match" -> Triple("Best Match", sk.aqua, R.drawable.ic_check)
        "Available with Upskilling" -> Triple("Available with Upskilling", sk.warn, R.drawable.ic_flag)
        else -> Triple("No Coverage", sk.crit, R.drawable.ic_alert)
    }
}

private enum class MatchBand(val label: String) {
    ALL("All"), HIGH("75%+ Ready"), MEDIUM("50-74% Partial"), LOW("Under 50%"),
}

private fun matchBandOf(relevance: Int) = when {
    relevance >= 75 -> MatchBand.HIGH
    relevance >= 50 -> MatchBand.MEDIUM
    else -> MatchBand.LOW
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AllocationDeskContent(
    data: Map<String, Any>,
    newIds: Set<String>,
    onBatchClick: (Map<*, *>) -> Unit,
    // Hoisted rather than taking the ViewModel: a content composable that owns
    // a ViewModel cannot be rendered in the JVM screen tests, and these two
    // values are all the wider-network lookup actually needs.
    globalSearchData: Map<String, Any>? = null,
    onGlobalSearch: (String) -> Unit = {},
) {
    val sk = MaterialTheme.skill
    val batches = data.rows("batches")
    val summary = data.obj("summary")

    var query by remember { mutableStateOf("") }
    var matchBand by remember { mutableStateOf(MatchBand.ALL) }
    var selectedModes by remember { mutableStateOf(setOf<String>()) }
    var selectedLanguages by remember { mutableStateOf(setOf<String>()) }
    var selectedSkillLevels by remember { mutableStateOf(setOf<String>()) }
    var showFilters by remember { mutableStateOf(false) }
    var fmatExpanded by remember { mutableStateOf(true) }
    var iltExpanded by remember { mutableStateOf(true) }
    var iloExpanded by remember { mutableStateOf(true) }
    var otherExpanded by remember { mutableStateOf(true) }
    
    var globalSearchCourse by remember { mutableStateOf<String?>(null) }

    // Built from what's actually in the data, not a guessed enum — RMS's real
    // delivery-mode strings have proven inconsistent before (see AI/CONTEXT.md).
    val availableModes = remember(batches) {
        batches.map { it.str("delivery_mode") }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val availableLanguages = remember(batches) {
        batches.map { it.str("language") }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val availableSkillLevels = remember(batches) {
        batches.map { it.str("skill_level") }.filter { it.isNotBlank() }.distinct().sorted()
    }

    // Filtering narrows the set; it never reorders it. The list a manager sees
    // is either the untouched RMS order or a subset of it — arrival order is
    // how demand is actually worked, and re-sorting by match% (the previous
    // behaviour) buried high-priority batches the team can't yet cover.
    val filtered = remember(batches, query, matchBand, selectedModes, selectedLanguages, selectedSkillLevels) {
        batches.filter { b ->
            val q = query.trim().lowercase()
            val matchesQuery = q.isBlank() ||
                b.str("course_name").lowercase().contains(q) ||
                b.str("customer").lowercase().contains(q) ||
                b.str("delivery_mode").lowercase().contains(q) ||
                b.str("demand_id").contains(q)
            val matchesBand = matchBand == MatchBand.ALL || matchBandOf(b.int("relevance")) == matchBand
            val matchesMode = selectedModes.isEmpty() || b.str("delivery_mode") in selectedModes
            val matchesLang = selectedLanguages.isEmpty() || b.str("language") in selectedLanguages
            val matchesSkill = selectedSkillLevels.isEmpty() || b.str("skill_level") in selectedSkillLevels
            matchesQuery && matchesBand && matchesMode && matchesLang && matchesSkill
        }
    }

    // Grouped by delivery mode, because FMAT, ILT and ILO are three different
    // products and a manager staffs them differently:
    //
    //   FMAT — the trainer travels to the customer. Highest delivery cost,
    //          the only mode carrying travel and visa exposure, and the one
    //          needing the earliest decision and the most experienced person.
    //   ILT  — classroom delivery at a Koenig site. Instructor-present and
    //          high value, without the travel commitment.
    //   ILO  — online delivery. The volume tier.
    //
    // FMAT and ILT both lead ILO whatever their location; an international
    // engagement is flagged on the card rather than given its own section, so
    // the mode grouping stays legible. Within each group RMS arrival order is
    // preserved — the grouping carries business priority, the order inside it
    // is how demand actually arrives.
    val fmatBatches = remember(filtered) { filtered.filter { it.str("delivery_mode_kind") == "FMAT" } }
    val iltBatches = remember(filtered) { filtered.filter { it.str("delivery_mode_kind") == "ILT" } }
    val iloBatches = remember(filtered) { filtered.filter { it.str("delivery_mode_kind") == "ILO" } }
    val otherModeBatches = remember(filtered) {
        filtered.filter { it.str("delivery_mode_kind") !in listOf("FMAT", "ILT", "ILO") }
    }

    val activeFilterCount = selectedModes.size + selectedLanguages.size + selectedSkillLevels.size + (if (matchBand != MatchBand.ALL) 1 else 0)

    if (showFilters) {
        FilterBottomSheet(
            availableModes = availableModes,
            availableLanguages = availableLanguages,
            availableSkillLevels = availableSkillLevels,
            selectedModes = selectedModes,
            selectedLanguages = selectedLanguages,
            selectedSkillLevels = selectedSkillLevels,
            matchBand = matchBand,
            onModesChange = { selectedModes = it },
            onLanguagesChange = { selectedLanguages = it },
            onSkillLevelsChange = { selectedSkillLevels = it },
            onBandChange = { matchBand = it },
            onReset = { 
                selectedModes = emptySet()
                selectedLanguages = emptySet()
                selectedSkillLevels = emptySet()
                matchBand = MatchBand.ALL 
            },
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
                val priorityCount = summary?.int("priority") ?: 0
                val atRisk = summary?.int("at_risk") ?: 0

                Box(Modifier.fillMaxWidth().glassSurface()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Demand Intelligence",
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
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatFigure("$priorityCount", "PRIORITY", sk.teal, Modifier.weight(1f))
                            StatFigure("$atRisk", "AT RISK", if (atRisk > 0) sk.crit else sk.aqua, Modifier.weight(1f))
                            StatFigure("$high", "BEST MATCH", sk.aqua, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(14.dp))
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
                        selectedLanguages.forEach { lang ->
                            ActiveFilterChip(lang) { selectedLanguages = selectedLanguages - lang }
                        }
                        selectedSkillLevels.forEach { skill ->
                            ActiveFilterChip(skill) { selectedSkillLevels = selectedSkillLevels - skill }
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

        // FMAT first: trainer travel means the longest lead time.
        modeSection(
            batches = fmatBatches,
            title = "FMAT — Trainer travels to customer",
            subtitle = "Highest delivery cost · travel and visa lead time",
            tint = sk.teal,
            expanded = fmatExpanded,
            onToggle = { fmatExpanded = !fmatExpanded },
            keyPrefix = "fmat_",
            newIds = newIds,
            isPriority = true,
            onGlobalSearch = { course -> globalSearchCourse = course; onGlobalSearch(course) },
            onBatchClick = onBatchClick,
        )

        modeSection(
            batches = iltBatches,
            title = "ILT — Classroom delivery",
            subtitle = "Instructor present on site",
            tint = sk.sky,
            expanded = iltExpanded,
            onToggle = { iltExpanded = !iltExpanded },
            keyPrefix = "ilt_",
            newIds = newIds,
            isPriority = true,
            onGlobalSearch = { course -> globalSearchCourse = course; onGlobalSearch(course) },
            onBatchClick = onBatchClick,
        )

        modeSection(
            batches = otherModeBatches,
            title = "Unspecified delivery mode",
            subtitle = "RMS did not state a mode — worth checking",
            tint = sk.warn,
            expanded = otherExpanded,
            onToggle = { otherExpanded = !otherExpanded },
            keyPrefix = "oth_",
            newIds = newIds,
            isPriority = true,
            onGlobalSearch = { course -> globalSearchCourse = course; onGlobalSearch(course) },
            onBatchClick = onBatchClick,
        )

        modeSection(
            batches = iloBatches,
            title = "ILO — Online delivery",
            subtitle = "Remote instructor-led",
            tint = sk.subText,
            expanded = iloExpanded,
            onToggle = { iloExpanded = !iloExpanded },
            keyPrefix = "ilo_",
            newIds = newIds,
            isPriority = false,
            onGlobalSearch = { course -> globalSearchCourse = course; onGlobalSearch(course) },
            onBatchClick = onBatchClick,
        )

        item { Spacer(Modifier.height(20.dp)) }
    }

    if (globalSearchCourse != null) {
        ModalBottomSheet(
            onDismissRequest = { globalSearchCourse = null },
            containerColor = sk.cardBg,
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth().padding(bottom = 24.dp)) {
                Text(
                    "Global Network Search",
                    style = MaterialTheme.typography.headlineSmall,
                    color = sk.bodyText,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    globalSearchCourse ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = sk.subText,
                )
                Spacer(Modifier.height(16.dp))
                
                // Snapshot the delegated state once: `globalSearchData` is a
                // `by collectAsState()` delegate, so Kotlin cannot smart-cast
                // it inside the branches below.
                val net = globalSearchData
                val netTrainers = net?.list("trainers").orEmpty()
                when {
                    net == null -> Box(
                        Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(color = sk.teal)
                    }

                    // RMS has not accepted any TrainerType value for this
                    // endpoint, so the question could not be asked. Saying
                    // "no trainers available" here would be a claim about the
                    // company's bench that we have no evidence for.
                    !net.bool("available") -> Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painterResource(R.drawable.ic_alert), null,
                                tint = sk.warn, modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Wider network unavailable",
                                style = MaterialTheme.typography.titleSmall,
                                color = sk.warn, fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            net.str("note").ifBlank {
                                "RMS did not accept this lookup, so the wider " +
                                "trainer network could not be searched."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = sk.subText,
                        )
                    }

                    netTrainers.isEmpty() -> Text(
                        "No trainers outside your team are mapped to this course.",
                        style = MaterialTheme.typography.bodySmall, color = sk.subText,
                    )

                    else -> LazyColumn(
                        Modifier.fillMaxWidth().heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(netTrainers.size) { i ->
                            val t = netTrainers[i]
                            Column(Modifier.fillMaxWidth()) {
                                Text(
                                    t.str("TrainerName").ifBlank { "Unnamed trainer" },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = sk.bodyText,
                                )
                                val meta = listOfNotNull(
                                    t.str("Type").takeIf { it.isNotBlank() },
                                    t.str("BaseLocation").takeIf { it.isNotBlank() },
                                ).joinToString(" · ")
                                if (meta.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        meta, style = MaterialTheme.typography.bodySmall,
                                        color = sk.subText,
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider(color = sk.cardBorder)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Filters ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBottomSheet(
    availableModes: List<String>,
    availableLanguages: List<String>,
    availableSkillLevels: List<String>,
    selectedModes: Set<String>,
    selectedLanguages: Set<String>,
    selectedSkillLevels: Set<String>,
    matchBand: MatchBand,
    onModesChange: (Set<String>) -> Unit,
    onLanguagesChange: (Set<String>) -> Unit,
    onSkillLevelsChange: (Set<String>) -> Unit,
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
                        }
                    }
                }
            }
            if (availableLanguages.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text("Language".uppercase(), style = MaterialTheme.typography.labelSmall, color = sk.subText, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    availableLanguages.forEach { lang ->
                        val checked = lang in selectedLanguages
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onLanguagesChange(if (checked) selectedLanguages - lang else selectedLanguages + lang)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = {
                                onLanguagesChange(if (checked) selectedLanguages - lang else selectedLanguages + lang)
                            })
                            Spacer(Modifier.width(4.dp))
                            Text(lang, style = MaterialTheme.typography.bodyMedium, color = sk.bodyText)
                        }
                    }
                }
            }
            if (availableSkillLevels.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text("Skill Level".uppercase(), style = MaterialTheme.typography.labelSmall, color = sk.subText, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    availableSkillLevels.forEach { skill ->
                        val checked = skill in selectedSkillLevels
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onSkillLevelsChange(if (checked) selectedSkillLevels - skill else selectedSkillLevels + skill)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = {
                                onSkillLevelsChange(if (checked) selectedSkillLevels - skill else selectedSkillLevels + skill)
                            })
                            Spacer(Modifier.width(4.dp))
                            Text(skill, style = MaterialTheme.typography.bodyMedium, color = sk.bodyText)
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
private fun StatFigure(value: String, label: String, tint: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tint)
        Text(
            label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.skill.labelText,
            fontSize = 8.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.em,
        )
    }
}

@Composable
private fun MiniStat(label: String, value: String, tint: Color) {
    Column {
        Text(
            value, style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold, color = tint, fontSize = 11.5.sp,
        )
        Text(
            label.uppercase(), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.skill.labelText, fontSize = 8.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 0.06.em,
        )
    }
}

// ── Batch card ───────────────────────────────────────────────────────────────

@Composable
internal fun BatchCard(
    b: Map<*, *>,
    isNew: Boolean,
    isPriority: Boolean = true,
    /** Offered only when this manager's own team maps to nobody. */
    onGlobalSearch: ((String) -> Unit)? = null,
    onClick: () -> Unit,
) {
    val sk = MaterialTheme.skill
    val candidates = b.list("candidates")
    val mode = b.str("delivery_mode")
    val (coverageLabel, coverageTint, coverageIcon) = coverageStyle(b.str("coverage_status"))
    val risk = b.str("assignment_risk")
    val riskTint = when (risk) { "High" -> sk.crit; "Medium" -> sk.warn; else -> sk.aqua }
    val international = b.bool("is_international") && b.str("delivery_mode_kind") in listOf("FMAT", "ILT")
    val managerRecommendation = b.obj("manager_recommendation")

    Box(
        Modifier
            .fillMaxWidth()
            .animateContentSize()
            .accentGlass(
                if (international) sk.sky else if (isPriority) sk.teal else sk.subText.copy(alpha = 0.3f),
                RoundedCornerShape(Radii.card), strong = isPriority || international,
            )
            .clickable(onClick = onClick),
    ) {
        Row {
            // Coverage is the primary scan signal — can my team even do this —
            // so it owns the leading edge, same convention as the roster card.
            Box(
                Modifier.width(4.dp).fillMaxHeight()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(coverageTint, coverageTint.copy(alpha = 0.3f))
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
                            if (isPriority) {
                                Surface(color = sk.teal, shape = RoundedCornerShape(4.dp)) {
                                    Text(
                                        "★ PRIORITY", style = MaterialTheme.typography.labelSmall,
                                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 9.sp,
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
                            }
                            if (international) {
                                Spacer(Modifier.width(6.dp))
                                InternationalBadge()
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            b.str("course_name").ifBlank { "Course not specified" },
                            style = MaterialTheme.typography.titleSmall, color = sk.bodyText,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        // Vendor first — the customer relationship, not a demand-id.
                        Text(
                            b.str("customer").ifBlank { "Vendor not specified" },
                            style = MaterialTheme.typography.labelSmall, color = sk.labelText,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(3.dp))
                        // Start -> End on one row, plus pax — the two facts a
                        // manager needs before anything else about timing.
                        Text(
                            listOfNotNull(
                                listOfNotNull(
                                    b.str("start_date").takeIf { it.isNotBlank() }?.shortDate(),
                                    b.str("end_date").takeIf { it.isNotBlank() }?.shortDate(),
                                ).joinToString(" → ").takeIf { it.isNotBlank() },
                                b.intOrNull("participants")?.takeIf { it > 0 }?.let { "$it pax" },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall, color = sk.subText,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Icon(painterResource(coverageIcon), null, tint = coverageTint, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.height(2.dp))
                        Text(
                            coverageLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = coverageTint, fontWeight = FontWeight.Bold, fontSize = 9.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            modifier = Modifier.widthIn(max = 88.dp),
                        )
                    }
                }

                if (international) {
                    Spacer(Modifier.height(10.dp))
                    InternationalOpportunityBanner(b)
                }

                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    MiniStat("Revenue", b.str("revenue_potential").ifBlank { "—" }, sk.indigo)
                    MiniStat("Priority", "${b.intOrNull("priority_score") ?: 0}", sk.teal)
                    MiniStat("Risk", risk.ifBlank { "—" }, riskTint)
                }

                if (candidates.isNotEmpty()) {
                    Spacer(Modifier.height(9.dp))
                    HorizontalDivider(color = sk.cardBorder)
                    Spacer(Modifier.height(7.dp))
                    Text(
                        "RECOMMENDED TRAINERS",
                        style = MaterialTheme.typography.labelSmall, color = sk.labelText,
                        fontSize = 8.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.em,
                    )
                    Spacer(Modifier.height(5.dp))
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
                                            listOfNotNull(
                                                backupRole,
                                                c.intOrNull("suitability_score")?.let { "$it suitability" },
                                                c.intOrNull("utilization")?.let { "${it}% utilised" },
                                            ).joinToString(" · "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = sk.subText, fontSize = 9.sp,
                                            maxLines = 1,
                                        )
                                    }
                                    val availability = c.obj("availability")
                                    val availabilityStatus = c.str("availability_status")
                                    if (availabilityStatus.isNotBlank()) {
                                        Text(
                                            when (availabilityStatus) {
                                                "available" -> "✓ Available for these dates"
                                                "conflict" -> "Schedule conflict · ${availability?.str("suggested_available_date")?.shortDate()} next"
                                                else -> "Availability unverified"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = when (availabilityStatus) {
                                                "available" -> sk.green
                                                "conflict" -> sk.red
                                                else -> sk.warn
                                            },
                                            fontSize = 8.5.sp, maxLines = 1,
                                        )
                                    }
                                    c.obj("suitability_components")?.let { parts ->
                                        Text(
                                            "Skill ${parts.int("skill")} · Ready ${parts.int("readiness")} · " +
                                                "Avail ${parts.int("availability")} · Cert ${parts.int("certification")} · Lang ${parts.int("language")}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = sk.subText, fontSize = 8.sp, maxLines = 1,
                                        )
                                    }
                                }
                                Text(
                                    if (blocked) "Blocked" else c.str("coverage"),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = dotTint, fontSize = 9.5.sp,
                                )
                            }

                            val missing = c.list("missing_skills").joinToString(", ")
                            when {
                                blocked -> Text(
                                    "Negative feedback — not auto-allocated until ${c.str("blocked_until").shortDate()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = sk.red,
                                    modifier = Modifier.padding(start = 13.dp, top = 2.dp)
                                )
                                missing.isNotBlank() -> Text(
                                    "Missing: $missing",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = sk.amber,
                                    modifier = Modifier.padding(start = 13.dp, top = 2.dp)
                                )
                                c.int("match") < 75 -> Text(
                                    "Upskilling required",
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
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.material3.Button(
                        onClick = { onGlobalSearch?.invoke(b.str("course_name")) },
                        enabled = onGlobalSearch != null,
                        shape = RoundedCornerShape(8.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = sk.blue.copy(alpha = 0.15f),
                            contentColor = sk.blue
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(painterResource(R.drawable.ic_search), contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Global Network Search", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                if (managerRecommendation != null) {
                    Spacer(Modifier.height(9.dp))
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(sk.aqua.copy(alpha = 0.10f))
                            .padding(horizontal = 9.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("★", color = sk.aqua, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(7.dp))
                        Column {
                            Text(
                                "Recommend Aishwar · ${managerRecommendation.int("skill_match")}% match · Level ${managerRecommendation.int("suggested_skill_level")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = sk.aqua, fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                            )
                            Text(
                                "Suggested ${managerRecommendation.str("suggested_availability").shortDate()} · " +
                                    if (managerRecommendation.bool("availability_verified")) "availability verified" else "availability unverified",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (managerRecommendation.bool("availability_verified")) sk.green else sk.warn,
                                fontSize = 8.5.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InternationalBadge() {
    val sk = MaterialTheme.skill
    val transition = rememberInfiniteTransition(label = "internationalGlobe")
    val rotation by transition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "globeRotation",
    )
    Surface(color = sk.sky.copy(alpha = 0.14f), shape = RoundedCornerShape(4.dp)) {
        Row(
            Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.ic_globe), contentDescription = null,
                tint = sk.sky, modifier = Modifier.size(11.dp).rotate(rotation),
            )
            Spacer(Modifier.width(3.dp))
            Text(
                "GLOBAL OPPORTUNITY", color = sk.sky, fontSize = 8.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 0.05.em,
            )
        }
    }
}

@Composable
private fun InternationalOpportunityBanner(batch: Map<*, *>) {
    val sk = MaterialTheme.skill
    val transition = rememberInfiniteTransition(label = "internationalOpportunity")
    val globeRotation by transition.animateFloat(
        initialValue = -5f, targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
        label = "internationalOpportunityGlobe",
    )
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(sk.sky.copy(alpha = 0.18f), sk.indigo.copy(alpha = 0.10f))
                )
            )
            .border(1.dp, sk.sky.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(horizontal = 11.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = sk.sky.copy(alpha = 0.20f), shape = RoundedCornerShape(9.dp)) {
                Icon(
                    painterResource(R.drawable.ic_globe), contentDescription = "International opportunity",
                    tint = sk.sky, modifier = Modifier.padding(7.dp).size(19.dp).rotate(globeRotation),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "INTERNATIONAL ${batch.str("delivery_mode_kind")} OPPORTUNITY",
                    style = MaterialTheme.typography.labelSmall,
                    color = sk.sky, fontWeight = FontWeight.ExtraBold,
                    fontSize = 9.sp, letterSpacing = 0.06.em,
                )
                Text(
                    batch.str("location").ifBlank { "Foreign location" },
                    style = MaterialTheme.typography.bodySmall,
                    color = sk.bodyText, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (batch.str("delivery_mode_kind") == "FMAT")
                        "TRAVEL REQUIRED · Visa and schedule readiness require manager review"
                    else
                        "INTERNATIONAL CLASSROOM · Confirm travel, location and trainer readiness",
                    style = MaterialTheme.typography.labelSmall,
                    color = sk.subText, fontSize = 8.5.sp, maxLines = 2,
                )
            }
            Surface(color = sk.indigo.copy(alpha = 0.16f), shape = RoundedCornerShape(7.dp)) {
                Text(
                    batch.str("revenue_potential").ifBlank { "Priority" },
                    style = MaterialTheme.typography.labelSmall,
                    color = sk.indigo, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                )
            }
        }
    }
}


/**
 * One delivery-mode band on the demand board.
 *
 * A `LazyListScope` extension rather than a composable so each batch stays its
 * own lazy item — wrapping a whole mode in a single item would compose every
 * card in it at once and lose recycling on a long board.
 */
private fun LazyListScope.modeSection(
    batches: List<Map<*, *>>,
    title: String,
    subtitle: String,
    tint: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    keyPrefix: String,
    newIds: Set<String>,
    isPriority: Boolean,
    onGlobalSearch: (String) -> Unit,
    onBatchClick: (Map<*, *>) -> Unit,
) {
    if (batches.isEmpty()) return

    item(key = keyPrefix + "header") {
        Spacer(Modifier.height(4.dp))
        ModeSectionHeader(
            title = title,
            subtitle = subtitle,
            count = batches.size,
            internationalCount = batches.count { it.bool("is_international") },
            tint = tint,
            expanded = expanded,
            onToggle = onToggle,
        )
    }
    if (expanded) {
        itemsIndexed(batches, key = { _, b -> keyPrefix + b.str("demand_id") }) { i, b ->
            Appear(i) {
                BatchCard(
                    b,
                    isNew = b.str("demand_id") in newIds,
                    isPriority = isPriority,
                    onGlobalSearch = onGlobalSearch,
                ) { onBatchClick(b) }
            }
        }
    }
}

/** Mode band header: what the mode is, how many, and how many are abroad. */
@Composable
private fun ModeSectionHeader(
    title: String,
    subtitle: String,
    count: Int,
    internationalCount: Int,
    tint: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
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
        Box(Modifier.width(4.dp).height(30.dp).clip(RoundedCornerShape(2.dp)).background(tint))
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = sk.frost,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = sk.labelText,
                fontSize = 9.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (internationalCount > 0) {
            Surface(color = sk.indigo.copy(alpha = 0.18f), shape = RoundedCornerShape(10.dp)) {
                Text(
                    "$internationalCount abroad",
                    style = MaterialTheme.typography.labelSmall,
                    color = sk.indigo,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
        }
        Surface(color = tint.copy(alpha = 0.16f), shape = RoundedCornerShape(10.dp)) {
            Text(
                "$count",
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        Icon(
            painterResource(R.drawable.ic_chevron), null, tint = sk.subText,
            modifier = Modifier.size(14.dp).rotate(rotation),
        )
    }
}
