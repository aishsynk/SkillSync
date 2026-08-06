package com.example.skillsync.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// ── Brand mark palette (matches ic_logo.xml) ─────────────────────────────────
val BrandCyan = Color(0xFF29B6F6)
val BrandBlue = Color(0xFF1E88E5)
val BrandDeep = Color(0xFF0D47A1)
val BrandNavy = Color(0xFF0A3880)

// ── Semantic palette — same hues as the SkillEdge web dashboard ──────────────
val Teal   = Color(0xFF00ACAC)
val Blue   = Color(0xFF348FE2)
val Green  = Color(0xFF90CA4B)
val Amber  = Color(0xFFF59C1A)
val Red    = Color(0xFFFF5B57)
val Indigo = Color(0xFF6610F2)

/**
 * Colours Material's scheme has no slot for — the dashboard's hero cards, table
 * chrome and status hues. Kept in a CompositionLocal so light/dark swap together.
 */
@Immutable
data class SkillColors(
    val teal: Color,
    val blue: Color,
    val green: Color,
    val amber: Color,
    val red: Color,
    val indigo: Color,
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
)

val LightSkillColors = SkillColors(
    teal       = Teal,
    blue       = Blue,
    green      = Color(0xFF6BA82F),
    amber      = Color(0xFFD9840A),
    red        = Color(0xFFE23F3B),
    indigo     = Indigo,
    pageBg     = Color(0xFFF2F5F8),
    cardBg     = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFE4EAEF),
    heroBg     = Color(0xFF2C3843),
    heroBgAlt  = Color(0xFF3A4855),
    heroText   = Color(0xFFFFFFFF),
    heroMuted  = Color(0xFFAEBAC4),
    bodyText   = Color(0xFF2F3A45),
    subText    = Color(0xFF77848F),
    track      = Color(0xFFE6EAEE),
    shimmer    = Color(0xFFDCE3E9),
)

val DarkSkillColors = SkillColors(
    teal       = Color(0xFF26C6C6),
    blue       = Color(0xFF56A6EE),
    green      = Color(0xFFA3D45F),
    amber      = Color(0xFFFFB33D),
    red        = Color(0xFFFF7A76),
    indigo     = Color(0xFF9A6BFF),
    pageBg     = Color(0xFF0F141A),
    cardBg     = Color(0xFF1A2129),
    cardBorder = Color(0xFF2A343E),
    heroBg     = Color(0xFF1E2830),
    heroBgAlt  = Color(0xFF27333D),
    heroText   = Color(0xFFF2F6F9),
    heroMuted  = Color(0xFF95A3AE),
    bodyText   = Color(0xFFE4EAEF),
    subText    = Color(0xFF8C99A4),
    track      = Color(0xFF2A343E),
    shimmer    = Color(0xFF273038),
)
