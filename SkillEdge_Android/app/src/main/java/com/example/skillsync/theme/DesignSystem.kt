package com.example.skillsync.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The V2 component vocabulary.
 *
 * Before this file the app had eight components that rendered "a number and a
 * label" and eight that rendered "a small pill", each with its own sizes and
 * colour rules — which is why five screens read as five products. Everything
 * here resolves through [Space], [Radii] and the type scale, so a screen file
 * holds layout and data mapping and never bespoke visuals.
 */

// ── Severity ────────────────────────────────────────────────────────────────

/**
 * The single source of urgency.
 *
 * Severity drives sort weight, stripe, tint, and label together. Colour is
 * never the only carrier — [weight] orders the item and [label] names it, so
 * urgency survives both greyscale and TalkBack.
 */
enum class Severity(val weight: Int, val label: String) {
    Critical(0, "Critical"),
    Warning(1, "Warning"),
    Watch(2, "Watch"),
    Info(3, "Info"),
    Good(4, "On track");

    @Composable
    fun tint(): Color = when (this) {
        Critical -> MaterialTheme.skill.crit
        Warning -> MaterialTheme.skill.warn
        Watch -> MaterialTheme.skill.sky
        Info -> MaterialTheme.skill.ice
        Good -> MaterialTheme.skill.good
    }
}

// ── Figure ──────────────────────────────────────────────────────────────────

enum class FigureSize { Hero, Large, Medium, Small }

/**
 * Every number in the app. [delta] is the baseline comparison — a figure with
 * no baseline cannot drive a decision, so the parameter exists to make its
 * absence a deliberate choice at the call site rather than an oversight.
 */
@Composable
fun Figure(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    delta: String? = null,
    deltaTint: Color? = null,
) = Figure(value, label, FigureSize.Medium, modifier, tint, delta, deltaTint)

@Composable
fun Figure(
    value: String,
    label: String,
    size: FigureSize,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    delta: String? = null,
    deltaTint: Color? = null,
) {
    val sk = MaterialTheme.skill
    val t = MaterialTheme.typography
    val valueStyle = when (size) {
        FigureSize.Hero -> t.displayLarge
        FigureSize.Large -> t.displaySmall
        FigureSize.Medium -> t.headlineMedium
        FigureSize.Small -> t.titleMedium
    }
    Column(modifier) {
        Text(
            value,
            style = valueStyle.copy(fontFeatureSettings = "tnum"),
            color = tint ?: sk.bodyText,
            maxLines = 1,
        )
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = sk.labelText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Space.xs),
        )
        if (delta != null) {
            Text(
                delta,
                style = MaterialTheme.typography.labelMedium,
                color = deltaTint ?: sk.subText,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

// ── Chip ────────────────────────────────────────────────────────────────────

/** Every pill in the app: status, filter, tag, code. One shape, one rhythm. */
@Composable
fun ToneChip(
    text: String,
    tint: Color,
    modifier: Modifier = Modifier,
    solid: Boolean = false,
) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = if (solid) MaterialTheme.skill.frost else tint,
        maxLines = 1,
        modifier = modifier
            .background(
                tint.copy(alpha = if (solid) 0.85f else 0.16f),
                RoundedCornerShape(Radii.chip),
            )
            .padding(horizontal = Space.sm, vertical = Space.xs),
    )
}

// ── Section heading ─────────────────────────────────────────────────────────

/**
 * Section headers state a conclusion, not a topic — "Utilisation is inside the
 * target corridor", not "Utilisation trend". The chart underneath is then read
 * as evidence for a claim the manager has already absorbed.
 */
@Composable
fun SectionHeading(
    title: String,
    conclusion: String? = null,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    val sk = MaterialTheme.skill
    Column(modifier.padding(top = Space.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = sk.labelText,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                Text(trailing, style = MaterialTheme.typography.labelSmall, color = sk.cyan)
            }
        }
        if (conclusion != null) {
            Text(
                conclusion,
                style = MaterialTheme.typography.titleMedium,
                color = sk.bodyText,
                modifier = Modifier.padding(top = Space.xs),
            )
        }
    }
}

// ── Card ────────────────────────────────────────────────────────────────────

/**
 * The one card. [severity] switches it to the accent treatment, so a critical
 * item is physically distinguishable from an informational one by its edge and
 * not only by a chip somewhere inside it.
 */
@Composable
fun SkillCard(
    modifier: Modifier = Modifier,
    severity: Severity? = null,
    strong: Boolean = false,
    padding: Dp = Space.lg,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = if (severity == null) {
        Modifier.glassSurface()
    } else {
        Modifier.accentGlass(severity.tint(), strong = strong)
    }
    Column(
        modifier
            .then(base)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(Space.md),
        content = content,
    )
}

// ── States ──────────────────────────────────────────────────────────────────

/** Empty, error and offline share one shape so absence never reads as breakage. */
@Composable
fun StateNote(
    message: String,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    val sk = MaterialTheme.skill
    Box(
        modifier
            .glassSurface()
            .padding(Space.xl),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = tint ?: sk.subText,
        )
    }
}

/** Screen gutter and inter-section rhythm, so every scroll shares one grid. */
object Layout {
    val gutter = Space.lg
    val section = Space.lg
}
