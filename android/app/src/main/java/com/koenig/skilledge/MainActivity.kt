package com.koenig.skilledge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.koenig.skilledge.core.theme.SkillEdgeTheme
import com.koenig.skilledge.presentation.login.LoginScreen
import com.koenig.skilledge.presentation.dashboard.DashboardScreen
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Timber logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        setContent {
            SkillEdgeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SkillEdgeNavigation()
                }
            }
        }
    }
}

@Composable
fun SkillEdgeNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "login"
    ) {
        // Login Screen
        composable("login") {
            LoginScreen(
                onLoginSuccess = { email ->
                    navController.navigate("dashboard/$email") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        // Dashboard Screen
        composable(
            "dashboard/{manager_email}",
            arguments = listOf(
                androidx.navigation.navArgument("manager_email") {
                    type = androidx.navigation.NavType.StringType
                }
            )
        ) {
            DashboardScreen(
                onSettingsClick = {
                    navController.navigate("settings")
                },
                onActionClick = { actionId ->
                    navController.navigate("action/$actionId")
                },
                onTrainerClick = { trainerId ->
                    navController.navigate("trainer/$trainerId")
                }
            )
        }

        // Settings Screen (placeholder)
        composable("settings") {
            SettingsPlaceholder(
                onBackClick = { navController.popBackStack() }
            )
        }

        // Action Detail Screen (placeholder)
        composable(
            "action/{action_id}",
            arguments = listOf(
                androidx.navigation.navArgument("action_id") {
                    type = androidx.navigation.NavType.StringType
                }
            )
        ) {
            ActionDetailPlaceholder(
                onBackClick = { navController.popBackStack() }
            )
        }

        // Trainer Detail Screen (placeholder)
        composable(
            "trainer/{trainer_id}",
            arguments = listOf(
                androidx.navigation.navArgument("trainer_id") {
                    type = androidx.navigation.NavType.StringType
                }
            )
        ) {
            TrainerDetailPlaceholder(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}

@Composable
private fun SettingsPlaceholder(onBackClick: () -> Unit) {
    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { androidx.compose.material3.Text("Settings") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBackClick) {
                        androidx.compose.material.icons.Icon(
                            androidx.compose.material.icons.filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(it),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            androidx.compose.material3.Text("Settings Coming Soon")
        }
    }
}

@Composable
private fun ActionDetailPlaceholder(onBackClick: () -> Unit) {
    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { androidx.compose.material3.Text("Action Details") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBackClick) {
                        androidx.compose.material.icons.Icon(
                            androidx.compose.material.icons.filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(it),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            androidx.compose.material3.Text("Action Details Coming Soon")
        }
    }
}

@Composable
private fun TrainerDetailPlaceholder(onBackClick: () -> Unit) {
    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { androidx.compose.material3.Text("Trainer Profile") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBackClick) {
                        androidx.compose.material.icons.Icon(
                            androidx.compose.material.icons.filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(it),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            androidx.compose.material3.Text("Trainer Profile Coming Soon")
        }
    }
}
