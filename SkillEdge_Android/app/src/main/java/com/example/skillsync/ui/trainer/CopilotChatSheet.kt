package com.example.skillsync.ui.trainer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import com.example.skillsync.theme.ToneChip
import com.example.skillsync.theme.skill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopilotChatSheet(
    managerEmail: String,
    targetEmail: String = "",
    viewModel: CopilotViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onDismiss: () -> Unit
) {
    val sk = MaterialTheme.skill
    val messages by viewModel.messages.collectAsState()

    // Team context = the Copilot was opened without a specific trainer target.
    val isTeam = targetEmail.isBlank()

    // Per-trainer questions (mapped from JS rules).
    val trainerQuestions = listOf(
        "can_assign_now" to "Can I assign now?",
        "can_deliver" to "What can deliver?",
        "weak_spot" to "Where weak?",
        "missing_certs" to "Which certification blocks this trainer?",
        "best_course" to "Best course fit?",
        "backup_trainers" to "Who are backup trainers?",
        "why_not_first" to "Why is not first choice?",
        "compare_trainer" to "Compare with another trainer",
        "what_if_oem" to "What if I move to another OEM?",
        "invest_oem" to "Which OEM should I invest in?",
        "explain_readiness" to "Explain readiness score",
        "explain_allocation" to "Explain allocation score",
        "why_low_confidence" to "Why is confidence low?",
        "next_action" to "What should I do next?",
        "missing_data" to "What data is missing?"
    )

    // Team-level suggested questions -> team question_key.
    val teamQuestions = listOf(
        "free_for_course" to "Who is free next week for…",
        "coverage_risk" to "Biggest coverage risk",
        "top_upskills" to "Top 3 upskills",
        "bench" to "Who is on the bench",
        "overloaded" to "Who is stretched",
        "feedback_watch" to "Who needs a 1:1"
    )

    var draft by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = sk.surface2,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isTeam) "Team Copilot" else "SkillEdge Copilot",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = sk.bodyText,
                    )
                    ToneChip("AI Intelligence", tint = sk.cyan)
                }
                IconButton(onClick = onDismiss) {
                    Text("✕", fontSize = 18.sp, color = sk.subText)
                }
            }

            // Chat Messages
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = sk.surface1.copy(alpha = 0.6f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, sk.cardBorder),
                        ) {
                            Text(
                                text = if (isTeam)
                                    "Ask about your team — who is free for a course, coverage risk, the bench, upskills, or who needs a 1:1."
                                else
                                    "Ask me anything about this trainer's readiness, gaps, and allocation fit.",
                                color = sk.subText,
                                modifier = Modifier.padding(20.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                items(messages) { msg ->
                    when (msg) {
                        is ChatMessage.User -> {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                Surface(
                                    color = sk.brand.copy(alpha = 0.22f),
                                    shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, sk.brand.copy(alpha = 0.45f)),
                                    modifier = Modifier.widthIn(max = 280.dp)
                                ) {
                                    Text(
                                        text = msg.text,
                                        modifier = Modifier.padding(12.dp),
                                        color = sk.frost,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                        is ChatMessage.Loading -> {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                Surface(
                                    color = sk.surface1,
                                    shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, sk.cardBorder),
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.padding(12.dp).size(22.dp),
                                        strokeWidth = 2.dp,
                                        color = sk.brand,
                                    )
                                }
                            }
                        }
                        is ChatMessage.Error -> {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                Surface(
                                    color = sk.crit.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, sk.crit.copy(alpha = 0.35f)),
                                ) {
                                    Text(
                                        text = msg.message,
                                        modifier = Modifier.padding(12.dp),
                                        color = sk.crit,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                        is ChatMessage.Team -> {
                            val r = msg.response
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                Surface(
                                    color = sk.surface1.copy(alpha = 0.90f),
                                    shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, sk.glassBorder),
                                    modifier = Modifier.widthIn(max = 320.dp)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(text = r.answer, style = MaterialTheme.typography.bodyMedium, color = sk.bodyText)
                                        if (r.data.isNotEmpty()) {
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                r.data.forEach { d ->
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Box(Modifier.size(4.dp).background(sk.cyan, RoundedCornerShape(2.dp)))
                                                        Text(
                                                            text = if (d.note.isBlank()) d.name else "${d.name} — ${d.note}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.Medium,
                                                            color = sk.ice,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        if (!r.evidence.isNullOrBlank()) {
                                            Text(r.evidence, style = MaterialTheme.typography.labelSmall, color = sk.subText)
                                        }
                                        val badgeColor = when (r.confidence) {
                                            "high" -> sk.good
                                            "low" -> sk.crit
                                            else -> sk.warn
                                        }
                                        ToneChip("Confidence ${r.confidence ?: "?"}", tint = badgeColor)
                                    }
                                }
                            }
                        }
                        is ChatMessage.Agent -> {
                            val r = msg.response
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                Surface(
                                    color = sk.surface1.copy(alpha = 0.90f),
                                    shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, sk.glassBorder),
                                    modifier = Modifier.widthIn(max = 320.dp)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(text = r.answer, style = MaterialTheme.typography.bodyMedium, color = sk.bodyText)

                                        if (r.evidence != null) {
                                            Surface(
                                                color = sk.surface2.copy(alpha = 0.6f),
                                                shape = RoundedCornerShape(8.dp),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, sk.cardBorder),
                                            ) {
                                                val parsedHtml = HtmlCompat.fromHtml(r.evidence.replace("<div>", "").replace("</div>", "<br>"), HtmlCompat.FROM_HTML_MODE_COMPACT)
                                                Text(
                                                    text = parsedHtml.toString().trim(),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = sk.subText,
                                                    modifier = Modifier.padding(8.dp)
                                                )
                                            }
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val badgeColor = when {
                                                r.confidence == "Low" -> sk.crit
                                                r.confidence?.contains("%") == true && (r.confidence.replace("%","").toIntOrNull() ?: 0) >= 90 -> sk.good
                                                else -> sk.warn
                                            }

                                            ToneChip("Confidence ${r.confidence}", tint = badgeColor)
                                            if (!r.decisionVersion.isNullOrEmpty() && r.decisionVersion != "Not available") {
                                                ToneChip("v${r.decisionVersion}", tint = sk.subText)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Suggestion chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isTeam) {
                    items(teamQuestions) { (key, label) ->
                        Surface(
                            onClick = { viewModel.askTeam(managerEmail, label, questionKey = key) },
                            shape = RoundedCornerShape(12.dp),
                            color = sk.surface1,
                            border = androidx.compose.foundation.BorderStroke(1.dp, sk.cardBorder),
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = sk.labelText,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                } else {
                    items(trainerQuestions) { (key, label) ->
                        Surface(
                            onClick = { viewModel.askQuestion(managerEmail, targetEmail, key, label) },
                            shape = RoundedCornerShape(12.dp),
                            color = sk.surface1,
                            border = androidx.compose.foundation.BorderStroke(1.dp, sk.cardBorder),
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = sk.labelText,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // Free-text ask bar (team context only — the per-trainer sheet is chip-driven).
            if (isTeam) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text("Ask about your team…", color = sk.subText) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = sk.brand,
                            unfocusedBorderColor = sk.glassBorder,
                            focusedTextColor = sk.bodyText,
                            unfocusedTextColor = sk.bodyText,
                            cursorColor = sk.brand,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (draft.isNotBlank()) {
                                viewModel.askTeam(managerEmail, draft, freeText = draft)
                                draft = ""
                            }
                        })
                    )
                    Button(
                        onClick = {
                            if (draft.isNotBlank()) {
                                viewModel.askTeam(managerEmail, draft, freeText = draft)
                                draft = ""
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = sk.brand)
                    ) { Text("Ask", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
