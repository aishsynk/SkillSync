package com.example.skillsync.ui.trainer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.skillsync.theme.SectionHeading
import com.example.skillsync.theme.SkillCard
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.components.list
import com.example.skillsync.ui.components.str

/**
 * Development plan for one reportee, rendered inside Trainer 360.
 *
 * The plan is the manager's own preparation and coaching goals for this trainer
 * — never an allocation, never generated prose. Stored items are grouped by
 * status with a one-tap status cycler; the "Suggested" block offers deterministic
 * system-computed goals (cert gaps tied to open demand, a coaching item on weak
 * feedback, a portfolio item when the trainer teaches fewer than three courses)
 * that persist only once the manager adopts one.
 *
 * Offline-first: the caller renders the last cached plan and refreshes in the
 * background, exactly like every other section on this screen.
 */

private val DEV_PLAN_KINDS = listOf("certification", "coaching", "portfolio", "other")
private val STATUS_ORDER = listOf("open", "in_progress", "done")
private val STATUS_LABEL = mapOf(
    "open" to "To do", "in_progress" to "In progress", "done" to "Done", "dropped" to "Dropped",
)

@Composable
internal fun DevPlanSection(
    devPlan: Map<String, Any>?,
    canEdit: Boolean = true,
    onAddGoal: (title: String, kind: String, targetDate: String, note: String) -> Unit = { _, _, _, _ -> },
    onAdoptSuggestion: (Map<*, *>) -> Unit = {},
    onCycleStatus: (id: String, nextStatus: String) -> Unit = { _, _ -> },
    onEndorseSkill: (courseId: String, courseName: String, skillLevel: Int, devPlanId: String) -> Unit = { _, _, _, _ -> },
) {
    val sk = MaterialTheme.skill
    val items = devPlan?.list("items").orEmpty()
    val suggested = devPlan?.list("suggested").orEmpty()
    var showAdd by remember { mutableStateOf(false) }
    var endorseTarget by remember { mutableStateOf<Map<*, *>?>(null) }

    SkillCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeading(
                "Development plan",
                if (items.isEmpty()) "No goals set yet" else "${items.size} goal${if (items.size == 1) "" else "s"} on the plan",
                modifier = Modifier.weight(1f),
            )
            if (canEdit) TextButton(onClick = { showAdd = true }) { Text("+ Add goal") }
        }

        if (items.isEmpty()) {
            Text(
                "Add a preparation or coaching goal, or adopt one of the suggestions below.",
                style = MaterialTheme.typography.bodySmall, color = sk.subText,
            )
        } else {
            val byStatus = items.groupBy { it.str("status").ifBlank { "open" } }
            (STATUS_ORDER + "dropped").forEach { status ->
                val group = byStatus[status].orEmpty()
                if (group.isEmpty()) return@forEach
                Text(
                    (STATUS_LABEL[status] ?: status).uppercase(),
                    style = MaterialTheme.typography.labelSmall, color = sk.labelText,
                    fontWeight = FontWeight.Bold,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    group.forEach { item ->
                        DevPlanRow(
                            item = item,
                            onCycleStatus = onCycleStatus,
                            onEndorse = { endorseTarget = item },
                        )
                    }
                }
            }
        }

        if (suggested.isNotEmpty() && canEdit) {
            HorizontalDivider(color = sk.cardBorder)
            Text(
                "SUGGESTED", style = MaterialTheme.typography.labelSmall,
                color = sk.labelText, fontWeight = FontWeight.Bold,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                suggested.forEach { s -> SuggestionRow(s, onAdoptSuggestion) }
            }
        }
    }

    if (showAdd) {
        AddGoalDialog(
            onDismiss = { showAdd = false },
            onConfirm = { title, kind, target, note ->
                onAddGoal(title, kind, target, note)
                showAdd = false
            },
        )
    }

    endorseTarget?.let { targetItem ->
        EndorseSkillDialog(
            goalTitle = targetItem.str("title"),
            onDismiss = { endorseTarget = null },
            onConfirm = { courseId, courseName, level ->
                onEndorseSkill(courseId, courseName, level, targetItem.str("id"))
                endorseTarget = null
            },
        )
    }
}

@Composable
private fun DevPlanRow(
    item: Map<*, *>,
    onCycleStatus: (String, String) -> Unit,
    onEndorse: () -> Unit = {},
) {
    val sk = MaterialTheme.skill
    val status = item.str("status").ifBlank { "open" }
    val id = item.str("id")
    val kind = item.str("kind")
    val done = status == "done"
    val tint = when (status) {
        "done" -> sk.green
        "in_progress" -> sk.sky
        "dropped" -> sk.subText
        else -> sk.amber
    }
    val next = STATUS_ORDER[(STATUS_ORDER.indexOf(status).coerceAtLeast(0) + 1) % STATUS_ORDER.size]
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.08f))
            .clickable(enabled = id.isNotBlank() && status != "dropped") { onCycleStatus(id, next) }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(16.dp).clip(RoundedCornerShape(4.dp))
                .background(if (done) tint else Color.Transparent)
                .then(if (done) Modifier else Modifier.background(tint.copy(alpha = 0.25f), RoundedCornerShape(4.dp))),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.str("title"),
                style = MaterialTheme.typography.bodySmall,
                color = sk.bodyText,
                fontWeight = FontWeight.SemiBold,
            )
            val meta = listOfNotNull(
                kind.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() },
                item.str("target_date").takeIf { it.isNotBlank() }?.let { "by $it" },
            ).joinToString(" · ")
            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.labelSmall, color = sk.subText)
            item.str("note").takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = sk.subText)
            }
        }
        
        if (kind in listOf("certification", "portfolio") || done) {
            Surface(
                color = sk.brand.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, sk.brand.copy(alpha = 0.35f)),
                modifier = Modifier.clickable { onEndorse() }.padding(end = 6.dp),
            ) {
                Text(
                    text = "Endorse RMS",
                    style = MaterialTheme.typography.labelSmall,
                    color = sk.cyan,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
        }

        Text(
            (STATUS_LABEL[status] ?: status),
            style = MaterialTheme.typography.labelSmall,
            color = tint, fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SuggestionRow(s: Map<*, *>, onAdopt: (Map<*, *>) -> Unit) {
    val sk = MaterialTheme.skill
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(sk.cardBorder.copy(alpha = 0.18f)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                s.str("title"), style = MaterialTheme.typography.bodySmall,
                color = sk.bodyText, fontWeight = FontWeight.SemiBold,
            )
            s.str("note").takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = sk.subText)
            }
        }
        TextButton(onClick = { onAdopt(s) }) { Text("Add to plan") }
    }
}

@Composable
private fun AddGoalDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, kind: String, targetDate: String, note: String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(DEV_PLAN_KINDS.first()) }
    var target by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var kindOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title.trim(), kind, target.trim(), note.trim()) },
                enabled = title.isNotBlank(),
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Add development goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Goal") }, singleLine = true,
                )
                Box {
                    OutlinedTextField(
                        value = kind.replaceFirstChar { it.uppercase() },
                        onValueChange = {}, readOnly = true,
                        label = { Text("Kind") },
                        trailingIcon = {
                            TextButton(onClick = { kindOpen = true }) { Text("▾") }
                        },
                    )
                    DropdownMenu(expanded = kindOpen, onDismissRequest = { kindOpen = false }) {
                        DEV_PLAN_KINDS.forEach { k ->
                            DropdownMenuItem(
                                text = { Text(k.replaceFirstChar { it.uppercase() }) },
                                onClick = { kind = k; kindOpen = false },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                )
            }
        },
    )
}

@Composable
private fun EndorseSkillDialog(
    goalTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (courseId: String, courseName: String, level: Int) -> Unit,
) {
    var courseName by remember { mutableStateOf(goalTitle.removePrefix("Master ").removePrefix("Pass ").removeSuffix(" exam").trim()) }
    var courseId by remember { mutableStateOf("") }
    var level by remember { mutableIntStateOf(8) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = { onConfirm(courseId.trim(), courseName.trim(), level) },
                enabled = courseName.isNotBlank(),
            ) { Text("Confirm & Push to RMS") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Endorse Skill to RMS (IDP)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(
                    "Officially approve this completed development goal in RMS. The skill rating will immediately count towards the Trainer Index and unlock delivery demand.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = courseName, onValueChange = { courseName = it },
                    label = { Text("Course Name") }, singleLine = true,
                )
                OutlinedTextField(
                    value = courseId, onValueChange = { courseId = it },
                    label = { Text("Course ID (optional)") }, singleLine = true,
                )
                Text(
                    "Proficiency Level: $level / 10",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Slider(
                    value = level.toFloat(),
                    onValueChange = { level = it.toInt() },
                    valueRange = 1f..10f,
                    steps = 8,
                )
            }
        },
    )
}
