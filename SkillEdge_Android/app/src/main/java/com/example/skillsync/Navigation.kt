package com.example.skillsync

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
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
import com.example.skillsync.ui.trainer.Trainer360Screen

@Composable
fun MainNavigation() {
    var current by remember { mutableStateOf<NavKey>(Login) }

    // Hardware/gesture back returns from a trainer profile to the shell.
    BackHandler(enabled = current is Trainer360) {
        (current as? Trainer360)?.let { current = Main(it.email, HomeTab.TEAM) }
    }

    AnimatedContent(
        targetState = current,
        transitionSpec = {
            val from = initialState
            val to = targetState
            when {
                // Drilling into a trainer: slide in from the right.
                to is Trainer360 ->
                    (slideInHorizontally(tween(Motion.NORMAL, easing = Motion.Emphasized)) { it } +
                        fadeIn(tween(Motion.NORMAL)))
                        .togetherWith(fadeOut(tween(Motion.FAST)))
                // Coming back out: slide away to the right.
                from is Trainer360 ->
                    fadeIn(tween(Motion.NORMAL))
                        .togetherWith(
                            slideOutHorizontally(tween(Motion.NORMAL, easing = Motion.Emphasized)) { it } +
                                fadeOut(tween(Motion.NORMAL))
                        )
                // Switching tabs inside the shell: cross-fade only.
                from is Main && to is Main ->
                    fadeIn(tween(Motion.FAST)).togetherWith(fadeOut(tween(Motion.FAST)))
                // Login -> dashboard.
                else ->
                    (slideInVertically(tween(Motion.SLOW, easing = Motion.Emphasized)) { it / 5 } +
                        fadeIn(tween(Motion.SLOW)))
                        .togetherWith(
                            fadeOut(tween(Motion.NORMAL)) +
                                scaleOut(tween(Motion.NORMAL), targetScale = 0.94f)
                        )
            }
        },
        label = "screen",
    ) { screen ->
        when (screen) {
            is Login -> LoginScreen(
                onLoginSuccess = { email -> current = Main(email) },
            )

            is Main -> MainScreen(
                email = screen.email,
                tab = screen.tab,
                onTabChange = { tab -> current = Main(screen.email, tab) },
                onTrainerClick = { trainerEmail, trainerName ->
                    current = Trainer360(screen.email, trainerEmail, trainerName)
                },
                modifier = Modifier,
            )

            is Trainer360 -> Trainer360Screen(
                trainerEmail = screen.trainerEmail,
                trainerName = screen.trainerName,
                onBack = { current = Main(screen.email, HomeTab.TEAM) },
            )
        }
    }
}
