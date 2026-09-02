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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
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
        // Warm graphite base — a violet-ink cast, not blue-black.
        drawRect(
            Brush.linearGradient(
                colors = listOf(Color(0xFF0B0910), Color(0xFF100D16), Color(0xFF0A0810)),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height),
            )
        )
        // Plum bloom, upper left — the warm identity anchor.
        drawRect(
            Brush.radialGradient(
                colors = listOf(Color(0xFF8A73C4).copy(alpha = 0.16f), Color.Transparent),
                center = Offset(size.width * 0.12f, -size.height * 0.04f),
                radius = size.width * 1.25f,
            )
        )
        // Champagne brass ember, upper right — the premium glow.
        drawRect(
            Brush.radialGradient(
                colors = listOf(Color(0xFFD8B26A).copy(alpha = 0.09f), Color.Transparent),
                center = Offset(size.width * 0.96f, size.height * 0.06f),
                radius = size.width * 0.95f,
            )
        )
        // Cool blue counter-glow, low-left — keeps blue reading as "signal".
        drawRect(
            Brush.radialGradient(
                colors = listOf(BrandBlue.copy(alpha = 0.07f), Color.Transparent),
                center = Offset(size.width * 0.02f, size.height * 0.62f),
                radius = size.width * 0.9f,
            )
        )
        // Grounded vignette at the bottom for contrast under content.
        drawRect(
            Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color(0xFF0A0810).copy(alpha = 0.88f)),
                startY = size.height * 0.58f,
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
                Color(0xFF201A29).copy(alpha = 0.86f),
                Color(0xFF141019).copy(alpha = 0.95f),
            )
        )
    )
    .then(if (tint == Color.Transparent) Modifier else Modifier.background(tint))
    .border(
        1.dp,
        Brush.verticalGradient(
            listOf(
                Color(0x3ED8B26A), // warm champagne highlight on the top edge
                Color(0x14C9B79A),
                Color(0x10201A29),
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
                Color(0xFF241C33),
                Color(0xFF2E2340),
                Color(0xFF1C2A55),
                Color(0xFF171430),
            ),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
        )
    )
    .border(
        1.dp,
        Brush.linearGradient(
            listOf(
                Color(0x66D8B26A),
                Color(0x338FB4F6),
                Color(0x10241C33),
            )
        ),
        shape
    )

/**
 * A true hairline rule — the editorial section break. One device pixel of a
 * faint ice tint, full bleed. Replaces heavier `HorizontalDivider` usage so the
 * rhythm between a conclusion and its evidence stays quiet.
 */
fun Modifier.editorialRule(
    color: Color = Color(0x1FBFDBFE),
    top: Boolean = false,
): Modifier = this.drawWithContent {
    drawContent()
    val y = if (top) 0f else size.height
    drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
}

/**
 * Real backdrop frost on API 31+, the layered gradient fake below it.
 *
 * `RenderEffect.createBlurEffect` samples what is already drawn behind this
 * node, so the aurora ground genuinely diffuses through the card. On older
 * devices that call does nothing, so we fall back to [glassSurface]'s translucent
 * gradient which reads as frost without the sample.
 */
@Composable
fun Modifier.frostedGlass(
    shape: Shape = RoundedCornerShape(Radii.card),
    blurRadius: Dp = 22.dp,
    tint: Color = Color(0xFF0F172A).copy(alpha = 0.55f),
): Modifier {
    val base = this.glassSurface(shape)
    if (android.os.Build.VERSION.SDK_INT < 31) return base
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { blurRadius.toPx() }
    return this
        .clip(shape)
        .graphicsLayer {
            renderEffect = android.graphics.RenderEffect
                .createBlurEffect(px, px, android.graphics.Shader.TileMode.CLAMP)
                .asComposeRenderEffect()
        }
        .background(tint)
        .border(
            1.dp,
            Brush.verticalGradient(listOf(Color(0x5293C5FD), Color(0x1F38BDF8), Color(0x121E293B))),
            shape,
        )
}

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
