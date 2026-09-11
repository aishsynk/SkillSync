package com.example.skillsync.feature.opportunity.ui

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
import androidx.compose.ui.Modifier
import com.example.skillsync.R
import androidx.compose.material3.Text

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpportunityListScreen(
    managerEmail: String,
    onBack: () -> Unit,
    onOpportunityClick: (String) -> Unit,
    onAccept: (String) -> Unit = {},
    onDecline: (String) -> Unit = {},
    viewModel: OpportunityViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(managerEmail) {
        viewModel.loadOpportunities(managerEmail)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AuroraBackground()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("OPPORTUNITIES", color = Color.White, fontWeight = FontWeight.Black) },
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Row {
                        FilterChip(selected = true, onClick = {}, label = { Text("All") })
                        FilterChip(selected = false, onClick = {}, label = { Text("Detected") })
                        FilterChip(selected = false, onClick = {}, label = { Text("Accepted") })
                        FilterChip(selected = false, onClick = {}, label = { Text("Declined") })
                        FilterChip(selected = false, onClick = {}, label = { Text("Missed") })
                    }
                }
                items(uiState.opportunities) { opp ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (opp.isCritical)
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                            else if (opp.isHighOpportunity)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                            else
                                MaterialTheme.colorScheme.surface
                        ),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text(opp.course, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                if (opp.isCritical) Text("🚨 CRITICAL", color = Color.Red)
                                else if (opp.isHighOpportunity) Text("⚡ HIGH", color = Color.Yellow)
                            }
                            Text("${opp.location} · ${opp.country}")
                            Text("${opp.datesStart} – ${opp.datesEnd}")
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text("Match: ${opp.skillMatchScore}%")
                                Spacer(Modifier.weight(1f))
                                Text("Verdict: ${opp.verdict}")
                            }
                            Row {
                                OutlinedButton(onClick = { onAccept(opp.id) }) { Text("ACCEPT") }
                                OutlinedButton(onClick = { onDecline(opp.id) }) { Text("DECLINE") }
                                TextButton(onClick = { onOpportunityClick(opp.id) }) { Text("DETAILS") }
                            }
                        }
                    }
                }
            }
        }
    }
}
