package com.example.skillsync.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Brand-locked schemes. Dynamic (wallpaper) colour is deliberately NOT used —
// SkillSync is a corporate tool and the teal/blue identity has to stay put.
private val LightScheme = lightColorScheme(
    primary            = Teal,
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFFD3F1F1),
    onPrimaryContainer = Color(0xFF00302F),
    secondary          = BrandBlue,
    onSecondary        = Color.White,
    tertiary           = Indigo,
    onTertiary         = Color.White,
    background         = Color(0xFFF2F5F8),
    onBackground       = Color(0xFF2F3A45),
    surface            = Color.White,
    onSurface          = Color(0xFF2F3A45),
    surfaceVariant     = Color(0xFFEDF1F5),
    onSurfaceVariant   = Color(0xFF77848F),
    outline            = Color(0xFFCBD5DD),
    outlineVariant     = Color(0xFFE4EAEF),
    error              = Color(0xFFE23F3B),
    onError            = Color.White,
    errorContainer     = Color(0xFFFFE4E3),
    onErrorContainer   = Color(0xFF6D0F0D),
)

private val DarkScheme = darkColorScheme(
    primary            = Color(0xFF26C6C6),
    onPrimary          = Color(0xFF00201F),
    primaryContainer   = Color(0xFF00504F),
    onPrimaryContainer = Color(0xFFB8ECEC),
    secondary          = Color(0xFF7FBBF5),
    onSecondary        = Color(0xFF00305B),
    tertiary           = Color(0xFF9A6BFF),
    onTertiary         = Color(0xFF250066),
    background         = Color(0xFF0F141A),
    onBackground       = Color(0xFFE4EAEF),
    surface            = Color(0xFF1A2129),
    onSurface          = Color(0xFFE4EAEF),
    surfaceVariant     = Color(0xFF222B34),
    onSurfaceVariant   = Color(0xFF8C99A4),
    outline            = Color(0xFF3A464F),
    outlineVariant     = Color(0xFF2A343E),
    error              = Color(0xFFFF7A76),
    onError            = Color(0xFF5B0F0D),
    errorContainer     = Color(0xFF6B1B19),
    onErrorContainer   = Color(0xFFFFDAD8),
)

val LocalSkillColors = staticCompositionLocalOf { LightSkillColors }

/** Extended brand colours: `MaterialTheme.skill.amber`. */
val MaterialTheme.skill: SkillColors
    @Composable @ReadOnlyComposable get() = LocalSkillColors.current

@Composable
fun SkillSyncTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalSkillColors provides if (darkTheme) DarkSkillColors else LightSkillColors
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = Typography,
            content = content,
        )
    }
}

/**
 * Screens sit edge-to-edge under the status bar, so each one declares whether the
 * surface behind it is dark (light icons) or light (dark icons).
 */
@Composable
fun StatusBarIcons(lightIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = (view.context as? Activity)?.window ?: return
    SideEffect {
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !lightIcons
    }
}
