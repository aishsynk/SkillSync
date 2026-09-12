package com.example.skillsync.feature.opportunity.ui

import androidx.compose.foundation.BorderStroke
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
import com.example.skillsync.theme.*

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

    LaunchedEffect(opp?.id) {
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
                SkillSyncTopBar(
                    title = "Decision Hub",
                    subtitle = if (opp != null) "Opportunity #SE-${opp.id}" else "Opportunity Detail",
                    onBack = onBack,
                )
            },
        ) { padding ->
            if (opp == null) {
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
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // SECTION 1: Inbound Requirement & Commercial Context
                    item {
                        SkillSyncCard(
                            modifier = Modifier.fillMaxWidth(),
                            severity = when {
                                opp.isCritical -> Severity.Critical
                                opp.isHighOpportunity -> Severity.Warning
                                else -> null
                            },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "INBOUND REQUIREMENT",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = sk.labelText,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (opp.isCritical) {
                                        ToneChip("CRITICAL", tint = sk.crit)
                                    } else if (opp.isHighOpportunity) {
                                        ToneChip("HIGH URGENCY", tint = sk.warn)
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

                            Text(
                                text = opp.course.ifBlank { opp.courseCode.ifBlank { opp.title } },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = sk.frost,
                            )

                            if (opp.courseCode.isNotBlank() && opp.courseCode != opp.course) {
                                Text(
                                    text = "Course Code: ${opp.courseCode}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = sk.cyan,
                                )
                            }

                            HorizontalDivider(color = sk.cardBorder.copy(alpha = 0.5f))

                            // Logistics & Location
                            val locationDetails = listOf(opp.location, opp.country).filter { it.isNotBlank() }.joinToString(", ")
                            if (locationDetails.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("Location", style = MaterialTheme.typography.labelSmall, color = sk.subText)
                                    Text(locationDetails, style = MaterialTheme.typography.bodySmall, color = sk.bodyText, fontWeight = FontWeight.Medium)
                                }
                            }

                            // Dates & Schedule
                            if (opp.datesStart.isNotBlank()) {
                                val datesDisplay = if (opp.datesEnd.isNotBlank() && opp.datesEnd != opp.datesStart) {
                                    "${opp.datesStart} – ${opp.datesEnd}"
                                } else opp.datesStart
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("Schedule Window", style = MaterialTheme.typography.labelSmall, color = sk.subText)
                                    Text(datesDisplay, style = MaterialTheme.typography.bodySmall, color = sk.cyan, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            // Sender & Inbound Channel
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Requester", style = MaterialTheme.typography.labelSmall, color = sk.subText)
                                Text("${opp.sender} (${opp.sourceApp})", style = MaterialTheme.typography.bodySmall, color = sk.bodyText)
                            }

                            // Document Handling Status
                            val docStatus = opp.documentStatus.lowercase()
                            if (docStatus.isNotBlank() && docStatus != "none") {
                                HorizontalDivider(color = sk.cardBorder.copy(alpha = 0.5f))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("Curriculum Document", style = MaterialTheme.typography.labelSmall, color = sk.subText)
                                    when (docStatus) {
                                        "awaiting_user_share", "mentioned" -> ToneChip("AWAITING SHARE", tint = sk.amber)
                                        "analysing" -> ToneChip("ANALYSING SYLLABUS", tint = sk.cyan)
                                        "analysed" -> ToneChip("ANALYSED", tint = sk.good)
                                        "failed" -> ToneChip("ANALYSIS FAILED", tint = sk.warn)
                                        else -> ToneChip(docStatus.uppercase(), tint = sk.subText)
                                    }
                                }
                            }
                        }
                    }

                    // SECTION 2: Capability Match & Recommendation Band
                    item {
                        val score = if (match.matchScore > 0) match.matchScore else opp.skillMatchScore
                        val recommendationBand = when {
                            score >= 90 -> "STRONGLY ACCEPT"
                            score >= 75 -> "ACCEPT WITH PREPARATION"
                            score >= 60 -> "REVIEW QUICKLY"
                            score >= 40 -> "HIGH PREPARATION"
                            score > 0 -> "NOT RECOMMENDED"
                            else -> "INSUFFICIENT EVIDENCE"
                        }
                        val bandTint = when {
                            score >= 90 -> sk.good
                            score >= 75 -> sk.cyan
                            score >= 60 -> sk.amber
                            score >= 40 -> sk.warn
                            else -> sk.crit
                        }

                        SkillSyncCard(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "CAPABILITY MATCH",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = sk.labelText,
                                )
                                ToneChip(
                                    text = recommendationBand,
                                    tint = bandTint,
                                )
                            }

                            // Score & Bar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                LinearProgressIndicator(
                                    progress = { (score / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(10.dp),
                                    color = bandTint,
                                    trackColor = sk.surface3,
                                )
                                Text(
                                    text = "$score%",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = bandTint,
                                )
                            }

                            val verdict = match.verdict.ifBlank { opp.verdict }
                            if (verdict.isNotBlank()) {
                                Text(
                                    text = "Assessment: $verdict",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = sk.bodyText,
                                )
                            }

                            val confidence = match.confidence.ifBlank { opp.confidence }
                            val prepHours = match.preparationHours.ifBlank { opp.preparationHours }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                if (confidence.isNotBlank()) {
                                    Text(
                                        text = "Confidence: $confidence",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = sk.subText,
                                    )
                                }
                                if (prepHours.isNotBlank() && prepHours != "0") {
                                    Text(
                                        text = "Estimated Preparation: $prepHours",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = sk.amber,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }

                    // SECTION 3: Evidence & Gap Breakdown
                    val strongList = match.strongAreas.ifEmpty { opp.strongAreas }
                    val weakList = match.weakAreas.ifEmpty { opp.weakAreas }
                    val majorGap = match.majorGap.ifBlank { opp.majorGap }

                    item {
                        SkillSyncCard(Modifier.fillMaxWidth()) {
                            Text(
                                text = "EVIDENCE & PREPARATION ANALYSIS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = sk.labelText,
                            )

                            if (strongList.isNotEmpty()) {
                                Text(
                                    text = "Verified Capability Evidence",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = sk.good,
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    strongList.forEach { area ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = area,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = sk.bodyText,
                                                modifier = Modifier.weight(1f),
                                            )
                                            ToneChip("EVIDENCE VERIFIED", tint = sk.good)
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "No direct verified delivery or certification record on file. Absence of proof is not assumed as delivery readiness.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = sk.subText,
                                )
                            }

                            if (weakList.isNotEmpty() || majorGap.isNotBlank()) {
                                HorizontalDivider(color = sk.cardBorder.copy(alpha = 0.5f))
                                Text(
                                    text = "Preparation Areas & Gaps",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = sk.warn,
                                )
                                if (majorGap.isNotBlank()) {
                                    Text(
                                        text = "Critical Gap: $majorGap",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = sk.crit,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    weakList.forEach { area ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = area,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = sk.bodyText,
                                                modifier = Modifier.weight(1f),
                                            )
                                            ToneChip("PREPARATION NEEDED", tint = sk.amber)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // SECTION 4: Decision & Communication Actions
                    item {
                        SkillSyncCard(Modifier.fillMaxWidth()) {
                            Text(
                                text = "MANAGER DECISION & RESPONSE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = sk.labelText,
                            )

                            // Communication Response Button
                            Button(
                                onClick = { onGenerateResponse(opp.id) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = sk.brand),
                                shape = RoundedCornerShape(Radii.chip),
                            ) {
                                Text(
                                    "GENERATE TAILORED RESPONSE",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = sk.frost,
                                )
                            }

                            HorizontalDivider(color = sk.cardBorder.copy(alpha = 0.5f))

                            // Decision Action Grid
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
                                    Text(
                                        "ACCEPT",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = sk.frost,
                                    )
                                }

                                OutlinedButton(
                                    onClick = { viewModel.declineOpportunity(opp.id) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(Radii.chip),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = sk.crit),
                                    border = BorderStroke(1.dp, sk.crit.copy(alpha = 0.5f)),
                                ) {
                                    Text(
                                        "DECLINE",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }

                                OutlinedButton(
                                    onClick = { viewModel.updateDocumentStatus(opp.id, "snoozed") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(Radii.chip),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = sk.warn),
                                    border = BorderStroke(1.dp, sk.warn.copy(alpha = 0.5f)),
                                ) {
                                    Text(
                                        "SNOOZE",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }

                                OutlinedButton(
                                    onClick = { viewModel.updateDocumentStatus(opp.id, "seen") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(Radii.chip),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = sk.sky),
                                    border = BorderStroke(1.dp, sk.sky.copy(alpha = 0.5f)),
                                ) {
                                    Text(
                                        "SEEN",
                                        style = MaterialTheme.typography.labelSmall,
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
