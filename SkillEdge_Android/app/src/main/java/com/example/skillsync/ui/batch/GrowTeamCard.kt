package com.example.skillsync.ui.batch

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.skillsync.data.api.RetrofitClient
import com.example.skillsync.theme.glassSurface
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.components.int
import com.example.skillsync.ui.components.list
import com.example.skillsync.ui.components.str
import kotlinx.coroutines.launch

/**
 * "Grow the team" — demand the team cannot cover yet, and the reportee who is
 * closest to being able to. One tap composes a "please build this skill" ask
 * addressed to that trainer (server-composed, so the wording is consistent and
 * editable without an app release).
 */
@Composable
internal fun GrowTeamCard(upskilling: Map<String, Any>?) {
    val sk = MaterialTheme.skill
    val opps = upskilling?.list("high_priority_opportunities").orEmpty()
        .filter { it.list("suggested_trainers").isNotEmpty() }
    if (opps.isEmpty()) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var askText by remember { mutableStateOf<String?>(null) }
    var askFor by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxWidth().glassSurface(RoundedCornerShape(14.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Grow the team", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = sk.frost)
        Text(
            "Demand your team cannot cover yet, and who is closest to being able to.",
            style = MaterialTheme.typography.labelSmall, color = sk.labelText,
        )

        opps.take(4).forEach { o ->
            val course = o.str("course_name")
            val batchN = o.int("unallocated_batch_count").coerceAtLeast(1)
            val batches = batchN.toString()
            val start = o.str("earliest_start_date")
            HorizontalDivider(color = sk.cardBorder.copy(alpha = 0.4f), thickness = 0.5.dp)
            Text(
                course, style = MaterialTheme.typography.bodyMedium,
                color = sk.bodyText, fontWeight = FontWeight.SemiBold,
            )
            Text(
                listOfNotNull(
                    "$batches unallocated ${if (batches == "1") "batch" else "batches"}",
                    start.takeIf { it.isNotBlank() }?.let { "earliest $it" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, color = sk.subText,
            )
            o.list("suggested_trainers").forEach { t ->
                val name = t.str("trainer_name")
                val via = t.str("adjacent_skill")
                val lvl = t.str("adjacent_skill_level").substringBefore(".")
                val prep = t.int("prep_days").takeIf { it > 0 }?.toString().orEmpty()
                val inTime = t["ready_before_earliest_batch"] == true
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.bodySmall, color = sk.bodyText, fontWeight = FontWeight.SemiBold)
                        Text(
                            listOfNotNull(
                                via.takeIf { it.isNotBlank() }?.let { "closest via $it" + (if (lvl.isNotBlank()) " (L$lvl)" else "") },
                                prep.takeIf { it.isNotBlank() }?.let { "ready in ~$it days" },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (inTime) sk.aqua else sk.warn,
                        )
                        if (!inTime && prep.isNotBlank()) {
                            Text("will not be ready before this batch", style = MaterialTheme.typography.labelSmall, color = sk.subText)
                        }
                    }
                    TextButton(onClick = {
                        askFor = name
                        scope.launch {
                            try {
                                val r = RetrofitClient.instance.getUpskillMessage(
                                    course = course,
                                    trainerName = name,
                                    level = lvl.ifBlank { null },
                                    readyBy = t.str("ready_by").ifBlank { null },
                                    batches = batches,
                                )
                                askText = (r["plain"] as? String).orEmpty().ifBlank { null }
                            } catch (_: Exception) {
                                askText = "Hi ${name.split(" ").first()},\n\n" +
                                    "$course is in active demand and you are the closest fit on the team. " +
                                    "Please prepare the course and mark your skill in RMS with a live date as soon as you can.\n\nRegards"
                            }
                        }
                    }) { Text("Ask", color = sk.sky) }
                }
            }
        }
    }

    askText?.let { text ->
        AlertDialog(
            onDismissRequest = { askText = null },
            title = { Text("Ask $askFor to upskill") },
            text = {
                Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.verticalScroll(rememberScrollState()))
            },
            confirmButton = {
                TextButton(onClick = {
                    BatchShare.shareAnywhere(context, text)
                    askText = null
                }) { Text("Send") }
            },
            dismissButton = {
                TextButton(onClick = {
                    BatchShare.copyMessage(context, text)
                    askText = null
                }) { Text("Copy") }
            },
        )
    }
}
