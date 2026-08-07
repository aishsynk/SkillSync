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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.skillsync.ui.auth.LoginScreen
import com.example.skillsync.ui.batch.AllocationState
import com.example.skillsync.ui.batch.AllocationViewModel
import com.example.skillsync.ui.batch.BatchDetailScreen
import com.example.skillsync.ui.components.list
import com.example.skillsync.ui.components.rows
import com.example.skillsync.ui.components.str
import com.example.skillsync.ui.components.Motion
import com.example.skillsync.ui.main.MainScreen
import com.example.skillsync.ui.main.MainScreenViewModel
import com.example.skillsync.ui.trainer.Trainer360Screen

@Composable
fun MainNavigation() {
    var current by remember { 
        mutableStateOf<NavKey>(
            if (com.example.skillsync.data.SessionManager.isLoggedIn()) {
                Main(com.example.skillsync.data.SessionManager.getEmail()!!)
            } else {
                Login
            }
        ) 
    }

    // Shared so a batch opened from the desk keeps its data and mark-skill state.
    val allocationViewModel: AllocationViewModel = viewModel()
    // Hoisted out of MainScreen so a skill write can invalidate the capability
    // cache that the dashboard and Courses tab read from.
    val mainViewModel: MainScreenViewModel = viewModel()

    // Hardware/gesture back returns from a pushed detail screen to the shell.
    BackHandler(enabled = current is Trainer360 || current is BatchDetail) {
        current = when (val c = current) {
            is Trainer360 -> Main(c.email, HomeTab.TEAM)
            is BatchDetail -> Main(c.email, HomeTab.DEMAND)
            else -> c
        }
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
                onBatchClick = { demandId -> current = BatchDetail(screen.email, demandId) },
                onLogout = { current = Login },
                modifier = Modifier,
                viewModel = mainViewModel,
                allocationViewModel = allocationViewModel,
            )

            is Trainer360 -> Trainer360Screen(
                trainerEmail = screen.trainerEmail,
                trainerName = screen.trainerName,
                managerEmail = screen.email,
                onBack = { current = Main(screen.email, HomeTab.TEAM) },
            )

            is BatchDetail -> {
                val allocState by allocationViewModel.state.collectAsState()
                val markState by allocationViewModel.mark.collectAsState()
                val data = (allocState as? AllocationState.Success)?.data
                val batch = data?.rows("batches")
                    ?.firstOrNull { it.str("demand_id") == screen.demandId }

                if (batch == null) {
                    // Reached without the desk loaded (e.g. process death); go back
                    // rather than render a detail screen with nothing in it.
                    LaunchedEffect(Unit) { current = Main(screen.email, HomeTab.DEMAND) }
                } else {
                    // Candidates are this manager's reportees, which is exactly the
                    // set they may mark a skill for.
                    val reportees = data.rows("batches")
                        .flatMap { it.list("candidates") }
                        .map { it.str("trainer_name") to it.str("trainer_email") }
                        .filter { it.second.isNotBlank() }
                        .distinctBy { it.second }
                        .sortedBy { it.first }

                    BatchDetailScreen(
                        batch = batch,
                        managerEmail = screen.email,
                        reportees = reportees,
                        markState = markState,
                        onMarkSkill = { courseId, trainerEmail, level, date, who ->
                            allocationViewModel.markSkill(
                                courseId, trainerEmail, level, date, who,
                                // A confirmed write changes course ownership and
                                // certification coverage, so the catalogue and the
                                // cert KPIs are re-read rather than left stale.
                                onSaved = { mainViewModel.refreshCapability(screen.email) },
                            )
                        },
                        onClearMark = { allocationViewModel.clearMark() },
                        onBack = { current = Main(screen.email, HomeTab.DEMAND) },
                    )
                }
            }
        }
    }
}
