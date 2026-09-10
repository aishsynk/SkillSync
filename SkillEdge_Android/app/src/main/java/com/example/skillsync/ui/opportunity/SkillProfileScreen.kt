package com.example.skillsync.ui.opportunity

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.skillsync.theme.AuroraBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillProfileScreen(
    managerEmail: String,
    onBack: () -> Unit,
    viewModel: OpportunityViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(managerEmail) {
        viewModel.loadSkillProfile(managerEmail)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AuroraBackground()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("MY SKILL PROFILE", color = Color.White, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                androidx.compose.ui.res.painterResource(R.drawable.ic_back),
                                contentDescription = "Back",
                                tint = Color.White,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Text("My Skill Profile", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("${uiState.skillProfile.experienceYears} years of experience")
                }
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Certifications", style = MaterialTheme.typography.titleSmall)
                            uiState.skillProfile.certifications.forEach { cert ->
                                Text("✓ $cert")
                            }
                            if (uiState.skillProfile.certifications.isEmpty()) {
                                Text("No certifications on file")
                            }
                        }
                    }
                }
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Technologies", style = MaterialTheme.typography.titleSmall)
                            uiState.skillProfile.technologies.forEach { tech ->
                                Text("• $tech")
                            }
                            if (uiState.skillProfile.technologies.isEmpty()) {
                                Text("No technologies on file")
                            }
                        }
                    }
                }
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Courses Delivered", style = MaterialTheme.typography.titleSmall)
                            uiState.skillProfile.coursesDelivered.forEach { course ->
                                Text("• $course")
                            }
                            if (uiState.skillProfile.coursesDelivered.isEmpty()) {
                                Text("No courses delivered on file")
                            }
                        }
                    }
                }
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Labs / Projects", style = MaterialTheme.typography.titleSmall)
                            uiState.skillProfile.labsProjects.forEach { project ->
                                Text("• $project")
                            }
                            if (uiState.skillProfile.labsProjects.isEmpty()) {
                                Text("No labs or projects on file")
                            }
                        }
                    }
                }
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Confidence by Topic", style = MaterialTheme.typography.titleSmall)
                            uiState.skillProfile.confidenceByTopic.forEach { (topic, confidence) ->
                                val barLength = (confidence * 20).toInt()
                                Text("$topic: ${(confidence * 100).toInt()}% ${"█".repeat(barLength)}${"░".repeat(20 - barLength)}")
                            }
                            if (uiState.skillProfile.confidenceByTopic.isEmpty()) {
                                Text("No topic confidence data")
                            }
                        }
                    }
                }
            }
        }
    }
}
