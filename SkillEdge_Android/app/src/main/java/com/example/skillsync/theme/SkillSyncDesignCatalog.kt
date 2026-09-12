package com.example.skillsync.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.skillsync.R

/**
 * SkillSync Design System Visual Catalog & Validation Surface.
 *
 * Provides inspectable previews for all Wave 1 primitives and global states
 * before mass adoption across the 31 application screens.
 */

@Composable
fun SkillSyncDesignCatalog() {
    SkillSyncTheme {
        SkillSyncScreen(
            topBar = {
                SkillSyncTopBar(
                    title = "Design Foundation Catalog",
                    subtitle = "SkillSync V4 Specification",
                    onBack = {},
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(Space.lg),
                verticalArrangement = Arrangement.spacedBy(Space.xl),
            ) {
                // 1. Page Header Preview
                SkillSyncSection(title = "1. Page Header & Hierarchy") {
                    SkillSyncPageHeader(
                        title = "Demand Fulfillment Desk",
                        contextInfo = "7 open enterprise client batches require certified trainer allocation",
                        tag = "Operations · Wave 1",
                        primaryAction = {
                            SkillSyncPrimaryButton(
                                text = "New Demand",
                                onClick = {},
                            )
                        }
                    )
                }

                // 2. Cards & Metrics
                SkillSyncSection(title = "2. Cards, Metrics & Severity Edge") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Space.md),
                    ) {
                        SkillSyncCard(
                            modifier = Modifier.weight(1f),
                            severity = Severity.Good,
                        ) {
                            SkillSyncMetric(
                                value = "94.2%",
                                label = "Staffing Rate",
                                delta = "+2.4%",
                                deltaGood = true,
                                size = FigureSize.Medium,
                            )
                        }
                        SkillSyncCard(
                            modifier = Modifier.weight(1f),
                            severity = Severity.Warning,
                        ) {
                            SkillSyncMetric(
                                value = "3",
                                label = "At Risk Batches",
                                delta = "+1 SLA",
                                deltaGood = false,
                                size = FigureSize.Medium,
                            )
                        }
                    }
                }

                // 3. Chips & Status
                SkillSyncSection(title = "3. Status Chips & Filter Pills") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        SkillSyncStatusChip(text = "CRITICAL SLA", severity = Severity.Critical)
                        SkillSyncStatusChip(text = "CONFIRMED", severity = Severity.Good)
                        SkillSyncStatusChip(text = "PENDING REVIEW", severity = Severity.Warning)
                        SkillSyncStatusChip(text = "INFO", severity = Severity.Info)
                    }
                    Spacer(Modifier.height(Space.sm))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        SkillSyncChip(text = "All Batches", selected = true, onClick = {}, count = 24)
                        SkillSyncChip(text = "Unallocated", selected = false, onClick = {}, count = 5)
                        SkillSyncChip(text = "In-Flight", selected = false, onClick = {}, count = 19)
                    }
                }

                // 4. Buttons & Interactive Inputs
                SkillSyncSection(title = "4. Action Buttons & Input Search") {
                    var query by remember { mutableStateOf("Azure Solutions Architect") }
                    SkillSyncSearchBar(
                        query = query,
                        onQueryChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Space.sm))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Space.md),
                    ) {
                        SkillSyncPrimaryButton(
                            text = "Confirm Assignment",
                            onClick = {},
                            modifier = Modifier.weight(1f),
                        )
                        SkillSyncSecondaryButton(
                            text = "Dismiss",
                            onClick = {},
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // 5. Scannable List Items
                SkillSyncSection(title = "5. List Items & Rows") {
                    SkillSyncListItem(
                        title = "AZ-104: Microsoft Azure Administrator",
                        subtitle = "5 Days · VILT · EMEA Timezone · 18 Attendees",
                        leadingIcon = R.drawable.ic_inbox,
                        trailingValue = "98% Match",
                        trailingBadge = {
                            SkillSyncStatusChip(text = "READY", severity = Severity.Good)
                        },
                        onClick = {},
                    )
                    Spacer(Modifier.height(Space.sm))
                    SkillSyncListItem(
                        title = "AWS Certified Solutions Architect",
                        subtitle = "Starts Monday · Travel Required · London, UK",
                        leadingIcon = R.drawable.ic_calendar,
                        trailingValue = "34% Match",
                        trailingBadge = {
                            SkillSyncStatusChip(text = "REVIEW REQ", severity = Severity.Warning)
                        },
                        onClick = {},
                    )
                }

                // 6. Global Operational States
                SkillSyncSection(title = "6. Global States: Info Banner, Empty & Error") {
                    SkillSyncInfoBanner(
                        title = "Capacity Alert",
                        message = "Cloud practice utilization exceeds 92% for the next 3 weeks.",
                        severity = Severity.Warning,
                    )
                    Spacer(Modifier.height(Space.md))
                    SkillSyncEmptyState(
                        title = "No unallocated batches",
                        description = "All incoming client training demands have been fulfilled for this sprint.",
                        actionLabel = "Refresh Schedule",
                        onAction = {},
                    )
                }
            }
        }
    }
}

@Preview(name = "SkillSync Design Catalog Preview", showBackground = true, backgroundColor = 0xFF0B0F19)
@Composable
fun PreviewSkillSyncDesignCatalog() {
    SkillSyncDesignCatalog()
}
