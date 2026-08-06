package com.koenig.skilledge.core.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// Light theme color scheme
private val LightColorScheme = lightColorScheme(
    primary = TealPrimary,
    onPrimary = BgLightCard,
    primaryContainer = TealLight,
    onPrimaryContainer = TealDark,

    secondary = AmberSecondary,
    onSecondary = BgLightCard,
    secondaryContainer = AmberLight,
    onSecondaryContainer = AmberDark,

    error = ErrorRed,
    onError = BgLightCard,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),

    background = BgLight,
    onBackground = SlateText,

    surface = BgLightCard,
    onSurface = SlateText,
    surfaceVariant = BgLightBorder,
    onSurfaceVariant = SlateTextLight,

    outline = BgLightBorder,
    outlineVariant = Color(0xFFC7D0D9),

    scrim = Color(0x00000000),
)

// Dark theme color scheme
private val DarkColorScheme = darkColorScheme(
    primary = TealLight,
    onPrimary = BgDark,
    primaryContainer = TealDark,
    onPrimaryContainer = TealLight,

    secondary = AmberLight,
    onSecondary = BgDark,
    secondaryContainer = AmberDark,
    onSecondaryContainer = AmberLight,

    error = Color(0xFFFF9999),
    onError = Color(0xFF660000),
    errorContainer = Color(0xFF410E0B),
    onErrorContainer = Color(0xFFF9DEDC),

    background = BgDark,
    onBackground = Color(0xFFF1F5F9),

    surface = BgDarkCard,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = BgDarkBorder,
    onSurfaceVariant = Color(0xFFCBD5E1),

    outline = BgDarkBorder,
    outlineVariant = Color(0xFF475569),

    scrim = Color(0x00000000),
)

// Shape definitions
val SkillEdgeShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
)

@Composable
fun SkillEdgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalView.current.context
            if (darkTheme) {
                androidx.compose.material3.dynamicDarkColorScheme(context)
            } else {
                androidx.compose.material3.dynamicLightColorScheme(context)
            }
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view)?.isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SkillEdgeTypography,
        shapes = SkillEdgeShapes,
        content = content
    )
}

// Convenience extension for accessing theme colors
@Composable
fun SkillEdgeColors.getStatusColor(status: String): androidx.compose.ui.graphics.Color {
    return when (status) {
        "teaching_now" -> StatusTeachingNow
        "preparing" -> StatusPreparing
        "scheduled_today" -> StatusScheduledToday
        "free" -> StatusFree
        "blocked" -> StatusBlocked
        else -> StatusUnknown
    }
}
