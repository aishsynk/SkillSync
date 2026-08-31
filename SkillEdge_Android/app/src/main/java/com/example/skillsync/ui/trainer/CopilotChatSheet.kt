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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopilotChatSheet(
    managerEmail: String,
    targetEmail: String = "",
    viewModel: CopilotViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onDismiss: () -> Unit
) {
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
        containerColor = MaterialTheme.colorScheme.surface,
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
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isTeam) "Team Copilot ✨" else "SkillEdge Copilot ✨",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = onDismiss) {
                    Text("✕", fontSize = 20.sp)
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
                        Text(
                            text = if (isTeam)
                                "Ask about your team — who is free for a course, coverage risk, the bench, upskills, or who needs a 1:1."
                            else
                                "Ask me anything about this trainer's readiness, gaps, and allocation fit.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 32.dp, horizontal = 16.dp),
                            fontSize = 14.sp
                        )
                    }
                }

                items(messages) { msg ->
                    when (msg) {
                        is ChatMessage.User -> {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
                                    modifier = Modifier.widthIn(max = 280.dp)
                                ) {
                                    Text(
                                        text = msg.text,
                                        modifier = Modifier.padding(12.dp),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                        is ChatMessage.Loading -> {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.padding(12.dp).size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }
                        is ChatMessage.Error -> {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
                                ) {
                                    Text(
                                        text = msg.message,
                                        modifier = Modifier.padding(12.dp),
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                        is ChatMessage.Team -> {
                            val r = msg.response
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                                    modifier = Modifier.widthIn(max = 320.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(text = r.answer, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (r.data.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            r.data.forEach { d ->
                                                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                                    Text("• ", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Text(
                                                        text = if (d.note.isBlank()) d.name else "${d.name} — ${d.note}",
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                        if (!r.evidence.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(r.evidence, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        val badgeColor = when (r.confidence) {
                                            "high" -> Color(0xFF81C784)
                                            "low" -> Color(0xFFE57373)
                                            else -> Color(0xFFFFB74D)
                                        }
                                        Surface(color = badgeColor.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                            Text("Confidence ${r.confidence ?: "?"}", fontSize = 10.sp, color = badgeColor, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                }
                            }
                        }
                        is ChatMessage.Agent -> {
                            val r = msg.response
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                                    modifier = Modifier.widthIn(max = 320.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(text = r.answer, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                        if (r.evidence != null) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.surface.copy(alpha=0.5f),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                val parsedHtml = HtmlCompat.fromHtml(r.evidence.replace("<div>", "").replace("</div>", "<br>"), HtmlCompat.FROM_HTML_MODE_COMPACT)
                                                Text(
                                                    text = parsedHtml.toString().trim(),
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(8.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val badgeColor = when {
                                                r.confidence == "Low" -> Color(0xFFE57373)
                                                r.confidence?.contains("%") == true && (r.confidence.replace("%","").toIntOrNull() ?: 0) >= 90 -> Color(0xFF81C784)
                                                else -> Color(0xFFFFB74D)
                                            }

                                            Surface(color = badgeColor.copy(alpha=0.2f), shape = RoundedCornerShape(4.dp)) {
                                                Text("Confidence ${r.confidence}", fontSize = 10.sp, color = badgeColor, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                            }
                                            if (!r.decisionVersion.isNullOrEmpty() && r.decisionVersion != "Not available") {
                                                Surface(color = Color.Gray.copy(alpha=0.2f), shape = RoundedCornerShape(4.dp)) {
                                                    Text("v${r.decisionVersion}", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                                }
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
                        SuggestionChip(
                            onClick = { viewModel.askTeam(managerEmail, label, questionKey = key) },
                            label = { Text(label) }
                        )
                    }
                } else {
                    items(trainerQuestions) { (key, label) ->
                        SuggestionChip(
                            onClick = { viewModel.askQuestion(managerEmail, targetEmail, key, label) },
                            label = { Text(label) }
                        )
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
                        placeholder = { Text("Ask about your team") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
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
                        }
                    ) { Text("Ask") }
                }
            }
        }
    }
}
