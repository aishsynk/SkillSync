package com.example.skillsync.feature.opportunity.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.skillsync.theme.*

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
                SkillSyncTopBar(
                    title = "Commercial Opportunities",
                    subtitle = "Viber & Network Inbound Radar",
                    onBack = onBack,
                )
            },
        ) { padding ->
            if (uiState.loading && uiState.opportunities.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    SkillSyncLoadingState()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Summary Strip
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SkillSyncMetric(
                                label = "Detected",
                                value = uiState.summary.detected.toString(),
                                tint = sk.sky,
                                modifier = Modifier.weight(1f),
                            )
                            SkillSyncMetric(
                                label = "Accepted",
                                value = uiState.summary.accepted.toString(),
                                tint = sk.good,
                                modifier = Modifier.weight(1f),
                            )
                            SkillSyncMetric(
                                label = "Declined",
                                value = uiState.summary.declined.toString(),
                                tint = sk.crit,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    // Filter Chips
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            filterOptions.forEach { filter ->
                                val isSelected = selectedFilter == filter
                                ToneChip(
                                    text = filter,
                                    tint = if (isSelected) sk.brand else sk.subText,
                                    modifier = Modifier.pressable { selectedFilter = filter },
                                )
                            }
                        }
                    }

                    // Empty State
                    if (filteredOpportunities.isEmpty()) {
                        item {
                            SkillSyncEmptyState(
                                title = if (uiState.opportunities.isEmpty()) "No Opportunities Detected" else "No Matching Opportunities",
                                description = if (uiState.opportunities.isEmpty()) {
                                    "Inbound Guardian is active and monitoring trusted communication channels for new training requirements."
                                } else {
                                    "No commercial opportunities currently match the '$selectedFilter' status filter."
                                },
                            )
                        }
                    }

                    // Opportunity Items
                    items(filteredOpportunities, key = { it.id }) { opp ->
                        val cardSeverity = when {
                            opp.isCritical -> Severity.Critical
                            opp.isHighOpportunity -> Severity.Warning
                            else -> null
                        }

                        SkillSyncCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressable { onOpportunityClick(opp.id) },
                            severity = cardSeverity,
                        ) {
                            // Header Row: Course title + Urgency tag
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = opp.course.ifBlank { opp.courseCode },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = sk.frost,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (opp.isCritical) {
                                        ToneChip("CRITICAL", tint = sk.crit)
                                    } else if (opp.isHighOpportunity) {
                                        ToneChip("HIGH PRIORITY", tint = sk.warn)
                                    }
                                    val statusTint = when (opp.status.lowercase()) {
                                        "accepted" -> sk.good
                                        "declined" -> sk.crit
                                        "snoozed" -> sk.amber
                                        else -> sk.sky
                                    }
                                    ToneChip(opp.status.uppercase(), tint = statusTint)
                                }
                            }

                            // Requester & Source Community
                            val senderInfo = listOfNotNull(
                                opp.sender.takeIf { it.isNotBlank() },
                                opp.sourceApp.takeIf { it.isNotBlank() },
                                opp.sourceGroup.takeIf { it.isNotBlank() },
                            ).joinToString(" · ")
                            if (senderInfo.isNotBlank()) {
                                Text(
                                    text = "From: $senderInfo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = sk.subText,
                                )
                            }

                            // Location & Dates
                            val locationStr = listOf(opp.location, opp.country).filter { it.isNotBlank() }.joinToString(", ")
                            if (locationStr.isNotBlank() || opp.datesStart.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (locationStr.isNotBlank()) {
                                        Text(
                                            text = locationStr,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = sk.bodyText,
                                        )
                                    }
                                    if (opp.datesStart.isNotBlank()) {
                                        val dateRange = if (opp.datesEnd.isNotBlank() && opp.datesEnd != opp.datesStart) {
                                            "${opp.datesStart} – ${opp.datesEnd}"
                                        } else opp.datesStart
                                        Text(
                                            text = dateRange,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = sk.cyan,
                                        )
                                    }
                                }
                            }

                            // Match Score & Recommendation
                            val score = opp.skillMatchScore
                            val recommendationLabel = when {
                                score >= 90 -> "STRONGLY ACCEPT"
                                score >= 75 -> "ACCEPT WITH PREPARATION"
                                score >= 60 -> "REVIEW QUICKLY"
                                score >= 40 -> "HIGH PREPARATION"
                                else -> "LOW MATCH"
                            }
                            val scoreTint = when {
                                score >= 90 -> sk.good
                                score >= 75 -> sk.cyan
                                score >= 60 -> sk.amber
                                else -> sk.crit
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ToneChip(
                                    text = "$recommendationLabel · $score%",
                                    tint = scoreTint,
                                )
                                if (opp.preparationHours.isNotBlank() && opp.preparationHours != "0") {
                                    Text(
                                        text = "Prep: ${opp.preparationHours}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = sk.amber,
                                    )
                                }
                            }

                            // Action Bar
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
                                    colors = ButtonDefaults.buttonColors(containerColor = sk.good),
                                    shape = RoundedCornerShape(Radii.chip),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier.pressable {},
                                ) {
                                    Text(
                                        "ACCEPT",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = sk.frost,
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        viewModel.declineOpportunity(opp.id)
                                        onDecline(opp.id)
                                    },
                                    shape = RoundedCornerShape(Radii.chip),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = sk.crit),
                                    border = BorderStroke(1.dp, sk.crit.copy(alpha = 0.5f)),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier.pressable {},
                                ) {
                                    Text(
                                        "DECLINE",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }

                                Spacer(Modifier.weight(1f))

                                TextButton(
                                    onClick = { onOpportunityClick(opp.id) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    modifier = Modifier.pressable {},
                                ) {
                                    Text(
                                        "DETAILS →",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = sk.sky,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
