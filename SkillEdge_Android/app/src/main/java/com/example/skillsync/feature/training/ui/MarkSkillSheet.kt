package com.example.skillsync.feature.training.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.example.skillsync.R
import com.example.skillsync.core.ui.Avatar
import com.example.skillsync.core.ui.shortDate
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.SkillSyncSearchBar
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.ToneChip
import com.example.skillsync.theme.pressable
import com.example.skillsync.theme.skill
import java.util.Calendar

/**
 * Records a skill for the signed-in manager (no picker) or for one or many
 * reportees (search + multi-select), all in the SkillSync design language —
 * no default Material `AlertDialog`/`ExposedDropdownMenuBox`.
 *
 * [people] null means "the signed-in manager"; the sheet records against a
 * fixed, single implicit target and skips the picker entirely. Non-null
 * shows the real reportee roster (never the demand's pre-matched candidate
 * list — see the caller) with search and multi-select, and blocks
 * confirmation until at least one person is chosen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkSkillSheet(
    title: String,
    courseName: String,
    requiredLevel: String,
    people: List<Pair<String, String>>?,
    working: Boolean,
    onDismiss: () -> Unit,
    /** Called when [people] is null — recording against the signed-in manager. */
    onConfirmMine: (level: Int, date: String) -> Unit = { _, _ -> },
    /** Called when [people] is non-null — one or more selected reportees. */
    onConfirmMany: (people: List<Pair<String, String>>, level: Int, date: String) -> Unit = { _, _, _ -> },
    initialSelected: Pair<String, String>? = null,
    initialLevel: Int? = null,
) {
    val sk = MaterialTheme.skill
    var query by remember { mutableStateOf("") }
    var selected by remember {
        mutableStateOf(
            when {
                initialSelected != null -> setOf(initialSelected)
                people == null -> emptySet()
                else -> emptySet()
            }
        )
    }
    var level by remember { mutableStateOf(initialLevel?.coerceIn(1, 10) ?: 4) }
    val today = remember { Calendar.getInstance() }
    val isoDate = remember {
        "%04d-%02d-%02d".format(
            today.get(Calendar.YEAR), today.get(Calendar.MONTH) + 1, today.get(Calendar.DAY_OF_MONTH),
        )
    }

    val filtered = remember(people, query) {
        (people ?: emptyList()).filter { it.first.contains(query, ignoreCase = true) }
    }
    val canConfirm = !working && (people == null || selected.isNotEmpty())

    ModalBottomSheet(onDismissRequest = { if (!working) onDismiss() }, containerColor = sk.surface1) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.lg)
                .padding(bottom = Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, color = sk.bodyText, fontWeight = FontWeight.Bold)
                Text(courseName, style = MaterialTheme.typography.bodySmall, color = sk.subText, maxLines = 2)
                if (requiredLevel.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    ToneChip("Assignment needs level $requiredLevel or above", sk.sky)
                }
            }

            if (people != null) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    if (selected.isNotEmpty()) {
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            selected.forEach { p ->
                                Row(
                                    Modifier
                                        .clip(RoundedCornerShape(Radii.chip))
                                        .background(sk.brand.copy(alpha = 0.18f))
                                        .pressable { selected = selected - p }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        p.first.substringBefore(" "),
                                        style = MaterialTheme.typography.labelMedium, color = sk.sky,
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text("×", style = MaterialTheme.typography.labelMedium, color = sk.sky, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Text(
                        if (selected.isEmpty()) "Select reportees" else "${selected.size} selected",
                        style = MaterialTheme.typography.labelSmall,
                        color = sk.labelText, fontWeight = FontWeight.Bold, letterSpacing = 0.06.em,
                    )
                    SkillSyncSearchBar(query, { query = it }, placeholder = "Search team…")
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                        Text(
                            "Select all", style = MaterialTheme.typography.labelMedium, color = sk.sky,
                            modifier = Modifier.pressable { selected = selected + filtered },
                        )
                        Text(
                            "Clear", style = MaterialTheme.typography.labelMedium, color = sk.subText,
                            modifier = Modifier.pressable { selected = emptySet() },
                        )
                    }

                    if (people.isEmpty()) {
                        Text(
                            "No reportees found for this manager account.",
                            style = MaterialTheme.typography.bodySmall, color = sk.warn,
                        )
                    } else {
                        LazyColumn(
                            Modifier.heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            items(filtered, key = { it.second }) { p ->
                                val checked = p in selected
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { selected = if (checked) selected - p else selected + p }
                                        .padding(vertical = 6.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Avatar(name = p.first, photoUrl = null, size = 30.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        p.first, style = MaterialTheme.typography.bodyMedium, color = sk.bodyText,
                                        modifier = Modifier.weight(1f), maxLines = 1,
                                    )
                                    Checkbox(checked = checked, onCheckedChange = {
                                        selected = if (checked) selected - p else selected + p
                                    })
                                }
                            }
                        }
                    }
                }
            }

            // Skill level — one clear 1-10 scale with what each band means.
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "SKILL LEVEL", style = MaterialTheme.typography.labelSmall,
                        color = sk.labelText, fontWeight = FontWeight.Bold, letterSpacing = 0.06.em,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "Level $level", style = MaterialTheme.typography.titleMedium,
                        color = sk.sky, fontWeight = FontWeight.Bold,
                    )
                }
                Slider(
                    value = level.toFloat(),
                    onValueChange = { level = it.toInt().coerceIn(1, 10) },
                    valueRange = 1f..10f, steps = 8,
                )
                Text(
                    when (level) {
                        in 1..3 -> "1–3 · Basic familiarity"
                        in 4..6 -> "4–6 · Can deliver with support"
                        in 7..8 -> "7–8 · Independent delivery"
                        else -> "9–10 · Expert / mentor"
                    },
                    style = MaterialTheme.typography.labelSmall, color = sk.subText,
                )
            }

            Text(
                "Recorded skills update RMS immediately and feed the allocation ranking algorithm.",
                style = MaterialTheme.typography.labelSmall, color = sk.subText,
            )

            if (people != null && selected.size > 1) {
                Text(
                    "This records level $level for ${selected.size} trainers. This updates trainer capability data and may affect allocation ranking.",
                    style = MaterialTheme.typography.labelSmall, color = sk.labelText,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onDismiss, enabled = !working,
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(Radii.chip),
                ) { Text("Cancel") }
                Button(
                    onClick = {
                        if (people == null) onConfirmMine(level, isoDate)
                        else onConfirmMany(selected.toList(), level, isoDate)
                    },
                    enabled = canConfirm,
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(Radii.chip),
                    colors = ButtonDefaults.buttonColors(containerColor = sk.brand),
                ) {
                    if (working) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp), color = sk.frost)
                    } else {
                        Text(
                            if (people != null && selected.size > 1) "Record skills (${selected.size})" else "Record skill",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
