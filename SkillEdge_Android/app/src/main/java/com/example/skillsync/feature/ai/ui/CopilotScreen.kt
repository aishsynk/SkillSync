package com.example.skillsync.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.skillsync.R
import com.example.skillsync.ai.*
import com.example.skillsync.theme.*
import com.example.skillsync.ui.components.Appear
import com.example.skillsync.ui.components.LocalNotify

/**
 * The delivery agent.
 *
 * Answers manager questions from the fused RMS fact base and proposes the next
 * action per trainer, with the evidence behind every claim. Accepting or
 * dismissing a suggestion trains the ranking, so the queue reorders around what
 * this manager actually acts on.
 *
 * The banner at the top is not modesty for its own sake. This agent has no
 * language model: it recognises a bounded set of questions and refuses the
 * rest. Telling the manager that up front is what makes the refusals readable
 * as a designed behaviour rather than a bug.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopilotScreen(
    team: TeamFact,
    onTrainerClick: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    val sk = MaterialTheme.skill
    val notify = LocalNotify.current
    val keyboard = LocalSoftwareKeyboardController.current

    var weights by remember { mutableStateOf(LearningStore.load()) }
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<Answer?>(null) }
    // Kinds the manager has already ruled on, so the queue does not re-offer them.
    var handled by remember { mutableStateOf<Set<String>>(emptySet()) }

    val queue = remember(team, weights, handled) {
        Recommender.forTeam(team, weights).filterNot { "${it.kind}:${it.subject}" in handled }
    }

    fun rule(s: Suggestion, accepted: Boolean) {
        weights = LearningStore.record(s.kind, accepted)
        handled = handled + "${s.kind}:${s.subject}"
        if (accepted) notify.success("Noted", "More like this will rank higher.")
        else notify.info("Dismissed", "Fewer like this from now on.")
    }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Delivery agent", style = MaterialTheme.typography.titleLarge, color = sk.bodyText)
                            Text(
                                if (weights.isTrained) "Trained on ${weights.events} of your decisions"
                                else "Learning from your decisions",
                                style = MaterialTheme.typography.labelSmall,
                                color = sk.labelText,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_back), "Back", tint = sk.ice)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
            bottomBar = {
                AskBar(
                    value = question,
                    onValueChange = { question = it },
                    onAsk = {
                        if (question.isNotBlank()) {
                            keyboard?.hide()
                            answer = Agent.ask(question, team, weights)
                        }
                    },
                )
            },
        ) { pv ->
            LazyColumn(
                Modifier.fillMaxSize().padding(pv),
                contentPadding = PaddingValues(
                    start = Layout.gutter, end = Layout.gutter,
                    top = Space.sm, bottom = Space.lg,
                ),
                verticalArrangement = Arrangement.spacedBy(Layout.section),
            ) {
                item { ScopeNote(team) }

                answer?.let { a ->
                    item { Appear(0) { AnswerCard(a, onTrainerClick) } }
                }

                item {
                    SectionHeading("Ask", "Tap one, or type your own below")
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        items(Agent.starters(team)) { starter ->
                            Text(
                                starter,
                                style = MaterialTheme.typography.labelMedium,
                                color = sk.ice,
                                maxLines = 1,
                                modifier = Modifier
                                    .background(sk.glass, RoundedCornerShape(Radii.chip))
                                    .pressable {
                                        question = starter
                                        answer = Agent.ask(starter, team, weights)
                                    }
                                    .padding(horizontal = Space.md, vertical = Space.sm),
                            )
                        }
                    }
                }

                item {
                    SectionHeading(
                        "Next best actions",
                        if (queue.isEmpty()) "Nothing outstanding across the team."
                        else "Ranked by what you have acted on before.",
                        trailing = if (queue.isEmpty()) null else "${queue.size}",
                    )
                }
                items(queue.take(12), key = { "${it.kind}:${it.subject}" }) { s ->
                    SuggestionCard(
                        suggestion = s,
                        onOpen = {
                            if (s.subjectEmail.isNotBlank()) onTrainerClick(s.subjectEmail, s.subject)
                        },
                        onAccept = { rule(s, true) },
                        onDismiss = { rule(s, false) },
                    )
                }
            }
        }
    }
}

/**
 * States what the agent can and cannot do, in the manager's own terms, before
 * they ask their first question and form the wrong expectation.
 */
@Composable
private fun ScopeNote(team: TeamFact) {
    val sk = MaterialTheme.skill
    Column(
        Modifier
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(Radii.card))
            .padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Text(
            "Reads your live RMS data",
            style = MaterialTheme.typography.titleSmall,
            color = sk.bodyText,
        )
        Text(
            "${team.trainers.size} reportees, utilisation, certifications, feedback, " +
                "readiness and open demand. It answers delivery questions and will say so " +
                "when a question is outside what it can check.",
            style = MaterialTheme.typography.bodySmall,
            color = sk.subText,
        )
    }
}

@Composable
private fun AnswerCard(a: Answer, onTrainerClick: (String, String) -> Unit) {
    val sk = MaterialTheme.skill
    val tint = when (a.confidence) {
        Confidence.HIGH -> sk.aqua
        Confidence.MEDIUM -> sk.warn
        Confidence.LOW -> sk.labelText
    }
    SkillCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                a.headline,
                style = MaterialTheme.typography.titleMedium,
                color = sk.bodyText,
                modifier = Modifier.weight(1f),
            )
            ToneChip(a.confidence.name.lowercase(), tint)
        }
        if (a.detail.isNotBlank()) {
            Text(a.detail, style = MaterialTheme.typography.bodyMedium, color = sk.subText)
        }
        a.unmet?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = sk.warn,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(sk.warn.copy(alpha = 0.10f), RoundedCornerShape(Radii.chip))
                    .padding(Space.md),
            )
        }
        if (a.evidence.isNotEmpty()) {
            Text("EVIDENCE", style = MaterialTheme.typography.labelSmall, color = sk.labelText)
            a.evidence.forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(4.dp).background(sk.sky, RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(Space.sm))
                    Text(line, style = MaterialTheme.typography.bodySmall, color = sk.subText)
                }
            }
        }
        a.suggestions.firstOrNull()?.let { s ->
            if (s.subjectEmail.isNotBlank()) {
                TextButton(onClick = { onTrainerClick(s.subjectEmail, s.subject) }) {
                    Text("Open ${s.subject}", style = MaterialTheme.typography.labelMedium, color = sk.sky)
                }
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    suggestion: Suggestion,
    onOpen: () -> Unit,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sk = MaterialTheme.skill
    var showWhy by remember { mutableStateOf(false) }

    SkillCard(Modifier.fillMaxWidth(), severity = severityFor(suggestion.kind)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToneChip(suggestion.kind.label, severityFor(suggestion.kind).tint())
            Spacer(Modifier.weight(1f))
            Text(
                "${suggestion.score}",
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = sk.labelText,
            )
        }
        Text(
            suggestion.headline,
            style = MaterialTheme.typography.titleMedium,
            color = sk.bodyText,
            modifier = Modifier.pressable(onOpen),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (showWhy) suggestion.rationale else suggestion.rationale.take(96).let {
                if (suggestion.rationale.length > 96) "$it…" else it
            },
            style = MaterialTheme.typography.bodySmall,
            color = sk.subText,
            modifier = Modifier.pressable { showWhy = !showWhy },
        )
        if (showWhy && suggestion.evidence.isNotEmpty()) {
            suggestion.evidence.forEach { line ->
                Text("· $line", style = MaterialTheme.typography.bodySmall, color = sk.labelText)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            FilledTonalButton(
                onClick = onAccept,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(Radii.chip),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = sk.aqua.copy(alpha = 0.18f),
                    contentColor = sk.aqua,
                ),
            ) { Text("Useful", style = MaterialTheme.typography.labelLarge) }
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(Radii.chip),
            ) {
                Text("Not now", style = MaterialTheme.typography.labelLarge, color = sk.subText)
            }
        }
    }
}

private fun severityFor(kind: SuggestionKind): Severity = when (kind) {
    SuggestionKind.ADDRESS_FEEDBACK -> Severity.Critical
    SuggestionKind.CLOSE_CERT_GAP, SuggestionKind.REBALANCE_LOAD,
    SuggestionKind.COVER_KEY_PERSON_RISK -> Severity.Warning
    SuggestionKind.ALLOCATE_TO_DEMAND, SuggestionKind.BUILD_BENCH_SKILL -> Severity.Watch
    SuggestionKind.CLOSE_OPEN_ACTIONS -> Severity.Info
    SuggestionKind.RECOGNISE -> Severity.Good
}

@Composable
private fun AskBar(value: String, onValueChange: (String) -> Unit, onAsk: () -> Unit) {
    val sk = MaterialTheme.skill
    Row(
        Modifier
            .fillMaxWidth()
            .background(sk.surface1)
            .navigationBarsPadding()
            .imePadding()
            .padding(Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("Ask about your team", style = MaterialTheme.typography.bodyMedium) },
            singleLine = true,
            shape = RoundedCornerShape(Radii.chip),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onAsk() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = sk.brand,
                unfocusedBorderColor = sk.glassBorder,
                focusedTextColor = sk.bodyText,
                unfocusedTextColor = sk.bodyText,
                cursorColor = sk.brand,
            ),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Space.sm))
        FilledTonalButton(
            onClick = onAsk,
            shape = RoundedCornerShape(Radii.chip),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = sk.brand.copy(alpha = 0.85f),
                contentColor = sk.frost,
            ),
        ) { Text("Ask", style = MaterialTheme.typography.labelLarge) }
    }
}
