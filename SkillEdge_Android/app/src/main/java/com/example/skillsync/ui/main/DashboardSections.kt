package com.example.skillsync.ui.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.skillsync.R
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.components.*
import java.util.Calendar
import kotlin.math.roundToInt

// ── Personalised header ───────────────────────────────────────────────────────

/**
 * Who is signed in, and the four numbers that frame their day.
 *
 * [profile] is null until `/api/data/manager-profile` answers. Rather than hold
 * the whole dashboard behind identity, the header degrades to the email local
 * part and fills in when the call lands.
 */
@Composable
fun ProfileHeader(
    email: String,
    profile: Map<String, Any>?,
    kpis: Map<*, *>?,
    capKpis: Map<*, *>?,
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit = {}
) {
    val sk = MaterialTheme.skill
    val name = profile?.str("name").orEmpty()
        .ifBlank { email.substringBefore("@").replace(".", " ").replaceFirstChar { it.uppercase() } }
    val role = profile?.str("role").orEmpty().ifBlank { "Delivery Manager" }
    val photo = profile?.str("photo_url").orEmpty()

    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenProfile),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        elevation = CardDefaults.cardElevation(4.dp),
    ) {
        Row(
            Modifier
                .background(Brush.linearGradient(listOf(Color(0xFF0A1128), Color(0xFF1E293B))))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Avatar(name = name, photoUrl = photo, size = 42.dp)
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color(0xFF10B981))
                            .border(1.dp, Color(0xFF1E293B), androidx.compose.foundation.shape.CircleShape)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(6.dp))
                        RoleBadge("MANAGER")
                    }
                    Text(
                        "Delivery Manager Cockpit",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                    )
                }
            }

            // Notification Bell with Badge
            Box(
                modifier = Modifier
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .clickable(onClick = onOpenNotifications)
                    .padding(6.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_alert),
                    contentDescription = "Notifications",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color(0xFFEF4444))
                        .align(Alignment.TopEnd),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "3",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

private fun figure(v: Int?) = v?.toString() ?: "—"

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 0..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

@Composable
private fun RoleBadge(role: String) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(
            role.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.skill.heroText,
            fontSize = 8.5.sp,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileMenuBottomSheet(
    email: String,
    onDismiss: () -> Unit,
    onLogout: () -> Unit,
    onViewProfile: () -> Unit
) {
    val sk = MaterialTheme.skill
    val sheetState = rememberModalBottomSheetState()
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sk.cardBg,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Session Information", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text("Logged in as: $email", color = Color.LightGray)
            Text("Session ID: ${com.example.skillsync.data.SessionManager.getSessionId()?.take(8)}...", color = Color.LightGray)
            Text("Last Sync: ${java.util.Date(com.example.skillsync.data.SessionManager.getLastSyncTime())}", color = Color.LightGray)
            
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
            Spacer(Modifier.height(8.dp))
            
            ListItem(
                headlineContent = { Text("View My Profile", color = Color.White) },
                leadingContent = { Icon(painterResource(R.drawable.ic_people), null, tint = Color.White) },
                modifier = Modifier.clickable { onViewProfile() },
                colors = ListItemDefaults.colors(containerColor = sk.cardBg)
            )
            ListItem(
                headlineContent = { Text("Logout", color = Color(0xFFF44336)) },
                leadingContent = { Icon(painterResource(R.drawable.ic_alert), null, tint = Color(0xFFF44336)) },
                modifier = Modifier.clickable { onLogout() },
                colors = ListItemDefaults.colors(containerColor = sk.cardBg)
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SummaryFigure(label: String, value: String, tint: Color? = null) {
    Column {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = tint ?: MaterialTheme.skill.heroText,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.skill.heroMuted, fontSize = 9.sp,
        )
    }
}

// ── Manager KPI grid ──────────────────────────────────────────────────────────

data class Kpi(
    val label: String,
    val value: String,
    val caption: String,
    val tint: Color,
    val drill: Drill? = null,
    val pending: Boolean = false,
    /** Needs team-capability, which is not fetched until something asks for it. */
    val needsCapability: Boolean = false,
)

/**
 * The twelve manager-level numbers, every one counted from a live RMS response.
 *
 * Four of them — certified trainers, certification gaps, readiness and skill
 * coverage — come from `team-capability`, which costs three RMS round-trips per
 * trainer and is therefore not fetched on open. Those cards read "Tap to load"
 * and fetch on demand. They never render 0 for "not fetched yet": a dashboard
 * that shows a confident zero where it simply has not looked is worse than one
 * that admits it does not know.
 */
@Composable
fun ManagerKpiGrid(
    kpis: Map<*, *>?,
    capKpis: Map<*, *>?,
    capabilityLoading: Boolean,
    ops: List<Map<*, *>>,
    states: List<Map<*, *>>,
    batches: List<Map<*, *>>,
    capTrainers: List<Map<*, *>>,
    onDrill: (Drill) -> Unit,
    onLoadCapability: () -> Unit = {},
) {
    val sk = MaterialTheme.skill
    fun n(key: String) = kpis?.intOrNull(key)
    fun c(key: String) = capKpis?.intOrNull(key)

    fun namesOf(rows: List<Map<*, *>>) = rows.map {
        it.str("trainer_name") to listOfNotNull(
            it.str("designation").takeIf(String::isNotBlank),
            it.intOrNull("current_utilization")?.let { u -> "$u%" },
        ).joinToString(" · ")
    }

    val stateBy = states.associateBy { it.str("trainer_email").lowercase() }
    fun opsWithStatus(vararg status: String) = ops.filter {
        stateBy[it.str("official_email").lowercase()]?.str("current_status") in status
    }

    val items = listOf(
        Kpi("Active trainers", figure(n("active_trainers")), "delivering or scheduled", sk.teal,
            Drill("Active trainers", "Delivering, scheduled or preparing",
                namesOf(opsWithStatus("teaching_now", "scheduled_today", "preparing")))),
        Kpi("Active batches", figure(n("active_batches")), "running today", sk.blue,
            Drill("Active batches", "Currently being delivered",
                batches.filter { it.str("engagement_state") == "current" }.map {
                    it.str("course_name") to "${it.str("trainer_name")} · ${it.str("delivery_mode")}"
                })),
        Kpi("Avg utilisation", n("avg_team_utilization")?.let { "$it%" } ?: "—",
            "3-mo avg · ${n("utilization_sample") ?: 0}/${n("total_team_members") ?: 0} tracked",
            utilTint(n("avg_team_utilization"), sk),
            Drill("Utilisation", "Three-month average per trainer",
                ops.sortedByDescending { it.int("current_utilization") }.map {
                    it.str("trainer_name") to
                        (if (it.bool("utilization_available")) "${it.int("current_utilization")}%" else "no data")
                })),
        Kpi("Readiness", c("team_readiness_score")?.let { "$it%" } ?: "—",
            "${c("ready_trainers") ?: 0} rated Ready", sk.teal,
            Drill("Readiness", "Qubits, approved catalogue depth and spare capacity",
                capTrainers.sortedByDescending { it.int("readiness_score") }.map {
                    it.str("trainer_name") to
                        "${it.intOrNull("readiness_score") ?: "—"} · ${it.str("readiness_bucket")}"
                }),
            pending = capabilityLoading, needsCapability = capKpis == null),
        Kpi("Certified", figure(c("certified_trainers")), "hold at least one exam", sk.green,
            Drill("Certified trainers", "Exams passed, from the RMS resume record",
                capTrainers.map { t ->
                    val held = t.obj("certification")?.list("held").orEmpty()
                    t.str("trainer_name") to "${held.size} certification${if (held.size == 1) "" else "s"}"
                }),
            pending = capabilityLoading, needsCapability = capKpis == null),
        Kpi("Cert gaps", figure(c("certification_gap_count")), "teach it, not certified", sk.red,
            Drill("Certification gaps", "Courses on the roster with no matching certification",
                capTrainers.flatMap { t ->
                    t.obj("certification")?.list("missing").orEmpty().map { m ->
                        "${m.str("code")} — ${m.str("name")}" to
                            "${t.str("trainer_name")} · via ${m.str("because")}"
                    }
                }),
            pending = capabilityLoading, needsCapability = capKpis == null),
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k -> KpiCard(k, Modifier.weight(1f), onDrill, onLoadCapability) }
                // Keep the last row's cards the same width as every other row.
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private fun utilTint(v: Int?, sk: com.example.skillsync.theme.SkillColors) = when {
    v == null -> sk.subText
    v > 85 -> sk.red
    v >= 60 -> sk.teal
    v >= 30 -> sk.amber
    else -> sk.subText
}

@Composable
private fun KpiCard(
    kpi: Kpi,
    modifier: Modifier,
    onDrill: (Drill) -> Unit,
    onLoadCapability: () -> Unit,
) {
    val sk = MaterialTheme.skill
    // An unloaded card fetches on tap; a loaded one drills into its rows.
    val action: (() -> Unit)? = when {
        kpi.needsCapability -> onLoadCapability
        kpi.drill != null -> ({ onDrill(kpi.drill) })
        else -> null
    }
    Card(
        modifier
            .heightIn(min = 78.dp)
            .then(if (action != null) Modifier.clickable(onClick = action) else Modifier),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = sk.cardBg),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(5.dp).clip(RoundedCornerShape(3.dp)).background(kpi.tint))
                Spacer(Modifier.width(5.dp))
                Text(
                    kpi.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = sk.subText, fontSize = 8.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(3.dp))
            if (kpi.pending) {
                ShimmerBox(width = 42.dp, height = 21.dp)
            } else if (kpi.needsCapability) {
                Text(
                    "Tap to load",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.padding(vertical = 3.dp),
                )
            } else {
                Text(
                    kpi.value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (kpi.value == "—") sk.subText else sk.bodyText,
                    maxLines = 1,
                )
            }
            Text(
                kpi.caption,
                style = MaterialTheme.typography.labelSmall,
                color = sk.subText, fontSize = 8.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Delivery Readiness Summary ────────────────────────────────────────────────

/**
 * Team Delivery Readiness card — sourced entirely from [delivery_intelligence_df]
 * which is always present in the unified payload. No extra API call required.
 *
 * Shows four bands: Ready / Ready with Prep / Needs Mentoring / Hold with counts,
 * percentages and animated progress bars so the manager instantly understands
 * how many of their team are deployment-ready without opening any trainer profile.
 */
@Composable
fun TeamReadinessSummaryCard(deliveryRows: List<Map<*, *>>) {
    if (deliveryRows.isEmpty()) return
    val sk = MaterialTheme.skill

    val total = deliveryRows.size
    val ready        = deliveryRows.count { it.str("delivery_readiness_label") == "Ready" }
    val readyPrep    = deliveryRows.count { it.str("delivery_readiness_label") == "Ready with Prep" }
    val needsMentor  = deliveryRows.count { it.str("delivery_readiness_label") == "Needs Mentoring" }
    val hold         = deliveryRows.count { it.str("delivery_readiness_label") == "Hold" }

    data class Band(val label: String, val count: Int, val color: Color, val emoji: String)
    val bands = listOf(
        Band("Ready",            ready,       sk.green,  "🟢"),
        Band("Ready with Prep",  readyPrep,   sk.teal,   "🟡"),
        Band("Needs Mentoring",  needsMentor, sk.amber,  "🟠"),
        Band("Hold",             hold,        sk.red,    "🔴"),
    )

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = sk.cardBg),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_trend), null,
                    tint = sk.teal, modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Delivery Readiness",
                    style = MaterialTheme.typography.titleMedium,
                    color = sk.bodyText,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    color = if (ready > 0) sk.green.copy(alpha = 0.14f) else sk.subText.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        "$ready of $total ready",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (ready > 0) sk.green else sk.subText,
                        fontWeight = FontWeight.Bold, fontSize = 9.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            bands.forEach { band ->
                val fraction = if (total > 0) band.count.toFloat() / total.toFloat() else 0f
                val pct      = (fraction * 100).toInt()
                val anim by animateFloatAsState(
                    targetValue = fraction,
                    animationSpec = tween(600),
                    label = "readiness_${band.label}",
                )
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Label column — fixed 120dp so all bars start at the same x
                    Row(
                        Modifier.width(120.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(band.emoji, fontSize = 9.sp)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            band.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = sk.bodyText,
                            fontSize = 10.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // Bar
                    Box(
                        Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(sk.track)
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(anim.coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(3.dp))
                                .background(band.color)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    // Count + pct
                    Text(
                        "${band.count}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (band.count > 0) band.color else sk.subText,
                        modifier = Modifier.width(18.dp),
                    )
                    Text(
                        "$pct%",
                        style = MaterialTheme.typography.labelSmall,
                        color = sk.subText, fontSize = 9.sp,
                        modifier = Modifier.width(26.dp),
                    )
                }
            }

            // Capacity split below the readiness bands
            val overloaded   = deliveryRows.count { it.str("delivery_capacity_status") == "Overloaded" }
            val balanced     = deliveryRows.count { it.str("delivery_capacity_status") == "Balanced" }
            val underused    = deliveryRows.count { it.str("delivery_capacity_status") == "Underutilized" }
            if (overloaded + balanced + underused > 0) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = sk.cardBorder)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Capacity Snapshot".uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = sk.subText, fontWeight = FontWeight.Bold, fontSize = 8.sp,
                )
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CapacityStat("Overloaded", overloaded, sk.red, Modifier.weight(1f))
                    CapacityStat("Balanced",   balanced,   sk.teal, Modifier.weight(1f))
                    CapacityStat("Available",  underused,  sk.green, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CapacityStat(label: String, count: Int, tint: Color, modifier: Modifier = Modifier) {
    val sk = MaterialTheme.skill
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.09f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "$count",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = if (count > 0) tint else sk.subText,
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = sk.subText, fontSize = 8.5.sp)
    }
}

// ── Team Feedback Risk Summary ─────────────────────────────────────────────────

/**
 * Team Feedback Risk card — sourced entirely from [trainer_operations_df].
 * Shows three risk bands: High / Medium / Low with counts, percentages, and animated progress bars.
 * Managers see at a glance how many team members have unresolved feedback issues.
 */
@Composable
fun TeamRiskSummaryCard(opsRows: List<Map<*, *>>) {
    if (opsRows.isEmpty()) return
    val sk = MaterialTheme.skill

    val total = opsRows.size
    val high   = opsRows.count { it.str("feedback_risk") == "High" }
    val medium = opsRows.count { it.str("feedback_risk") == "Medium" }
    val low    = opsRows.count { it.str("feedback_risk") == "Low" }

    data class RiskBand(val label: String, val count: Int, val color: Color, val emoji: String)
    val bands = listOf(
        RiskBand("High",   high,   sk.red,    "🔴"),
        RiskBand("Medium", medium, sk.amber,  "🟡"),
        RiskBand("Low",    low,    sk.green,  "🟢"),
    )

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = sk.cardBg),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_alert), null,
                    tint = sk.red, modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Feedback Risk",
                    style = MaterialTheme.typography.titleMedium,
                    color = sk.bodyText,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    color = if (high > 0) sk.red.copy(alpha = 0.14f) else sk.subText.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        "$high need attention",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (high > 0) sk.red else sk.subText,
                        fontWeight = FontWeight.Bold, fontSize = 9.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            bands.forEach { band ->
                val fraction = if (total > 0) band.count.toFloat() / total.toFloat() else 0f
                val pct      = (fraction * 100).toInt()
                val anim by animateFloatAsState(
                    targetValue = fraction,
                    animationSpec = tween(600),
                    label = "risk_${band.label}",
                )
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier.width(120.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(band.emoji, fontSize = 9.sp)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            band.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = sk.bodyText,
                            fontSize = 10.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(sk.track)
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(anim.coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(3.dp))
                                .background(band.color)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${band.count}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (band.count > 0) band.color else sk.subText,
                        modifier = Modifier.width(18.dp),
                    )
                    Text(
                        "$pct%",
                        style = MaterialTheme.typography.labelSmall,
                        color = sk.subText, fontSize = 9.sp,
                        modifier = Modifier.width(26.dp),
                    )
                }
            }
        }
    }
}

// ── Capacity & Bench Risk ────────────────────────────────────────────────────

/**
 * Stream 4: Bench Risk — highlights underutilized trainers who should be deployed.
 * Shows count of trainers on bench, their capacity status distribution.
 */
@Composable
fun TeamCapacityAlertCard(opsRows: List<Map<*, *>>) {
    val sk = MaterialTheme.skill
    val stretched = opsRows.count { it.str("capacity_bucket") == "Stretched" }
    val balanced = opsRows.count { it.str("capacity_bucket") == "Balanced" }
    val light = opsRows.count { it.str("capacity_bucket") == "Light" }
    val bench = opsRows.count { it.str("capacity_bucket") == "On Bench" }

    if (bench == 0 && light == 0) return

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = sk.cardBg),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_people), null,
                    tint = sk.amber, modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Capacity Optimization",
                    style = MaterialTheme.typography.titleMedium,
                    color = sk.bodyText,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                if (bench > 0) {
                    Surface(
                        color = sk.amber.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            "$bench on bench",
                            style = MaterialTheme.typography.labelSmall,
                            color = sk.amber,
                            fontWeight = FontWeight.Bold, fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CapacityStat("Stretched",  stretched, sk.red,   Modifier.weight(1f))
                CapacityStat("Balanced",   balanced,  sk.teal,  Modifier.weight(1f))
                CapacityStat("Light",      light,     sk.amber, Modifier.weight(1f))
                CapacityStat("On Bench",   bench,     sk.green, Modifier.weight(1f))
            }
        }
    }
}

// ── Analytics ─────────────────────────────────────────────────────────────────

/**
 * The five figures that answer "what needs attention" without opening anything:
 * where the team sits on capacity, how deployment is split, whether certification
 * keeps up with what is being taught, and which way utilisation is trending.
 */
@Composable
fun TeamAnalytics(
    ops: List<Map<*, *>>,
    states: List<Map<*, *>>,
    capKpis: Map<*, *>?,
    capTrainers: List<Map<*, *>>,
    capabilityLoading: Boolean = false,
) {
    val sk = MaterialTheme.skill

    // "utilization_available" distinguishes "RMS returned no row for this
    // trainer" (excluded) from "RMS says they're genuinely at 0% load"
    // (a real data point, counted in the On Bench bucket below). A raw
    // `current_utilization > 0` filter used to conflate the two, which
    // silently miscounted every idle-but-measured trainer as "no data" and
    // skewed the average.
    val utils = ops.filter { it.bool("utilization_available") }.map { it.int("current_utilization") }
    val stretched = utils.count { it > 85 }
    val balanced = utils.count { it in 60..85 }
    val light = utils.count { it in 30..59 }
    val bench = utils.count { it < 30 }
    val noData = ops.size - utils.size

    val capacitySlices = listOf(
        Slice("Stretched (>85%)", stretched, sk.red),
        Slice("Balanced (60-85%)", balanced, sk.teal),
        Slice("Light (30-59%)", light, sk.amber),
        Slice("On bench (<30%)", bench, sk.green),
        Slice("No utilisation data", noData, sk.subText),
    )

    val deployment = listOf(
        Slice("Delivering", states.count { it.str("current_status") == "teaching_now" }, sk.teal),
        Slice("Scheduled", states.count { it.str("current_status") == "scheduled_today" }, sk.blue),
        Slice("Preparing", states.count { it.str("current_status") == "preparing" }, sk.indigo),
        Slice("Available", states.count { it.str("current_status") == "free" }, sk.green),
        Slice("Unknown", states.count { it.str("current_status") == "unknown" }, sk.subText),
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

        AnalyticsCard("Capacity distribution", "3-month avg utilisation per trainer, bucketed") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DonutChart(
                    slices = capacitySlices,
                    centerValue = if (utils.isEmpty()) "—" else "${utils.average().toInt()}%",
                    centerLabel = "avg",
                    size = 108.dp,
                )
                Spacer(Modifier.width(14.dp))
                ChartLegend(capacitySlices, Modifier.weight(1f))
            }
            if (noData > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "$noData trainer${if (noData == 1) "" else "s"} returned no utilisation from RMS — " +
                        "counted separately rather than as 0%.",
                    style = MaterialTheme.typography.labelSmall, color = sk.subText, fontSize = 9.sp,
                )
            }
        }

        AnalyticsCard("Deployment right now", "What each trainer is doing today") {
            StackedBar(deployment)
            Spacer(Modifier.height(10.dp))
            ChartLegend(deployment.filter { it.value > 0 }.ifEmpty { deployment })
        }

        if (capTrainers.isNotEmpty()) {
            AnalyticsCard(
                "Certification coverage",
                "Tracks each trainer teaches, against the exams they hold",
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GaugeChart(
                        value = capKpis?.intOrNull("team_skill_coverage_pct"),
                        label = "team",
                        tint = coverageTint(capKpis?.intOrNull("team_skill_coverage_pct"), sk),
                        size = 96.dp,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        capTrainers.forEach { t ->
                            val cert = t.obj("certification")
                            val cov = cert?.intOrNull("coverage_pct")
                            val gaps = cert?.int("gap_count") ?: 0
                            MeterRow(
                                label = t.str("trainer_name"),
                                value = cov ?: 0,
                                max = 100,
                                tint = coverageTint(cov, sk),
                                valueText = cov?.let { "$it%" } ?: "n/a",
                                caption = if (gaps > 0)
                                    "$gaps gap${if (gaps == 1) "" else "s"}: " +
                                        cert?.list("missing").orEmpty().take(3)
                                            .joinToString(", ") { m -> m.str("code") }
                                else "no gaps on courses they teach",
                            )
                        }
                    }
                }
            }
        }

        AnalyticsCard("Readiness by trainer", "Qubits, catalogue depth and spare capacity") {
            if (capTrainers.isEmpty()) {
                // Not an error and not empty data — simply not fetched yet.
                Text(
                    if (capabilityLoading) "Reading capability from RMS…"
                    else "Tap a certification tile above, or open Courses, to load capability data.",
                    style = MaterialTheme.typography.labelSmall, color = sk.subText,
                )
            } else {
                BarChart(
                    bars = capTrainers.map {
                        BarDatum(
                            label = it.str("trainer_name").split(" ").first().take(9),
                            value = it.intOrNull("readiness_score") ?: 0,
                            color = when (it.str("readiness_bucket")) {
                                "Ready" -> sk.green
                                "Developing" -> sk.amber
                                "Needs support" -> sk.red
                                else -> sk.subText
                            },
                        )
                    },
                    height = 112.dp,
                )
            }
        }

        // Team utilisation trend, averaged across everyone who has a series.
        val trend = remember(ops) { teamUtilisationTrend(ops) }
        if (trend.size >= 2) {
            AnalyticsCard("Utilisation trend", "Team average over the last ${trend.size} months") {
                TrendChart(points = trend, tint = sk.teal, height = 96.dp)
            }
        }
    }
}

private fun coverageTint(v: Int?, sk: com.example.skillsync.theme.SkillColors) = when {
    v == null -> sk.subText
    v >= 80 -> sk.green
    v >= 50 -> sk.amber
    else -> sk.red
}

/**
 * Month-by-month team average. Each trainer carries their own `utilization_series`
 * and the months do not always align, so values are bucketed by month label and
 * averaged over whoever actually reported that month.
 */
internal fun teamUtilisationTrend(ops: List<Map<*, *>>): List<TrendPoint> {
    val ordered = LinkedHashMap<String, MutableList<Int>>()
    ops.forEach { t ->
        t.list("utilization_series").takeLast(12).forEach { m ->
            ordered.getOrPut(m.str("month")) { mutableListOf() }.add(m.int("utilization"))
        }
    }
    return ordered.entries
        .map { (month, values) ->
            TrendPoint(month.take(3), (values.sum() / values.size.coerceAtLeast(1)))
        }
        .takeLast(12)
}

// ── Capacity forecast (trend projection, not ML) ────────────────────────────────

data class UtilForecast(
    val trainerName: String,
    val trainerEmail: String,
    val current: Int,
    val projected: Int,
    val direction: String, // "Rising" | "Falling" | "Flat"
)

/**
 * A month-over-month average delta, projected one step forward — the only
 * time-series signal RMS actually gives us is [utilization_series], so this is
 * a plain linear projection of real numbers, not a trained model. Needs at
 * least 3 months on record; fewer than that and a slope is noise, not signal.
 */
internal fun utilizationForecasts(ops: List<Map<*, *>>): List<UtilForecast> {
    return ops.mapNotNull { t ->
        val values = t.list("utilization_series").takeLast(6).map { it.int("utilization") }
        val proj = projectNextUtilization(values) ?: return@mapNotNull null
        UtilForecast(
            trainerName  = t.str("trainer_name"),
            trainerEmail = t.str("official_email"),
            current      = values.last(),
            projected    = proj.projected,
            direction    = proj.direction,
        )
    }
}

data class UtilProjection(val projected: Int, val direction: String)

/**
 * Shared by the team-level forecast card and the single-trainer utilisation
 * section. Null when fewer than 3 months are on record — a slope from less
 * than that is noise, not signal.
 */
internal fun projectNextUtilization(values: List<Int>): UtilProjection? {
    if (values.size < 3) return null
    val slope = averageMonthOverMonthDelta(values)
    val projected = (values.last() + slope).roundToInt().coerceIn(0, 100)
    val direction = when {
        slope > 3f -> "Rising"
        slope < -3f -> "Falling"
        else -> "Flat"
    }
    return UtilProjection(projected, direction)
}

private fun averageMonthOverMonthDelta(values: List<Int>): Float {
    if (values.size < 2) return 0f
    var sum = 0
    for (i in 1 until values.size) sum += values[i] - values[i - 1]
    return sum.toFloat() / (values.size - 1)
}

/**
 * Stream 6 (predictive): trainers whose own utilization trend is heading toward
 * overload or the bench next month, surfaced *before* it happens rather than
 * only once the capacity bucket already shows Stretched/On Bench. Explicitly
 * labelled as a trend projection — this reads real monthly numbers, it does not
 * model or predict a person's behaviour.
 */
@Composable
fun TeamCapacityForecastCard(opsRows: List<Map<*, *>>) {
    val sk = MaterialTheme.skill
    val forecasts = remember(opsRows) { utilizationForecasts(opsRows) }
    val towardOverload = forecasts.filter { it.projected >= 90 && it.current < 90 && it.direction == "Rising" }
    val towardBench = forecasts.filter { it.projected <= 25 && it.direction == "Falling" }

    if (towardOverload.isEmpty() && towardBench.isEmpty()) return

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = sk.cardBg),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_trend), null,
                    tint = sk.indigo, modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    "Capacity Forecast",
                    style = MaterialTheme.typography.titleLarge,
                    color = sk.bodyText,
                )
                Spacer(Modifier.width(7.dp))
                // Unmissable distinction from the Readiness/Risk/Capacity
                // cards above: those are today's numbers, this is a
                // projection of where they're headed.
                Surface(color = sk.indigo.copy(alpha = 0.14f), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        "NEXT MONTH",
                        style = MaterialTheme.typography.labelSmall,
                        color = sk.indigo, fontWeight = FontWeight.Bold, fontSize = 9.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                "Projected from each trainer's own utilisation trend — not today's number, a forecast of where it's headed",
                style = MaterialTheme.typography.labelSmall,
                color = sk.subText, fontSize = 9.sp,
            )
            Spacer(Modifier.height(12.dp))

            if (towardOverload.isNotEmpty()) {
                Text(
                    "⚠️ Trending toward overload".uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = sk.red, fontWeight = FontWeight.Bold, fontSize = 9.sp,
                )
                Spacer(Modifier.height(6.dp))
                towardOverload.forEach { f -> ForecastRow(f, sk.red) }
                Spacer(Modifier.height(10.dp))
            }
            if (towardBench.isNotEmpty()) {
                Text(
                    "Trending toward bench".uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = sk.amber, fontWeight = FontWeight.Bold, fontSize = 9.sp,
                )
                Spacer(Modifier.height(6.dp))
                towardBench.forEach { f -> ForecastRow(f, sk.amber) }
            }
        }
    }
}

@Composable
private fun ForecastRow(f: UtilForecast, tint: Color) {
    val sk = MaterialTheme.skill
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            f.trainerName,
            style = MaterialTheme.typography.bodySmall,
            color = sk.bodyText,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${f.current}% → ${f.projected}%",
            style = MaterialTheme.typography.labelSmall,
            color = tint, fontWeight = FontWeight.Bold, fontSize = 10.sp,
        )
    }
}

@Composable
private fun AnalyticsCard(title: String, subtitle: String, body: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.skill.cardBg),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.skill.bodyText)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.skill.subText,
            )
            Spacer(Modifier.height(12.dp))
            body()
        }
    }
}
