package com.example.skillsync.feature.training.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.co
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
        loc.isNotBlank() -> loc
        else -> if (mode.contains("ILO") || mode.contains("VIRTUAL")) "Remote / Virtual" else "Location not provided"
    }
    val accentBase = when (categoryTheme) { "international" -> sk.azure; "national" -> sk.teal; else -> sk.indigo }
    val accentLight = when (categoryTheme) { "international" -> sk.cyan; "national" -> sk.emerald; else -> sk.violet }

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), colors = CardDefaults.cardColors(containerColor = sk.surface), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, if (isNew) accentBase else sk.cardBorder)) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().background(accentBase.copy(alpha = 0.15f)).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_globe), null, tint = accentLight, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(when(categoryTheme) { "international" -> "INTERNATIONAL $mode"; "national" -> "INDIA NATIONAL $mode"; else -> "ILO / REMOTE" }, color = accentLight, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                if (pax > 0) Text("$pax pax", color = accentLight, style = MaterialTheme.typography.labelSmall)
            }
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_globe), null, tint = sk.subText, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(location, style = MaterialTheme.typography.titleMedium, color = sk.bodyText)
                }
                if (categoryTheme == "international") {
                    Spacer(Modifier.height(4.dp))
                    Text("Travels for ${b.str("start_date").shortDate()}", color = sk.subText, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    if (urgent) ToneChip("PRIORITY", sk.cyan)
                    ToneChip(mode, sk.brand)
                    if (international) ToneChip("GLOBAL OPPORTUNITY", sk.azure)
                }
                Spacer(Modifier.height(12.dp))
                Text(b.str("course_name").ifBlank{"Course TBA"}, style = MaterialTheme.typography.bodyLarge, color = sk.bodyText, fontWeight = FontWeight.Bold)
                if (b.str("customer").isNotBlank()) Text(b.str("customer"), style = MaterialTheme.typography.bodyMedium, color = sk.subText)
                Spacer(Modifier.height(12.dp))
                if (categoryTheme == "international") {
                    Box(Modifier.fillMaxWidth().background(sk.surface, RoundedCornerShape(8.dp)).padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_globe), null, tint = sk.azure, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("INTERNATIONAL $mode OPPORTUNITY", color = sk.azure, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text(location, color = sk.bodyText, style = MaterialTheme.typography.labelMedium)
                                Text("TRAVEL REQUIRED · Visa and schedule readiness require manager review", color = sk.subText, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
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
                        val dotTint = if (isBlocked) sk.red else sk.good
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(dotTint))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(c.str("trainer_name").ifBlank{"Unknown Trainer"}, style = MaterialTheme.typography.labelMedium, color = if (isBlocked) sk.subText else sk.bodyText, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                                    Text("${c.str("backup_role").ifBlank{"Primary Trainer"}} · ${c.intOrNull("suitability_score") ?: 90} suitability", style = MaterialTheme.typography.labelSmall, color = sk.subText)
                                    Spacer(Modifier.height(4.dp))
                                    Box(Modifier.background(sk.surface, RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                        Text("Availability unknown", style = MaterialTheme.typography.labelSmall, color = sk.subText)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text("Skill 100 · Ready ${c.intOrNull("suitability_score") ?: 98} · Avail 100 · Cert 100 · Lang 100", style = MaterialTheme.typography.bodySmall, color = sk.subText)
                                }
                                if (!isBlocked) Text("Best Match", style = MaterialTheme.typography.labelMedium, color = sk.good, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                Button(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = sk.surface, contentColor = sk.cyan), shape = RoundedCornerShape(24.dp)) {
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
private fun InternationalPriorityZone(
    items: List<Triple<Map<*, *>, Int?, Triple<Boolean, Boolean, Boolean>>>,
    newIds: Set<String>,
    expandedId: String?,
    onToggleExpand: (String) -> Unit,
    onOpenDetails: (Map<*, *>) -> Unit
) {
    val sk = MaterialTheme.skill
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { 20 }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Brush.horizontalGradient(listOf(sk.brand, sk.azure, sk.cyan)))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(painterResource(R.drawable.ic_globe), null, tint = sk.cyan, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("INTERNATIONAL ILT / FMAT", color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Strategic delivery opportunities", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                    Text("Travel / international readiness required", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                }
                Text("${items.size}", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }

            // Items
            items.forEachIndexed { index, (batch, days, flags) ->
                val id = batch.str("demand_id")
                DeliveryOpportunityCard(
                    b = batch, days = days, blocked = flags.first, urgent = flags.second,
                    isNew = id in newIds, expanded = expandedId == id,
                    onToggleExpand = { onToggleExpand(id) }, onOpenDetails = { onOpenDetails(batch) }
                )
            }
        }
    }
}

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
