package com.example.skillsync.feature.opportunity.ui
import com.example.skillsync.feature.guardian.ui.OpportunityGuardianScreen

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
fun OpportunityDetailScreen(
    managerEmail: String,
    opportunityId: String,
    onBack: () -> Unit,
    onGenerateResponse: (String) -> Unit = {},
    viewModel: OpportunityViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(opportunityId) {
        viewModel.loadOpportunities(managerEmail)
    }

    val opp = uiState.opportunities.find { it.id == opportunityId }

    Box(modifier = Modifier.fillMaxSize()) {
        AuroraBackground()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("OPPORTUNITY DETAIL", color = Color.White, fontWeight = FontWeight.Black) },
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
            if (opp == null) {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                    item { Text("Loading...", color = Color.White) }
                }
            } else {
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
                                Text("OPPORTUNITY #SE-${opp.id}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("${opp.course} · ${opp.location}, ${opp.country}")
                                Text("${opp.datesStart} – ${opp.datesEnd}")
                                Text("Sender: ${opp.sender} (${opp.sourceApp} → ${opp.sourceGroup})")
                                Text("Detected: ${opp.detectedAt}")
                                Text("Status: ${opp.status}")
                            }
                        }
                    }
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("YOUR MATCH", style = MaterialTheme.typography.titleMedium)
                                Text("██████████████████░░ ${opp.skillMatchScore}%")
                                Text("Verdict: ${opp.verdict}")
                                Text("Confidence: ${opp.confidence}")
                                Text("Preparation: ${opp.preparationHours}")
                                Text("Major gap: ${opp.majorGap}")
                            }
                        }
                    }
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("STRONG AREAS", style = MaterialTheme.typography.titleSmall)
                                opp.strongAreas.forEach { area ->
                                    Text("✅ $area")
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
                                Text("PREPARATION REQUIRED", style = MaterialTheme.typography.titleSmall)
                                opp.weakAreas.forEach { area ->
                                    Text("⚠️ $area")
                                }
                            }
                        }
                    }
                    item {
                        Row {
                            OutlinedButton(onClick = { onGenerateResponse(opp.id) }) { Text("GENERATE RESPONSE") }
                        }
                    }
                    item {
                        Row {
                            OutlinedButton(onClick = { /* accept */ }) { Text("ACCEPT") }
                            OutlinedButton(onClick = { /* decline */ }) { Text("DECLINE") }
                            OutlinedButton(onClick = { /* snooze */ }) { Text("SNOOZE 10 MIN") }
                            OutlinedButton(onClick = { /* mark seen */ }) { Text("MARK SEEN") }
                        }
                    }
                }
            }
        }
    }
}
