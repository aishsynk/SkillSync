package com.example.skillsync.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.example.skillsync.R
import com.example.skillsync.theme.AuroraBackground
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.SkillColors
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.batch.BatchShare
import com.example.skillsync.ui.batch.BulkBatchShare
import com.example.skillsync.ui.components.longDate
import com.example.skillsync.ui.components.str
import com.example.skillsync.ui.components.intOrNull

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
    onBack: () -> Unit,
    vm: PrioritiesViewModel = viewModel(),
) {
    val sk = MaterialTheme.skill
    val context = LocalContext.current

    LaunchedEffect(managerEmail) { if (managerEmail.isNotBlank()) vm.init(managerEmail, context) }

    val state by vm.state.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val bulkBatches by vm.bulkBatches.collectAsState()

    var showBulkShare by remember { mutableStateOf(false) }
    var bulkDraft by remember { mutableStateOf("") }

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
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item { SummaryStrip(s.counts, s.items.size, sk) }
                        if (bulkBatches.isNotEmpty()) {
                            item {
                                BulkShareBar(
                                    count = bulkBatches.size,
                                    onShare = { openBulkShare() },
                                )
                            }
                        }
                        items(s.items, key = { it.id.ifBlank { it.title } }) { item ->
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
) {
    val sk = MaterialTheme.skill
    var text by remember(message) { mutableStateOf(message) }

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
            Row {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = { onShare(text) }) { Text("Share") }
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
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = MaterialTheme.typography.bodySmall,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 420.dp),
            )
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

@Composable
private fun SummaryStrip(counts: Map<String, Int>, total: Int, sk: SkillColors) {
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
                Surface(
                    shape = RoundedCornerShape(Radii.chip),
                    color = sk.surface1,
                    border = androidx.compose.foundation.BorderStroke(1.dp, sk.cardBorder),
                ) {
                    Text(
                        "${kindLabel(kind)} $n",
                        style = MaterialTheme.typography.labelSmall,
                        color = sk.labelText,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PriorityCard(item: PriorityItem, sk: SkillColors, onClick: () -> Unit) {
    val stripe = severityColor(item.severity, sk)
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(Radii.card),
        color = sk.cardBg.copy(alpha = 0.88f),
        border = androidx.compose.foundation.BorderStroke(1.dp, sk.cardBorder),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(stripe),
            )
            Row(
                Modifier.weight(1f).padding(Space.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(stripe.copy(alpha = 0.15f))
                                .border(1.dp, stripe.copy(alpha = 0.30f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                item.severity.replaceFirstChar { it.uppercase() },
                                color = stripe,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(kindLabel(item.kind), color = sk.subText, fontSize = 11.sp)
                        if (item.coverable) {
                            Text("· coverable", color = sk.good, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Text(
                        item.title,
                        color = sk.bodyText,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.detail.isNotBlank()) {
                        Text(
                            item.detail,
                            color = sk.subText,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (item.due.isNotBlank()) {
                        Text(
                            "Due ${item.due}",
                            color = sk.sky,
                            style = MaterialTheme.typography.labelSmall,
                            textDecoration = TextDecoration.Underline,
                        )
                    }
                }
                Icon(
                    painterResource(R.drawable.ic_chevron),
                    contentDescription = null,
                    tint = sk.subText,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
