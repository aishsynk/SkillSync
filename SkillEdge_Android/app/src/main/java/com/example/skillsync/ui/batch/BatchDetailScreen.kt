package com.example.skillsync.ui.batch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.skillsync.R
import com.example.skillsync.theme.StatusBarIcons
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.components.*

/**
 * Everything known about one unallocated batch, plus the four actions a manager
 * takes from here: read the outline, claim it themselves, claim it for a
 * reportee, or message the team about it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchDetailScreen(
    batch: Map<*, *>,
    managerEmail: String,
    reportees: List<Pair<String, String>>,   // name to email
    markState: MarkState,
    onMarkSkill: (courseId: String, trainerEmail: String, level: Int, date: String, who: String) -> Unit,
    onClearMark: () -> Unit,
    onBack: () -> Unit,
) {
    val sk = MaterialTheme.skill
    val context = LocalContext.current
    StatusBarIcons(lightIcons = true)

    var showMine by remember { mutableStateOf(false) }
    var showReportee by remember { mutableStateOf(false) }
    var shareTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showMessagePreview by remember { mutableStateOf(false) }

    val courseId = batch.str("course_id")
    val courseName = batch.str("course_name")
    val relevance = batch.int("relevance")
    val candidates = batch.list("candidates")

    val shareBatch = remember(batch) {
        BatchShare.Batch(
            courseName = courseName,
            startDate = batch.str("start_date").longDate(),
            endDate = batch.str("end_date").longDate(),
            sessionTime = batch.str("session_time"),
            days = batch.intOrNull("days"),
            deliveryMode = batch.str("delivery_mode"),
            language = batch.str("language"),
            participants = batch.intOrNull("participants")?.toString().orEmpty(),
            location = batch.str("location"),
            vendor = batch.str("customer"),
            reference = batch.str("demand_id"),
            tocUrl = batch.str("toc_url"),
        )
    }

    fun messageFor(target: Pair<String, String>?): String =
        BatchShare.composeMessage(shareBatch, recipient = target?.first ?: "Team")

    fun htmlFor(target: Pair<String, String>?): String =
        BatchShare.htmlMessage(shareBatch, recipient = target?.first ?: "Team")

    // Confirm or explain the RMS write, then reset so the dialog can reopen.
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(markState) {
        when (markState) {
            is MarkState.Done -> {
                snackbar.showSnackbar(markState.message, duration = SnackbarDuration.Short)
                onClearMark()
            }
            is MarkState.Unconfirmed -> {
                snackbar.showSnackbar(markState.message, duration = SnackbarDuration.Long)
                onClearMark()
            }
            is MarkState.Failed -> {
                snackbar.showSnackbar("Not saved: ${markState.message}", duration = SnackbarDuration.Long)
                onClearMark()
            }
            else -> Unit
        }
    }

    Scaffold(
        containerColor = sk.pageBg,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Batch detail", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                        Text(
                            "Ref ${batch.str("demand_id")}",
                            fontSize = 9.5.sp, color = Color.White.copy(alpha = 0.78f),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_back), "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary),
            )
        },
    ) { pv ->
        Column(
            Modifier.fillMaxSize().padding(pv).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Headline
            Card(
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = sk.cardBg),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        courseName.ifBlank { "Course not specified" },
                        style = MaterialTheme.typography.headlineSmall, color = sk.bodyText,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.clip(RoundedCornerShape(8.dp))
                                .background(relevanceColor(relevance).copy(alpha = 0.15f))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "$relevance% team match",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold, color = relevanceColor(relevance),
                            )
                        }
                        if (batch.str("tentative").equals("Yes", true)) {
                            Spacer(Modifier.width(6.dp))
                            Chip("Tentative", sk.amber)
                        }
                        if (batch.str("third_party").equals("Yes", true)) {
                            Spacer(Modifier.width(6.dp))
                            Chip("Third party", sk.indigo)
                        }
                    }
                }
            }

            // Delivery facts
            DetailCard("Delivery") {
                Fact("Dates", listOfNotNull(
                    batch.str("start_date").takeIf { it.isNotBlank() }?.shortDate(),
                    batch.str("end_date").takeIf { it.isNotBlank() }?.shortDate(),
                ).joinToString(" to ").ifBlank { "Not set" })
                batch.intOrNull("days")?.let { Fact("Duration", "$it day${if (it == 1) "" else "s"}") }
                Fact("Daily time", batch.str("session_time"))
                Fact("Mode", batch.str("delivery_mode"))
                Fact("Participants", batch.intOrNull("participants")?.toString() ?: "—")
                Fact("Vendor", batch.str("customer"))
                Fact("Language", batch.str("language"))
                Fact("Location", batch.str("location"))
                Fact("Courseware", batch.str("courseware"))
                Fact("Allocation for", batch.str("allocation_for"))
                Fact("Course id", courseId)
                batch.str("remarks").takeIf { it.isNotBlank() }?.let { Fact("Remarks", it) }
            }

            batch.str("schedule").takeIf { it.isNotBlank() }?.let {
                DetailCard("Session schedule") {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = sk.bodyText)
                }
            }

            batch.str("scid").takeIf { it.isNotBlank() }?.let {
                DetailCard("Student card") {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = sk.subText)
                }
            }

            // Candidates
            DetailCard("Who on your team can deliver this") {
                if (candidates.isEmpty()) {
                    Text(
                        "No one on your team maps to this course.",
                        style = MaterialTheme.typography.bodySmall, color = sk.subText,
                    )
                } else {
                    candidates.forEach { c ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    c.str("trainer_name"),
                                    style = MaterialTheme.typography.titleSmall, color = sk.bodyText,
                                )
                                Text(
                                    "via ${c.str("via_course")}",
                                    style = MaterialTheme.typography.labelSmall, color = sk.subText,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Chip("${c.int("match")}% · Q${c.int("qubits_score")}", relevanceColor(c.int("match")))
                            // Addresses the message to this trainer by name rather
                            // than sending an unaddressed team broadcast.
                            TextButton(onClick = {
                                shareTarget = c.str("trainer_name") to c.str("trainer_email")
                                showMessagePreview = true
                            }) { Text("Message", fontSize = 11.sp) }
                        }
                    }
                }
            }

            // Actions — one compact row rather than four full-width buttons,
            // which took a third of the screen and pushed the batch facts off it.
            Spacer(Modifier.height(2.dp))
            ActionBar(
                actions = listOf(
                    ActionItem("Outline", R.drawable.ic_book, sk.blue) {
                        BatchShare.openUrl(context, batch.str("toc_url"))
                    },
                    ActionItem("My skill", R.drawable.ic_check, sk.teal) { showMine = true },
                    ActionItem("Reportee", R.drawable.ic_people, sk.indigo) { showReportee = true },
                    ActionItem("Message", R.drawable.ic_mail, sk.green) {
                        shareTarget = null
                        showMessagePreview = true
                    },
                ),
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showMessagePreview) {
        MessagePreviewDialog(
            message = messageFor(shareTarget),
            recipient = shareTarget?.first,
            onDismiss = { showMessagePreview = false },
            onCopy = { text ->
                // Only pass the HTML variant when the text is untouched; once it
                // has been edited the two would disagree and the rich paste would
                // silently drop the manager's changes.
                val html = if (text == messageFor(shareTarget)) htmlFor(shareTarget) else null
                BatchShare.copyMessage(context, text, html)
                showMessagePreview = false
            },
            onShare = { text ->
                BatchShare.shareAnywhere(context, text)
                showMessagePreview = false
            },
        )
    }

    if (showMine) {
        MarkSkillDialog(
            title = "Mark my skill",
            subtitle = courseName,
            people = null,
            working = markState is MarkState.Working,
            onDismiss = { showMine = false },
            onConfirm = { _, level, date ->
                onMarkSkill(courseId, managerEmail, level, date, "you")
                showMine = false
            },
        )
    }

    if (showReportee) {
        MarkSkillDialog(
            title = "Mark reportee's skill",
            subtitle = courseName,
            people = reportees,
            working = markState is MarkState.Working,
            onDismiss = { showReportee = false },
            onConfirm = { who, level, date ->
                val email = who?.second.orEmpty()
                if (email.isNotBlank()) {
                    onMarkSkill(courseId, email, level, date, who?.first ?: email)
                }
                showReportee = false
            },
        )
    }
}

// ── Pieces ────────────────────────────────────────────────────────────────────

/**
 * Shows the exact text before it leaves the app, and lets the manager edit it.
 *
 * Copy is the primary action, not a Viber deep link. `viber://forward?text=`
 * carries the body inside a URI and Viber truncates it at roughly a hundred
 * characters, so complete messages arrived cut off mid sentence with their
 * meaning lost. The clipboard has no such limit.
 */
@Composable
private fun MessagePreviewDialog(
    message: String,
    recipient: String?,
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
                Text(
                    if (recipient != null) "Message $recipient" else "Message the team",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    "${text.length} of 1000 characters · paste into Viber or Teams",
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
                modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 360.dp),
            )
        },
    )
}

@Composable
private fun DetailCard(title: String, body: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.skill.cardBg),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.skill.bodyText)
            Spacer(Modifier.height(8.dp))
            body()
        }
    }
}

/** Skips itself when the value is blank, so the card never shows empty rows. */
@Composable
private fun Fact(label: String, value: String) {
    if (value.isBlank() || value == "—") return
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.skill.subText, modifier = Modifier.width(104.dp),
        )
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.skill.bodyText)
    }
}

@Composable
private fun Chip(text: String, tint: Color) {
    Surface(color = tint.copy(alpha = 0.14f), shape = RoundedCornerShape(10.dp)) {
        Text(
            text, style = MaterialTheme.typography.labelSmall, color = tint,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

internal data class ActionItem(
    val label: String,
    val icon: Int,
    val tint: Color,
    val onClick: () -> Unit,
)

/**
 * Four actions on one row, each a tinted glyph over a short label.
 *
 * Replaces four stacked full-width buttons that ran to roughly 220dp — a third
 * of a phone screen spent on four words, which pushed the batch facts the
 * manager came to read below the fold. Each cell still fills the row height, so
 * the touch targets stay comfortably above the 48dp minimum.
 */
@Composable
private fun ActionBar(actions: List<ActionItem>) {
    val sk = MaterialTheme.skill
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = sk.cardBg),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(74.dp), verticalAlignment = Alignment.CenterVertically) {
            actions.forEach { a ->
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = a.onClick),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(11.dp))
                            .background(a.tint.copy(alpha = 0.13f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(a.icon), null,
                            tint = a.tint, modifier = Modifier.size(17.dp),
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        a.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = sk.subText, fontSize = 9.5.sp, maxLines = 1,
                    )
                }
            }
        }
    }
}
