package com.example.skillsync.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * One brand-locked command-centre scheme, dark in both system modes.
 *
 * Dynamic (wallpaper) colour stays off — SkillEdge is an operations console and
 * the blue identity has to be the same on every device. The previous teal
 * primary is gone entirely: teal was doubling as both brand and "healthy"
 * status, which made the two impossible to tell apart on a KPI card.
 */
private val CommandScheme = darkColorScheme(
    primary            = BrandBlue,
    onPrimary          = Color.White,
    primaryContainer   = RoyalBlue,
    onPrimaryContainer = FrostWhite,
    secondary          = Cyan,
    onSecondary        = Color(0xFF04222A),
    secondaryContainer = Color(0xFF0B3A44),
    onSecondaryContainer = Color(0xFFB2EBF2),
    tertiary           = LightAzure,
    onTertiary         = Color(0xFF07203A),
    background         = Surface0,
    onBackground       = FrostWhite,
    surface            = Surface2,
    onSurface          = FrostWhite,
    surfaceVariant     = Surface3,
    onSurfaceVariant   = Color(0xB390CAF9),
    outline            = Color(0x3D90CAF9),
    outlineVariant     = Color(0x2490CAF9),
    error              = StatusCrit,
    onError            = Color(0xFF3E0710),
    errorContainer     = Color(0xFF4A1220),
    onErrorContainer   = Color(0xFFFFD9DE),
    scrim              = Color(0xCC05080D),
)

val LocalSkillColors = staticCompositionLocalOf { CommandSkillColors }

/** Extended brand colours: `MaterialTheme.skill.aqua`. */
val MaterialTheme.skill: SkillColors
    @Composable @ReadOnlyComposable get() = LocalSkillColors.current

@Composable
fun SkillSyncTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSkillColors provides CommandSkillColors) {
        MaterialTheme(
            colorScheme = CommandScheme,
            typography = Typography,
            content = content,
        )
    }
}

/**
 * Screens sit edge-to-edge under the status bar. Every surface in the command
 * centre is dark, so status-bar icons are light throughout; the parameter is
 * kept so existing call sites compile unchanged.
 */
@Composable
fun StatusBarIcons(@Suppress("UNUSED_PARAMETER") lightIcons: Boolean = true) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = (view.context as? Activity)?.window ?: return
    SideEffect {
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
    }
}
