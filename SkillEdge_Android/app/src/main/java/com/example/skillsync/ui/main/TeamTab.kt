package com.example.skillsync.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.skillsync.R
import com.example.skillsync.theme.IconSlot
import com.example.skillsync.theme.glassSurface
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.components.*

// ── Filter model ──────────────────────────────────────────────────────────────

enum class TeamSort(val label: String) {
    HEALTH("Health"),
    UTILISATION("Utilisation"),
    READINESS("Readiness"),
    RISK("Risk"),
    NAME("Name"),
    STATUS("Status"),
    CERT_GAPS("Cert gaps"),
}

/** Utilisation bands, kept as ranges so the chip label and the test agree. */
enum class UtilBand(val label: String, val range: IntRange) {
    STRETCHED("Over 85%", 86..100),
    BALANCED("60-85%", 60..85),
    LIGHT("30-59%", 30..59),
    BENCH("Under 30%", 0..29),
}

enum class ReadinessBand(val label: String) { READY("Ready"), DEVELOPING("Developing"), SUPPORT("Needs support") }

/** Matches trainer_operations_df.feedback_risk verbatim — no capability fetch needed. */
enum class RiskBand(val label: String) { HIGH("High"), MEDIUM("Medium"), LOW("Low") }

data class TeamFilters(
    val query: String = "",
    val sort: TeamSort = TeamSort.HEALTH,
    val status: String? = null,
    val util: UtilBand? = null,
    val readiness: ReadinessBand? = null,
    val risk: RiskBand? = null,
    val skill: String? = null,
    val certification: String? = null,
    val gapsOnly: Boolean = false,
) {
    /** Everything except sort and query — what the "Filters" badge counts. */
    val activeCount: Int
        get() = listOfNotNull(status, util, readiness, risk, skill, certification).size +
            (if (gapsOnly) 1 else 0)
}

private val STATUS_OPTIONS = listOf(
    "teaching_now" to "Delivering",
    "scheduled_today" to "Scheduled",
    "preparing" to "Preparing",
    "free" to "Available",
    "unknown" to "Unknown",
)

// ── Screen ────────────────────────────────────────────────────────────────────

/**
 * Team roster with filters that operate on real capability, not just the roster
 * row: skill and certification filtering needs `team-capability`, so those two
 * controls are disabled with an explanation until it arrives rather than
 * silently returning nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TeamTab(
    data: Map<String, Any>,
    capability: Map<String, Any>?,
    onTrainerClick: (String, String) -> Unit,
) {
    val sk = MaterialTheme.skill
    val columns = if (LocalConfiguration.current.screenWidthDp >= 600) 2 else 1
    val ops = data.rows("trainer_operations_df")
    val stateMap = data.rows("trainer_current_state_df").associateBy { it.str("trainer_email").lowercase() }
    val capMap = (capability?.rows("trainers") ?: emptyList())
        .associateBy { it.str("trainer_email").lowercase() }
    // Delivery readiness — always in the unified payload, no extra API call.
    val deliveryMap = data.rows("delivery_intelligence_df")
        .associateBy { it.str("trainer_email").lowercase() }

    var filters by remember { mutableStateOf(TeamFilters()) }
    var sheetOpen by remember { mutableStateOf(false) }

    // Options are derived from what the team actually holds, so the picker can
    // never offer a skill nobody has.
    val skillOptions = remember(capability) {
        capMap.values.flatMap { it.list("courses").map { c -> c.str("course") } }
            .distinct().sorted()
    }
    val certOptions = remember(capability) {
        capMap.values
            .flatMap { it.obj("certification")?.list("held").orEmpty().map { c -> c.str("name") } }
            .distinct().sorted()
    }

    val shown = remember(ops, filters, stateMap, capMap) {
        // Pulled out of the delegated `filters` property: Kotlin cannot smart-cast
        // through a state delegate, so the null checks below would not narrow.
        val f = filters
        val q = f.query.trim().lowercase()
        ops.filter { t ->
            val email = t.str("official_email").lowercase()
            val st = stateMap[email]
            val cap = capMap[email]
            val cert = cap?.obj("certification")

            val matchesQuery = q.isBlank() ||
                t.str("trainer_name").lowercase().contains(q) ||
                t.str("designation").lowercase().contains(q) ||
                email.contains(q) ||
                st?.obj("current_batch")?.str("course_name")?.lowercase()?.contains(q) == true ||
                st?.obj("next_batch")?.str("course_name")?.lowercase()?.contains(q) == true ||
                cap?.list("courses")?.any { c -> c.str("course").lowercase().contains(q) } == true

            val matchesStatus = f.status == null || st?.str("current_status") == f.status

            val u = t.intOrNull("current_utilization")
            val band = f.util
            val matchesUtil = band == null || (u != null && u in band.range)

            val matchesReadiness = f.readiness == null ||
                cap?.str("readiness_bucket") == f.readiness.label

            val matchesRisk = f.risk == null || t.str("feedback_risk") == f.risk.label

            val matchesSkill = f.skill == null ||
                cap?.list("courses")?.any { c -> c.str("course") == f.skill } == true

            val matchesCert = f.certification == null ||
                cert?.list("held")?.any { c -> c.str("name") == f.certification } == true

            val matchesGaps = !f.gapsOnly || (cert?.int("gap_count") ?: 0) > 0

            matchesQuery && matchesStatus && matchesUtil &&
                matchesReadiness && matchesRisk && matchesSkill && matchesCert && matchesGaps
        }.let { list ->
            when (f.sort) {
                TeamSort.HEALTH -> list.sortedBy { t ->
                    val email = t.str("official_email").lowercase()
                    trainerHealth(t, capMap[email], deliveryMap[email]).first
                }
                TeamSort.UTILISATION -> list.sortedByDescending { it.int("current_utilization") }
                TeamSort.NAME -> list.sortedBy { it.str("trainer_name") }
                TeamSort.STATUS -> list.sortedBy {
                    stateMap[it.str("official_email").lowercase()]?.str("status_label") ?: "zz"
                }
                TeamSort.READINESS -> list.sortedByDescending {
                    capMap[it.str("official_email").lowercase()]?.intOrNull("readiness_score") ?: -1
                }
                TeamSort.RISK -> list.sortedByDescending {
                    when (it.str("feedback_risk")) { "High" -> 2; "Medium" -> 1; else -> 0 }
                }
                TeamSort.CERT_GAPS -> list.sortedByDescending {
                    capMap[it.str("official_email").lowercase()]
                        ?.obj("certification")?.int("gap_count") ?: 0
                }
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column {
                SearchField(
                    value = filters.query,
                    onValueChange = { filters = filters.copy(query = it) },
                    placeholder = "Search name, designation or course",
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterButton(filters.activeCount) { sheetOpen = true }
                    SortMenu(filters.sort) { filters = filters.copy(sort = it) }
                    STATUS_OPTIONS.take(3).forEach { (key, label) ->
                        SelectChip(label, filters.status == key) {
                            filters = filters.copy(status = if (filters.status == key) null else key)
                        }
                    }
                }
                if (filters.activeCount > 0) {
                    Spacer(Modifier.height(6.dp))
                    ActiveFilterChips(filters) { filters = it }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "${shown.size} of ${ops.size} trainers",
                    style = MaterialTheme.typography.labelSmall, color = sk.subText,
                )
            }
        }

        if (shown.isEmpty()) {
            item {
                EmptyStateCard(
                    if (ops.isEmpty()) "No reportees returned. Check your account permissions."
                    else "No trainer matches these filters."
                )
            }
        }
        // Phones need a full-width command card so names, assignments and the
        // action signal remain legible. Tablets retain the two-up comparison
        // surface where each card still has enough physical width.
        val rows = shown.chunked(columns)
        itemsIndexed(rows) { rowIndex, pair ->
            Appear(rowIndex) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    pair.forEach { t ->
                        Box(Modifier.weight(1f)) {
                            TeamMemberCard(
                                trainer = t,
                                state = stateMap[t.str("official_email").lowercase()],
                                capability = capMap[t.str("official_email").lowercase()],
                                delivery = deliveryMap[t.str("official_email").lowercase()],
                            ) {
                                onTrainerClick(t.str("official_email"), t.str("trainer_name"))
                            }
                        }
                    }
                    // Keeps a lone trailing card at half width instead of
                    // letting it stretch across the row.
                    if (columns > 1 && pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    if (sheetOpen) {
        TeamFilterSheet(
            filters = filters,
            skillOptions = skillOptions,
            certOptions = certOptions,
            capabilityReady = capability != null,
            onApply = { filters = it },
            onDismiss = { sheetOpen = false },
        )
    }
}

// ── Filter surface ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeamFilterSheet(
    filters: TeamFilters,
    skillOptions: List<String>,
    certOptions: List<String>,
    capabilityReady: Boolean,
    onApply: (TeamFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    val sk = MaterialTheme.skill
    var draft by remember { mutableStateOf(filters) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = sk.cardBg) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Filter team", style = MaterialTheme.typography.headlineSmall, color = sk.bodyText)

            FilterGroup("Status") {
                STATUS_OPTIONS.forEach { (key, label) ->
                    SelectChip(label, draft.status == key) {
                        draft = draft.copy(status = if (draft.status == key) null else key)
                    }
                }
            }

            FilterGroup("Utilisation") {
                UtilBand.entries.forEach { b ->
                    SelectChip(b.label, draft.util == b) {
                        draft = draft.copy(util = if (draft.util == b) null else b)
                    }
                }
            }

            // Straight off trainer_operations_df -- no capability fetch needed,
            // so this stays enabled even before team-capability has loaded.
            FilterGroup("Risk") {
                RiskBand.entries.forEach { b ->
                    SelectChip(b.label, draft.risk == b) {
                        draft = draft.copy(risk = if (draft.risk == b) null else b)
                    }
                }
            }

            FilterGroup("Readiness") {
                ReadinessBand.entries.forEach { b ->
                    SelectChip(b.label, draft.readiness == b, enabled = capabilityReady) {
                        draft = draft.copy(readiness = if (draft.readiness == b) null else b)
                    }
                }
            }

            Dropdown(
                label = "Skill / course",
                value = draft.skill,
                options = skillOptions,
                enabled = capabilityReady && skillOptions.isNotEmpty(),
                onSelect = { draft = draft.copy(skill = it) },
            )

            Dropdown(
                label = "Certification held",
                value = draft.certification,
                options = certOptions,
                enabled = capabilityReady && certOptions.isNotEmpty(),
                onSelect = { draft = draft.copy(certification = it) },
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = draft.gapsOnly,
                    onCheckedChange = { draft = draft.copy(gapsOnly = it) },
                    enabled = capabilityReady,
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "Certification gaps only",
                        style = MaterialTheme.typography.bodyMedium, color = sk.bodyText,
                    )
                    Text(
                        "Trainers teaching a course they hold no certification for",
                        style = MaterialTheme.typography.labelSmall, color = sk.subText,
                    )
                }
            }

            if (!capabilityReady) {
                Text(
                    "Skill, certification and readiness filters need the capability data, " +
                        "which is still loading.",
                    style = MaterialTheme.typography.labelSmall, color = sk.amber,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { draft = TeamFilters(query = draft.query, sort = draft.sort) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("Clear all") }
                Button(
                    onClick = { onApply(draft); onDismiss() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("Show results") }
            }
        }
    }
}

@Composable
private fun FilterGroup(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.skill.subText,
            fontWeight = FontWeight.Bold, fontSize = 9.sp,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) { content() }
    }
}

@Composable
private fun ActiveFilterChips(filters: TeamFilters, onChange: (TeamFilters) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        filters.status?.let { s ->
            DismissChip(STATUS_OPTIONS.firstOrNull { it.first == s }?.second ?: s) {
                onChange(filters.copy(status = null))
            }
        }
        filters.util?.let { DismissChip(it.label) { onChange(filters.copy(util = null)) } }
        filters.readiness?.let { DismissChip(it.label) { onChange(filters.copy(readiness = null)) } }
        filters.skill?.let { DismissChip(it.take(28)) { onChange(filters.copy(skill = null)) } }
        filters.certification?.let {
            DismissChip(it.take(28)) { onChange(filters.copy(certification = null)) }
        }
        if (filters.gapsOnly) DismissChip("Has gaps") { onChange(filters.copy(gapsOnly = false)) }
    }
}

// ── Controls ──────────────────────────────────────────────────────────────────

@Composable
internal fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    val sk = MaterialTheme.skill
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, fontSize = 12.sp) },
        leadingIcon = {
            Icon(
                painterResource(R.drawable.ic_search), null,
                tint = sk.subText, modifier = Modifier.size(17.dp),
            )
        },
        trailingIcon = if (value.isNotEmpty()) {
            {
                TextButton(onClick = { onValueChange("") }, contentPadding = PaddingValues(6.dp)) {
                    Text("Clear", fontSize = 10.sp)
                }
            }
        } else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = sk.cardBg,
            focusedContainerColor = sk.cardBg,
            unfocusedBorderColor = sk.cardBorder,
        ),
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
    )
}

@Composable
private fun FilterButton(activeCount: Int, onClick: () -> Unit) {
    val sk = MaterialTheme.skill
    val on = activeCount > 0
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(9.dp),
        color = if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else sk.cardBg,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, if (on) MaterialTheme.colorScheme.primary else sk.cardBorder,
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Filters",
                style = MaterialTheme.typography.labelMedium, fontSize = 11.sp,
                color = if (on) MaterialTheme.colorScheme.primary else sk.bodyText,
            )
            if (on) {
                Spacer(Modifier.width(5.dp))
                Box(
                    Modifier.size(15.dp).clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$activeCount", color = Color.White,
                        fontSize = 8.5.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortMenu(current: TeamSort, onSelect: (TeamSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val sk = MaterialTheme.skill
    Box {
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(9.dp),
            color = sk.cardBg,
            border = androidx.compose.foundation.BorderStroke(1.dp, sk.cardBorder),
        ) {
            Row(
                Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Sort: ${current.label}",
                    style = MaterialTheme.typography.labelMedium, fontSize = 11.sp, color = sk.bodyText,
                )
                Icon(
                    painterResource(R.drawable.ic_chevron), null,
                    tint = sk.subText,
                    modifier = Modifier.size(13.dp).padding(start = 3.dp),
                )
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            TeamSort.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.label, fontSize = 13.sp) },
                    onClick = { onSelect(s); open = false },
                    trailingIcon = if (s == current) {
                        {
                            Icon(
                                painterResource(R.drawable.ic_check), null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    } else null,
                )
            }
        }
    }
}

@Composable
internal fun SelectChip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label, fontSize = 10.5.sp, maxLines = 1) },
        shape = RoundedCornerShape(9.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.skill.cardBg,
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            selectedLabelColor = MaterialTheme.colorScheme.primary,
            labelColor = MaterialTheme.skill.bodyText,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = MaterialTheme.skill.cardBorder,
            selectedBorderColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
private fun DismissChip(label: String, onClear: () -> Unit) {
    Surface(
        onClick = onClear,
        shape = RoundedCornerShape(9.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f),
    ) {
        Row(
            Modifier.padding(start = 9.dp, end = 7.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary, maxLines = 1,
            )
            Spacer(Modifier.width(5.dp))
            Text("✕", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Read-only dropdown with an explicit "Any" reset entry. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Dropdown(
    label: String,
    value: String?,
    options: List<String>,
    enabled: Boolean,
    onSelect: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = open && enabled,
        onExpandedChange = { if (enabled) open = !open },
    ) {
        OutlinedTextField(
            value = value ?: "Any",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label, fontSize = 11.sp) },
            textStyle = MaterialTheme.typography.bodySmall,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = open && enabled, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Any", fontSize = 13.sp) },
                onClick = { onSelect(null); open = false },
            )
            options.take(200).forEach { o ->
                DropdownMenuItem(
                    text = { Text(o, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    onClick = { onSelect(o); open = false },
                )
            }
        }
    }
}

/**
 * Empty state with a glyph and a cause, not a bare grey sentence. An empty
 * screen is the one moment a manager cannot tell "nothing to report" apart from
 * "this is broken", so the state has to say which it is.
 */
@Composable
internal fun EmptyStateCard(message: String) {
    val sk = MaterialTheme.skill
    Box(Modifier.fillMaxWidth().glassSurface()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconSlot(tint = sk.sky, size = 46.dp) {
                Icon(
                    painterResource(R.drawable.ic_check), null,
                    tint = sk.sky, modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = sk.subText,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 18.sp,
            )
        }
    }
}
