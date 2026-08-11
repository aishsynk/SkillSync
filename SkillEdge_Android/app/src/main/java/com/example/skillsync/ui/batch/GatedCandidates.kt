package com.example.skillsync.ui.batch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.skillsync.R
import com.example.skillsync.data.api.AllocationCandidatesResponse
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.SectionHeading
import com.example.skillsync.theme.SkillCard
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.pressable
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.components.intOrNull
import com.example.skillsync.ui.components.list
import com.example.skillsync.ui.components.obj
import com.example.skillsync.ui.components.str

/**
 * The fully gated candidate evaluation for one batch.
 *
 * The demand board can only overlay availability; checking client exclusions
 * and leave needs a per-trainer call that is too costly across a whole board.
 * That work happens here, and this section is the reason the board is allowed
 * to say it did not check them.
 *
 * Two things are non-negotiable in this surface:
 *
 *  - **Blocked candidates stay visible, with the gate that stopped them.**
 *    Silently filtering an excluded trainer makes their absence look like an
 *    oversight, and a manager cannot audit a list they cannot see.
 *  - **A course that could not be resolved is never rendered as an empty
 *    pool.** "We could not check" and "nobody can do this" are opposite facts.
 */
@Composable
internal fun GatedCandidatesSection(
    response: AllocationCandidatesResponse?,
    loading: Boolean,
    unverified: String?,
    modifier: Modifier = Modifier,
) {
    val sk = MaterialTheme.skill

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.md)) {
        SectionHeading(
            "Who can actually take this",
            when {
                loading -> "Checking leave, client rules and visas…"
                unverified != null -> "Could not verify"
                response != null -> {
                    val ok = response.counts["eligible"]?.toInt() ?: 0
                    val no = response.counts["blocked"]?.toInt() ?: 0
                    "$ok eligible, $no blocked, checked against real dates"
                }
                else -> "Checked against the RMS calendar, leave and client rules"
            },
        )

        when {
            loading -> SkillCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = sk.sky, strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(Space.md))
                    Text(
                        "Running the full eligibility check",
                        style = MaterialTheme.typography.bodyMedium, color = sk.subText,
                    )
                }
            }

            unverified != null -> SkillCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        painterResource(R.drawable.ic_alert), null,
                        tint = sk.warn, modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(Space.sm))
                    Column {
                        Text(
                            "Availability could not be verified",
                            style = MaterialTheme.typography.titleSmall, color = sk.bodyText,
                        )
                        Text(
                            unverified,
                            style = MaterialTheme.typography.bodySmall, color = sk.subText,
                        )
                        Text(
                            "This is not the same as nobody being available.",
                            style = MaterialTheme.typography.bodySmall, color = sk.warn,
                            modifier = Modifier.padding(top = Space.xs),
                        )
                    }
                }
            }

            response == null -> Unit

            else -> {
                response.candidates.forEach { c -> EligibleCandidate(c) }
                if (response.candidates.isEmpty()) {
                    SkillCard(Modifier.fillMaxWidth()) {
                        Text(
                            "Nobody on your team clears every requirement for these dates.",
                            style = MaterialTheme.typography.bodyMedium, color = sk.bodyText,
                        )
                        Text(
                            "The blocked list below shows what stopped each person.",
                            style = MaterialTheme.typography.bodySmall, color = sk.subText,
                        )
                    }
                }
                if (response.blocked.isNotEmpty()) BlockedList(response.blocked)
            }
        }
    }
}

@Composable
private fun EligibleCandidate(c: Map<String, Any>) {
    val sk = MaterialTheme.skill
    var open by rememberSaveable(c.str("trainer_name")) { mutableStateOf(false) }
    val factors = c.list("factors")
    val intl = c.obj("international")

    SkillCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    c.str("trainer_name"),
                    style = MaterialTheme.typography.titleMedium, color = sk.bodyText,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    c.obj("availability")?.str("status")?.replace('_', ' ')
                        ?.replaceFirstChar { it.uppercase() } ?: "",
                    style = MaterialTheme.typography.bodySmall, color = sk.subText,
                )
            }
            Text(
                // intOrNull, not str: Gson decodes JSON numbers as Double, so
                // str() would render a score as "87.0".
                "${c.intOrNull("fit") ?: 0}",
                style = MaterialTheme.typography.headlineSmall
                    .copy(fontFeatureSettings = "tnum"),
                color = sk.aqua,
            )
        }

        if (c["requires_verification"] == true) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(sk.warn.copy(alpha = 0.12f), RoundedCornerShape(Radii.chip))
                    .padding(horizontal = Space.md, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.ic_alert), null,
                    tint = sk.warn, modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(Space.sm))
                Text(
                    intl?.str("visa_detail")?.takeIf { it.isNotBlank() }
                        ?.let { "Visa: $it. Verify before assigning." }
                        ?: "No visa record held. Verify before assigning.",
                    style = MaterialTheme.typography.bodySmall, color = sk.bodyText,
                )
            }
        }

        Text(
            if (open) "Hide the reasoning" else "Why this score",
            style = MaterialTheme.typography.labelMedium,
            color = sk.sky,
            modifier = Modifier.pressable { open = !open },
        )

        if (open) {
            factors.forEach { f ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val n = f.intOrNull("contribution") ?: 0
                    val positive = n >= 0
                    Text(
                        if (positive) "+$n" else "$n",
                        style = MaterialTheme.typography.labelMedium
                            .copy(fontFeatureSettings = "tnum"),
                        color = if (positive) sk.aqua else sk.warn,
                        modifier = Modifier.width(38.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            f.str("name"),
                            style = MaterialTheme.typography.bodyMedium, color = sk.bodyText,
                        )
                        Text(
                            f.str("evidence"),
                            style = MaterialTheme.typography.bodySmall, color = sk.subText,
                        )
                    }
                }
            }
            if (factors.isEmpty()) {
                Text(
                    "No individual factors were recorded for this candidate.",
                    style = MaterialTheme.typography.bodySmall, color = sk.subText,
                )
            }
        }
    }
}

/**
 * Excluded candidates, kept on screen with the gate that stopped them. A
 * manager who cannot see that their strongest trainer was blocked by a client
 * exclusion will assume the system simply missed them.
 */
@Composable
private fun BlockedList(blocked: List<Map<String, Any>>) {
    val sk = MaterialTheme.skill
    var open by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .border(1.dp, sk.cardBorder, RoundedCornerShape(Radii.chip))
                .pressable { open = !open }
                .padding(Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${blocked.size} not eligible",
                style = MaterialTheme.typography.titleSmall,
                color = sk.bodyText,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (open) "Hide" else "Show why",
                style = MaterialTheme.typography.labelMedium, color = sk.sky,
            )
        }

        if (open) {
            blocked.forEach { b ->
                val gates = b.list("blockers")
                Row(
                    Modifier.fillMaxWidth().padding(top = Space.sm),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        Modifier
                            .padding(top = 6.dp)
                            .size(6.dp)
                            .background(gateTint(gates), RoundedCornerShape(3.dp))
                    )
                    Spacer(Modifier.width(Space.sm))
                    Column(Modifier.weight(1f)) {
                        Text(
                            b.str("trainer_name"),
                            style = MaterialTheme.typography.bodyMedium, color = sk.subText,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        gates.forEach { g ->
                            Text(
                                gateLabel(g.str("gate"), g.str("detail")),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (g.str("gate") == "dnc") sk.crit else sk.labelText,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun gateTint(gates: List<Map<*, *>>): Color {
    val sk = MaterialTheme.skill
    return when {
        gates.any { it.str("gate") == "dnc" } -> sk.crit
        gates.any { it.str("gate").contains("visa") } -> sk.warn
        else -> sk.labelText
    }
}

/** Plain language for each gate; a manager should not have to read field names. */
private fun gateLabel(gate: String, detail: String): String = when (gate) {
    "dnc" -> "Client exclusion: $detail"
    "availability" -> "Not available: $detail"
    "skill_level" -> "Skill: $detail"
    "visa" -> "Visa: $detail"
    "travel_window" -> "Travel: $detail"
    "international_travel_window" -> "International travel: $detail"
    "shift_window" -> "Shift: $detail"
    else -> detail.ifBlank { gate }
}
