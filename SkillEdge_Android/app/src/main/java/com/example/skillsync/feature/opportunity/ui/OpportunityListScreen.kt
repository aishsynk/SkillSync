package com.example.skillsync.feature.opportunity.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.skillsync.R
import com.example.skillsync.theme.AuroraBackground
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.pressable
import com.example.skillsync.theme.skill

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
    val sk = MaterialTheme.skill
    val uiState by viewModel.uiState.collectAsState()
    var selectedFilter by remember { mutableStateOf("All") }

    LaunchedEffect(managerEmail) {
        viewModel.loadOpportunities(managerEmail)
    }

    val filterOptions = listOf("All", "Detected", "Accepted", "Declined", "Missed")
    val filteredOpportunities = remember(uiState.opportunities, selectedFilter) {
        if (selectedFilter == "All") uiState.opportunities
        else uiState.opportunities.filter { it.status.equals(selectedFilter, ignoreCase = true) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AuroraBackground()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("OPPORTUNITIES", color = sk.frost, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painterResource(R.drawable.ic_back),
                                contentDescription = "Back",
                                tint = sk.ice,
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        filterOptions.forEach { filter ->
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { selectedFilter = filter },
                                label = { Text(filter) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = sk.brand.copy(alpha = 0.22f),
                                    selectedLabelColor = sk.sky,
                                    labelColor = sk.subText,
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (selectedFilter == filter) sk.brand else sk.cardBorder,
                                ),
                            )
                        }
                    }
                }

                if (filteredOpportunities.isEmpty() && !uiState.loading) {
                    item {
                        OpportunityEmptyState(
                            if (uiState.opportunities.isEmpty()) "No inbound opportunities detected yet."
                            else "No opportunities matching '$selectedFilter'."
                        )
                    }
                }

                items(filteredOpportunities, key = { it.id }) { opp ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pressable { onOpportunityClick(opp.id) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (opp.isCritical)
                                sk.crit.copy(alpha = 0.14f)
                            else if (opp.isHighOpportunity)
                                sk.amber.copy(alpha = 0.12f)
                            else
                                sk.cardBg
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (opp.isCritical) sk.crit.copy(alpha = 0.5f)
                            else if (opp.isHighOpportunity) sk.amber.copy(alpha = 0.4f)
                            else sk.cardBorder
                        ),
                        shape = RoundedCornerShape(Radii.card),
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    opp.course.ifBlank { opp.courseCode },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = sk.bodyText,
                                    modifier = Modifier.weight(1f),
                                )
                                if (opp.isCritical) {
                                    Surface(
                                        color = sk.crit.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(6.dp),
                                    ) {
                                        Text(
                                            "CRITICAL",
                                            color = sk.crit,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                } else if (opp.isHighOpportunity) {
                                    Surface(
                                        color = sk.warn.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(6.dp),
                                    ) {
                                        Text(
                                            "HIGH",
                                            color = sk.warn,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }

                            if (opp.location.isNotBlank() || opp.country.isNotBlank()) {
                                Text(
                                    listOf(opp.location, opp.country).filter { it.isNotBlank() }.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = sk.subText,
                                )
                            }

                            if (opp.datesStart.isNotBlank()) {
                                Text(
                                    if (opp.datesEnd.isNotBlank() && opp.datesEnd != opp.datesStart) "${opp.datesStart} – ${opp.datesEnd}" else opp.datesStart,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = sk.cyan,
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    "Match: ${opp.skillMatchScore}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (opp.skillMatchScore >= 80) sk.good else if (opp.skillMatchScore >= 50) sk.warn else sk.crit,
                                )
                                if (opp.verdict.isNotBlank()) {
                                    Text(
                                        "Verdict: ${opp.verdict}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = sk.subText,
                                    )
                                }
                            }

                            Spacer(Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.acceptOpportunity(opp.id)
                                        onAccept(opp.id)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = sk.good.copy(alpha = 0.85f)),
                                    shape = RoundedCornerShape(Radii.chip),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Text("ACCEPT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
                                }

                                OutlinedButton(
                                    onClick = {
                                        viewModel.declineOpportunity(opp.id)
                                        onDecline(opp.id)
                                    },
                                    shape = RoundedCornerShape(Radii.chip),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = sk.crit),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, sk.crit.copy(alpha = 0.5f)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Text("DECLINE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }

                                Spacer(Modifier.weight(1f))

                                TextButton(
                                    onClick = { onOpportunityClick(opp.id) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                ) {
                                    Text("DETAILS →", style = MaterialTheme.typography.labelSmall, color = sk.sky, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OpportunityEmptyState(message: String) {
    val sk = MaterialTheme.skill
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = sk.cardBg),
        shape = RoundedCornerShape(Radii.card),
        border = BorderStroke(1.dp, sk.cardBorder),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = sk.sky,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = sk.subText,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
