package com.example.skillsync.feature.capability.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.skillsync.feature.opportunity.ui.OpportunityViewModel
import com.example.skillsync.theme.AuroraBackground
import com.example.skillsync.theme.SkillSyncCard
import com.example.skillsync.theme.SkillSyncTopBar
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.ToneChip
import com.example.skillsync.theme.skill

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
        val displayName = remember(managerEmail) {
            managerEmail.substringBefore("@").replace(".", " ").split(" ")
                .filter { it.isNotBlank() }
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                .ifBlank { "Capability Graph" }
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                SkillSyncTopBar(
                    title = "Capability Graph",
                    subtitle = displayName,
                    onBack = onBack,
                )
            },
        ) { padding ->
            val sk = MaterialTheme.skill
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.md),
                verticalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                item {
                    SkillSyncCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(
                                    displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = sk.frost,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "Courseware & Technology Capability Network",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = sk.subText,
                                )
                            }
                            ToneChip(
                                text = "${graph.skills.size} Skills",
                                tint = sk.cyan,
                            )
                        }
                    }
                }

                // Certified
                item {
                    SkillSyncCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Certified Courses",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = sk.frost,
                            )
                            ToneChip(text = "${graph.certified.size}", tint = sk.good)
                        }
                        Spacer(modifier = Modifier.height(Space.xs))
                        if (graph.certified.isEmpty()) {
                            Text(
                                "No verified certifications recorded in RMS.",
                                style = MaterialTheme.typography.bodySmall,
                                color = sk.subText,
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                graph.certified.forEach { node ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            "${node.name} (${node.code})",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = sk.bodyText,
                                            modifier = Modifier.weight(1f),
                                        )
                                        ToneChip(text = "Level ${node.level}", tint = sk.good)
                                    }
                                }
                            }
                        }
                    }
                }

                // Delivered
                item {
                    SkillSyncCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Delivered Courses",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = sk.frost,
                            )
                            ToneChip(text = "${graph.delivered.size}", tint = sk.cyan)
                        }
                        Spacer(modifier = Modifier.height(Space.xs))
                        if (graph.delivered.isEmpty()) {
                            Text(
                                "No course deliveries recorded.",
                                style = MaterialTheme.typography.bodySmall,
                                color = sk.subText,
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                graph.delivered.forEach { node ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            "${node.name} (${node.code})",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = sk.bodyText,
                                            modifier = Modifier.weight(1f),
                                        )
                                        ToneChip(text = "${node.count} delivered", tint = sk.cyan)
                                    }
                                }
                            }
                        }
                    }
                }

                // Built
                item {
                    SkillSyncCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Built Content & Labs",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = sk.frost,
                            )
                            ToneChip(text = "${graph.built.size}", tint = sk.brand)
                        }
                        Spacer(modifier = Modifier.height(Space.xs))
                        if (graph.built.isEmpty()) {
                            Text(
                                "No built course assets or labs recorded.",
                                style = MaterialTheme.typography.bodySmall,
                                color = sk.subText,
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                graph.built.forEach { node ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            "${node.name} (${node.code})",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = sk.bodyText,
                                            modifier = Modifier.weight(1f),
                                        )
                                        ToneChip(text = "Level ${node.level}", tint = sk.brand)
                                    }
                                }
                            }
                        }
                    }
                }

                // Skills & Gaps
                item {
                    SkillSyncCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Skill Assessment & Gaps",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = sk.frost,
                        )
                        Spacer(modifier = Modifier.height(Space.xs))
                        if (graph.skills.isEmpty()) {
                            Text(
                                "No skill assessment points mapped.",
                                style = MaterialTheme.typography.bodySmall,
                                color = sk.subText,
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                graph.skills.forEach { skill ->
                                    val (statusLabel, statusTint) = when {
                                        skill.gap -> "GAP" to sk.warn
                                        skill.moderate -> "MODERATE" to sk.amber
                                        else -> "STRONG" to sk.good
                                    }
                                    val pct = (skill.confidence * 100).toInt()
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                skill.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = sk.bodyText,
                                                fontWeight = FontWeight.Medium,
                                            )
                                            Text(
                                                "Confidence: $pct% · Strength: ${skill.strength.ifBlank { "Unrated" }}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = sk.subText,
                                            )
                                        }
                                        ToneChip(text = statusLabel, tint = statusTint)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
