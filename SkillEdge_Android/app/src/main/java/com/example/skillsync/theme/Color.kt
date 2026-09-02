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
// Blue still carries structure and "healthy". It reads louder now because the
// ground beneath it went warm graphite instead of blue-black.
val DeepNavy   = Color(0xFF16131C)
val RoyalBlue  = Color(0xFF2B4CC8)
val AzureBlue  = Color(0xFF3661E4)
val BrandBlue  = Color(0xFF5B8DEF)
val SkyBlue    = Color(0xFF8FB4F6)
val Cyan       = Color(0xFF4CD6D0)
val Aqua       = Color(0xFF56D6A0)
val LightAzure = Color(0xFFB7CBF3)
val IceBlue    = Color(0xFFDCE6FA)
val SoftBlue   = Color(0xFFE7EDFB)
val FrostWhite = Color(0xFFF7F5F1)   // warm paper white

// ── The V3 signature accent — champagne brass. Premium moments only:
//    hero figure underline, international demand, the login lockup. ───────────
val Brass      = Color(0xFFD8B26A)
val BrassDeep  = Color(0xFF9A7B3E)
val Plum       = Color(0xFF8A73C4)

// ── Dark surfaces — warm violet-graphite, not blue-black. OLED-safe. ─────────
val Surface0 = Color(0xFF0C0A11)
val Surface1 = Color(0xFF141119)
val Surface2 = Color(0xFF1B1722)
val Surface3 = Color(0xFF261F30)

// ── Semantic status ──────────────────────────────────────────────────────────
val StatusGood = Aqua
val StatusWarn = Color(0xFFE6B450)
val StatusCrit = Color(0xFFEF6F7B)

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
    /** V3 signature accent — champagne brass, premium moments only. */
    val brass: Color,
    val plum: Color,
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
    cardBorder  = Color(0xFF2E2838),
    heroBg      = DeepNavy,
    heroBgAlt   = BrandBlue,
    heroText    = FrostWhite,
    heroMuted   = Color(0xFFB6ADBE),
    bodyText    = FrostWhite,
    subText     = Color(0xFFB0A7BA),
    track       = Color(0xFF2E2838),
    shimmer     = Color(0xFF272130),

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
    glassBorder = Color(0x33C9B79A),
    glow        = Color(0x33D8B26A),
    good        = StatusGood,
    warn        = StatusWarn,
    crit        = StatusCrit,
    labelText   = Color(0xFF9E93AC),
    brass       = Brass,
    plum        = Plum,
)

// Both entry points resolve to the command scheme; the app has one identity.
val LightSkillColors = CommandSkillColors
val DarkSkillColors = CommandSkillColors
