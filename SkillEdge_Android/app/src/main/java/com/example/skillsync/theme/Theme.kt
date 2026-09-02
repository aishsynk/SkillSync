package com.example.skillsync.theme

import android.app.Activity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
    onSurfaceVariant   = Color(0xB3C9BEE0),
    outline            = Color(0x3EC9B79A),
    outlineVariant     = Color(0x24C9B79A),
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
    val notify = com.example.skillsync.ui.components.rememberNotifyState()
    CompositionLocalProvider(
        LocalSkillColors provides CommandSkillColors,
        com.example.skillsync.ui.components.LocalNotify provides notify,
    ) {
        MaterialTheme(
            colorScheme = CommandScheme,
            typography = Typography,
        ) {
            // The toast host is mounted once, above every screen, so a message
            // raised as the user navigates is not torn down with the screen
            // that raised it. Screens call `LocalNotify.current.success(…)`
            // and never own notification plumbing themselves.
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                content()
                com.example.skillsync.ui.components.ToastHost(
                    notify,
                    androidx.compose.ui.Modifier
                        .align(androidx.compose.ui.Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = Space.sm),
                )
            }
        }
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
