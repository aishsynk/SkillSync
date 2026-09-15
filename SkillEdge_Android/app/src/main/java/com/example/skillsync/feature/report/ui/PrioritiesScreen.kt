package com.example.skillsync.feature.report.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.skillsync.R
import com.example.skillsync.theme.ActionRow
import com.example.skillsync.theme.AuroraBackground
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.SkillColors
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.accentGlass
import com.example.skillsync.theme.pressable
import com.example.skillsync.theme.skill
import com.example.skillsync.feature.training.ui.BatchShare
import com.example.skillsync.feature.training.ui.BulkBatchShare
import com.example.skillsync.core.ui.LocalNotify
import com.example.skillsync.core.ui.longDate
import com.example.skillsync.core.ui.str
import com.example.skillsync.core.ui.intOrNull
import androidx.compose.material3.Text

/**
 * "This Week" — the manager's ranked board of what needs them, driven by
 * `GET /api/v2/manager/priorities`. Summary strip of counts by kind, then the
 * items as severity-striped cards in the order the backend ranked them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrioritiesScreen(
    managerEmail: String,
    onOpenDemand: (String) -> Unit,
    onOpenTrainer: (email: String, name: String) -> Unit,
    onOpenActions: () -> Unit,
    onOpenRunway: () -> Unit = {},
    onOpenRamp: () -> Unit = {},
    onOpenPipelineRadar: () -> Unit = {},
    onOpenDeliveryCompliance: () -> Unit = {},
    /**
     * (recipientType, recipientName, purpose, relatedEntityType, relatedEntityId) — the same
     * shared Communication Intelligence entry point wired from Today. A priority item only offers
     * this when it names a real recipient/purpose (see [communicateHintFor]); nothing here writes
     * a message itself.
     */
    onCommunicate: (String, String, String, String, String) -> Unit = { _, _, _, _, _ -> },
    onBack: () -> Unit,
    vm: PrioritiesViewModel = viewModel(),
) {
    val sk = MaterialTheme.skill
    val context = LocalContext.current
    val notify = LocalNotify.current

    LaunchedEffect(managerEmail) { if (managerEmail.isNotBlank()) vm.init(managerEmail, context) }

    val state by vm.state.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val bulkBatches by vm.bulkBatches.collectAsState()

    var showBulkShare by remember { mutableStateOf(false) }
    var bulkDraft by remember { mutableStateOf("") }
    var selectedKind by remember { mutableStateOf<String?>(null) }

    fun buildShareBatches(): List<BatchShare.Batch> =
        bulkBatches.map { m ->
            BatchShare.Batch(
                courseName = m.str("course_name"),
                startDate = m.str("start_date").longDate(),
                endDate = m.str("end_date").longDate(),
                sessionTime = m.str("session_time"),
                days = m.intOrNull("days"),
                deliveryMode = m.str("delivery_mode"),
                language = m.str("language"),
                participants = m.intOrNull("participants")?.toString().orEmpty(),
                location = m.str("location"),
                vendor = m.str("customer"),
                reference = m.str("demand_id"),
                tocUrl = m.str("toc_url").ifBlank { m.str("course_url") },
            )
        }

    fun openBulkShare() {
        val list = buildShareBatches()
        if (list.isEmpty()) {
            android.widget.Toast.makeText(context.applicationContext, "No unallocated batches to share right now", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        bulkDraft = BulkBatchShare.composeBulkMessage(list, recipient = "Team")
        showBulkShare = true
    }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("This Week", fontWeight = FontWeight.Bold, color = sk.bodyText, style = MaterialTheme.typography.titleLarge)
                            Text(
                                "What needs you — ranked",
                                color = sk.sky,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_back), "Back", tint = sk.ice)
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenPipelineRadar) {
                            Icon(painterResource(R.drawable.ic_calendar), "Pre-Demand Pipeline Radar", tint = sk.ice)
                        }
                        IconButton(onClick = onOpenDeliveryCompliance) {
                            Icon(painterResource(R.drawable.ic_check), "Live Delivery Compliance", tint = sk.ice)
                        }
                        IconButton(onClick = { openBulkShare() }) {
                            Icon(painterResource(R.drawable.ic_mail), "Share unallocated pipeline", tint = sk.ice)
                        }
                        IconButton(onClick = onOpenRamp) {
                            Icon(painterResource(R.drawable.ic_people), "New trainer ramp", tint = sk.ice)
                        }
                        IconButton(onClick = onOpenRunway) {
                            Icon(painterResource(R.drawable.ic_trend), "Capacity Runway", tint = sk.ice)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (val s = state) {
                    is PrioritiesState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = sk.brand)
                    }

                    is PrioritiesState.Error -> Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("Could not load this week", color = sk.warn, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        Text(s.message, color = sk.subText, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { vm.refresh() }, colors = ButtonDefaults.buttonColors(containerColor = sk.brand)) {
                            Text("Retry")
                        }
                    }

                is PrioritiesState.Success -> PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = { vm.refresh() },
                ) {
                    if (s.items.isEmpty() && bulkBatches.isEmpty()) {
                        Column(
                            Modifier.fillMaxSize().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text("Nothing needs you this week.", color = sk.bodyText, style = MaterialTheme.typography.titleSmall)
                        }
                        return@PullToRefreshBox
                    }
                    val visibleItems = selectedKind?.let { k -> s.items.filter { it.kind == k } } ?: s.items
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item {
                            SummaryStrip(
                                counts = s.counts, total = s.items.size, sk = sk,
                                selectedKind = selectedKind,
                                onSelectKind = { k -> selectedKind = if (selectedKind == k) null else k },
                            )
                        }
                        if (bulkBatches.isNotEmpty()) {
                            item {
                                BulkShareBar(
                                    count = bulkBatches.size,
                                    onShare = { openBulkShare() },
                                )
                            }
                        }
                        if (visibleItems.isEmpty()) {
                            item {
                                Text(
                                    "No items in this filter.", color = sk.subText,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(vertical = 24.dp),
                                )
                            }
                        }
                        items(visibleItems, key = { it.id.ifBlank { it.title } }) { item ->
                            PriorityCard(
                                item = item,
                                sk = sk,
                                onClick = {
                                    when (item.targetType) {
                                        "demand" -> onOpenDemand(item.targetId)
                                        "trainer" -> onOpenTrainer(item.targetId, item.targetName)
                                        else -> onOpenActions()
                                    }
                                },
                                onCommunicate = communicateHintFor(item)?.let { hint ->
                                    { onCommunicate(hint.recipientType, hint.recipientName, hint.purpose, hint.relatedEntityType, hint.relatedEntityId) }
                                },
                            )
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }

    if (showBulkShare) {
        BulkSharePreviewDialog(
            message = bulkDraft,
            count = bulkBatches.size,
            onDismiss = { showBulkShare = false },
            onCopy = { text ->
                val list = buildShareBatches()
                // Compare against the stored draft rather than recomputing from the
                // current list: recomputing would fail if the pipeline changed
                // between opening the dialog and tapping Copy, and exact equality
                // against a fresh compose would lose HTML when the user barely
                // edited whitespace. Trimming is the lightest normalization that
                // preserves HTML for an unedited draft while dropping it after a
                // real edit.
                val html = if (text.trim() == bulkDraft.trim()) {
                    BulkBatchShare.htmlBulkMessage(list)
                } else null
                BatchShare.copyMessage(context, text, html)
                showBulkShare = false
            },
            onShare = { text ->
                BatchShare.shareAnywhere(context, text)
                showBulkShare = false
            },
            onAutoViber = { text ->
                val outboxItem = com.example.skillsync.core.storage.ViberOutboxItem(
                    id = "viber_pipeline_bulk_${System.currentTimeMillis()}",
                    category = com.example.skillsync.core.storage.ViberOutboxItem.CAT_DEMAND,
                    recipientName = "Team",
                    recipientEmail = "team",
                    courseName = "Open Pipeline (${bulkBatches.size} batches)",
                    messageText = text,
                )
                com.example.skillsync.core.storage.ViberOutboxStore.enqueue(managerEmail, listOf(outboxItem))
                CoroutineScope(Dispatchers.IO).launch {
                    com.example.skillsync.feature.viber.ViberDispatcher.dispatchBatch(context, managerEmail, listOf(outboxItem))
                }
                notify.success("Auto-dispatching pipeline to Viber in background...")
                showBulkShare = false
            },
        )
    }
    }
}

@Composable
private fun BulkShareBar(count: Int, onShare: () -> Unit) {
    val sk = MaterialTheme.skill
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onShare() },
        shape = RoundedCornerShape(Radii.card),
        color = sk.cardBg.copy(alpha = 0.85f),
        border = androidx.compose.foundation.BorderStroke(1.dp, sk.cardBorder),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(Space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                    .background(sk.brand.copy(alpha = 0.16f))
                    .border(1.dp, sk.brand.copy(alpha = 0.30f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_mail), "Share pipeline", tint = sk.brand, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Share pipeline with team",
                    color = sk.bodyText,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "$count unallocated ${if (count == 1) "batch" else "batches"} · one message for your reportees to review",
                    color = sk.subText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(painterResource(R.drawable.ic_chevron), null, tint = sk.subText, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun BulkSharePreviewDialog(
    message: String,
    count: Int,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onAutoViber: (String) -> Unit = {},
) {
    val sk = MaterialTheme.skill
    var text by remember(message) { mutableStateOf(message) }
    var instruction by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { onCopy(text) }, shape = RoundedCornerShape(10.dp)) {
                Icon(painterResource(R.drawable.ic_check), null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(7.dp))
                Text("Copy", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = { onShare(text) }) { Text("Share") }
                TextButton(onClick = { onAutoViber(text) }) {
                    Text("Auto-Viber", color = Color(0xFF818CF8), fontWeight = FontWeight.Bold)
                }
            }
        },
        title = {
            Column {
                Text("Share pipeline", style = MaterialTheme.typography.titleLarge)
                Text(
                    "$count ${if (count == 1) "batch" else "batches"} · ${text.length} of 3500 characters · paste into Viber or Teams",
                    style = MaterialTheme.typography.labelSmall, color = sk.subText,
                )
            }
        },
        text = {
            Column(
                Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                com.example.skillsync.feature.communication.ui.ComposerModelStrip()
                com.example.skillsync.feature.communication.ui.ComposerStep(
                    1, "Verified context", "Open pipeline from RMS — always included.",
                    com.example.skillsync.feature.communication.ui.ComposerTints.context,
                ) {
                    com.example.skillsync.feature.communication.ui.VerifiedContextRows(
                        listOf("Pipeline" to "$count open ${if (count == 1) "batch" else "batches"}", "Audience" to "Team"),
                    )
                }
                com.example.skillsync.feature.communication.ui.ComposerStep(
                    2, "Manager instruction", "Optional — appended after the batch list.",
                    com.example.skillsync.feature.communication.ui.ComposerTints.instruction,
                ) {
                    com.example.skillsync.feature.communication.ui.ManagerInstructionField(
                        value = instruction,
                        onValueChange = {
                            instruction = it
                            text = if (it.isBlank()) message else "$message\n\n${it.trim()}"
                        },
                        minLines = 1,
                    )
                }
                com.example.skillsync.feature.communication.ui.ComposerStep(
                    3, "Generated message", "Editable before you copy or share.",
                    com.example.skillsync.feature.communication.ui.ComposerTints.generated,
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        textStyle = MaterialTheme.typography.bodySmall,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 360.dp),
                    )
                }
            }
        },
    )
}

private fun kindLabel(kind: String): String = when (kind) {
    "unstaffed_demand" -> "Unstaffed"
    "one_to_one" -> "1:1 due"
    "overload" -> "Overload"
    "cert_gap" -> "Cert gap"
    "action_overdue" -> "Overdue"
    else -> kind.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

@Composable
private fun severityColor(severity: String, sk: SkillColors): Color = when (severity) {
    "high" -> sk.red
    "medium" -> sk.amber
    else -> sk.subText
}

/**
 * Doubles as the ranked-board's only filter — tapping a kind narrows the list
 * below to that kind, tapping it again clears the filter. Previously
 * decorative (counts only, no click), which is why "This Week" read as an
 * inbox with no way to actually narrow it despite already grouping by kind.
 */
@Composable
private fun SummaryStrip(
    counts: Map<String, Int>,
    total: Int,
    sk: SkillColors,
    selectedKind: String?,
    onSelectKind: (String) -> Unit,
) {
    val order = listOf("unstaffed_demand", "one_to_one", "overload", "cert_gap", "action_overdue")
    val entries = order.mapNotNull { k -> counts[k]?.takeIf { it > 0 }?.let { k to it } } +
        counts.filterKeys { it !in order }.filterValues { it > 0 }.toList()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "$total ${if (total == 1) "item" else "items"} open",
            color = sk.bodyText,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries) { (kind, n) ->
                val isSelected = kind == selectedKind
                Surface(
                    shape = RoundedCornerShape(Radii.chip),
                    color = if (isSelected) sk.brand.copy(alpha = 0.22f) else sk.surface1,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) sk.brand.copy(alpha = 0.6f) else sk.cardBorder),
                    modifier = Modifier.clickable { onSelectKind(kind) },
                ) {
                    Text(
                        "${kindLabel(kind)} $n",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) sk.frost else sk.labelText,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

/**
 * "This Week"'s row, now the shared [ActionRow] wrapped in [accentGlass] for
 * severity — the D2 pilot proving the same primitive that carries Today's
 * attention items also carries an action-inbox row, without the two screens
 * looking identical (Today uses it inline in a plain Column; here it's the
 * whole scrollable list).
 */
@Composable
private fun PriorityCard(
    item: PriorityItem,
    sk: SkillColors,
    onClick: () -> Unit,
    onCommunicate: (() -> Unit)? = null,
) {
    val stripe = severityColor(item.severity, sk)
    val metadata = buildString {
        append(kindLabel(item.kind))
        if (item.coverable) append(" · coverable")
        if (item.due.isNotBlank()) append(" · Due ${item.due}")
    }
    Box(Modifier.fillMaxWidth().accentGlass(stripe).pressable(onClick)) {
        ActionRow(
            title = item.title,
            modifier = Modifier.padding(horizontal = Space.md),
            supportingText = item.detail,
            metadata = metadata,
            tint = stripe,
            primaryActionLabel = if (onCommunicate != null) "Communicate" else null,
            onPrimaryAction = onCommunicate,
            secondaryContent = {
                Icon(
                    painterResource(R.drawable.ic_chevron),
                    contentDescription = null,
                    tint = sk.subText,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
    }
}
