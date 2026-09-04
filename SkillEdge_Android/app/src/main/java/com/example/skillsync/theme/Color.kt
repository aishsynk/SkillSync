package com.example.skillsync.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * SkillEdge Command Centre palette.
 *
 * One blue ramp carries structure and brand, cyan carries positive performance,
 * and amber/rose are reserved for exceptions only. That is the whole colour
 * strategy: if a screen reads mostly blue, the delivery org is healthy, so
 * colour itself is the top-level status signal before any number is read.
 *
 * Status hues follow the familiar web convention (info blue, success green,
 * warning amber, danger rose) so a colour reads the same here as it does in a
 * Bootstrap alert. Status hues are held apart from the blue ramp — a status
 * colour that doubles as branding stops meaning anything.
 *
 * The warm-graphite / brass "editorial" experiment (V3–V3.72) is retired: it
 * washed the blue out and muddied every status colour. This is the clean blue
 * console scheme restored, with the token names kept intact.
 */

// ── Primary ramp (Tailwind blue) ─────────────────────────────────────────────
val DeepNavy   = Color(0xFF111827)
val RoyalBlue  = Color(0xFF1D4ED8)
val AzureBlue  = Color(0xFF2563EB)
val BrandBlue  = Color(0xFF3B82F6)
val SkyBlue    = Color(0xFF60A5FA)
val Cyan       = Color(0xFF22D3EE)
val Aqua       = Color(0xFF34D399)
val LightAzure = Color(0xFF93C5FD)
val IceBlue    = Color(0xFFBFDBFE)
val SoftBlue   = Color(0xFFDBEAFE)
val FrostWhite = Color(0xFFF8FAFC)

// Retired accents — kept as names only so call sites compile. They resolve
// into the blue ramp now; nothing renders "brass" or "plum" any more.
val Brass      = SkyBlue
val BrassDeep  = AzureBlue
val Plum       = LightAzure

// ── Dark surfaces (elevation steps, not drop shadows) ────────────────────────
val Surface0 = Color(0xFF0B0F17)
val Surface1 = Color(0xFF101722)
val Surface2 = Color(0xFF151E2B)
val Surface3 = Color(0xFF1C2736)

// ── Semantic status (web-standard hues) ──────────────────────────────────────
val StatusGood = Aqua
val StatusWarn = Color(0xFFFBBF24)
val StatusCrit = Color(0xFFFB7185)

// Names kept from the previous palette so existing call sites keep compiling.
val BrandCyan = Cyan
val BrandDeep = DeepNavy
val BrandNavy = DeepNavy
val Teal   = Cyan
val Blue   = BrandBlue
val Green  = Aqua
val Amber  = StatusWarn
val Red    = StatusCrit
val Indigo = LightAzure

// ── Gradients ────────────────────────────────────────────────────────────────
val GradientHero     = listOf(DeepNavy, BrandBlue)
val GradientRoyalSky = listOf(RoyalBlue, SkyBlue)
val GradientAzureCyan = listOf(AzureBlue, Cyan)
val GradientBrandIce = listOf(BrandBlue, IceBlue)
val GradientAurora   = listOf(Surface0, Surface0, Surface1)

/** Blue ramp used for chart series; index 0 is the oldest period. */
val ChartRamp = listOf(RoyalBlue, AzureBlue, BrandBlue, SkyBlue, IceBlue)

fun brushHero() = Brush.linearGradient(GradientHero)
fun brushRoyalSky() = Brush.linearGradient(GradientRoyalSky)
fun brushAzureCyan() = Brush.linearGradient(GradientAzureCyan)
fun brushBrandIce() = Brush.linearGradient(GradientBrandIce)

/**
 * Colours Material's scheme has no slot for. Held in a CompositionLocal so every
 * surface, stroke and chart series resolves through a name rather than a literal.
 */
@Immutable
data class SkillColors(
    // Semantic / legacy slots
    val teal: Color,
    val blue: Color,
    val green: Color,
    val amber: Color,
    val red: Color,
    val indigo: Color,
    // Surfaces
    val pageBg: Color,
    val cardBg: Color,
    val cardBorder: Color,
    val heroBg: Color,
    val heroBgAlt: Color,
    val heroText: Color,
    val heroMuted: Color,
    val bodyText: Color,
    val subText: Color,
    val track: Color,
    val shimmer: Color,
    // Command-centre additions
    val navy: Color,
    val royal: Color,
    val azure: Color,
    val brand: Color,
    val sky: Color,
    val cyan: Color,
    val aqua: Color,
    val ice: Color,
    val frost: Color,
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    /** Frosted card fill — translucent so the aurora reads through it. */
    val glass: Color,
    /** 1dp hairline that gives a glass card its edge. */
    val glassBorder: Color,
    /** Accent used for glow rings on focused/active elements. */
    val glow: Color,
    val good: Color,
    val warn: Color,
    val crit: Color,
    val labelText: Color,
    /** Retired brass/plum accents — resolve to the blue ramp. */
    val brass: Color,
    val plum: Color,
)

/**
 * The single command-centre scheme — one dark blue identity in both system modes.
 */
val CommandSkillColors = SkillColors(
    teal        = Cyan,
    blue        = BrandBlue,
    green       = Aqua,
    amber       = StatusWarn,
    red         = StatusCrit,
    indigo      = LightAzure,

    pageBg      = Surface0,
    cardBg      = Surface2,
    cardBorder  = Color(0xFF263345),
    heroBg      = DeepNavy,
    heroBgAlt   = BrandBlue,
    heroText    = FrostWhite,
    heroMuted   = Color(0xFFA8B3C5),
    bodyText    = FrostWhite,
    subText     = Color(0xFFA8B3C5),
    track       = Color(0xFF263345),
    shimmer     = Color(0xFF202C3C),

    navy        = DeepNavy,
    royal       = RoyalBlue,
    azure       = AzureBlue,
    brand       = BrandBlue,
    sky         = SkyBlue,
    cyan        = Cyan,
    aqua        = Aqua,
    ice         = IceBlue,
    frost       = FrostWhite,
    surface1    = Surface1,
    surface2    = Surface2,
    surface3    = Surface3,
    glass       = Surface2,
    glassBorder = Color(0xFF263345),
    glow        = Color(0x333B82F6),
    good        = StatusGood,
    warn        = StatusWarn,
    crit        = StatusCrit,
    labelText   = Color(0xFF9AA8BF),
    brass       = SkyBlue,
    plum        = LightAzure,
)

// Both entry points resolve to the command scheme; the app has one identity.
val LightSkillColors = CommandSkillColors
val DarkSkillColors = CommandSkillColors
