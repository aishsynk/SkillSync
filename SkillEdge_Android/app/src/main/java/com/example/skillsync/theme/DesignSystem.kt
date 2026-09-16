package com.example.skillsync.theme

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import com.example.skillsync.core.ui.pressable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

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

// ── Metric family ─────────────────────────────────────────────────────────
//
// [Figure] already covers "a value, a label, an optional delta." These cover
// the rest of the metric vocabulary a screen actually needs — a sparkline, a
// bounded progress fill, a radial hero ring — as small, composable primitives
// rather than one configurable MetricCard with a growing parameter list.

/**
 * A trend line for a real time series only. Renders nothing (not a flat/fake
 * line) when [values] has fewer than 2 points — a single reading or an empty
 * series is not a trend, and drawing one would manufacture history that was
 * never measured. [values] should already be in chronological order.
 */
@Composable
fun MetricSparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    val sk = MaterialTheme.skill
    val color = tint ?: sk.cyan
    if (values.size < 2) return
    val min = values.min()
    val max = values.max()
    val span = (max - min).takeIf { it > 0f } ?: 1f
    Canvas(
        modifier
            .semantics { contentDescription = "Trend: ${values.first().toInt()} to ${values.last().toInt()}" },
    ) {
        val stepX = size.width / (values.size - 1)
        val points = values.mapIndexed { i, v ->
            Offset(i * stepX, size.height - ((v - min) / span) * size.height)
        }
        for (i in 0 until points.lastIndex) {
            drawLine(color, points[i], points[i + 1], strokeWidth = size.height * 0.12f, cap = StrokeCap.Round)
        }
        drawCircle(color, radius = size.height * 0.22f, center = points.last())
    }
}

/**
 * A bounded progress fill — the shared implementation behind any "X of Y" or
 * percentage bar. [fraction] is coerced to 0..1; the caller decides whether an
 * unknown/no-data state should render this at all (an absent metric should be
 * omitted, not drawn as a 0% bar that reads as "measured zero").
 */
@Composable
fun MetricProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    trackHeight: Dp = 6.dp,
) {
    val sk = MaterialTheme.skill
    val clamped = fraction.coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(trackHeight)
            .background(sk.track, RoundedCornerShape(Radii.chip))
            .semantics { contentDescription = "${(clamped * 100).toInt()} percent" },
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(clamped)
                .background(tint ?: sk.cyan, RoundedCornerShape(Radii.chip)),
        )
    }
}

/**
 * The one hero ring per screen (see the surface-usage rule on [heroSurface] —
 * the same "at most one" discipline applies here). [value]/[max] draw the arc;
 * [value] `null` renders the track only with an em dash, the honest state for
 * "no reading yet" rather than a ring stuck at zero.
 */
@Composable
fun HeroRing(
    value: Int?,
    modifier: Modifier = Modifier,
    max: Int = 100,
    centerText: String = value?.toString() ?: "—",
) {
    val sk = MaterialTheme.skill
    Box(modifier.semantics { contentDescription = if (value != null) "$value of $max" else "No reading" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = size.minDimension * 0.12f, cap = StrokeCap.Round)
            val inset = stroke.width / 2
            drawArc(
                color = sk.surface3,
                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                style = stroke, size = Size(size.width - stroke.width, size.height - stroke.width),
                topLeft = Offset(inset, inset),
            )
            if (value != null) {
                drawArc(
                    brush = Brush.linearGradient(listOf(sk.azure, sk.cyan)),
                    startAngle = -90f, sweepAngle = 360f * (value.coerceIn(0, max) / max.toFloat()), useCenter = false,
                    style = stroke, size = Size(size.width - stroke.width, size.height - stroke.width),
                    topLeft = Offset(inset, inset),
                )
            }
        }
        Text(centerText, style = MaterialTheme.typography.titleMedium, color = sk.frost, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
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
    onClick: (() -> Unit)? = null,
    padding: Dp = Space.lg,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .cardSurface(severity, strong, onClick)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(Space.md),
        content = content,
    )
}

/**
 * The severity-aware glass surface + optional press affordance shared by
 * [SkillCard] and `SkillSyncCard` (`theme/SkillSyncComponents.kt`) — pulled
 * out so the two card entry points can't drift into two different-looking
 * surfaces. See `AI/DECISIONS.md`, Design V3 Phase 1 card consolidation.
 */
@Composable
internal fun Modifier.cardSurface(severity: Severity?, strong: Boolean, onClick: (() -> Unit)?): Modifier {
    val base = if (severity == null) {
        this.glassSurface()
    } else {
        this.accentGlass(severity.tint(), strong = strong)
    }
    return if (onClick != null) base.pressable(onClick = onClick) else base
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
 * Press feedback for any tappable card: a 0.98 scale on the shared [SkillMotion]
 * press spring, not a fixed-duration tween — the D1 foundation pass folded this
 * into the one named motion system rather than leaving it as its own arbitrary
 * animation spec.
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
        animationSpec = SkillMotion.press(),
        label = "press",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

/**
 * A one-shot fade + slight rise on first composition, keyed on [key] so a
 * recomposition from unrelated state (a click, a data refresh) never replays
 * it. [delayMs] staggers a group of cards (Pulse tiles, a ranked list) so
 * they arrive in sequence rather than all at once — the "content entrance"
 * motion called for across Today's cards, in one place instead of
 * duplicated per composable.
 */
@Composable
fun Modifier.entrance(key: Any, delayMs: Int = 0): Modifier {
    var visible by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        if (delayMs > 0) delay(delayMs.toLong())
        visible = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = SkillMotion.gentle(), label = "entranceAlpha",
    )
    val riseDp by animateDpAsState(
        targetValue = if (visible) 0.dp else 8.dp,
        animationSpec = SkillMotion.gentle(), label = "entranceRise",
    )
    return this.graphicsLayer { this.alpha = alpha; translationY = riseDp.toPx() }
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
