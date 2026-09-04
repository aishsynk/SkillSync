package com.example.skillsync.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Command-centre scale — one clean sans family, system default.
 *
 * The Fraunces serif "editorial" experiment is retired: a serif headline on an
 * operations console read as decoration, not data. `Display` and `Sans` are both
 * the system sans now; the names are kept so call sites compile unchanged.
 *
 * Steps are wide enough to be felt (44 / 34 / 28 / 24 / 20 / 18 / 15 / 14 / 12 /
 * 11) and every size is a whole sp so the layout scales predictably with the
 * system font setting. Figures are set light and tight so a large numeral reads
 * as a headline value; labels are SemiBold small-caps tracking.
 */
val Sans = FontFamily.SansSerif
val Display = FontFamily.SansSerif

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Light,
        fontSize = 44.sp, lineHeight = 46.sp, letterSpacing = (-1.6).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Light,
        fontSize = 34.sp, lineHeight = 36.sp, letterSpacing = (-1.2).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Normal,
        fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.8).sp,
    ),

    headlineLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp,
    ),

    titleLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 20.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 18.sp,
    ),

    bodyLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 17.sp,
    ),

    labelLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.8.sp,
    ),
)

/**
 * Tabular figures. Applied to every number that can change on refresh so
 * columns stop jittering as digits swap width.
 */
val NumericStyle = TextStyle(
    fontFamily = Sans,
    fontWeight = FontWeight.Light,
    fontFeatureSettings = "tnum",
    letterSpacing = (-1.0).sp,
)

/** Tabular figures for smaller inline numbers inside rows and chips. */
val NumericInline = TextStyle(
    fontFamily = Sans,
    fontWeight = FontWeight.Medium,
    fontFeatureSettings = "tnum",
)
