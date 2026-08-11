package com.example.skillsync.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.skillsync.R
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.SectionHeading
import com.example.skillsync.theme.Severity
import com.example.skillsync.theme.SkillCard
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.ToneChip
import com.example.skillsync.theme.glassSurface
import com.example.skillsync.theme.pressable
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.components.Avatar

/**
 * Skill to Select Members to Assign — design vision §7.6.
 *
 * The old flow was modelled on the API rather than the job: RMS takes one
 * course and one trainer, so the UI became one-skill-one-person-with-a-search.
 * A manager's actual verb is "my team needs this capability", which is one
 * skill against many people, and forcing them to repeat a search per person
 * was the clearest example of a developer-shaped workflow in the product.
 *
 * Three steps, in the order a manager thinks in:
 *
 *  1. **Select** — every reportee listed with what they already hold, so the
 *     choice is informed rather than a name-matching exercise. Select All and
 *     a "hide those who already have it" filter, because the common case is
 *     "everyone who is missing this".
 *  2. **Preview** — exactly what will change, per person, before anything is
 *     written. This is a production RMS write, and it cannot be undone.
 *  3. **Result** — per-row outcomes. Partial failure is expected, so a single
 *     "done" would be a lie.
 *
 * What is deliberately absent: Remove Skill and Edit Level. §7.6 asks for
 * both, but the RMS estate has exactly one skill write — `Add Trainer Skill`
 * (key 255). There is no remove and no update. Rather than ship buttons that
 * silently fail, the sheet states the limitation where a manager would look
 * for them.
 */

/** One selectable reportee, with what they already hold for this course. */
data class SkillCandidate(
    val name: String,
    val email: String,
    val photoUrl: String = "",
    val alreadyHas: Boolean = false,
    val currentLevel: Int? = null,
)

/** Outcome of one row, from `/api/v2/skills/bulk-assign`. */
data class SkillWriteResult(
    val email: String,
    val ok: Boolean,
    val message: String,
)

private enum class Step { SELECT, PREVIEW, RESULT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SkillAssignFlow(
    courseName: String,
    candidates: List<SkillCandidate>,
    working: Boolean,
    results: List<SkillWriteResult>?,
    onAssign: (selected: List<SkillCandidate>, level: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sk = MaterialTheme.skill
    var step by remember { mutableStateOf(Step.SELECT) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var level by remember { mutableStateOf(5) }
    var hideExisting by remember { mutableStateOf(true) }

    // Move to the result step the moment outcomes arrive, so the manager is
    // never left on a preview that has already been executed.
    LaunchedEffect(results) { if (results != null) step = Step.RESULT }

    val visible = remember(candidates, hideExisting) {
        if (hideExisting) candidates.filterNot { it.alreadyHas } else candidates
    }
    val chosen = candidates.filter { it.email in selected }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = sk.surface1) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.lg)
                .padding(bottom = Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            SectionHeading(
                when (step) {
                    Step.SELECT -> "Assign skill"
                    Step.PREVIEW -> "Confirm"
                    Step.RESULT -> "Result"
                },
                courseName,
                trailing = when (step) {
                    Step.SELECT -> "${selected.size} selected"
                    Step.PREVIEW -> "${chosen.size} people"
                    Step.RESULT -> null
                },
            )

            when (step) {
                Step.SELECT -> SelectStep(
                    visible = visible,
                    selected = selected,
                    hideExisting = hideExisting,
                    hiddenCount = candidates.count { it.alreadyHas },
                    onToggle = { email ->
                        selected = if (email in selected) selected - email else selected + email
                    },
                    onSelectAll = {
                        selected = if (selected.containsAll(visible.map { it.email }))
                            selected - visible.map { it.email }.toSet()
                        else selected + visible.map { it.email }.toSet()
                    },
                    onHideExisting = { hideExisting = it },
                )

                Step.PREVIEW -> PreviewStep(chosen, level) { level = it }

                Step.RESULT -> ResultStep(results.orEmpty(), candidates)
            }

            // ── Footer ──────────────────────────────────────────────────────
            when (step) {
                Step.SELECT -> Button(
                    onClick = { step = Step.PREVIEW },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(Radii.chip),
                ) {
                    Text(
                        if (selected.isEmpty()) "Select who needs this"
                        else "Review ${selected.size} assignment${if (selected.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                Step.PREVIEW -> Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Button(
                        onClick = { onAssign(chosen, level) },
                        enabled = !working,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("assign-confirm"),
                        shape = RoundedCornerShape(Radii.chip),
                    ) {
                        if (working) {
                            CircularProgressIndicator(
                                color = sk.frost, strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(Space.sm))
                            Text("Writing to RMS", style = MaterialTheme.typography.labelLarge)
                        } else {
                            Text(
                                "Assign to ${chosen.size} ${if (chosen.size == 1) "person" else "people"}",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                    TextButton(
                        onClick = { step = Step.SELECT },
                        enabled = !working,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Back to selection", color = sk.subText) }
                }

                Step.RESULT -> Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(Radii.chip),
                ) { Text("Done", style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

@Composable
private fun SelectStep(
    visible: List<SkillCandidate>,
    selected: Set<String>,
    hideExisting: Boolean,
    hiddenCount: Int,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onHideExisting: (Boolean) -> Unit,
) {
    val sk = MaterialTheme.skill
    val allShown = visible.isNotEmpty() && visible.all { it.email in selected }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (allShown) "Clear all" else "Select all",
            style = MaterialTheme.typography.labelMedium,
            color = sk.sky,
            modifier = Modifier.pressable(onSelectAll),
        )
        Spacer(Modifier.weight(1f))
        if (hiddenCount > 0) {
            Text(
                if (hideExisting) "$hiddenCount already have it" else "Showing everyone",
                style = MaterialTheme.typography.labelSmall, color = sk.labelText,
                modifier = Modifier.pressable { onHideExisting(!hideExisting) },
            )
        }
    }

    if (visible.isEmpty()) {
        Text(
            "Everyone on your team already holds this skill.",
            style = MaterialTheme.typography.bodyMedium, color = sk.subText,
        )
        return
    }

    LazyColumn(
        Modifier.fillMaxWidth().heightIn(max = 320.dp),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        items(visible, key = { it.email }) { c ->
            val isSelected = c.email in selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        if (isSelected) sk.brand.copy(alpha = 0.14f) else Color.Transparent,
                        RoundedCornerShape(Radii.chip),
                    )
                    .pressable { onToggle(c.email) }
                    .padding(Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) sk.brand else Color.Transparent)
                        .border(1.dp, if (isSelected) sk.brand else sk.glassBorder, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            painterResource(R.drawable.ic_check), null,
                            tint = sk.frost, modifier = Modifier.size(12.dp),
                        )
                    }
                }
                Spacer(Modifier.width(Space.sm))
                Avatar(c.name, c.photoUrl, 28.dp)
                Spacer(Modifier.width(Space.sm))
                Column(Modifier.weight(1f)) {
                    Text(
                        c.name,
                        style = MaterialTheme.typography.bodyMedium, color = sk.bodyText,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    // What they already hold, so the choice is informed rather
                    // than a name-matching exercise.
                    Text(
                        when {
                            c.alreadyHas && c.currentLevel != null -> "Already holds it at level ${c.currentLevel}"
                            c.alreadyHas -> "Already holds this skill"
                            else -> "Does not hold this skill"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (c.alreadyHas) sk.warn else sk.subText,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewStep(chosen: List<SkillCandidate>, level: Int, onLevel: (Int) -> Unit) {
    val sk = MaterialTheme.skill

    Text("SKILL LEVEL", style = MaterialTheme.typography.labelSmall, color = sk.labelText)
    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        listOf(3, 5, 8, 10).forEach { l ->
            val on = level == l
            Text(
                "$l",
                style = MaterialTheme.typography.labelLarge,
                color = if (on) sk.frost else sk.subText,
                modifier = Modifier
                    .background(
                        if (on) sk.brand else sk.glass,
                        RoundedCornerShape(Radii.chip),
                    )
                    .pressable { onLevel(l) }
                    .padding(horizontal = Space.lg, vertical = Space.sm),
            )
        }
    }

    SkillCard(Modifier.fillMaxWidth(), severity = Severity.Warning) {
        Text(
            "This writes to production RMS and cannot be undone.",
            style = MaterialTheme.typography.bodyMedium, color = sk.bodyText,
        )
        Text(
            "RMS has no remove or edit endpoint, so a wrong entry has to be " +
                "corrected by the RMS team rather than in this app.",
            style = MaterialTheme.typography.bodySmall, color = sk.subText,
        )
    }

    Text("WILL BE ASSIGNED", style = MaterialTheme.typography.labelSmall, color = sk.labelText)
    Column(
        Modifier.fillMaxWidth().heightIn(max = 200.dp),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        chosen.take(12).forEach { c ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.name,
                    style = MaterialTheme.typography.bodyMedium, color = sk.bodyText,
                    modifier = Modifier.weight(1f), maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (c.alreadyHas) {
                    ToneChip("adds a second entry", sk.warn)
                } else {
                    ToneChip("level $level", sk.aqua)
                }
            }
        }
        if (chosen.size > 12) {
            Text(
                "and ${chosen.size - 12} more",
                style = MaterialTheme.typography.bodySmall, color = sk.labelText,
            )
        }
    }
}

@Composable
private fun ResultStep(results: List<SkillWriteResult>, candidates: List<SkillCandidate>) {
    val sk = MaterialTheme.skill
    val nameFor = candidates.associate { it.email to it.name }
    val ok = results.count { it.ok }
    val failed = results.size - ok

    Text(
        when {
            failed == 0 -> "All $ok recorded in RMS."
            ok == 0 -> "None were recorded."
            else -> "$ok recorded, $failed refused."
        },
        style = MaterialTheme.typography.titleMedium,
        color = if (failed == 0) sk.aqua else sk.warn,
    )

    Column(
        Modifier.fillMaxWidth().heightIn(max = 300.dp),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        // Failures first: they are the rows that still need the manager.
        results.sortedBy { it.ok }.forEach { r ->
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .padding(top = 6.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (r.ok) sk.aqua else sk.crit)
                )
                Spacer(Modifier.width(Space.sm))
                Column(Modifier.weight(1f)) {
                    Text(
                        nameFor[r.email] ?: r.email,
                        style = MaterialTheme.typography.bodyMedium, color = sk.bodyText,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        r.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (r.ok) sk.subText else sk.crit,
                    )
                }
            }
        }
    }
}
