package com.example.skillsync.feature.ai.ui

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.example.skillsync.R
import com.example.skillsync.feature.ai.*
import com.example.skillsync.theme.*
import com.example.skillsync.core.ui.Appear
import com.example.skillsync.core.ui.LocalNotify
import androidx.compose.material3.Text

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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
                    // FlowRow, not a LazyRow: the starters are short and few, and
                    // the horizontal list was clipping the last chip against the
                    // gutter with no affordance that it scrolled.
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                        verticalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        Agent.starters(team).forEach { starter ->
                            Text(
                                starter,
                                style = MaterialTheme.typography.labelMedium,
                                color = sk.ice,
                                maxLines = 2,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(Radii.chip))
                                    .background(sk.glass)
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
    Row(
        Modifier
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(Radii.card))
            .padding(Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconSlot(sk.sky, size = 30.dp) {
            Icon(painterResource(R.drawable.ic_search), null, tint = sk.sky, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(
                "Reads your live RMS data",
                style = MaterialTheme.typography.labelLarge,
                color = sk.bodyText,
            )
            Text(
                "${team.trainers.size} reportees · utilisation, certifications, feedback, " +
                    "readiness, open demand. Outside that it says so.",
                style = MaterialTheme.typography.labelSmall,
                color = sk.subText,
            )
        }
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
            ToneChip("confidence " + a.confidence.name.lowercase(), tint)
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
    val tint = severityFor(suggestion.kind).tint()

    Column(
        Modifier.fillMaxWidth().accentGlass(tint).padding(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        // Action, then who it concerns, on one line — the card's identity is
        // the pairing, not a headline that wraps over three lines.
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToneChip(suggestion.kind.label, tint)
            Spacer(Modifier.width(Space.sm))
            Text(
                suggestion.subject,
                style = MaterialTheme.typography.labelMedium,
                color = sk.labelText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painterResource(R.drawable.ic_chevron),
                contentDescription = null,
                tint = sk.subText,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            suggestion.headline,
            style = MaterialTheme.typography.bodyMedium,
            color = sk.bodyText,
            modifier = Modifier.pressable(onOpen),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // The reason is never mid-sentence truncated any more: it shows in full
        // to two lines, and tapping reveals the evidence behind it.
        Text(
            suggestion.rationale,
            style = MaterialTheme.typography.bodySmall,
            color = sk.subText,
            maxLines = if (showWhy) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.pressable { showWhy = !showWhy },
        )
        if (showWhy && suggestion.evidence.isNotEmpty()) {
            Text(
                "EVIDENCE",
                style = MaterialTheme.typography.labelSmall,
                color = sk.labelText, fontWeight = FontWeight.Bold, letterSpacing = 0.08.em,
            )
            suggestion.evidence.forEach { line ->
                Text("· $line", style = MaterialTheme.typography.bodySmall, color = sk.labelText)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            CompactFeedback("Useful", R.drawable.ic_check, sk.aqua, onAccept)
            Spacer(Modifier.width(Space.lg))
            CompactFeedback("Not now", R.drawable.ic_forward, sk.subText, onDismiss)
            Spacer(Modifier.weight(1f))
            if (!showWhy && suggestion.evidence.isNotEmpty()) {
                Text(
                    "Why",
                    style = MaterialTheme.typography.labelMedium,
                    color = sk.sky,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radii.chip))
                        .pressable { showWhy = true }
                        .padding(horizontal = Space.sm, vertical = 4.dp),
                )
            }
        }
    }
}

/** Icon-plus-label feedback action; deliberately not a full-width button. */
@Composable
private fun CompactFeedback(label: String, iconRes: Int, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(Radii.chip))
            .pressable(onClick)
            .padding(horizontal = Space.sm, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(iconRes), contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
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
