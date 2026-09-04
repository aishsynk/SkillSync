package com.example.skillsync.theme

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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

/** Every pill in the app: status, filter, tag, code. One shape, one rhythm with crisp edge definition. */
@Composable
fun ToneChip(
    text: String,
    tint: Color,
    modifier: Modifier = Modifier,
    solid: Boolean = false,
) {
    val chipShape = RoundedCornerShape(Radii.chip)
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = if (solid) MaterialTheme.skill.frost else tint,
        maxLines = 1,
        modifier = modifier
            .background(
                tint.copy(alpha = if (solid) 0.85f else 0.16f),
                chipShape,
            )
            .border(
                1.dp,
                tint.copy(alpha = if (solid) 0.90f else 0.28f),
                chipShape,
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

    /**
     * Maximum content width. On a phone this is wider than the screen and does
     * nothing; on a tablet it stops every card and row from stretching across
     * the whole panel, which is what leaves the big empty bands between a label
     * and its value. Pair with `Alignment.TopCenter` so the column is centred.
     */
    val readableMax = 760.dp
}

/**
 * Centres its content and caps it at [Layout.readableMax]. A no-op on phones,
 * the fix for tablet sprawl everywhere else. Wrap a screen's root scroll in it.
 */
@Composable
fun ReadableColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = Layout.readableMax)
                .fillMaxWidth()
                .fillMaxHeight(),
            content = content,
        )
    }
}

// ── Motion ──────────────────────────────────────────────────────────────────

/**
 * Press feedback for any tappable card: a 0.98 scale over 100ms.
 *
 * Compose's ripple is invisible on a dark translucent surface, so without this
 * a card that opens a drill-down felt identical to one that did nothing.
 */
@Composable
fun Modifier.pressable(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = tween(100),
        label = "press",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

/**
 * Slow alpha breath, reserved for [Severity.Critical]. Capped to one element on
 * screen by convention — a dashboard where several things pulse reads as noise
 * rather than as urgency.
 */
@Composable
fun rememberCriticalPulse(active: Boolean): Float {
    if (!active) return 1f
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    return a
}
