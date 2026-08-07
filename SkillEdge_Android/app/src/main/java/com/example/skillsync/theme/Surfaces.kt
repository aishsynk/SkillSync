package com.example.skillsync.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The surfaces that give the command centre its depth.
 *
 * Compose has no backdrop blur below API 31, and a real blur behind a scrolling
 * LazyColumn is expensive on the mid-range devices this ships to. So "glass" is
 * built the way it actually reads on a phone: a translucent surface fill that
 * lets the aurora ground through, a light top-edge gradient, and a 1dp ice
 * hairline. That combination is what the eye reads as frosted — not the blur.
 */

// ── Radius ladder — radius decreases with the element, so hierarchy is felt ──
object Radii {
    val hero = 24.dp
    val card = 20.dp
    val kpi = 18.dp
    val chip = 14.dp
    val icon = 11.dp
}

/** Spacing scale. Replaces the ad-hoc 6/9/10/14dp values used previously. */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/**
 * The aurora ground the whole app sits on: the mandated
 * #0F2027 → #203A43 → #2C5364 mesh, with a royal bloom top-left and a cyan
 * bloom top-right. Drawn once behind the scaffold, never per-card.
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        drawRect(
            Brush.linearGradient(
                colors = GradientAurora,
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height),
            )
        )
        // Royal bloom, upper left — the brand anchor.
        drawRect(
            Brush.radialGradient(
                colors = listOf(RoyalBlue.copy(alpha = 0.42f), Color.Transparent),
                center = Offset(size.width * 0.12f, -size.height * 0.04f),
                radius = size.width * 1.05f,
            )
        )
        // Cyan bloom, upper right — keeps the ground from reading flat navy.
        drawRect(
            Brush.radialGradient(
                colors = listOf(Cyan.copy(alpha = 0.16f), Color.Transparent),
                center = Offset(size.width * 0.95f, size.height * 0.05f),
                radius = size.width * 0.8f,
            )
        )
        // Deep navy settle at the bottom so long scrolls stay legible.
        drawRect(
            Brush.verticalGradient(
                colors = listOf(Color.Transparent, DeepNavy.copy(alpha = 0.55f)),
                startY = size.height * 0.45f,
                endY = size.height,
            )
        )
    }
}

/** Frosted card surface: translucent fill, top-edge sheen, ice hairline. */
fun Modifier.glassSurface(
    shape: Shape = RoundedCornerShape(Radii.card),
    tint: Color = Color.Transparent,
): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(
                Color(0xB31E293B),   // Surface-3 @ 70% — catches light at the top
                Color(0x99172030),   // Surface-2 @ 60%
            )
        )
    )
    .then(if (tint == Color.Transparent) Modifier else Modifier.background(tint))
    .border(1.dp, Color(0x2490CAF9), shape)

/**
 * Glass card carrying a status accent: a 3dp gradient stripe down the left edge
 * plus a matching wash. Severity has to be readable as shape and position, not
 * colour alone, so the stripe is the primary carrier and the tint reinforces it.
 */
fun Modifier.accentGlass(
    accent: Color,
    shape: Shape = RoundedCornerShape(Radii.card),
    strong: Boolean = false,
): Modifier = this
    .clip(shape)
    .background(
        Brush.horizontalGradient(
            listOf(
                accent.copy(alpha = if (strong) 0.20f else 0.10f),
                Color(0x99172030),
            )
        )
    )
    .border(1.dp, accent.copy(alpha = if (strong) 0.42f else 0.20f), shape)

/** Hero surface: Deep Navy → Brand Blue at 135°, the app's single loudest fill. */
fun Modifier.heroSurface(shape: Shape = RoundedCornerShape(Radii.hero)): Modifier = this
    .clip(shape)
    .background(
        Brush.linearGradient(
            colors = listOf(DeepNavy, RoyalBlue, BrandBlue.copy(alpha = 0.82f)),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
        )
    )
    .border(1.dp, LightAzure.copy(alpha = 0.26f), shape)

/** Soft blue glow ring for focused, active or pressed elements. */
fun Modifier.glowRing(
    accent: Color = BrandBlue,
    shape: Shape = RoundedCornerShape(Radii.kpi),
    width: Dp = 1.dp,
): Modifier = this.border(width, accent.copy(alpha = 0.45f), shape)

/** Small square icon slot behind a KPI or section glyph. */
@Composable
fun IconSlot(
    tint: Color,
    size: Dp = 26.dp,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(Radii.icon))
            .background(tint.copy(alpha = 0.16f))
            .border(1.dp, tint.copy(alpha = 0.24f), RoundedCornerShape(Radii.icon)),
        contentAlignment = Alignment.Center,
    ) { content() }
}
