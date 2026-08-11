package com.example.skillsync.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.skillsync.R
import com.example.skillsync.theme.IconSlot
import com.example.skillsync.theme.Space
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import com.example.skillsync.theme.pressable
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.accentGlass
import com.example.skillsync.theme.glassSurface
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.components.*

/**
 * The manager's decision inbox.
 *
 * Everything asking for a decision lives here — certification gaps, feedback
 * incidents, bench capacity, unallocated demand — with one lifecycle applied
 * to all of it. Certification gaps in particular used to be a separate board,
 * which meant half the queue was invisible from the inbox.
 *
 * An action is not a notification: it can be moved (start, close, escalate),
 * annotated with follow-up notes, given a due date, and raised by hand.
 */
private enum class ActionFilter(val label: String) {
    OPEN("Open"), IN_PROGRESS("In progress"), ESCALATED("Escalated"),
    CLOSED("Closed"), ALL("All"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActionsInbox(
    managerEmail: String,
    actions: List<Map<String, Any>>,
    initialLoading: Boolean,
    error: String?,
    onSetState: (id: String, state: String, note: String) -> Unit,
    onAddNote: (id: String, note: String) -> Unit,
    onRaise: (title: String, detail: String, category: String, priority: String) -> Unit,
    onTrainerClick: (String, String) -> Unit,
    onDismissError: () -> Unit,
) {
    val sk = MaterialTheme.skill
    var filter by remember { mutableStateOf(ActionFilter.OPEN) }
    var category by remember { mutableStateOf<String?>(null) }
    var detailFor by remember { mutableStateOf<Map<String, Any>?>(null) }
    var showRaise by remember { mutableStateOf(false) }

    val categories = remember(actions) {
        actions.mapNotNull { it.str("category").takeIf { c -> c.isNotBlank() } }.distinct().sorted()
    }

    val shown = remember(actions, filter, category) {
        actions.filter { a ->
            val st = a.str("lifecycle_state").ifBlank { "open" }
            val matchesState = when (filter) {
                ActionFilter.ALL -> true
                ActionFilter.OPEN -> st == "open"
                ActionFilter.IN_PROGRESS -> st == "in_progress"
                ActionFilter.ESCALATED -> st == "escalated"
                ActionFilter.CLOSED -> st == "closed"
            }
            val matchesCat = category == null || a.str("category") == category
            matchesState && matchesCat
        }
    }

    /**
     * Three lanes, per design vision §7.5: Now, This week, Watching.
     *
     * A flat list sorted by whatever the API returned made every item look
     * equally urgent, which is the same failure the dashboard had. Lane
     * membership is derived from priority and age: an item that has been open
     * for a week without being touched has earned promotion regardless of the
     * priority it was raised with, because ageing is itself a signal.
     */
    // §7.5 bulk selection. Managers work on groups — "close all six of these
    // certification reminders" — and doing that one card at a time is why the
    // inbox never empties.
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    val lanes = remember(shown) {
        val now = System.currentTimeMillis()
        fun ageDays(a: Map<String, Any>): Long {
            val raw = a.str("created_at").ifBlank { a.str("due_date") }
            val parsed = runCatching { java.time.LocalDate.parse(raw.take(10)) }.getOrNull()
                ?: return 0
            return java.time.temporal.ChronoUnit.DAYS.between(parsed, java.time.LocalDate.now())
                .coerceAtLeast(0)
        }
        val order = listOf("Now", "This week", "Watching")
        shown.groupBy { a ->
            val priority = a.str("priority").lowercase()
            val escalated = a.str("lifecycle_state") == "escalated"
            when {
                escalated || priority in setOf("high", "critical") -> "Now"
                ageDays(a) >= 7 -> "Now"
                priority == "medium" -> "This week"
                else -> "Watching"
            }
        }.toList().sortedBy { order.indexOf(it.first) }
    }

    val counts = remember(actions) {
        mapOf(
            ActionFilter.OPEN to actions.count { it.str("lifecycle_state").ifBlank { "open" } == "open" },
            ActionFilter.IN_PROGRESS to actions.count { it.str("lifecycle_state") == "in_progress" },
            ActionFilter.ESCALATED to actions.count { it.str("lifecycle_state") == "escalated" },
            ActionFilter.CLOSED to actions.count { it.str("lifecycle_state") == "closed" },
            ActionFilter.ALL to actions.size,
        )
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        ActionFilter.entries.forEach { f ->
                            StateChip(
                                label = f.label,
                                count = counts[f] ?: 0,
                                selected = filter == f,
                            ) { filter = f }
                        }
                    }
                    if (categories.size > 1) {
                        Spacer(Modifier.height(7.dp))
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            CategoryChip("All types", category == null) { category = null }
                            categories.forEach { c ->
                                CategoryChip(c, category == c) {
                                    category = if (category == c) null else c
                                }
                            }
                        }
                    }
                }
            }

            if (error != null) {
                item {
                    Box(Modifier.fillMaxWidth().accentGlass(sk.crit, RoundedCornerShape(Radii.card))) {
                        Row(
                            Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_alert), null,
                                tint = sk.crit, modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                error, style = MaterialTheme.typography.bodySmall,
                                color = sk.crit, modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onDismissError) { Text("Dismiss") }
                        }
                    }
                }
            }

            if (initialLoading && actions.isEmpty()) {
                items(3) { ShimmerBox(height = 96.dp, shape = RoundedCornerShape(Radii.card), modifier = Modifier.fillMaxWidth()) }
            } else if (shown.isEmpty()) {
                item {
                    EmptyStateCard(
                        if (actions.isEmpty()) "Nothing needs a decision right now."
                        else "No ${filter.label.lowercase()} actions of this type."
                    )
                }
            } else {
                if (selectedIds.isNotEmpty()) {
                    item(key = "bulk-bar") {
                        BulkActionBar(
                            count = selectedIds.size,
                            onResolve = {
                                selectedIds.forEach { onSetState(it, "closed", "") }
                                selectedIds = emptySet()
                            },
                            onEscalate = {
                                selectedIds.forEach { onSetState(it, "escalated", "") }
                                selectedIds = emptySet()
                            },
                            onClear = { selectedIds = emptySet() },
                        )
                    }
                }
                lanes.forEach { (lane, rows) ->
                    item(key = "lane-$lane") {
                        LaneHeader(lane, rows.size, rows.count { it.str("id") in selectedIds }) {
                            val ids = rows.map { r -> r.str("id") }.toSet()
                            selectedIds = if (selectedIds.containsAll(ids)) selectedIds - ids
                                          else selectedIds + ids
                        }
                    }
                    itemsIndexed(rows, key = { _, a -> a.str("id") }) { i, a ->
                    Appear(i) {
                        ActionCard(
                            action = a,
                            selected = a.str("id") in selectedIds,
                            onToggleSelect = {
                                val id = a.str("id")
                                selectedIds = if (id in selectedIds) selectedIds - id
                                              else selectedIds + id
                            },
                            onOpen = { detailFor = a },
                            onQuickState = { st -> onSetState(a.str("id"), st, "") },
                            onTrainer = {
                                val em = a.str("trainer_email")
                                if (em.isNotBlank()) onTrainerClick(em, a.str("trainer_name"))
                            },
                        )
                    }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showRaise = true },
            containerColor = sk.sky,
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) {
            Icon(painterResource(R.drawable.ic_flag), null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(9.dp))
            Text("Raise action", fontWeight = FontWeight.SemiBold)
        }
    }

    detailFor?.let { a ->
        ActionDetailSheet(
            action = a,
            onDismiss = { detailFor = null },
            onSetState = { st, note -> onSetState(a.str("id"), st, note); detailFor = null },
            onAddNote = { note -> onAddNote(a.str("id"), note) },
            onTrainer = {
                val em = a.str("trainer_email")
                if (em.isNotBlank()) { detailFor = null; onTrainerClick(em, a.str("trainer_name")) }
            },
        )
    }

    if (showRaise) {
        RaiseActionSheet(
            onDismiss = { showRaise = false },
            onRaise = { t, d, c, p -> onRaise(t, d, c, p); showRaise = false },
        )
    }
}

// ── Pieces ────────────────────────────────────────────────────────────────────

@Composable
private fun stateTint(state: String): Color {
    val sk = MaterialTheme.skill
    return when (state) {
        "closed" -> sk.aqua
        "escalated" -> sk.crit
        "in_progress" -> sk.sky
        "reassigned" -> sk.indigo
        else -> sk.warn
    }
}

@Composable
private fun ActionCard(
    action: Map<String, Any>,
    selected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onOpen: () -> Unit,
    onQuickState: (String) -> Unit,
    onTrainer: () -> Unit,
) {
    val sk = MaterialTheme.skill
    val state = action.str("lifecycle_state").ifBlank { "open" }
    val priority = action.str("priority")
    val edge = when {
        state == "closed" -> sk.aqua
        state == "escalated" -> sk.crit
        priority == "high" -> sk.crit
        priority == "medium" -> sk.warn
        else -> sk.sky
    }
    val notes = action.list("notes")

    Box(
        Modifier
            .fillMaxWidth()
            .accentGlass(edge, RoundedCornerShape(Radii.card), strong = priority == "high" && state == "open")
            .clickable(onClick = onOpen),
    ) {
        Column(Modifier.padding(start = Space.md, top = Space.md, end = Space.md, bottom = Space.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Selection control. Long-press-to-select is undiscoverable on a
                // list a manager visits once a day, so the affordance is always
                // visible and toggles independently of opening the item.
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (selected) sk.brand else Color.Transparent)
                        .border(
                            1.dp,
                            if (selected) sk.brand else sk.glassBorder,
                            RoundedCornerShape(4.dp),
                        )
                        .pressable(onToggleSelect),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            painterResource(R.drawable.ic_check), null,
                            tint = sk.frost, modifier = Modifier.size(11.dp),
                        )
                    }
                }
                Spacer(Modifier.width(Space.sm))
                Text(
                    action.str("category").uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = edge, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(8.dp))
                if (action.str("source") == "raised") {
                    Text(
                        "RAISED BY YOU", style = MaterialTheme.typography.labelSmall,
                        color = sk.labelText, fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.weight(1f))
                StatePill(state)
            }

            Spacer(Modifier.height(6.dp))
            Text(
                action.str("title"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold, color = sk.frost,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            val detail = action.str("detail")
            if (detail.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    detail, style = MaterialTheme.typography.labelSmall,
                    color = sk.labelText, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }

            val who = action.str("trainer_name")
            if (who.isNotBlank()) {
                Spacer(Modifier.height(7.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(7.dp)).clickable(onClick = onTrainer)
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(who, null, 20.dp)
                    Spacer(Modifier.width(7.dp))
                    Text(
                        who, style = MaterialTheme.typography.labelSmall,
                        color = sk.ice, fontWeight = FontWeight.SemiBold,
                    )
                    Icon(
                        painterResource(R.drawable.ic_chevron), null,
                        tint = sk.subText, modifier = Modifier.size(12.dp),
                    )
                }
            }

            val due = action.str("due_date")
            if (due.isNotBlank() || notes.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (due.isNotBlank()) {
                        Icon(
                            painterResource(R.drawable.ic_calendar), null,
                            tint = sk.labelText, modifier = Modifier.size(11.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "due ${due.shortDate()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = sk.labelText,
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    if (notes.isNotEmpty()) {
                        Text(
                            "${notes.size} follow-up${if (notes.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = sk.labelText,
                        )
                    }
                }
            }

            // Quick transitions — the two a manager reaches for most often.
            if (state != "closed") {
                Spacer(Modifier.height(9.dp))
                HorizontalDivider(color = sk.cardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                Row(Modifier.fillMaxWidth()) {
                    if (state == "open") {
                        QuickAction("Start", sk.sky, Modifier.weight(1f)) { onQuickState("in_progress") }
                    }
                    QuickAction("Close", sk.aqua, Modifier.weight(1f)) { onQuickState("closed") }
                    QuickAction("Escalate", sk.crit, Modifier.weight(1f)) { onQuickState("escalated") }
                }
            }
        }
    }
}

@Composable
private fun QuickAction(label: String, tint: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(label, color = tint, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatePill(state: String) {
    val tint = stateTint(state)
    val label = when (state) {
        "in_progress" -> "IN PROGRESS"
        else -> state.uppercase()
    }
    Box(
        Modifier.clip(RoundedCornerShape(20.dp)).background(tint.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            label, style = MaterialTheme.typography.labelSmall, color = tint, fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun StateChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val sk = MaterialTheme.skill
    Box(
        Modifier
            .clip(RoundedCornerShape(Radii.chip))
            .background(if (selected) sk.sky.copy(alpha = 0.18f) else sk.glass)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label, style = MaterialTheme.typography.labelMedium,
                color = if (selected) sk.sky else sk.labelText,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
            if (count > 0) {
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier.clip(CircleShape)
                        .background(if (selected) sk.sky else sk.labelText.copy(alpha = 0.3f))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text(
                        "$count", fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else sk.frost,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val sk = MaterialTheme.skill
    Box(
        Modifier
            .clip(RoundedCornerShape(Radii.chip))
            .background(if (selected) sk.ice.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            label, style = MaterialTheme.typography.labelSmall,
            color = if (selected) sk.ice else sk.subText,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionDetailSheet(
    action: Map<String, Any>,
    onDismiss: () -> Unit,
    onSetState: (String, String) -> Unit,
    onAddNote: (String) -> Unit,
    onTrainer: () -> Unit,
) {
    val sk = MaterialTheme.skill
    var note by remember { mutableStateOf("") }
    val notes = action.list("notes")
    val history = action.list("history")

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = sk.cardBg) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
        ) {
            Text(
                action.str("category").uppercase(),
                style = MaterialTheme.typography.labelSmall, color = sk.ice, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                action.str("title"), style = MaterialTheme.typography.headlineSmall,
                color = sk.frost,
            )
            val detail = action.str("detail")
            if (detail.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(detail, style = MaterialTheme.typography.bodySmall, color = sk.subText)
            }

            val who = action.str("trainer_name")
            if (who.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onTrainer).padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(who, null, 30.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(who, style = MaterialTheme.typography.titleSmall, color = sk.frost)
                        Text(
                            "Open trainer profile",
                            style = MaterialTheme.typography.labelSmall, color = sk.sky,
                        )
                    }
                    Icon(
                        painterResource(R.drawable.ic_chevron), null,
                        tint = sk.subText, modifier = Modifier.size(15.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "MOVE THIS ACTION", style = MaterialTheme.typography.labelSmall,
                color = sk.ice, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "in_progress" to "Start",
                    "closed" to "Close",
                    "escalated" to "Escalate",
                    "reassigned" to "Reassign",
                    "open" to "Reopen",
                ).forEach { (st, label) ->
                    OutlinedButton(
                        onClick = { onSetState(st, note) },
                        shape = RoundedCornerShape(Radii.chip),
                        border = androidx.compose.foundation.BorderStroke(1.dp, stateTint(st).copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = stateTint(st)),
                    ) { Text(label, fontWeight = FontWeight.SemiBold) }
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Follow-up note") },
                placeholder = { Text("What did you do, or what is next?") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                minLines = 2,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onAddNote(note); note = "" },
                enabled = note.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Add follow-up") }

            if (notes.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text(
                    "FOLLOW-UP TRAIL", style = MaterialTheme.typography.labelSmall,
                    color = sk.ice, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                notes.reversed().forEach { n ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        Text(n.str("text"), style = MaterialTheme.typography.bodySmall, color = sk.frost)
                        Text(
                            n.str("at").shortDate(),
                            style = MaterialTheme.typography.labelSmall,
                            color = sk.subText,
                        )
                    }
                    HorizontalDivider(color = sk.cardBorder.copy(alpha = 0.4f), thickness = 0.5.dp)
                }
            }

            if (history.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "${history.size} audited event${if (history.size == 1) "" else "s"} on record",
                    style = MaterialTheme.typography.labelSmall, color = sk.subText,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RaiseActionSheet(
    onDismiss: () -> Unit,
    onRaise: (String, String, String, String) -> Unit,
) {
    val sk = MaterialTheme.skill
    var title by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Certification") }
    var priority by remember { mutableStateOf("medium") }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = sk.cardBg) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Raise an action", style = MaterialTheme.typography.headlineSmall, color = sk.frost)
            Spacer(Modifier.height(4.dp))
            Text(
                "For anything RMS cannot infer on its own.",
                style = MaterialTheme.typography.bodySmall, color = sk.subText,
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("What needs doing?") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                singleLine = true,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = detail, onValueChange = { detail = it },
                label = { Text("Detail (optional)") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                minLines = 2,
            )

            Spacer(Modifier.height(14.dp))
            Text("TYPE", style = MaterialTheme.typography.labelSmall, color = sk.ice, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                listOf("Certification", "Feedback", "Allocation", "Capacity", "Other").forEach {
                    CategoryChip(it, category == it) { category = it }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("PRIORITY", style = MaterialTheme.typography.labelSmall, color = sk.ice, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("high", "medium", "low").forEach {
                    CategoryChip(it.replaceFirstChar { c -> c.uppercase() }, priority == it) { priority = it }
                }
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { onRaise(title, detail, category, priority) },
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Raise action", fontWeight = FontWeight.SemiBold) }
        }
    }
}

/**
 * A queue lane, per design vision §7.5.
 *
 * The count belongs in the header, not on each row: a manager decides which
 * lane to work before they decide which item, and a lane with nothing in it
 * still needs to be visible so its emptiness is information.
 */
@Composable
private fun LaneHeader(
    lane: String,
    count: Int,
    selectedInLane: Int = 0,
    onSelectLane: () -> Unit = {},
) {
    val sk = MaterialTheme.skill
    val tint = when (lane) {
        "Now" -> sk.crit
        "This week" -> sk.warn
        else -> sk.labelText
    }
    Row(
        Modifier.fillMaxWidth().padding(top = Space.sm, bottom = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(tint))
        Spacer(Modifier.width(Space.sm))
        Text(
            lane.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (selectedInLane > 0) "$selectedInLane of $count" else "$count",
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = if (selectedInLane > 0) sk.sky else sk.labelText,
            modifier = Modifier.pressable(onSelectLane),
        )
    }
}

/**
 * The bulk bar, shown only while a selection exists.
 *
 * Resolve and escalate are the two things a manager does to a group. Both run
 * per row through the same state endpoint a single card uses, so a bulk action
 * cannot take a path the audited single path does not.
 */
@Composable
private fun BulkActionBar(
    count: Int,
    onResolve: () -> Unit,
    onEscalate: () -> Unit,
    onClear: () -> Unit,
) {
    val sk = MaterialTheme.skill
    Row(
        Modifier
            .fillMaxWidth()
            .background(sk.brand.copy(alpha = 0.16f), RoundedCornerShape(Radii.card))
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$count selected",
            style = MaterialTheme.typography.labelMedium,
            color = sk.frost,
            modifier = Modifier.weight(1f),
        )
        Text(
            "Resolve",
            style = MaterialTheme.typography.labelMedium,
            color = sk.aqua,
            modifier = Modifier.pressable(onResolve).padding(horizontal = Space.sm),
        )
        Text(
            "Escalate",
            style = MaterialTheme.typography.labelMedium,
            color = sk.warn,
            modifier = Modifier.pressable(onEscalate).padding(horizontal = Space.sm),
        )
        Text(
            "Clear",
            style = MaterialTheme.typography.labelMedium,
            color = sk.subText,
            modifier = Modifier.pressable(onClear).padding(start = Space.sm),
        )
    }
}
