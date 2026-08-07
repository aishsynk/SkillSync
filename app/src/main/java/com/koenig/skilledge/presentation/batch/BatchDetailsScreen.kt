package com.koenig.skilledge.presentation.batch

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koenig.skilledge.core.theme.AmberSecondary
import com.koenig.skilledge.core.theme.SuccessGreen
import com.koenig.skilledge.core.theme.TealPrimary
import com.koenig.skilledge.domain.models.BatchDetailsData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchDetailsScreen(
    batchDetails: BatchDetailsData,
    onBackClick: () -> Unit = {},
    onAssignTrainerClick: (String) -> Unit = {}
) {
    var isPaxExpanded by remember { mutableStateOf(true) }
    var isLogisticsExpanded by remember { mutableStateOf(false) }
    var isFinancialsExpanded by remember { mutableStateOf(false) }
    var isSyllabusExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Batch Details Modernized", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Compact, accordion-driven intelligence view", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp)
        ) {
            // Batch Summary Card (Phase 5 Core Requirement)
            item {
                BatchSummaryCard(batchDetails = batchDetails)
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Accordion 1: Customer Details & Pax Roster
            item {
                ExpandableAccordion(
                    title = "👥 Customer Details & Student Roster (${batchDetails.paxCount} Pax)",
                    isExpanded = isPaxExpanded,
                    onToggle = { isPaxExpanded = !isPaxExpanded }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Enrolled Participants (${batchDetails.paxRoster.size} Listed)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        batchDetails.paxRoster.forEach { student ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(student.studentName, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text(student.studentEmail, fontSize = 11.sp, color = TealPrimary)
                            }
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Accordion 2: Logistics & Delivery Information
            item {
                ExpandableAccordion(
                    title = "🚚 Logistics & Recording Links",
                    isExpanded = isLogisticsExpanded,
                    onToggle = { isLogisticsExpanded = !isLogisticsExpanded }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        DetailRow("Location / Venue", batchDetails.location)
                        DetailRow("Daily Schedule", "${batchDetails.startTime} – ${batchDetails.endTime}")
                        DetailRow("Sales Confirmation ID", batchDetails.scid)
                        if (!batchDetails.recordingLink.isNullOrEmpty()) {
                            DetailRow("Session Recording", batchDetails.recordingLink!!, isLink = true)
                        } else {
                            DetailRow("Session Recording", "Available post live delivery completion")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Accordion 3: Financial Information
            item {
                ExpandableAccordion(
                    title = "💰 Financial & Contract Summary",
                    isExpanded = isFinancialsExpanded,
                    onToggle = { isFinancialsExpanded = !isFinancialsExpanded }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        DetailRow("Total Batch Fee", batchDetails.totalFee)
                        DetailRow("Contract Currency", batchDetails.currency)
                        DetailRow("CSM Representative", batchDetails.csmName)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Accordion 4: Course Syllabus & Supporting Info
            item {
                ExpandableAccordion(
                    title = "📚 Course Syllabus & Prerequisites",
                    isExpanded = isSyllabusExpanded,
                    onToggle = { isSyllabusExpanded = !isSyllabusExpanded }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        DetailRow("Qubits Benchmark Score", "Min 75% Required")
                        DetailRow("Official Course TOC", batchDetails.tocUrl, isLink = true)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Action Button
            item {
                Button(
                    onClick = { onAssignTrainerClick(batchDetails.assignmentId) },
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Confirm Trainer Assignment", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun BatchSummaryCard(batchDetails: BatchDetailsData) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = batchDetails.courseName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = TealPrimary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = batchDetails.deliveryMode,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TealPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Clean Date Range Representation (Avoids Repetitive Dates)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DateRange, contentDescription = "Date", tint = TealPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${batchDetails.startDate}  –  ${batchDetails.endDate}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = "Location", tint = AmberSecondary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Region: ${batchDetails.region}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ExpandableAccordion(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Toggle",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column {
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    content()
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, isLink: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isLink) TealPrimary else MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun String?.isNull_or_empty(): Boolean = this.isNullOrEmpty()
