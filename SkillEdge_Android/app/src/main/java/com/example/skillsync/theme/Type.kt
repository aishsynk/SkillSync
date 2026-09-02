package com.example.skillsync.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.skillsync.R

/**
 * SkillEdge V3 — the editorial voice.
 *
 * Two families do all the work. **Fraunces** — a warm, high-contrast serif with a
 * light optical cut — carries every screen title, section conclusion and hero
 * numeral; it is the thing that makes a screen read like a printed briefing
 * rather than a form. **Inter** carries body copy, row leads, labels and every
 * control. System SansSerif is gone.
 *
 * The scale is deliberately wide: a display step is not a slightly bigger title.
 * Titles are Fraunces at light/regular weight with negative tracking so a large
 * size still reads as considered, not loud. Labels are Inter SemiBold, small,
 * with open tracking so a caption reads as a caption.
 */

val Display = FontFamily(
    Font(R.font.fraunces_light, FontWeight.Light),
    Font(R.font.fraunces_regular, FontWeight.Normal),
    Font(R.font.fraunces_medium, FontWeight.Medium),
)

val Sans = FontFamily(
    Font(R.font.inter_light, FontWeight.Light),
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

val Typography = Typography(
    // ── Editorial display — Fraunces. Hero numerals and screen-defining lines. ──
    displayLarge = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Light,
        fontSize = 52.sp, lineHeight = 54.sp, letterSpacing = (-1.8).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Light,
        fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-1.2).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Normal,
        fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.6).sp,
    ),

    // ── Screen + section headings — Fraunces. ──
    headlineLarge = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Normal,
        fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Normal,
        fontSize = 21.sp, lineHeight = 27.sp, letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Medium,
        fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp,
    ),

    // ── Card titles and row leads — Inter, so structure reads crisply. ──
    titleLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp, lineHeight = 23.sp, letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 20.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 18.sp,
    ),

    // ── Prose — Inter. ──
    bodyLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 18.sp,
    ),

    // ── Labels — Inter SemiBold, tracked caps. ──
    labelLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 1.0.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp, lineHeight = 13.sp, letterSpacing = 1.4.sp,
    ),
)

/**
 * Hero figure style — Fraunces Light, tight, tabular. Every KPI numeral that can
 * change on refresh uses this so columns never jitter as digits swap width.
 */
val NumericStyle = TextStyle(
    fontFamily = Display,
    fontWeight = FontWeight.Light,
    fontFeatureSettings = "tnum",
    letterSpacing = (-1.0).sp,
)

/** Tabular Inter, for smaller inline figures inside rows and chips. */
val NumericInline = TextStyle(
    fontFamily = Sans,
    fontWeight = FontWeight.Medium,
    fontFeatureSettings = "tnum",
)
