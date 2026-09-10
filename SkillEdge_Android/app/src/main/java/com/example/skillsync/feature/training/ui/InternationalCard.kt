package com.example.skillsync.ui.batch

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.skillsync.R
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.ToneChip
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.components.list
import com.example.skillsync.ui.components.obj
import com.example.skillsync.ui.components.str

/**
 * International demand as a card *class*, per design vision §7.4.
 *
 * The previous treatment was a badge: the same card as every other batch, with
 * a small globe chip added. §7.4 is explicit that a badge is not enough, and
 * the audit went further — the badge was decoration, because the matching
 * engine had no concept of international at all.
 *
 * That is no longer true. Visa eligibility, travel windows and time-zone fit
 * are now computed (see `international_verdict` in `backend.py`), so this
 * treatment finally reports a real verdict rather than dressing up a guess.
 * The elevation is earned by the content, which is the only honest reason to
 * give a card more visual weight than its neighbours.
 *
 * The tier is composed of four things a badge cannot do: a full-bleed ribbon
 * that owns the top edge, a globe medallion in the leading slot, a destination
 * and travel-window line, and readiness stated as a verdict.
 */
@Composable
internal fun InternationalRibbon(batch: Map<*, *>) {
    val sk = MaterialTheme.skill
    val mode = batch.str("delivery_mode").uppercase()
    val pax = batch.str("participants")

    // A slow sheen, four seconds, on the ribbon only. Motion is reserved for
    // this one element on the screen; more than one moving thing reads as noise
    // and stops signalling anything.
    val t = rememberInfiniteTransition(label = "ribbon")
    val sweep by t.animateFloat(
        initialValue = -0.4f, targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(4000), RepeatMode.Restart),
        label = "sheen",
    )

    Row(
        Modifier
            .fillMaxWidth()
            .height(30.dp)
            .clip(RoundedCornerShape(topStart = Radii.card, topEnd = Radii.card))
            .background(Brush.horizontalGradient(listOf(sk.royal, sk.azure, sk.cyan)))
            .background(
                Brush.linearGradient(
                    0f to androidx.compose.ui.graphics.Color.Transparent,
                    0.5f to sk.frost.copy(alpha = 0.18f),
                    1f to androidx.compose.ui.graphics.Color.Transparent,
                    start = Offset(sweep * 600f, 0f),
                    end = Offset(sweep * 600f + 220f, 60f),
                )
            )
            .padding(horizontal = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_globe), null,
            tint = sk.frost, modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(Space.sm))
        Text(
            "INTERNATIONAL $mode",
            style = MaterialTheme.typography.labelSmall,
            color = sk.frost,
        )
        Spacer(Modifier.weight(1f))
        if (pax.isNotBlank()) {
            Text(
                "$pax pax",
                style = MaterialTheme.typography.labelSmall,
                color = sk.frost,
            )
        }
    }
}

/**
 * The destination line: where, when, and whether the team can actually go.
 *
 * Readiness is a computed verdict now. Where the engine could not check — no
 * visa record, or the course could not be resolved — it says so rather than
 * showing a reassuring blank, because an unqualified international card is
 * exactly the kind of confident-but-wrong surface this rebuild exists to stop.
 */
@Composable
internal fun InternationalDestination(batch: Map<*, *>) {
    val sk = MaterialTheme.skill
    val location = batch.str("location").ifBlank { "Destination not stated" }
    val start = batch.str("start_date")

    val candidates = batch.list("candidates")
    val ready = candidates.count { it.obj("international_readiness")?.str("visa") == "available" }
    val needsCheck = candidates.count { it.obj("international_readiness")?.str("visa") == "unknown" }
    val blocked = candidates.count { it.obj("international_readiness")?.str("visa") == "not_available" }

    Row(
        Modifier.fillMaxWidth().padding(top = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Globe medallion in the leading slot, at 40dp: a card class needs an
        // anchor the eye lands on before it reads a word.
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(sk.cyan.copy(alpha = 0.16f))
                .border(1.dp, sk.cyan.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_globe), null,
                tint = sk.cyan, modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(
                location,
                style = MaterialTheme.typography.titleSmall,
                color = sk.bodyText,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (start.isNotBlank()) "Travels for $start" else "Start date not confirmed",
                style = MaterialTheme.typography.bodySmall,
                color = sk.subText,
            )
        }
    }

    // Always rendered, including when there are no ranked candidates at all.
    // Silence on an international card reads as "no travel issues", which is a
    // claim the data has not made.
    Row(
        Modifier.fillMaxWidth().padding(top = Space.sm),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        if (ready > 0) ToneChip("$ready visa ready", sk.aqua)
        if (needsCheck > 0) ToneChip("$needsCheck to verify", sk.warn)
        if (blocked > 0) ToneChip("$blocked blocked", sk.crit)
        if (ready == 0 && needsCheck == 0 && blocked == 0) {
            ToneChip("Travel readiness not checked", sk.labelText)
        }
    }
}
