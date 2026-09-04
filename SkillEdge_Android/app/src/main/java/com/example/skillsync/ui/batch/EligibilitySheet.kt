package com.example.skillsync.ui.batch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.skillsync.data.api.RetrofitClient
import com.example.skillsync.theme.SkillCard
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.components.*
import kotlinx.coroutines.delay

/**
 * "Why my team isn't eligible" for one open batch.
 *
 * Koenig's algorithm owns allocation — the manager cannot allocate. Their only
 * lever is preparation: make their trainer the top ELIGIBLE candidate before the
 * algorithm runs. This sheet lists, per trainer, exactly what blocks them and
 * offers the one fix the manager is allowed to make — recording a skill. The
 * other fixes (confirm availability, book an exam) are shown as guidance only;
 * there is no write path for them yet.
 *
 * Backed by GET /api/v2/eligibility/batch. That endpoint warms in the
 * background, so a cold call comes back `loading:true` with empty lists — this
 * sheet retries a few times before giving up rather than showing an empty team.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EligibilitySheet(
    managerEmail: String,
    demandId: String,
    courseId: String,
    courseName: String,
    markState: MarkState,
    onMarkSkill: (courseId: String, trainerEmail: String, level: Int, date: String, who: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sk = MaterialTheme.skill

    var loading by remember { mutableStateOf(true) }
    var data by remember { mutableStateOf<Map<String, Any>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var markTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(managerEmail, demandId) {
        loading = true
        failed = false
        var attempt = 0
        while (attempt < 4) {
            try {
                val res = RetrofitClient.instance.getBatchEligibility(managerEmail, demandId)
                data = res
                val stillWarming = (res["loading"] == true) &&
                    res.list("ready").isEmpty() && res.list("blocked").isEmpty()
                if (!stillWarming) { failed = false; break }
            } catch (_: Exception) {
                failed = true
            }
            attempt++
            delay(1500)
        }
        loading = false
    }

    // A skill write invalidates the picture — pull it again once RMS confirms.
    LaunchedEffect(markState) {
        if (markState is MarkState.Done) {
            try {
                data = RetrofitClient.instance.getBatchEligibility(managerEmail, demandId)
            } catch (_: Exception) { /* keep the stale view */ }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = sk.cardBg,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.md, vertical = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Why my team isn't eligible",
                        style = MaterialTheme.typography.titleMedium,
                        color = sk.bodyText,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "The algorithm allocates. Your lever is clearing the fixable blockers first.",
                        style = MaterialTheme.typography.labelSmall,
                        color = sk.cyan,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Text("✕", color = sk.subText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            val ready = data?.list("ready").orEmpty()
            val blocked = data?.list("blocked").orEmpty()
            val note = data?.str("note").orEmpty()

            when {
                loading -> Box(
                    Modifier.fillMaxWidth().height(180.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = sk.sky) }

                data == null || failed && ready.isEmpty() && blocked.isEmpty() -> SkillCard(Modifier.fillMaxWidth()) {
                    Text(
                        "Could not run the eligibility check right now.",
                        style = MaterialTheme.typography.bodyMedium, color = sk.bodyText,
                    )
                    Text(
                        "This is not the same as nobody being available — try again shortly.",
                        style = MaterialTheme.typography.bodySmall, color = sk.subText,
                    )
                }

                ready.isEmpty() && blocked.isEmpty() -> SkillCard(Modifier.fillMaxWidth()) {
                    Text(
                        note.ifBlank { "Eligibility could not be verified from the available RMS response." },
                        style = MaterialTheme.typography.bodyMedium, color = sk.bodyText,
                    )
                }

                else -> LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    if (ready.isNotEmpty()) {
                        item {
                            Text(
                                "READY NOW · ${ready.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = sk.good, fontWeight = FontWeight.Bold,
                            )
                        }
                        items(ready) { t -> ReadyRow(t) }
                    }
                    item {
                        Text(
                            "BLOCKED · ${blocked.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = sk.warn, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = if (ready.isNotEmpty()) 8.dp else 0.dp),
                        )
                    }
                    items(blocked) { t ->
                        BlockedRow(
                            trainer = t,
                            onMarkSkill = { email, name -> markTarget = name to email },
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    markTarget?.let { target ->
        MarkSkillDialog(
            title = "Mark ${target.first}'s skill",
            subtitle = courseName,
            people = listOf(target),
            working = markState is MarkState.Working,
            onDismiss = { markTarget = null },
            onConfirm = { who, level, date ->
                val email = who?.second.orEmpty()
                if (email.isNotBlank()) {
                    onMarkSkill(courseId, email, level, date, who?.first ?: email)
                }
                markTarget = null
            },
        )
    }
}

@Composable
private fun ReadyRow(trainer: Map<*, *>) {
    val sk = MaterialTheme.skill
    SkillCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✓", color = sk.good, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.width(Space.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    trainer.str("trainer_name").ifBlank { "Trainer" },
                    style = MaterialTheme.typography.titleSmall, color = sk.bodyText,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                trainer.str("note").takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = sk.subText)
                }
            }
        }
    }
}

@Composable
private fun BlockedRow(
    trainer: Map<*, *>,
    onMarkSkill: (email: String, name: String) -> Unit,
) {
    val sk = MaterialTheme.skill
    val email = trainer.str("trainer_email")
    val name = trainer.str("trainer_name").ifBlank { "Trainer" }
    val blockers = trainer.list("blockers")

    SkillCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                name,
                style = MaterialTheme.typography.titleSmall, color = sk.bodyText,
                fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            blockers.forEach { b ->
                val gate = b.str("gate")
                val fixableBy = b.str("fixable_by")
                Column(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, sk.cardBorder, RoundedCornerShape(8.dp))
                        .padding(Space.sm),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier
                                .padding(top = 6.dp)
                                .size(6.dp)
                                .background(
                                    if (gate == "dnc") sk.crit
                                    else if (gate.contains("visa")) sk.warn
                                    else sk.labelText,
                                    RoundedCornerShape(3.dp),
                                ),
                        )
                        Spacer(Modifier.width(Space.sm))
                        Text(
                            gateLabelFor(gate, b.str("detail")),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (gate == "dnc") sk.crit else sk.bodyText,
                        )
                    }
                    b.str("fix_hint").takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = sk.subText)
                    }
                    when (fixableBy) {
                        "mark_skill" -> if (email.isNotBlank()) {
                            Button(
                                onClick = { onMarkSkill(email, name) },
                                colors = ButtonDefaults.buttonColors(containerColor = sk.teal),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            ) {
                                Text("Mark skill", style = MaterialTheme.typography.labelMedium, color = Color.White)
                            }
                        }
                        "book_exam" -> FixTag("Book an exam / mock — no in-app action yet", sk.amber)
                        "confirm_availability" -> FixTag("Confirm availability in RMS — no in-app action yet", sk.amber)
                        else -> FixTag("Outside the manager's control", sk.subText)
                    }
                }
            }
        }
    }
}

@Composable
private fun FixTag(text: String, tint: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = tint,
        fontWeight = FontWeight.SemiBold,
    )
}

/** Plain language per gate — mirrors GatedCandidates.gateLabel. */
private fun gateLabelFor(gate: String, detail: String): String = when (gate) {
    "dnc" -> "Client exclusion: $detail"
    "availability" -> "Not available: $detail"
    "skill_level" -> "Skill: $detail"
    "visa" -> "Visa: $detail"
    "travel_window" -> "Travel: $detail"
    "international_travel_window" -> "International travel: $detail"
    "shift_window" -> "Shift: $detail"
    "mock_rating", "mock_missing" -> "Mock: $detail"
    else -> detail.ifBlank { gate }
}
