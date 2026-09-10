package com.example.skillsync.feature.guardian.ui
import com.example.skillsync.feature.opportunity.ui.OpportunityViewModel

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
import androidx.compose.ui.Modifier
import com.example.skillsync.R
import androidx.compose.material3.Text

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpportunityGuardianScreen(
    managerEmail: String,
    onBack: () -> Unit,
    onTabChange: (String) -> Unit,
    viewModel: OpportunityViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(managerEmail) {
        viewModel.loadGuardianConfig(managerEmail)
        viewModel.loadSkillProfile(managerEmail)
        viewModel.loadOpportunities(managerEmail)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AuroraBackground()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("OPPORTUNITY GUARDIAN", color = Color.White, fontWeight = FontWeight.Black) },
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
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Trusted Sources", style = MaterialTheme.typography.titleSmall)
                            uiState.guardianConfig.trustedSources.forEach { source ->
                                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text("${source.app} / ${source.group} / ${source.sender}")
                                    Checkbox(checked = source.enabled, onCheckedChange = {})
                                }
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
                            Text("Quiet Hours: ${uiState.guardianConfig.quietHoursStart} – ${uiState.guardianConfig.quietHoursEnd}")
                            Text("Normal messages: ${if (uiState.guardianConfig.quietHoursNormalMessages) "Ignore" else "Notify"}")
                            Text("High opportunities: ${if (uiState.guardianConfig.quietHoursHighOpportunities) "Persistent alert" else "Normal"}")
                            Text("Critical opportunities: ${if (uiState.guardianConfig.quietHoursCriticalOpportunities) "Alarm escalation" else "Normal"}")
                        }
                    }
                }
                item {
                    Text("Opportunities Summary", style = MaterialTheme.typography.titleMedium)
                    Text("Detected: ${uiState.summary.detected} | Accepted: ${uiState.summary.accepted} | Declined: ${uiState.summary.declined} | Missed: ${uiState.summary.missed}")
                }
                item {
                    Text("Skill Profile", style = MaterialTheme.typography.titleMedium)
                    Text("Technologies: ${uiState.skillProfile.technologies.joinToString(", ")}")
                    Text("Certifications: ${uiState.skillProfile.certifications.joinToString(", ")}")
                }
                item {
                    Text("Match Result", style = MaterialTheme.typography.titleMedium)
                    Text("Score: ${uiState.matchResult.matchScore}% | Verdict: ${uiState.matchResult.verdict}")
                    Text("Confidence: ${uiState.matchResult.confidence}")
                    Text("Major gap: ${uiState.matchResult.majorGap}")
                    Text("Prep: ${uiState.matchResult.preparationHours}")
                }
            }
        }
    }
}
