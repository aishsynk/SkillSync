package com.example.skillsync.feature.guardian.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.skillsync.feature.opportunity.ui.OpportunityViewModel
import com.example.skillsync.theme.*

@Composable
fun OpportunityGuardianScreen(
    managerEmail: String,
    onBack: () -> Unit,
    onTabChange: (String) -> Unit = {},
    viewModel: OpportunityViewModel = viewModel(),
) {
    val sk = MaterialTheme.skill
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
                SkillSyncTopBar(
                    title = "Opportunity Guardian",
                    subtitle = "Automated Commercial Radar Control",
                    onBack = onBack,
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // 1. GUARDIAN STATUS & LISTENER PERMISSION
                item {
                    SkillSyncCard(
                        modifier = Modifier.fillMaxWidth(),
                        severity = if (uiState.guardianConfig.enabled) Severity.Good else Severity.Warning,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = "GUARDIAN INGRESS ENGINE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = sk.labelText,
                                )
                                Text(
                                    text = if (uiState.guardianConfig.enabled) "Radar Active" else "Radar Paused",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = sk.frost,
                                )
                            }
                            ToneChip(
                                text = if (uiState.guardianConfig.enabled) "MONITORING ACTIVE" else "PAUSED",
                                tint = if (uiState.guardianConfig.enabled) sk.good else sk.warn,
                            )
                        }

                        HorizontalDivider(color = sk.cardBorder.copy(alpha = 0.5f))

                        Text(
                            text = "Selective Background Listener: Opportunity Guardian monitors only authorized commercial channels for training demand keywords. It does not access private chat history or unapproved applications.",
                            style = MaterialTheme.typography.bodySmall,
                            color = sk.subText,
                        )
                    }
                }

                // 2. TRUSTED SOURCES
                item {
                    SkillSyncCard(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "TRUSTED SENDER CHANNELS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = sk.labelText,
                            )
                            ToneChip(
                                text = "${uiState.guardianConfig.trustedSources.count { it.enabled }} Active",
                                tint = sk.cyan,
                            )
                        }

                        Text(
                            text = "Inbound messages are filtered by sender identity and group origin before parsing training requirements.",
                            style = MaterialTheme.typography.bodySmall,
                            color = sk.subText,
                        )

                        if (uiState.guardianConfig.trustedSources.isEmpty()) {
                            Text(
                                text = "Default trusted channel: Viber · Trailblazers · Gaurav Joshi",
                                style = MaterialTheme.typography.bodySmall,
                                color = sk.bodyText,
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                uiState.guardianConfig.trustedSources.forEachIndexed { idx, source ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${source.group} · ${source.sender}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = sk.frost,
                                            )
                                            Text(
                                                text = "App: ${source.app}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = sk.subText,
                                            )
                                        }
                                        Switch(
                                            checked = source.enabled,
                                            onCheckedChange = { isChecked ->
                                                viewModel.toggleTrustedSource(managerEmail, idx, isChecked)
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = sk.frost,
                                                checkedTrackColor = sk.brand,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. QUIET HOURS & ESCALATION
                item {
                    SkillSyncCard(Modifier.fillMaxWidth()) {
                        Text(
                            text = "QUIET HOURS & ESCALATION RULES",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = sk.labelText,
                        )

                        val qStart = uiState.guardianConfig.quietHoursStart.ifBlank { "23:00" }
                        val qEnd = uiState.guardianConfig.quietHoursEnd.ifBlank { "07:00" }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Standard Schedule", style = MaterialTheme.typography.bodySmall, color = sk.subText)
                            Text("$qStart – $qEnd", style = MaterialTheme.typography.bodyMedium, color = sk.cyan, fontWeight = FontWeight.Bold)
                        }

                        HorizontalDivider(color = sk.cardBorder.copy(alpha = 0.5f))

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Standard Opportunities", style = MaterialTheme.typography.bodySmall, color = sk.bodyText)
                                ToneChip(if (uiState.guardianConfig.quietHoursNormalMessages) "MUTED" else "NOTIFY", tint = sk.subText)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("High Priority Requirements", style = MaterialTheme.typography.bodySmall, color = sk.bodyText)
                                ToneChip(if (uiState.guardianConfig.quietHoursHighOpportunities) "PERSISTENT ALERT" else "NORMAL NOTIFICATION", tint = sk.amber)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Critical Immediate Demands", style = MaterialTheme.typography.bodySmall, color = sk.bodyText)
                                ToneChip(if (uiState.guardianConfig.quietHoursCriticalOpportunities) "ALARM ESCALATION" else "HIGH NOTIFICATION", tint = sk.crit)
                            }
                        }
                    }
                }

                // 4. DETECTION HEALTH SUMMARY
                item {
                    SkillSyncCard(Modifier.fillMaxWidth()) {
                        Text(
                            text = "INBOUND DETECTION HEALTH",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = sk.labelText,
                        )

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
                            SkillSyncMetric(
                                label = "Missed",
                                value = uiState.summary.missed.toString(),
                                tint = sk.amber,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                // 5. PRIVACY & DEVICE REALISM NOTICE
                item {
                    SkillSyncCard(Modifier.fillMaxWidth()) {
                        Text(
                            text = "DEVICE ARCHITECTURE & TRANSPARENCY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = sk.labelText,
                        )
                        Text(
                            text = "• Zero Accessibility Scraping: SkillSync uses the standard Android NotificationListenerService API and never injects accessibility screen-readers.\n" +
                                "• Local Evaluation First: Message text fingerprinting and deduplication are executed locally on-device.\n" +
                                "• Physical Device Requirement: Live Viber notification delivery verification requires an active physical device with the Viber app installed and permission granted.",
                            style = MaterialTheme.typography.bodySmall,
                            color = sk.subText,
                        )
                    }
                }
            }
        }
    }
}
