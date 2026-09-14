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
                SkillSyncSection(title = "6. Global States: Info Banner, Empty, Error & Partial") {
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
                    Spacer(Modifier.height(Space.md))
                    SkillSyncPartialDataState(
                        "Demand loaded. Availability source is temporarily unavailable — showing workload only.",
                    )
                }

                // 7. D1 — Metric family: sparkline, progress, hero ring
                SkillSyncSection(title = "7. Metric Family: Sparkline, Progress, Hero Ring") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
                        HeroRing(value = 82, modifier = Modifier.size(72.dp))
                        HeroRing(value = null, modifier = Modifier.size(72.dp))
                    }
                    Spacer(Modifier.height(Space.md))
                    Text("Multi-point trend", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.skill.labelText)
                    MetricSparkline(values = listOf(62f, 58f, 70f, 66f, 80f), modifier = Modifier.fillMaxWidth().height(28.dp))
                    Spacer(Modifier.height(Space.sm))
                    Text("Single point — renders nothing, not a fake flat line", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.skill.labelText)
                    MetricSparkline(values = listOf(70f), modifier = Modifier.fillMaxWidth().height(28.dp))
                    Spacer(Modifier.height(Space.md))
                    MetricProgress(fraction = 0.46f, tint = Severity.Warning.tint())
                }

                // 8. D1 — Action row
                SkillSyncSection(title = "8. Action Row") {
                    ActionRow(
                        title = "Unstaffed: DP-700T00",
                        supportingText = "Open batch 01 Oct needs a trainer.",
                        metadata = "Due 2026-10-01",
                        tint = Severity.Critical.tint(),
                        primaryActionLabel = "Communicate",
                        onPrimaryAction = {},
                        onClick = {},
                    )
                    Spacer(Modifier.height(Space.sm))
                    ActionRow(title = "Minimal row — title only")
                }

                // 9. D1 — Timeline
                SkillSyncSection(title = "9. Timeline") {
                    Column {
                        TimelineItem(
                            title = "Demand raised", supportingText = "By allocation desk", timestamp = "10 Sep",
                            tint = MaterialTheme.skill.sky, isFirst = true,
                        )
                        TimelineItem(
                            title = "2 candidates matched", supportingText = "Both partial-fit", timestamp = "12 Sep",
                            tint = MaterialTheme.skill.sky,
                        )
                        TimelineItem(
                            title = "Awaiting your review", timestamp = "Today",
                            tint = Severity.Warning.tint(), isLast = true,
                        )
                    }
                }

                // 10. D1 — Segmented selector
                SkillSyncSection(title = "10. Segmented Selector") {
                    var sel by remember { mutableStateOf("BRIEF") }
                    SegmentedSelector(
                        options = listOf("BRIEF" to "Briefing", "QUEUE" to "Action queue"),
                        selected = sel,
                        onSelect = { sel = it },
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
