package com.example.skillsync.feature.opportunity.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.skillsync.R
import com.example.skillsync.theme.AuroraBackground
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.skill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpportunityDetailScreen(
    managerEmail: String,
    opportunityId: String,
    onBack: () -> Unit,
    onGenerateResponse: (String) -> Unit = {},
    viewModel: OpportunityViewModel = viewModel(),
) {
    val sk = MaterialTheme.skill
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(opportunityId) {
        viewModel.loadOpportunities(managerEmail)
    }

    val opp = uiState.opportunities.find { it.id == opportunityId }

    LaunchedEffect(opp) {
        if (opp != null) {
            viewModel.matchOpportunity(managerEmail, opp)
        }
    }

    val match = uiState.matchResult

    Box(modifier = Modifier.fillMaxSize()) {
        AuroraBackground()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("OPPORTUNITY DETAIL", color = sk.frost, fontWeight = FontWeight.Bold) },
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
            if (opp == null) {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                    item { Text("Loading opportunity details...", color = sk.subText, modifier = Modifier.padding(16.dp)) }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = sk.cardBg),
                            border = androidx.compose.foundation.BorderStroke(1.dp, sk.cardBorder),
                            shape = RoundedCornerShape(Radii.card),
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("OPPORTUNITY #SE-${opp.id}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = sk.frost)
                                Text("${opp.course} · ${opp.location}, ${opp.country}", style = MaterialTheme.typography.bodyMedium, color = sk.bodyText)
                                Text("${opp.datesStart} – ${opp.datesEnd}", style = MaterialTheme.typography.labelSmall, color = sk.cyan)
                                Text("Sender: ${opp.sender} (${opp.sourceApp} → ${opp.sourceGroup})", style = MaterialTheme.typography.bodySmall, color = sk.subText)
                                Text("Detected: ${opp.detectedAt}", style = MaterialTheme.typography.labelSmall, color = sk.labelText)
                                Text("Status: ${opp.status.uppercase()}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = sk.sky)
                            }
                        }
                    }

                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = sk.cardBg),
                            border = androidx.compose.foundation.BorderStroke(1.dp, sk.cardBorder),
                            shape = RoundedCornerShape(Radii.card),
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val score = if (match.matchScore > 0) match.matchScore else opp.skillMatchScore
                                Text("CAPABILITY MATCH", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = sk.frost)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    LinearProgressIndicator(
                                        progress = (score / 100f).coerceIn(0f, 1f),
                                        modifier = Modifier.weight(1f).height(8.dp),
                                        color = if (score >= 80) sk.good else if (score >= 50) sk.warn else sk.crit,
                                        trackColor = sk.surface3,
                                    )
                                    Text("$score%", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = sk.bodyText)
                                }
                                val verdict = match.verdict.ifBlank { opp.verdict }
                                if (verdict.isNotBlank()) Text("Verdict: $verdict", style = MaterialTheme.typography.bodySmall, color = sk.bodyText)
                                val conf = match.confidence.ifBlank { opp.confidence }
                                if (conf.isNotBlank()) Text("Confidence: $conf", style = MaterialTheme.typography.bodySmall, color = sk.subText)
                                val prep = match.preparationHours.ifBlank { opp.preparationHours }
                                if (prep.isNotBlank()) Text("Preparation: $prep", style = MaterialTheme.typography.bodySmall, color = sk.amber)
                                val gap = match.majorGap.ifBlank { opp.majorGap }
                                if (gap.isNotBlank()) Text("Major gap: $gap", style = MaterialTheme.typography.bodySmall, color = sk.crit)
                            }
                        }
                    }

                    val strong = match.strongAreas.ifEmpty { opp.strongAreas }
                    if (strong.isNotEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = sk.cardBg),
                                border = androidx.compose.foundation.BorderStroke(1.dp, sk.cardBorder),
                                shape = RoundedCornerShape(Radii.card),
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("STRONG AREAS", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = sk.good)
                                    strong.forEach { area ->
                                        Text("• $area", style = MaterialTheme.typography.bodySmall, color = sk.bodyText)
                                    }
                                }
                            }
                        }
                    }

                    val weak = match.weakAreas.ifEmpty { opp.weakAreas }
                    if (weak.isNotEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = sk.cardBg),
                                border = androidx.compose.foundation.BorderStroke(1.dp, sk.cardBorder),
                                shape = RoundedCornerShape(Radii.card),
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("PREPARATION REQUIRED", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = sk.warn)
                                    weak.forEach { area ->
                                        Text("• $area", style = MaterialTheme.typography.bodySmall, color = sk.bodyText)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = { onGenerateResponse(opp.id) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = sk.brand),
                            shape = RoundedCornerShape(Radii.chip),
                        ) {
                            Text("GENERATE RESPONSE", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = { viewModel.acceptOpportunity(opp.id) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = sk.good),
                                shape = RoundedCornerShape(Radii.chip),
                            ) {
                                Text("ACCEPT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            OutlinedButton(
                                onClick = { viewModel.declineOpportunity(opp.id) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(Radii.chip),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = sk.crit),
                                border = androidx.compose.foundation.BorderStroke(1.dp, sk.crit.copy(alpha = 0.5f)),
                            ) {
                                Text("DECLINE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { viewModel.updateDocumentStatus(opp.id, "snoozed") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(Radii.chip),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = sk.warn),
                                border = androidx.compose.foundation.BorderStroke(1.dp, sk.warn.copy(alpha = 0.5f)),
                            ) {
                                Text("SNOOZE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { viewModel.updateDocumentStatus(opp.id, "seen") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(Radii.chip),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = sk.sky),
                                border = androidx.compose.foundation.BorderStroke(1.dp, sk.sky.copy(alpha = 0.5f)),
                            ) {
                                Text("SEEN", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
