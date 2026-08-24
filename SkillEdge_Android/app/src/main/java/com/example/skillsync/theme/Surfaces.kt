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
    val hero = 20.dp
    val card = 16.dp
    val kpi = 14.dp
    val chip = 10.dp
    val icon = 10.dp
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
        // Deep luxury Midnight-Cobalt base
        drawRect(
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF070B12),
                    Color(0xFF0C1322),
                    Color(0xFF090E18),
                ),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height),
            )
        )
        // Vibrant Royal Blue bloom, upper left — primary identity anchor
        drawRect(
            Brush.radialGradient(
                colors = listOf(BrandBlue.copy(alpha = 0.18f), Color.Transparent),
                center = Offset(size.width * 0.15f, -size.height * 0.05f),
                radius = size.width * 1.2f,
            )
        )
        // Electric Cyan bloom, upper right — provides vibrant executive glow
        drawRect(
            Brush.radialGradient(
                colors = listOf(Cyan.copy(alpha = 0.10f), Color.Transparent),
                center = Offset(size.width * 0.95f, size.height * 0.08f),
                radius = size.width * 0.9f,
            )
        )
        // Subtle Amethyst / Violet ambient light mid-screen for depth
        drawRect(
            Brush.radialGradient(
                colors = listOf(Color(0xFF8B5CF6).copy(alpha = 0.06f), Color.Transparent),
                center = Offset(size.width * 0.05f, size.height * 0.55f),
                radius = size.width * 0.85f,
            )
        )
        // Crisp grounded vignette at the bottom for flawless contrast
        drawRect(
            Brush.verticalGradient(
                colors = listOf(Color.Transparent, Surface0.copy(alpha = 0.85f)),
                startY = size.height * 0.60f,
                endY = size.height,
            )
        )
    }
}

/** Frosted card surface: rich translucent gradient with top-edge sheen and ice hairline border. */
fun Modifier.glassSurface(
    shape: Shape = RoundedCornerShape(Radii.card),
    tint: Color = Color.Transparent,
): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(
                Color(0xFF1E293B).copy(alpha = 0.82f),
                Color(0xFF0F172A).copy(alpha = 0.94f),
            )
        )
    )
    .then(if (tint == Color.Transparent) Modifier else Modifier.background(tint))
    .border(
        1.dp,
        Brush.verticalGradient(
            listOf(
                Color(0x5293C5FD), // glowing ice highlight on top edge
                Color(0x1F38BDF8),
                Color(0x121E293B),
            )
        ),
        shape
    )

/**
 * Glass card carrying a status accent: a glowing gradient stripe down the left edge
 * plus a matching translucent wash.
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
                accent.copy(alpha = if (strong) 0.18f else 0.10f),
                Color(0xFF131D2E).copy(alpha = 0.92f),
                Color(0xFF0F172A).copy(alpha = 0.96f),
            )
        )
    )
    .border(
        1.dp,
        Brush.horizontalGradient(
            listOf(
                accent.copy(alpha = if (strong) 0.65f else 0.40f),
                accent.copy(alpha = 0.15f),
                Color(0x2093C5FD),
            )
        ),
        shape
    )

/** Hero surface: Deep Navy → Brand Blue at 135°, the app's single loudest fill. */
fun Modifier.heroSurface(shape: Shape = RoundedCornerShape(Radii.hero)): Modifier = this
    .clip(shape)
    .background(
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF0F265C),
                Color(0xFF1E3A8A),
                Color(0xFF1E40AF),
                Color(0xFF172554),
            ),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
        )
    )
    .border(
        1.dp,
        Brush.linearGradient(
            listOf(
                Color(0x8060A5FA),
                Color(0x3338BDF8),
                Color(0x101D4ED8),
            )
        ),
        shape
    )

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
