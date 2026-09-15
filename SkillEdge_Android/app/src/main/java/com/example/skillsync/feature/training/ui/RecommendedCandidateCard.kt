package com.example.skillsync.feature.training.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.skillsync.core.ui.*
import com.example.skillsync.theme.skill

/** The one verdict a manager reads first. Hard eligibility decides it; score never overrides. */
internal enum class CandidateVerdict(val label: String, val glyph: String) {
    Recommended("RECOMMENDED", "✓"),
    NeedsReview("NEEDS REVIEW", "!"),
    Conflict("CONFLICT", "✕"),
    Blocked("BLOCKED", "✕"),
    NotEligible("NOT ELIGIBLE", "✕");

    val hardFail: Boolean get() = this == Conflict || this == Blocked || this == NotEligible
}

private enum class Gate { Pass, Unknown, Fail }

/**
 * Recommended-trainer card. Reading order is deliberate:
 * 1. who + the verdict (a filled status badge, not a coloured word),
 * 2. the four eligibility gates — skill, dates, certification, clearance —
 *    as equal-width pass/unknown/fail cells,
 * 3. suitability, which is visually suppressed whenever a gate hard-fails,
 * 4. the single most important reason, in a tinted strip.
 * A high match score can never make a failed gate look acceptable.
 */
@Composable
internal fun RecommendedCandidateCard(c: Map<*, *>, rank: Int, international: Boolean) {
    val sk = MaterialTheme.skill

    val match = c.int("match")
    val blocked = c.bool("blocked")
    val dnc = c.bool("dnc_flag")
    val realAvail = c.obj("real_availability")
    val realStatus = realAvail?.str("status")
    val legacyStatus = c.str("availability_status")
    val conflict = realStatus == "unavailable" || legacyStatus == "conflict"
    val availVerified = c.bool("availability_verified") || (realStatus != null && realStatus != "unknown")
    val missing = c.list("missing_skills").joinToString(", ").ifBlank {
        (c["missing_skills"] as? List<*>)?.filterIsInstance<String>()?.joinToString(", ").orEmpty()
    }
    val parts = c.obj("suitability_components")
    val certKnown = c.containsKey("certification_covered") || parts != null
    val certified = c.bool("certification_covered") || parts?.int("certification") == 100

    val verdict = when {
        blocked -> CandidateVerdict.Blocked
        conflict -> CandidateVerdict.Conflict
        match in 1 until 50 -> CandidateVerdict.NotEligible
        !availVerified || match < 75 || missing.isNotBlank() -> CandidateVerdict.NeedsReview
        else -> CandidateVerdict.Recommended
    }
    val tint = when (verdict) {
        CandidateVerdict.Recommended -> sk.good
        CandidateVerdict.NeedsReview -> sk.warn
        else -> sk.crit
    }

    val skillGate = when { match >= 75 && missing.isBlank() -> Gate.Pass; match >= 50 -> Gate.Unknown; else -> Gate.Fail }
    val datesGate = when { conflict -> Gate.Fail; availVerified -> Gate.Pass; else -> Gate.Unknown }
    val certGate = when { !certKnown -> Gate.Unknown; certified -> Gate.Pass; else -> Gate.Unknown }
    val clearGate = if (blocked) Gate.Fail else Gate.Pass

    val reason = when (verdict) {
        CandidateVerdict.Blocked ->
            if (dnc) "On the client's do-not-contact list — cannot be assigned to this batch."
            else "Negative feedback on file — held from allocation until ${c.str("blocked_until").shortDate()}."
        CandidateVerdict.Conflict ->
            "Booked on these dates. A strong skill match does not override a real conflict."
        CandidateVerdict.NotEligible ->
            if (missing.isNotBlank()) "Missing required skills: $missing." else "Skill match $match% is below the 50% eligibility floor."
        CandidateVerdict.NeedsReview -> when {
            !availVerified -> "Availability has not been checked — confirm dates before assigning."
            missing.isNotBlank() -> "Missing: $missing — plan upskilling before delivery."
            else -> "Skill match $match% — upskilling likely before delivery."
        }
        CandidateVerdict.Recommended -> listOfNotNull(
            "Available on these dates",
            "$match% skill match",
            if (certified) "certified" else null,
            c.str("via_course").takeIf { it.isNotBlank() }?.let { "via $it" },
        ).joinToString(" · ") + "."
    }

    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(tint.copy(alpha = if (verdict.hardFail) 0.12f else 0.08f), sk.surface1)))
            .border(1.dp, tint.copy(alpha = 0.45f), shape),
    ) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(tint))
        Column(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // 1 — identity + verdict
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Avatar(name = c.str("trainer_name"), photoUrl = c.str("photo_url").takeIf { it.isNotBlank() }, size = 42.dp)
                    Box(
                        Modifier.size(18.dp).clip(CircleShape).background(if (verdict.hardFail) sk.labelText else sk.brand)
                            .border(2.dp, sk.navy, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text("$rank", fontSize = 10.sp, color = sk.frost, fontWeight = FontWeight.Black) }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        c.str("trainer_name"),
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        color = if (verdict.hardFail) sk.subText else sk.frost,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(
                            c.str("backup_role").takeIf { it.isNotBlank() },
                            c.str("coverage").takeIf { it.isNotBlank() && !verdict.hardFail },
                        ).joinToString(" · ").ifBlank { "Candidate #$rank" },
                        style = MaterialTheme.typography.labelSmall, color = sk.subText, maxLines = 1,
                    )
                }
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(tint).padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(verdict.glyph, fontSize = 11.sp, color = sk.navy, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(4.dp))
                    Text(verdict.label, fontSize = 10.sp, color = sk.navy, fontWeight = FontWeight.Black, letterSpacing = 0.04.em)
                }
            }

            // 2 — eligibility gates
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GateCell("SKILL", if (match > 0) "$match%" else "—", skillGate, Modifier.weight(1f))
                GateCell(
                    "DATES",
                    when (datesGate) { Gate.Pass -> "Free"; Gate.Fail -> "Booked"; Gate.Unknown -> "Unchecked" },
                    datesGate, Modifier.weight(1f),
                )
                GateCell("CERT", when { !certKnown -> "Unknown"; certified -> "Held"; else -> "None" }, certGate, Modifier.weight(1f))
                GateCell("CLEARANCE", if (blocked) (if (dnc) "DNC" else "Held") else "Clear", clearGate, Modifier.weight(1f))
            }

            // 3 — suitability, suppressed under a hard fail
            val suitability = c.intOrNull("suitability_score")
            if (suitability != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("SUITABILITY", fontSize = 10.sp, color = sk.labelText, fontWeight = FontWeight.Bold, letterSpacing = 0.08.em)
                    Spacer(Modifier.width(8.dp))
                    if (verdict.hardFail) {
                        Text(
                            "$suitability — does not apply while ineligible",
                            style = MaterialTheme.typography.labelSmall, color = sk.labelText,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(sk.track)) {
                            Box(
                                Modifier.fillMaxWidth(suitability.coerceIn(0, 100) / 100f).fillMaxHeight()
                                    .background(Brush.horizontalGradient(listOf(tint.copy(alpha = 0.6f), tint))),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("$suitability", style = MaterialTheme.typography.titleMedium, color = sk.frost, fontWeight = FontWeight.Black)
                    }
                }
                if (!verdict.hardFail && parts != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FactorBar("Skill", parts.int("skill"), Modifier.weight(1f))
                        FactorBar("Ready", parts.int("readiness"), Modifier.weight(1f))
                        FactorBar("Avail", if (availVerified) parts.int("availability") else null, Modifier.weight(1f))
                        FactorBar("Lang", parts.int("language"), Modifier.weight(1f))
                    }
                }
            }

            // 4 — the important reason
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(tint.copy(alpha = 0.14f))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(verdict.glyph, color = tint, fontWeight = FontWeight.Black, fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                Text(reason, style = MaterialTheme.typography.labelMedium, color = sk.bodyText)
            }

            if (realAvail != null) CandidateVerdictRow(c, international)
            if (!verdict.hardFail && c.bool("recent_negative_6mo")) {
                Text("Feedback on file within the last 6 months", style = MaterialTheme.typography.labelSmall, color = sk.subText)
            }
        }
    }
}

@Composable
private fun GateCell(label: String, value: String, gate: Gate, modifier: Modifier = Modifier) {
    val sk = MaterialTheme.skill
    val tint = when (gate) { Gate.Pass -> sk.good; Gate.Unknown -> sk.warn; Gate.Fail -> sk.crit }
    val glyph = when (gate) { Gate.Pass -> "✓"; Gate.Unknown -> "?"; Gate.Fail -> "✕" }
    Column(
        modifier.clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = if (gate == Gate.Fail) 0.20f else 0.10f))
            .border(1.dp, tint.copy(alpha = if (gate == Gate.Fail) 0.7f else 0.3f), RoundedCornerShape(8.dp))
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
                Text(glyph, fontSize = 9.sp, color = sk.navy, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(4.dp))
            Text(value, fontSize = 11.sp, color = sk.frost, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(label, fontSize = 8.sp, color = sk.labelText, fontWeight = FontWeight.Bold, letterSpacing = 0.06.em, maxLines = 1, textAlign = TextAlign.Center)
    }
}

@Composable
private fun FactorBar(label: String, value: Int?, modifier: Modifier = Modifier) {
    val sk = MaterialTheme.skill
    Column(modifier) {
        Row {
            Text(label, fontSize = 9.sp, color = sk.labelText, modifier = Modifier.weight(1f))
            Text(value?.toString() ?: "—", fontSize = 9.sp, color = sk.bodyText, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(2.dp))
        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(sk.track)) {
            if (value != null) {
                Box(Modifier.fillMaxWidth(value.coerceIn(0, 100) / 100f).fillMaxHeight().background(Color(0xFF6FA8FF)))
            }
        }
    }
}
