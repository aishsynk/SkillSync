package com.example.skillsync.ui.opportunity

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
fun CapabilityGraphScreen(
    managerEmail: String,
    onBack: () -> Unit,
    viewModel: OpportunityViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(managerEmail) {
        viewModel.loadSkillProfile(managerEmail)
    }

    val graph = uiState.skillProfile.capabilityGraph

    Box(modifier = Modifier.fillMaxSize()) {
        AuroraBackground()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("CAPABILITY GRAPH", color = Color.White, fontWeight = FontWeight.Black) },
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
                    Text("Aishwar", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Certified", style = MaterialTheme.typography.titleSmall)
                            graph.certified.forEach { node ->
                                Text("${node.name} (${node.code}) · L${node.level} · ${node.count}")
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
                            Text("Delivered", style = MaterialTheme.typography.titleSmall)
                            graph.delivered.forEach { node ->
                                Text("${node.name} (${node.code}) · L${node.level} · ${node.count}")
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
                            Text("Built", style = MaterialTheme.typography.titleSmall)
                            graph.built.forEach { node ->
                                Text("${node.name} (${node.code}) · L${node.level} · ${node.count}")
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
                            Text("Skills", style = MaterialTheme.typography.titleSmall)
                            graph.skills.forEach { skill ->
                                val icon = when {
                                    skill.gap -> "⬜ GAP"
                                    skill.moderate -> "🟡 MODERATE"
                                    else -> "🟢 STRONG"
                                }
                                Text("$icon ${skill.name} (${(skill.confidence * 100).toInt()}%)")
                            }
                        }
                    }
                }
            }
        }
    }
}
