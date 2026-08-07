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
 * Status hues are deliberately held apart from the blue ramp — a status colour
 * that doubles as branding stops meaning anything.
 */

// ── Primary ramp ─────────────────────────────────────────────────────────────
val DeepNavy   = Color(0xFF0B1F5E)
val RoyalBlue  = Color(0xFF144EA6)
val AzureBlue  = Color(0xFF1976D2)
val BrandBlue  = Color(0xFF2196F3)
val SkyBlue    = Color(0xFF42A5F5)
val Cyan       = Color(0xFF00BCD4)
val Aqua       = Color(0xFF26C6DA)
val LightAzure = Color(0xFF64B5F6)
val IceBlue    = Color(0xFF90CAF9)
val SoftBlue   = Color(0xFFBBDEFB)
val FrostWhite = Color(0xFFE3F2FD)

// ── Dark surfaces (elevation steps, not drop shadows) ────────────────────────
val Surface0 = Color(0xFF0D1117)   // scaffold
val Surface1 = Color(0xFF121826)   // section ground
val Surface2 = Color(0xFF172030)   // card
val Surface3 = Color(0xFF1E293B)   // raised / pressed

// ── Semantic status ──────────────────────────────────────────────────────────
val StatusGood = Aqua
val StatusWarn = Color(0xFFF0A828)
val StatusCrit = Color(0xFFF0556B)

// Names kept from the previous palette so existing call sites keep compiling;
// the values now resolve into the command-centre ramp.
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
val GradientHero     = listOf(DeepNavy, BrandBlue)              // Deep Navy → Brand Blue
val GradientRoyalSky = listOf(RoyalBlue, SkyBlue)               // Royal Blue → Sky Blue
val GradientAzureCyan = listOf(AzureBlue, Cyan)                 // Azure Blue → Cyan
val GradientBrandIce = listOf(BrandBlue, IceBlue)               // Brand Blue → Ice Blue
val GradientAurora   = listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))

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
)

/**
 * The single command-centre scheme.
 *
 * The app deliberately commits to one dark identity rather than shipping a light
 * variant: on the aurora mesh ground a light theme halves the contrast of every
 * status colour, and an operations console that reads like a spreadsheet loses
 * the at-a-glance triage the whole layout is built around.
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
    cardBorder  = Color(0x2490CAF9),   // Ice @ 14%
    heroBg      = DeepNavy,
    heroBgAlt   = BrandBlue,
    heroText    = FrostWhite,
    heroMuted   = Color(0xB390CAF9),   // Ice @ 70%
    bodyText    = FrostWhite,
    subText     = Color(0xB390CAF9),
    track       = Color(0x1A90CAF9),   // Ice @ 10%
    shimmer     = Color(0x1F90CAF9),

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
    glass       = Color(0x9E172030),   // Surface-2 @ 62%
    glassBorder = Color(0x2490CAF9),
    glow        = Color(0x2A2196F3),   // Brand @ 16%
    good        = StatusGood,
    warn        = StatusWarn,
    crit        = StatusCrit,
    labelText   = Color(0x7390CAF9),   // Ice @ 45% — uppercase labels
)

// Both entry points resolve to the command scheme; the app has one identity.
val LightSkillColors = CommandSkillColors
val DarkSkillColors = CommandSkillColors
