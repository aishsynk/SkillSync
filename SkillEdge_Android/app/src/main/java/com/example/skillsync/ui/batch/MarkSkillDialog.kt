package com.example.skillsync.ui.batch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.components.shortDate
import java.util.Calendar

/**
 * Captures the two things RMS needs to record a skill — an effective date and a
 * numeric level — plus a reportee when marking on someone else's behalf.
 *
 * [people] null marks the signed-in manager; non-null shows a picker and blocks
 * confirmation until someone is chosen, because posting a skill against the
 * wrong person writes bad data into production RMS.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkSkillDialog(
    title: String,
    subtitle: String,
    people: List<Pair<String, String>>?,
    working: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (person: Pair<String, String>?, level: Int, date: String) -> Unit,
) {
    val sk = MaterialTheme.skill
    var person by remember { mutableStateOf(people?.firstOrNull()) }
    var expanded by remember { mutableStateOf(false) }
    var level by remember { mutableStateOf(4) }
    var showDatePicker by remember { mutableStateOf(false) }

    val today = remember { Calendar.getInstance() }
    val dateState = rememberDatePickerState(initialSelectedDateMillis = today.timeInMillis)
    val isoDate = remember(dateState.selectedDateMillis) {
        val ms = dateState.selectedDateMillis ?: today.timeInMillis
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    val needsPerson = people != null && person == null

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(person, level, isoDate) },
                enabled = !working && !needsPerson,
            ) {
                if (working) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                } else {
                    Text("Record skill", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !working) { Text("Cancel") }
        },
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall, color = sk.subText,
                )

                if (people != null) {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                    ) {
                        OutlinedTextField(
                            value = person?.first ?: "Select a reportee",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Reportee") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.menuAnchor(
                                MenuAnchorType.PrimaryNotEditable, true,
                            ).fillMaxWidth(),
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            if (people.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No reportees found") },
                                    onClick = { expanded = false },
                                    enabled = false,
                                )
                            }
                            people.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p.first) },
                                    onClick = { person = p; expanded = false },
                                )
                            }
                        }
                    }
                }

                // Effective date
                Column {
                    Text("Effective from", style = MaterialTheme.typography.labelSmall, color = sk.subText)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(sk.track.copy(alpha = 0.5f))
                            .clickable { showDatePicker = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            isoDate.shortDate() + isoDate.takeLast(5).let { "" },
                            style = MaterialTheme.typography.bodyMedium, color = sk.bodyText,
                        )
                        Spacer(Modifier.weight(1f))
                        Text("Change", style = MaterialTheme.typography.labelSmall,
                             color = MaterialTheme.colorScheme.primary)
                    }
                }

                // Skill level
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Skill level", style = MaterialTheme.typography.labelSmall, color = sk.subText)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "$level",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = level.toFloat(),
                        onValueChange = { level = it.toInt().coerceIn(1, 10) },
                        valueRange = 1f..10f,
                        steps = 8,
                    )
                    Text(
                        "1 is lowest, 10 is highest. Keep at 4 or below unless you can fully deliver.",
                        style = MaterialTheme.typography.labelSmall, color = sk.subText, fontSize = 10.sp,
                    )
                }

                if (needsPerson) {
                    Text(
                        "Choose a reportee before recording.",
                        style = MaterialTheme.typography.labelSmall, color = sk.amber,
                    )
                }
                Text(
                    "This writes to RMS and is visible to the allocation team.",
                    style = MaterialTheme.typography.labelSmall, color = sk.subText, fontSize = 10.sp,
                )
            }
        },
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton(onClick = { showDatePicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = dateState)
        }
    }
}
