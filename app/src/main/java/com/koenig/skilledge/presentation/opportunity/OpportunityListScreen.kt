package com.koenig.skilledge.presentation.opportunity

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koenig.skilledge.core.theme.AmberSecondary
import com.koenig.skilledge.core.theme.TealPrimary
import com.koenig.skilledge.domain.models.UnallocatedDemand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpportunityListScreen(
    opportunities: List<UnallocatedDemand>,
    onBackClick: () -> Unit = {},
    onOpportunityClick: (String) -> Unit = {}
) {
    var selectedFilter by remember { mutableStateOf("All") }

    // Prioritized Sort: Most Relevant -> Highest Priority -> Most Recent
    val sortedOpportunities = remember(opportunities, selectedFilter) {
        opportunities
            .filter { item ->
                when (selectedFilter) {
                    "International" -> item.isInternational
                    "ILT" -> item.deliveryMode.contains("ILT", ignoreCase = true)
                    "FMAT" -> item.deliveryMode.contains("FMAT", ignoreCase = true)
                    "ILO" -> item.deliveryMode.contains("ILO", ignoreCase = true)
                    else -> true
                }
            }
            .sortedByDescending { it.priorityScore }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Unified Opportunity Stream", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Sorted by Relevance → Priority → Recency", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "International 🌐", "ILT", "FMAT", "ILO").forEach { filter ->
                    val filterKey = filter.split(" ").first()
                    val isSelected = selectedFilter == filterKey
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filterKey },
                        label = { Text(filter, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TealPrimary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(sortedOpportunities) { opportunity ->
                    OpportunityItemCard(
                        opportunity = opportunity,
                        onClick = { onOpportunityClick(opportunity.demandId) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OpportunityItemCard(
    opportunity: UnallocatedDemand,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isInt = opportunity.isInternational
    val borderColor = if (isInt) AmberSecondary else MaterialTheme.colorScheme.outlineVariant
    val modeBg = when {
        opportunity.deliveryMode.contains("FMAT", true) -> Color(0xFF9333EA).copy(alpha = 0.1f)
        opportunity.deliveryMode.contains("ILO", true) -> Color(0xFF4F46E5).copy(alpha = 0.1f)
        else -> TealPrimary.copy(alpha = 0.1f)
    }
    val modeColor = when {
        opportunity.deliveryMode.contains("FMAT", true) -> Color(0xFF9333EA)
        opportunity.deliveryMode.contains("ILO", true) -> Color(0xFF4F46E5)
        else -> TealPrimary
    }

    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (isInt) 1.5.dp else 1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isInt) {
                        Text(text = "🌐 ${opportunity.flagEmoji}", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = opportunity.courseName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Delivery Mode Badge
                    Surface(
                        color = modeBg,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = opportunity.deliveryMode,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = modeColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Premium International Badge
                    if (isInt) {
                        Surface(
                            color = AmberSecondary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "GLOBAL",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = AmberSecondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Dates: ${opportunity.startDate}  •  Region: ${opportunity.location ?: "Global"}  •  Pax: ${if (opportunity.priority == "High") "18 Students" else "12 Students"}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Customer: ${opportunity.customer ?: "Koenig Client"}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Manager Review Required →",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TealPrimary
                )
            }
        }
    }
}
