package com.example.skillsync

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import com.example.skillsync.ui.auth.LoginScreen
import com.example.skillsync.ui.components.Motion
import com.example.skillsync.ui.main.MainScreen

@Composable
fun MainNavigation() {
    var currentScreen by remember { mutableStateOf<NavKey>(Login) }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            // Dashboard rises over a login screen that fades and settles back.
            (slideInVertically(tween(Motion.SLOW, easing = Motion.Emphasized)) { it / 5 } +
                fadeIn(tween(Motion.SLOW)))
                .togetherWith(
                    fadeOut(tween(Motion.NORMAL)) +
                        scaleOut(tween(Motion.NORMAL), targetScale = 0.94f)
                )
        },
        label = "screen",
    ) { screen ->
        when (screen) {
            is Login -> LoginScreen(
                onLoginSuccess = { email -> currentScreen = Main(email) },
            )
            is Main -> MainScreen(
                email = screen.email,
                onItemClick = { navKey -> currentScreen = navKey },
                modifier = Modifier,
            )
        }
    }
}
