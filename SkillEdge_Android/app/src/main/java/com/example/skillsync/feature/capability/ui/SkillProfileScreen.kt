package com.example.skillsync.feature.capability.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.skillsync.feature.opportunity.ui.OpportunityViewModel
import com.example.skillsync.theme.AuroraBackground
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.SkillSyncCard
import com.example.skillsync.theme.SkillSyncTopBar
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.ToneChip
import com.example.skillsync.theme.skill

@Composable
fun SkillProfileScreen(
    managerEmail: String,
    onBack: () -> Unit,
    viewModel: OpportunityViewModel = viewModel(),
) {
    val sk = MaterialTheme.skill
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(managerEmail) {
        viewModel.loadSkillProfile(managerEmail)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AuroraBackground()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                SkillSyncTopBar(
                    title = "My Skill Profile",
                    subtitle = managerEmail.ifBlank { "Trainer Capability Record" },
                    onBack = onBack,
                )
            },
        ) { padding ->
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
                                    "Profile Overview",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = sk.frost,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "${uiState.skillProfile.experienceYears} years of verified enterprise delivery experience",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = sk.subText,
                                )
                            }
                            ToneChip(
                                text = "${uiState.skillProfile.experienceYears}y Exp",
                                tint = sk.cyan,
                            )
                        }
                    }
                }

                // Certifications
                item {
                    SkillSyncCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Certifications",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = sk.frost,
                        )
                        Spacer(modifier = Modifier.height(Space.xs))
                        if (uiState.skillProfile.certifications.isEmpty()) {
                            Text(
                                "No certifications on file",
                                style = MaterialTheme.typography.bodySmall,
                                color = sk.subText,
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                uiState.skillProfile.certifications.forEach { cert ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(sk.good),
                                        )
                                        Spacer(modifier = Modifier.width(Space.sm))
                                        Text(
                                            cert,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = sk.bodyText,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Technologies
                item {
                    SkillSyncCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Technologies",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = sk.frost,
                        )
                        Spacer(modifier = Modifier.height(Space.xs))
                        if (uiState.skillProfile.technologies.isEmpty()) {
                            Text(
                                "No technologies on file",
                                style = MaterialTheme.typography.bodySmall,
                                color = sk.subText,
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Space.xs),
                            ) {
                                uiState.skillProfile.technologies.forEach { tech ->
                                    ToneChip(text = tech, tint = sk.brand)
                                }
                            }
                        }
                    }
                }

                // Courses Delivered
                item {
                    SkillSyncCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Courses Delivered",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = sk.frost,
                        )
                        Spacer(modifier = Modifier.height(Space.xs))
                        if (uiState.skillProfile.coursesDelivered.isEmpty()) {
                            Text(
                                "No courses delivered on file",
                                style = MaterialTheme.typography.bodySmall,
                                color = sk.subText,
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                uiState.skillProfile.coursesDelivered.forEach { course ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(sk.cyan),
                                        )
                                        Spacer(modifier = Modifier.width(Space.sm))
                                        Text(
                                            course,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = sk.bodyText,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Labs / Projects
                item {
                    SkillSyncCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Labs / Projects",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = sk.frost,
                        )
                        Spacer(modifier = Modifier.height(Space.xs))
                        if (uiState.skillProfile.labsProjects.isEmpty()) {
                            Text(
                                "No labs or projects on file",
                                style = MaterialTheme.typography.bodySmall,
                                color = sk.subText,
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                uiState.skillProfile.labsProjects.forEach { project ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(sk.amber),
                                        )
                                        Spacer(modifier = Modifier.width(Space.sm))
                                        Text(
                                            project,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = sk.bodyText,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Confidence by Topic (Evidence-Backed, No ASCII bars)
                item {
                    SkillSyncCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Confidence by Topic",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = sk.frost,
                        )
                        Spacer(modifier = Modifier.height(Space.xs))
                        if (uiState.skillProfile.confidenceByTopic.isEmpty()) {
                            Text(
                                "Insufficient evidence — no topic confidence data recorded.",
                                style = MaterialTheme.typography.bodySmall,
                                color = sk.subText,
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                                uiState.skillProfile.confidenceByTopic.forEach { (topic, confidence) ->
                                    val pct = (confidence * 100).toInt()
                                    val evidenceTag = when {
                                        confidence >= 0.85 -> "DELIVERED"
                                        confidence >= 0.70 -> "CERTIFIED"
                                        confidence >= 0.50 -> "WORKED_WITH"
                                        confidence > 0.0 -> "LEARNED"
                                        else -> "INSUFFICIENT_EVIDENCE"
                                    }
                                    val tint = when (evidenceTag) {
                                        "DELIVERED" -> sk.good
                                        "CERTIFIED" -> sk.cyan
                                        "WORKED_WITH" -> sk.brand
                                        "LEARNED" -> sk.amber
                                        else -> sk.subText
                                    }

                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(
                                                topic,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = sk.bodyText,
                                                fontWeight = FontWeight.Medium,
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                if (confidence > 0.0) {
                                                    Text(
                                                        "$pct%",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = tint,
                                                        fontWeight = FontWeight.Bold,
                                                    )
                                                    ToneChip(text = evidenceTag, tint = tint)
                                                } else {
                                                    Text(
                                                        "Insufficient evidence",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = sk.warn,
                                                    )
                                                }
                                            }
                                        }
                                        if (confidence > 0.0) {
                                            LinearProgressIndicator(
                                                progress = { confidence.toFloat().coerceIn(0f, 1f) },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(6.dp)
                                                    .clip(RoundedCornerShape(Radii.chip)),
                                                color = tint,
                                                trackColor = sk.cardBorder,
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
    }
}
