package com.example.skillsync.ui.batch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.skillsync.R
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.components.*

/** Relevance -> colour. 75%+ is green, matching the agreed banding. */
@Composable
internal fun relevanceColor(relevance: Int): Color {
    val sk = MaterialTheme.skill
    return when {
        relevance >= 75 -> sk.green
        relevance >= 50 -> sk.amber
        relevance > 0 -> sk.red
        else -> sk.subText
    }
}

@Composable
internal fun AllocationDeskContent(
    data: Map<String, Any>,
    newIds: Set<String>,
    onBatchClick: (Map<*, *>) -> Unit,
) {
    val sk = MaterialTheme.skill
    val batches = data.rows("batches")
    val summary = data.obj("summary")

    var query by remember { mutableStateOf("") }
    var onlyRelevant by remember { mutableStateOf(false) }

    val filtered = remember(batches, query, onlyRelevant) {
        batches.filter { b ->
            val q = query.trim().lowercase()
            val matchesQuery = q.isBlank() ||
                b.str("course_name").lowercase().contains(q) ||
                b.str("customer").lowercase().contains(q) ||
                b.str("delivery_mode").lowercase().contains(q) ||
                b.str("demand_id").contains(q)
            val matchesBand = !onlyRelevant || b.int("relevance") >= 75
            matchesQuery && matchesBand
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(sk.pageBg),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column {
                if (newIds.isNotEmpty()) {
                    NewBatchBanner(newIds.size)
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryPill("Total", summary?.int("total") ?: batches.size, sk.subText)
                    SummaryPill("75%+", summary?.int("high") ?: 0, sk.green)
                    SummaryPill("Partial", summary?.int("medium") ?: 0, sk.amber)
                    SummaryPill("No match", summary?.int("unmatched") ?: 0, sk.red)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search course, vendor, mode, ref", fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                FilterChip(
                    selected = onlyRelevant,
                    onClick = { onlyRelevant = !onlyRelevant },
                    label = { Text("My team can deliver (75%+)", fontSize = 11.sp) },
                )
            }
        }

        if (filtered.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (batches.isEmpty()) "No unallocated batches right now."
                        else "No batches match this filter.",
                        style = MaterialTheme.typography.bodySmall, color = sk.subText,
                    )
                }
            }
        }

        itemsIndexed(filtered) { i, b ->
            Appear(i) {
                BatchCard(b, isNew = b.str("demand_id") in newIds) { onBatchClick(b) }
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun NewBatchBanner(count: Int) {
    val sk = MaterialTheme.skill
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(sk.blue.copy(alpha = 0.13f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_inbox), null, tint = sk.blue, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            if (count == 1) "1 new batch since you last checked"
            else "$count new batches since you last checked",
            style = MaterialTheme.typography.titleSmall, color = sk.bodyText,
        )
    }
}

@Composable
private fun SummaryPill(label: String, value: Int, tint: Color) {
    Column(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.skill.cardBg).padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text("$value", style = MaterialTheme.typography.titleLarge, color = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.skill.subText)
    }
}

@Composable
internal fun BatchCard(b: Map<*, *>, isNew: Boolean, onClick: () -> Unit) {
    val sk = MaterialTheme.skill
    val relevance = b.int("relevance")
    val tint = relevanceColor(relevance)
    val candidates = b.list("candidates")

    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = sk.cardBg),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row {
            // Relevance is the primary scan signal, so it owns the leading edge.
            Box(Modifier.width(4.dp).fillMaxHeight().background(tint))
            Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isNew) {
                                Surface(color = sk.blue, shape = RoundedCornerShape(4.dp)) {
                                    Text(
                                        "NEW", style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                b.str("course_name").ifBlank { "Course not specified" },
                                style = MaterialTheme.typography.titleSmall, color = sk.bodyText,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            listOfNotNull(
                                b.str("start_date").takeIf { it.isNotBlank() }?.shortDate(),
                                b.intOrNull("days")?.let { "${it}d" },
                                b.str("delivery_mode").takeIf { it.isNotBlank() },
                                b.intOrNull("participants")?.takeIf { it > 0 }?.let { "$it pax" },
                                b.str("customer").takeIf { it.isNotBlank() },
                                b.str("customer_priority").takeIf { it.isNotBlank() }?.let { "Priority: $it" },
                                b.str("revenue_impact").takeIf { it.isNotBlank() }?.let { "Rev: $it" }
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall, color = sk.subText,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "$relevance%",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold, color = tint,
                        )
                        Text("match", style = MaterialTheme.typography.labelSmall, color = sk.subText)
                    }
                }

                if (candidates.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = sk.cardBorder)
                    Spacer(Modifier.height(7.dp))
                    candidates.take(3).forEach { c ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(5.dp).clip(RoundedCornerShape(3.dp))
                                .background(relevanceColor(c.int("match"))))
                            Spacer(Modifier.width(7.dp))
                            Text(
                                c.str("category"),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), 
                                color = relevanceColor(c.int("match")),
                                modifier = Modifier.weight(0.5f),
                            )
                            Text(
                                c.str("trainer_name"),
                                style = MaterialTheme.typography.labelMedium, color = sk.bodyText,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${c.int("match")}%",
                                style = MaterialTheme.typography.labelSmall, color = sk.subText,
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "No one on your team maps to this course.",
                        style = MaterialTheme.typography.labelSmall, color = sk.subText,
                    )
                }
            }
        }
    }
}
