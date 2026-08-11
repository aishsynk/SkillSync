package com.example.skillsync.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Sans = FontFamily.SansSerif

/**
 * Command-centre scale.
 *
 * The previous scale packed title/body/label into a 1.5sp band, so four nominal
 * levels read as one and no screen had a visual top. Steps are now wide enough
 * to be felt (40 / 24 / 18 / 15 / 14 / 12 / 11) and every size is a whole sp so
 * the layout scales predictably with the system font setting.
 *
 * Figures are set light and tight — a large thin numeral reads as a headline
 * value, while bold body text reads as a label, which is the opposite of what
 * the old scale did.
 */
val Typography = Typography(
    // Hero figures. Light weight + negative tracking = "value", not "shouting".
    displayLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Light,
        fontSize = 44.sp, lineHeight = 46.sp, letterSpacing = (-1.6).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Light,
        fontSize = 34.sp, lineHeight = 36.sp, letterSpacing = (-1.2).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.8).sp,
    ),

    // Screen and section headings.
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

    // Card titles and row leads.
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

    // Prose.
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

    // Labels. Small caps tracking is what makes a dashboard label read as a
    // label rather than as shrunken body text.
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
    fontFeatureSettings = "tnum",
)
