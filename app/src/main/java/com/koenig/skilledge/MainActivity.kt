package com.koenig.skilledge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.koenig.skilledge.core.theme.SkillEdgeTheme
import com.koenig.skilledge.presentation.dashboard.DashboardScreen
import com.koenig.skilledge.presentation.login.LoginScreen
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPlaceholder(onBackClick: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Text("Settings Coming Soon")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionDetailPlaceholder(onBackClick: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Action Details") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Text("Action Details Coming Soon")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrainerDetailPlaceholder(onBackClick: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trainer Profile") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Text("Trainer Profile Coming Soon")
        }
    }
}
