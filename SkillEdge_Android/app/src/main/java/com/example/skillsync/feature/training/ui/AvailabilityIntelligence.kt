package com.example.skillsync.ui.batch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.skillsync.R
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.components.intOrNull
import com.example.skillsync.ui.components.obj
import com.example.skillsync.ui.components.str

/**
 * The Demand screen's expression of the intelligence layer.
 *
 * Everything here reports a verdict the backend computed from real RMS data —
 * the free-date calendar, visa records and time zones — rather than a proxy.
 * Until now this screen inferred availability from a utilisation percentage,
 * which was the product's largest correctness error: a trainer at 80% can be
 * free on the dates that matter, and one at 40% can be on leave.
 *
 * Three principles hold in every component below:
 *
 *  1. Unknown is shown, never hidden. Roughly half of trainers carry no visa
 *     record, so an unknown reads as "verification required" — a task for the
 *     manager, not a reason to drop someone from the list.
 *  2. "Could not check" never looks like "nothing found". They are opposite
 *     facts and lead to opposite actions.
 *  3. Nothing claims a check it did not perform. The board does not evaluate
 *     DNC or leave, and says so, because a surface that looks authoritative
 *     while skipping a non-overridable rule is worse than one that never
 *     claimed the check.
 */

// ── Batch-level coverage verdict ────────────────────────────────────────────

/**
 * Whether this course could be checked at all, and what was found.
 *
 * A course-specific free-schedule response is availability evidence, not the
 * authoritative skill inventory. Empty rows must never become a claim that no
 * trainer holds the skill, especially when the card has matched candidates.
 */
@Composable
internal fun CoverageVerdictStrip(batch: Map<*, *>) {
    val sk = MaterialTheme.skill
    val intel = batch.obj("availability_intelligence") ?: return
    val source = intel.str("source")
    if (source == "rms_free_schedule") return          // the normal case needs no strip

    val (tint, label, detail) = when (source) {
        "availability_unknown" -> Triple(
            sk.warn,
            "COURSE AVAILABILITY NOT VERIFIED",
            intel.str("note").ifBlank { "RMS returned no course-specific free-schedule rows. Open the batch to verify dates." },
        )
        else -> Triple(
            sk.warn,
            "AVAILABILITY NOT VERIFIED",
            intel.str("note").ifBlank { "This course could not be matched in the RMS catalogue." },
        )
    }

    Row(
        Modifier
            .fillMaxWidth()
            .background(tint.copy(alpha = 0.12f), RoundedCornerShape(Radii.chip))
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(Radii.chip))
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painterResource(R.drawable.ic_alert), null,
            tint = tint, modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(Space.sm))
        androidx.compose.foundation.layout.Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = sk.subText,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Candidate-level verdicts ────────────────────────────────────────────────

/**
 * Real availability for this candidate on these exact dates.
 *
 * Deliberately replaces the "% utilised" caption that used to sit here. A
 * percentage told a manager how busy someone had been; this tells them whether
 * the person can actually take the batch.
 */
@Composable
internal fun AvailabilityChip(candidate: Map<*, *>) {
    val sk = MaterialTheme.skill
    val avail = candidate.obj("real_availability") ?: return
    val status = avail.str("status")
    val reason = avail.str("reason")

    val (tint, label) = when (status) {
        "available" -> sk.aqua to "Free on these dates"
        "available_with_conflicts" -> sk.sky to "Free, provisional clashes"
        "partially_available" -> sk.warn to "Partly free"
        "unavailable" -> sk.crit to (reason.ifBlank { "Not available" })
        else -> sk.labelText to "Availability unknown"
    }
    Tone(label, tint)
}

/**
 * Visa state, in the manager's own vocabulary.
 *
 * Unknown is a first-class outcome here, not a silent exclusion: an unrecorded
 * visa means unrecorded at least as often as it means unavailable, and hiding
 * those trainers would remove about half the pool from consideration.
 */
@Composable
internal fun VisaChip(candidate: Map<*, *>) {
    val sk = MaterialTheme.skill
    val intl = candidate.obj("international_readiness") ?: return
    val (tint, label) = when (intl.str("visa")) {
        "available" -> sk.aqua to "Visa available"
        "not_available" -> sk.crit to "Visa not available"
        else -> sk.warn to "Visa unknown, verify"
    }
    Tone(label, tint)
}

/** Time-zone fit for an international delivery, where it was computable. */
@Composable
internal fun TimeZoneChip(candidate: Map<*, *>) {
    val sk = MaterialTheme.skill
    val intl = candidate.obj("international_readiness") ?: return
    val fit = intl.str("timezone_fit")
    if (fit.isBlank() || fit == "unknown") return
    val (tint, label) = when (fit) {
        "comfortable" -> sk.aqua to "Time zone fits"
        "workable" -> sk.sky to "Unsocial hours"
        else -> sk.warn to "Night or early shift"
    }
    Tone(label, tint)
}

/**
 * Evidence a manager can weigh: proficiency and how often this person has
 * actually delivered *this* course, which the previous card never showed.
 */
@Composable
internal fun ExperienceChips(candidate: Map<*, *>) {
    val sk = MaterialTheme.skill
    // These arrive as JSON numbers, which Gson decodes to Double — str() would
    // render "Level 9.0". intOrNull keeps them whole.
    val level = candidate.intOrNull("skill_level")
    val runs = candidate.intOrNull("course_deliveries")
    if (level != null) Tone("Level $level", sk.indigo)
    if (runs != null && runs > 0) Tone("$runs delivered", sk.indigo)
}

/**
 * States what the board did not check.
 *
 * Client exclusions and leave need a per-trainer call that is too costly across
 * a whole board, so they are applied when the batch is opened. Saying so keeps
 * the list honest rather than quietly authoritative.
 */
@Composable
internal fun UncheckedNotice(batch: Map<*, *>) {
    val sk = MaterialTheme.skill
    val intel = batch.obj("availability_intelligence") ?: return
    // Compare the decoded boolean rather than its string form.
    if (intel["dnc_checked"] == true) return
    Text(
        "Client exclusions and leave are checked when you open this batch.",
        style = MaterialTheme.typography.bodySmall,
        color = sk.labelText,
    )
}

@Composable
private fun Tone(text: String, tint: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = tint,
        maxLines = 1,
        modifier = Modifier
            .background(tint.copy(alpha = 0.14f), RoundedCornerShape(Radii.chip))
            .padding(horizontal = Space.sm, vertical = 2.dp),
    )
}

/** The verdict row under a candidate's name. */
@Composable
internal fun CandidateVerdictRow(candidate: Map<*, *>, international: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(top = Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvailabilityChip(candidate)
        if (international) {
            VisaChip(candidate)
            TimeZoneChip(candidate)
        }
        ExperienceChips(candidate)
    }
}

/** Kept for callers that only need the coloured dot. */
@Composable
internal fun VerdictDot(tint: Color) {
    Box(Modifier.size(6.dp).background(tint, RoundedCornerShape(3.dp)))
}
