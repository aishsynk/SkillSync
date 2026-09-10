package com.example.skillsync.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.skillsync.theme.skill
import kotlinx.coroutines.delay

/** Shared timing/easing so every screen animates with the same personality. */
object Motion {
    const val FAST = 170
    const val NORMAL = 320
    const val SLOW = 520

    /** Material 3 "emphasized decelerate" — quick start, soft landing. */
    val Emphasized: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val Standard: Easing = FastOutSlowInEasing

    /**
     * Expo-out — the V3 signature curve. Almost all of the travel happens in the
     * first third, so content arrives fast and settles without a bounce. Used for
     * reveals, section entrances and page transitions.
     */
    val Expo: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    /** Slight overshoot; used for anything that should feel physical. */
    fun <T> springy() = spring<T>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)

    /** Tight, no-overshoot spring for press feedback. */
    fun <T> press() = spring<T>(dampingRatio = 0.9f, stiffness = Spring.StiffnessHigh)

    /** Per-item delay for staggered list entrances, capped so long lists stay snappy. */
    fun stagger(index: Int, step: Int = 40, max: Int = 240) = (index * step).coerceAtMost(max)
}

/**
 * Press feedback for any tappable surface: a small scale dip plus a haptic tick.
 * Honours the system "remove animations" setting — with animator scale at 0 the
 * card still clicks, it just doesn't move.
 */
@Composable
fun Modifier.pressable(
    enabled: Boolean = true,
    hapticOnPress: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val reduceMotion = animatorScaleIsZero()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) 0.972f else 1f,
        animationSpec = Motion.press(),
        label = "press",
    )
    LaunchedEffect(pressed) {
        if (pressed && hapticOnPress) {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
        }
    }
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}

@Composable
private fun animatorScaleIsZero(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember {
        try {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        } catch (_: Throwable) {
            false
        }
    }
}

/**
 * Fades and lifts its content into place once, [index]-staggered. Safe inside
 * LazyColumn items — each item runs its own one-shot animation on first compose.
 */
@Composable
fun Appear(
    index: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(Motion.stagger(index).toLong())
        shown = true
    }
    val p by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(Motion.SLOW, easing = Motion.Emphasized),
        label = "appear",
    )
    Box(
        modifier.graphicsLayer {
            alpha = p
            translationY = (1f - p) * 44f
        }
    ) { content() }
}

/**
 * Progress that grows from zero the first time it is drawn. A plain
 * `animateFloatAsState` seeds its animator with the initial target, so it would
 * snap straight to the final value and never animate on first composition.
 */
@Composable
fun animateProgressFromZero(target: Float, durationMillis: Int = 850): State<Float> {
    var start by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { start = true }
    return animateFloatAsState(
        targetValue = if (start) target.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis, easing = Motion.Emphasized),
        label = "progress",
    )
}

/** Counts from zero to [target] so KPI numbers land with weight instead of blinking in. */
@Composable
fun AnimatedCount(
    target: Int,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    suffix: String = "",
) {
    var start by remember { mutableStateOf(false) }
    LaunchedEffect(target) { start = true }
    val v by animateFloatAsState(
        targetValue = if (start) target.toFloat() else 0f,
        animationSpec = tween(900, easing = Motion.Emphasized),
        label = "count",
    )
    Text("${v.toInt()}$suffix", style = style, color = color, modifier = modifier)
}

/** Horizontal offset that decays to rest — drives the invalid-credentials shake. */
@Composable
fun rememberShake(trigger: Any?): State<Float> {
    val offset = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == null) return@LaunchedEffect
        offset.snapTo(0f)
        offset.animateTo(
            targetValue = 0f,
            animationSpec = keyframes {
                durationMillis = 420
                (-15f) at 60
                13f at 120
                (-9f) at 190
                6f at 260
                (-3f) at 330
                0f at 420
            },
        )
    }
    return offset.asState()
}

/** A sweeping highlight used for skeleton placeholders while data loads. */
@Composable
fun shimmerBrush(): Brush {
    val base = MaterialTheme.skill.shimmer
    val highlight = MaterialTheme.skill.cardBg
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(
        initialValue = -600f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "sweep",
    )
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(x, 0f),
        end = Offset(x + 400f, 260f),
    )
}

/** Skeleton block matching the footprint of the content it stands in for. */
@Composable
fun ShimmerBox(
    width: Dp? = null,
    height: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(6.dp),
) {
    Box(
        modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .height(height)
            .clip(shape)
            .background(shimmerBrush())
    )
}
