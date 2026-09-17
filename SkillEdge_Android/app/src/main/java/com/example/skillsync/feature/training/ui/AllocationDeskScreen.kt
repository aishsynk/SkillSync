package com.example.skillsync.feature.training.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import Button
import ButtonDefaults
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import Alignment
import Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import FontWeight
import TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.skillsync.R
import com.example.skillsync.theme.Figure
import com.example.skillsync.theme.FigureSize
import com.example.skillsync.theme.SectionHeading
import com.example.skillsync.theme.ToneChip
import com.example.skillsync.core.ui.ShimmerBox
import com.example.skillsync.feature.home.SearchField
import com.example.skillsync.theme.SkillCard
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.pressable
import com.example.skillsync.theme.accentGlass
import com.example.skillsync.theme.glassSurface
import com.example.skillsync.theme.skill
import com.example.skillsync.core.ui.*
import Text
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import androidx.compose.foundation.lazy.items
import BorderStroke
import Card
import CardDefaults
import FontWeight
import TextOverflow


impor
    val sk = MaterialTheme.skill
    val mode = b.str("delivery_mode").uppercase()
    val pax = b.intOrNull("participants") ?: 0
    val international = b.bool("is_international")
    
    val city = b.str("city")
    val country = b.str("country")
    val loc = b.str("location")
    val location = when {
        city.isNotBlank() && country.isNotBlank() -> ", "
        loc.isNotBlank() && country.isNotBlank() -> ", "
        city.isNotBlank() -> city
        country.isNotBlank() -> country
        loc.isNotBlank() -> loc
        else -> if (mode.contains("ILO") || mode.contains("VIRTUAL")) "Remote / Virtual" else "Location not provided"
    }

    val state = when {
        blocked -> PlanState("BLOCKED", "Required capability not currently covered", sk.warn, R.drawable.ic_alert)
        b.str("coverage_status") == "Best Match" -> PlanState("READY TO ALLOCATE", "Allocation pending", sk.good, R.drawable.ic_check)
        else -> PlanState("NEEDS REVIEW", "Coverage not yet confirmed", sk.sky, R.drawable.ic_flag)
    }

    val accentBase = when (categoryTheme) { "international" -> sk.azure; "national" -> sk.teal; else -> sk.indigo }
    val accentLight = when (categoryTheme) { "international" -> sk.cyan; "national" -> sk.emerald; else -> sk.violet }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        colors = CardDefaults.cardColors(containerColor = sk.surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isNew) accentBase else sk.cardBorder)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().background(accentBase.copy(alpha = 0.15f)).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_globe), null, tint = accentLight, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    val ribbonText = when(categoryTheme) {
                        "international" -> "INTERNATIONAL "
                        "national" -> "INDIA NATIONAL "
                        else -> "ILO / REMOTE"
                    }
                    Text(ribbonText, color = accentLight, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                if (pax > 0) Text(" pax", color = accentLight, style = MaterialTheme.typography.labelSmall)
            }

            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_globe), null, tint = sk.subText, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(location, style = MaterialTheme.typography.titleMedium, color = sk.bodyText)
                }
                if (categoryTheme == "international") {
                    Spacer(Modifier.height(4.dp))
                    Text("Travels for ", color = sk.subText, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    if (urgent) ToneChip("PRIORITY", sk.cyan)
                    ToneChip(mode, sk.brand)
                    if (international) ToneChip("GLOBAL OPPORTUNITY", sk.azure)
                }
                Spacer(Modifier.height(12.dp))

                Text(b.str("course_name").ifBlank{"Course TBA"}, style = MaterialTheme.typography.bodyLarge, color = sk.bodyText, fontWeight = FontWeight.Bold)
                if (b.str("customer").isNotBlank()) {
                    Text(b.str("customer"), style = MaterialTheme.typography.bodyMedium, color = sk.subText)
                }
                val start = b.str("start_date").shortDate()
                val end = b.str("end_date").shortDate()
                Text(
                    listOfNotNull(
                        if (start.isNotBlank() && end.isNotBlank()) " → " else start,
                        if (pax > 0) " pax" else null
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = sk.subText
                )
                Spacer(Modifier.height(12.dp))

                if (categoryTheme == "international") {
                    Box(Modifier.fillMaxWidth().background(sk.surface, RoundedCornerShape(8.dp)).padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_globe), null, tint = sk.azure, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("INTERNATIONAL  OPPORTUNITY", color = sk.azure, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text(location, color = sk.bodyText, style = MaterialTheme.typography.labelMedium)
                                Text("TRAVEL REQUIRED · Visa and schedule readiness require manager review", color = sk.subText, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column {
                        Text(b.str("opportunity_score").ifBlank{"High"}, style = MaterialTheme.typography.titleMedium, color = sk.azure)
                        Text("OPPORTUNITY", style = MaterialTheme.typography.labelSmall, color = sk.subText)
                    }
                    Column {
                        Text(b.str("priority_score").ifBlank{"95"}, style = MaterialTheme.typography.titleMedium, color = sk.cyan)
                        Text("PRIORITY", style = MaterialTheme.typography.labelSmall, color = sk.subText)
                    }
                    Column {
                        Text(b.str("assignment_risk").ifBlank{"Low"}, style = MaterialTheme.typography.titleMedium, color = sk.good)
                        Text("RISK", style = MaterialTheme.typography.labelSmall, color = sk.subText)
                    }
                }
                Spacer(Modifier.height(16.dp))

                if (blocked) {
                    Box(Modifier.fillMaxWidth().background(sk.red.copy(alpha=0.1f), RoundedCornerShape(8.dp)).border(1.dp, sk.red.copy(alpha=0.3f), RoundedCornerShape(8.dp)).padding(12.dp)) {
                        Row {
                            Icon(painterResource(R.drawable.ic_alert), null, tint = sk.red, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("NO TRAINER HOLDS THIS COURSE", color = sk.red, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Text("Nobody in RMS is skilled on it. This needs hiring or training, not reallocation.", color = sk.red.copy(alpha=0.8f), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                val candidates = b.list("candidates")
                if (candidates.isNotEmpty()) {
                    Text("RECOMMENDED TRAINERS", style = MaterialTheme.typography.labelSmall, color = sk.labelText, fontWeight = FontWeight.Bold)
                    Text("Client exclusions and leave are checked when you open this batch.", style = MaterialTheme.typography.labelSmall, color = sk.subText)
                    Spacer(Modifier.height(8.dp))
                    
                    candidates.take(3).forEach { c ->
                        val isBlocked = c.bool("blocked")
                        val backupRole = c.str("backup_role").ifBlank{"Primary Trainer"}
                        val dotTint = if (isBlocked) sk.red else sk.good
                        
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(dotTint))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        c.str("trainer_name").ifBlank{"Unknown Trainer"},
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isBlocked) sk.subText else sk.bodyText,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        " ·  suitability",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = sk.subText
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Box(Modifier.background(sk.surface, RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                        Text("Availability unknown", style = MaterialTheme.typography.labelSmall, color = sk.subText)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Skill 100 · Ready  · Avail 100 · Cert 100 · Lang 100",
                                        style = MaterialTheme.typography.bodySmall, color = sk.subText
                                    )
                                }
                                if (!isBlocked) {
                                    Text("Best Match", style = MaterialTheme.typography.labelMedium, color = sk.good, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                Button(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = sk.surface, contentColor = sk.cyan),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Search Wider Trainer Network", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(8.dp))
                    Icon(painterResource(R.drawable.ic_globe), null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ── International Priority ───────────────────────────────────────────────────

@Composable
private fun DeliveryOpportunityCard(
    b: Map<*, *>, days: Int?, blocked: Boolean, urgent: Boolean,
    isNew: Boolean, expanded: Boolean, onToggleExpand: () -> Unit, onOpenDetails: () -> Unit
) {
    val sk = MaterialTheme.skill
    val mode = b.str("delivery_mode").uppercase()
    val pax = b.intOrNull("participants") ?: 0
    val international = b.bool("is_international")
    
    val city = b.str("city")
    val country = b.str("country")
    val loc = b.str("location")
    val location = when {
        city.isNotBlank() && country.isNotBlank() -> "$city, $country"
        loc.isNotBlank() && country.isNotBlank() -> "$loc, $country"
        city.isNotBlank() -> city
        country.isNotBlank() -> country
        else -> "Location not provided"
    }
    
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400))
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(sk.surface1)
                .border(1.dp, sk.azure.copy(alpha=0.5f), RoundedCornerShape(6.dp))
                .clickable { onToggleExpand() }
        ) {
            Row(
                Modifier.fillMaxWidth().background(sk.azure.copy(alpha=0.1f)).padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (international) "INTERNATIONAL $mode" else mode, color = sk.azure, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                if (pax > 0) Text("$pax pax", color = sk.azure, style = MaterialTheme.typography.labelSmall)
            }
            
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ToneChip("PRIORITY", sk.cyan)
                    ToneChip(mode, sk.brand)
                    if (international) ToneChip("GLOBAL OPPORTUNITY", sk.azure)
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_globe), null, tint = sk.subText, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(location, style = MaterialTheme.typography.titleSmall, color = sk.bodyText)
                }
                
                Text(b.str("course_name").ifBlank{"Course TBA"}, style = MaterialTheme.typography.bodyMedium, color = sk.bodyText, maxLines = 2, overflow = TextOverflow.Ellipsis)
                
                Text(
                    listOfNotNull(
                        b.str("start_date").takeIf { it.isNotBlank() }?.shortDate(),
                        b.str("end_date").takeIf { it.isNotBlank() }?.shortDate(),
                    ).joinToString(" – ").takeIf { it.isNotBlank() } ?: "Dates pending",
                    style = MaterialTheme.typography.labelSmall, color = sk.subText
                )

                if (blocked) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(sk.crit.copy(alpha=0.1f)).border(1.dp, sk.crit.copy(alpha=0.3f), RoundedCornerShape(4.dp)).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(painterResource(R.drawable.ic_alert), null, tint = sk.crit, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("NO TRAINER HOLDS THIS COURSE", color = sk.crit, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text("Capability gap detected", color = sk.crit, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            if (expanded) {
                Column(Modifier.fillMaxWidth().padding(12.dp).border(1.dp, sk.cardBorder, RoundedCornerShape(6.dp)).padding(12.dp)) {
                    if (international) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_people), null, tint = sk.subText, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Travel readiness required", style = MaterialTheme.typography.labelSmall, color = sk.subText)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(onClick = onOpenDetails, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = sk.azure)) {
                        Text("View Intelligence & Recommend")
                    }
                }
            }
        }
    }
}

internal fun relevanceColor(relevance: Int): androidx.compose.ui.graphics.Color {
    val sk = androidx.compose.material3.MaterialTheme.skill
    return when {
        relevance >= 75 -> sk.green
        relevance >= 50 -> sk.amber
        relevance > 0 -> sk.red
        else -> sk.subText
    }
}

internal fun coverageStyle(coverage: String): Triple<String, androidx.compose.ui.graphics.Color, Int> {
    val sk = androidx.compose.material3.MaterialTheme.skill
    return when (coverage) {
        "Best Match" -> Triple("Best Match", sk.aqua, com.example.skillsync.R.drawable.ic_check)
        "Available with Upskilling" -> Triple("Available with Upskilling", sk.warn, com.example.skillsync.R.drawable.ic_flag)
        else -> Triple("No Coverage", sk.crit, com.example.skillsync.R.drawable.ic_alert)
    }
}
