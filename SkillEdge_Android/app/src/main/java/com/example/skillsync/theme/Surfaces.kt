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
 * The atmospheric ground: deep enterprise slate (#0B0F19 → #111827) with a quiet,
 * restrained brand bloom top-left and subtle cyan sheen top-right. Drawn behind
 * the root scaffold to provide depth without muddying contrast or competing with data.
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        // Deep enterprise slate base
        drawRect(
            Brush.linearGradient(
                colors = listOf(Color(0xFF0B0F19), Color(0xFF0E1422), Color(0xFF0A0D15)),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height),
            )
        )
        // Quiet brand blue bloom, upper left — calm authority, low opacity
        drawRect(
            Brush.radialGradient(
                colors = listOf(BrandBlue.copy(alpha = 0.08f), Color.Transparent),
                center = Offset(size.width * 0.10f, -size.height * 0.04f),
                radius = size.width * 1.15f,
            )
        )
        // Subtle cyan sheen, upper right — light accent
        drawRect(
            Brush.radialGradient(
                colors = listOf(Cyan.copy(alpha = 0.04f), Color.Transparent),
                center = Offset(size.width * 0.95f, size.height * 0.05f),
                radius = size.width * 0.90f,
            )
        )
        // Low-left counter-glow
        drawRect(
            Brush.radialGradient(
                colors = listOf(AzureBlue.copy(alpha = 0.05f), Color.Transparent),
                center = Offset(size.width * 0.02f, size.height * 0.60f),
                radius = size.width * 0.85f,
            )
        )
        // Grounded bottom vignette for strong list contrast
        drawRect(
            Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color(0xFF0B0F19).copy(alpha = 0.92f)),
                startY = size.height * 0.55f,
                endY = size.height,
            )
        )
    }
}

/**
 * SURFACE USAGE RULES — pick by what the content IS, not by what looks nice.
 *
 *   [heroSurface]   — the one major executive identity/insight per screen (the
 *                      readiness ring, a screen's single hero number). At most
 *                      one per screen; using it twice erases the "hero" meaning.
 *   [frostedGlass]  — temporary, floating or chrome surfaces: a bottom sheet, a
 *                      dialog, a docked control. NOT every scrolling card — a
 *                      real-time blur behind a long LazyColumn is the exact
 *                      performance/legibility trap this rule exists to avoid.
 *   [accentGlass]   — a surface that carries real severity/intelligence/action
 *                      meaning (a critical attention row, an AI-context card).
 *                      If the tint doesn't mean something, use [glassSurface].
 *   [glassSurface]  — ordinary contained content with no particular emphasis —
 *                      the default card.
 *   Plain Row/Column — information that needs no container at all. A section
 *                      does not automatically need a card; whitespace,
 *                      dividers and typography can separate sections just as
 *                      well, and do so more quietly.
 *
 * If every surface on a screen ends up [frostedGlass] or [accentGlass],
 * that's the same "everything looks the same" failure with a different name.
 */

/** Frosted card surface: rich translucent gradient with top-edge sheen and ice hairline border. */
fun Modifier.glassSurface(
    shape: Shape = RoundedCornerShape(Radii.card),
    tint: Color = Color.Transparent,
): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(
                Color(0xFF182234).copy(alpha = 0.88f),
                Color(0xFF101724).copy(alpha = 0.95f),
            )
        )
    )
    .then(if (tint == Color.Transparent) Modifier else Modifier.background(tint))
    .border(
        1.dp,
        Brush.verticalGradient(
            listOf(
                Color(0x3338BDF8), // clean sky/ice highlight on top edge
                Color(0x14263345),
                Color(0x0A101724),
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

/**
 * Hero surface: deep navy → royal → azure diagonal, the app's single loudest
 * fill, plus a soft cyan edge-illumination in the upper-right corner — a
 * flagship highlight, not a neon glow. This is the one place the V2 palette
 * is allowed to look proud of itself.
 */
fun Modifier.heroSurface(shape: Shape = RoundedCornerShape(Radii.hero)): Modifier = this
    .clip(shape)
    .background(
        Brush.linearGradient(
            colors = listOf(
                DeepNavy,
                Color(0xFF132549),
                RoyalBlue.copy(alpha = 0.65f),
                AzureBlue.copy(alpha = 0.40f),
                Color(0xFF0E1624),
            ),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
        )
    )
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.radialGradient(
                colors = listOf(Cyan.copy(alpha = 0.16f), Color.Transparent),
                center = Offset(size.width * 0.90f, size.height * 0.02f),
                radius = size.width * 0.60f,
            )
        )
    }
    .border(
        1.dp,
        Brush.linearGradient(
            listOf(
                Color(0x8038BDF8),
                Color(0x403B82F6),
                Color(0x10111827),
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

/**
 * Small square icon slot behind a KPI or section glyph. A tonal gradient fill
 * (not a flat alpha wash) so each domain's icon reads as a distinct chip of
 * colour rather than the same grey-blue square repeated with a different
 * glyph inside it — the caller's [tint] is what actually differentiates
 * Planning/Delivery/People/Automation/Risk sections from each other.
 */
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
            .background(
                Brush.linearGradient(
                    listOf(tint.copy(alpha = 0.32f), tint.copy(alpha = 0.12f)),
                )
            )
            .border(1.dp, tint.copy(alpha = 0.42f), RoundedCornerShape(Radii.icon)),
        contentAlignment = Alignment.Center,
    ) { content() }
}
