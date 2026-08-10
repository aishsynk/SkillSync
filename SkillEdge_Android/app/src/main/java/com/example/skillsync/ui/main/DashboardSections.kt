package com.example.skillsync.ui.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.skillsync.R
import com.example.skillsync.theme.IconSlot
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.Surface0
import com.example.skillsync.theme.accentGlass
import com.example.skillsync.theme.glassSurface
import com.example.skillsync.theme.heroSurface
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
    val photo = profile?.str("photo_url").orEmpty()
    val unread = (kpis?.intOrNull("open_actions") ?: 0) + (kpis?.intOrNull("open_demand") ?: 0)

    // Identity sits directly on the aurora — no bar, no fill. The old teal
    // header spent a full band of screen on chrome that carried no information.
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(Radii.chip)).clickable(onClick = onOpenProfile),
            contentAlignment = Alignment.BottomEnd,
        ) {
            Avatar(name = name, photoUrl = photo, size = 38.dp)
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(sk.aqua)
                    .border(2.dp, Surface0, CircleShape)
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = sk.frost,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Delivery Manager · Live",
                style = MaterialTheme.typography.labelSmall,
                color = sk.labelText,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.09.em,
            )
        }
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(Radii.icon))
                .background(sk.glass)
                .border(1.dp, sk.glassBorder, RoundedCornerShape(Radii.icon))
                .clickable(onClick = onOpenNotifications),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_alert),
                contentDescription = "Alerts",
                tint = sk.ice,
                modifier = Modifier.size(17.dp),
            )
            if (unread > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 3.dp, y = (-3).dp)
                        .defaultMinSize(minWidth = 15.dp, minHeight = 15.dp)
                        .clip(CircleShape)
                        .background(sk.crit)
                        .border(2.dp, Surface0, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$unread",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 3.dp),
                    )
                }
            }
        }
    }
}

/**
 * The hero: one readiness score, its trend, and the three numbers that frame
 * the manager's day. Everything below this card exists to explain this card.
 */
@Composable
fun CommandHero(kpis: Map<*, *>?, capKpis: Map<*, *>?) {
    val sk = MaterialTheme.skill
    // Readiness prefers the capability figure (computed from real Qubits and
    // catalogue depth) and falls back to the dashboard's own score, so the hero
    // still reads before team-capability has been fetched.
    val readiness = capKpis?.intOrNull("team_readiness_score")
        ?: kpis?.intOrNull("team_readiness_score")
    val deployable = kpis?.intOrNull("deployable_pct")
    val trend = kpis?.str("readiness_trend").orEmpty()
    val strength = kpis?.intOrNull("total_team_members")
    val active = kpis?.intOrNull("active_trainers")
    val util = kpis?.intOrNull("avg_team_utilization")

    Box(Modifier.fillMaxWidth().heroSurface()) {
        Column(Modifier.padding(Space.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "TEAM READINESS",
                        style = MaterialTheme.typography.labelSmall,
                        color = sk.ice,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.13.em,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        readiness?.toString() ?: "—",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Light,
                        color = Color.White,
                        letterSpacing = (-0.04).em,
                    )
                    if (trend.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${if (trend.startsWith("-")) "▼" else "▲"} $trend vs last month",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (trend.startsWith("-")) sk.crit else sk.aqua,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                        )
                    }
                }
                ReadinessRing(score = readiness, innerValue = deployable, size = 74.dp)
            }

            Spacer(Modifier.height(Space.md))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SummaryFigure("STRENGTH", strength?.toString() ?: "—")
                SummaryFigure("DEPLOYED", active?.toString() ?: "—")
                SummaryFigure("UTILISATION", util?.let { "$it%" } ?: "—")
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
            Text("Background sync: Automatic", color = Color.LightGray)
            
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
    val needsCapability: Boolean = false,
    val icon: Int = R.drawable.ic_trend,
    val trend: String = "",
    val trendDir: Int = 0,
    val spark: List<Int> = emptyList(),
    val critical: Boolean = false,
)

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
        com.example.skillsync.ui.main.DrillRow(
            it.str("trainer_name"),
            listOfNotNull(
                it.str("designation").takeIf(String::isNotBlank),
                it.intOrNull("current_utilization")?.let { u -> "$u%" },
            ).joinToString(" · ")
        )
    }

    val stateBy = states.associateBy { it.str("trainer_email").lowercase() }
    fun opsWithStatus(vararg status: String) = ops.filter {
        stateBy[it.str("official_email").lowercase()]?.str("current_status") in status
    }

    val utilHistory = (kpis?.get("utilization_history") as? List<*>)
        ?.mapNotNull { (it as? Number)?.toInt() }.orEmpty()
    val atRisk = n("high_risk_trainers") ?: 0
    val openActions = (n("open_actions") ?: 0) + (n("open_demand") ?: 0)
    val bench = n("bench_trainers") ?: 0

    val items = listOf(
        Kpi("Team strength", figure(n("total_team_members")), "direct reportees", sk.brand,
            Drill("Team strength", "Everyone reporting to you", namesOf(ops)),
            icon = R.drawable.ic_people,
            trend = if (bench > 0) "$bench on bench" else "fully engaged",
            trendDir = if (bench > 0) 0 else 1),

        Kpi("Active trainers", figure(n("active_trainers")), "on a live batch", sk.aqua,
            Drill("Active trainers", "Currently engaged on a delivery",
                ops.filter { o ->
                    states.firstOrNull {
                        it.str("trainer_email").equals(o.str("official_email"), true)
                    }?.str("current_status") in listOf("teaching_now", "scheduled_today", "preparing")
                }.map {
                    com.example.skillsync.ui.main.DrillRow(
                        it.str("trainer_name"),
                        it.str("designation").ifBlank { it.str("capacity_bucket") },
                        it.str("official_email"),
                    )
                }),
            icon = R.drawable.ic_award,
            trend = "${n("unallocated_trainers") ?: 0} unallocated",
            trendDir = if ((n("unallocated_trainers") ?: 0) == 0) 1 else 0),

        Kpi("Active deliveries", figure(n("active_batches")), "running today", sk.royal,
            Drill("Active deliveries", "Currently being delivered",
                batches.filter { it.str("engagement_state") == "current" }.map {
                    com.example.skillsync.ui.main.DrillRow(
                        it.str("course_name"),
                        "${it.str("trainer_name")} · ${it.str("delivery_mode")}"
                    )
                }),
            icon = R.drawable.ic_inbox,
            trend = "${n("upcoming_batches") ?: 0} upcoming",
            trendDir = 1),

        Kpi("Utilisation", n("avg_team_utilization")?.let { "$it%" } ?: "—",
            "3-mo avg · ${n("utilization_sample") ?: 0}/${n("total_team_members") ?: 0} tracked",
            utilTint(n("avg_team_utilization"), sk),
            Drill("Utilisation", "Three-month average per trainer",
                ops.sortedByDescending { it.int("current_utilization") }.map {
                    com.example.skillsync.ui.main.DrillRow(
                        it.str("trainer_name"),
                        if (it.bool("utilization_available")) "${it.int("current_utilization")}%" else "no data"
                    )
                }),
            icon = R.drawable.ic_trend,
            trend = kpis?.str("utilization_trend").orEmpty(),
            trendDir = if (kpis?.str("utilization_trend").orEmpty().startsWith("-")) -1 else 1,
            spark = utilHistory),

        Kpi("Cert coverage", n("cert_coverage_pct")?.let { "$it%" } ?: "—",
            "${c("certification_gap_count") ?: 0} gaps open", sk.sky,
            Drill("Certification coverage", "Courses taught against certificates held",
                capTrainers.map {
                    val cert = it.obj("certification")
                    com.example.skillsync.ui.main.DrillRow(
                        it.str("trainer_name"),
                        "${cert?.intOrNull("coverage_pct") ?: "—"}% · ${cert?.int("gap_count") ?: 0} gap(s)",
                        it.str("trainer_email"),
                    )
                }),
            icon = R.drawable.ic_certificate,
            trend = "${c("certified_trainers") ?: 0} certified",
            trendDir = 1,
            pending = capabilityLoading && n("cert_coverage_pct") == null),

        Kpi("At risk", "$atRisk", "resources flagged", sk.crit,
            Drill("At-risk resources", "Feedback risk flagged on the operations record",
                ops.filter { it.str("feedback_risk") == "High" }.map {
                    com.example.skillsync.ui.main.DrillRow(
                        it.str("trainer_name"),
                        "${it.str("designation")} · ${it.str("recommended_action")}",
                        it.str("trainer_email").ifBlank { it.str("email") }
                    )
                }),
            icon = R.drawable.ic_alert,
            trend = if (atRisk == 0) "team clear" else "needs review",
            trendDir = if (atRisk == 0) 1 else -1,
            critical = atRisk > 0),
    )

    // Two per row for a 6-tile grid
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        items.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                row.forEach { k -> KpiCard(k, Modifier.weight(1f), onDrill, onLoadCapability) }
                repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
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
    val shape = RoundedCornerShape(Radii.kpi)
    Box(
        modifier
            .heightIn(min = 118.dp)
            .then(
                if (kpi.critical) Modifier.accentGlass(sk.crit, shape, strong = true)
                else Modifier.glassSurface(shape)
            )
            .then(if (action != null) Modifier.clickable(onClick = action) else Modifier)
    ) {
        // Gradient stripe keyed to the metric family — the tile's identity is
        // readable before any text is.
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(
                    Brush.verticalGradient(listOf(kpi.tint, kpi.tint.copy(alpha = 0.15f)))
                )
        )
        Column(Modifier.padding(start = 14.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)) {
            IconSlot(tint = kpi.tint, size = 26.dp) {
                Icon(
                    painterResource(kpi.icon), null,
                    tint = kpi.tint, modifier = Modifier.size(14.dp),
                )
            }
            Spacer(Modifier.height(10.dp))

            if (kpi.pending) {
                ShimmerBox(width = 52.dp, height = 24.dp)
            } else if (kpi.needsCapability) {
                Text(
                    "Tap to load",
                    style = MaterialTheme.typography.titleSmall,
                    color = sk.sky,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            } else {
                Text(
                    kpi.value,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.03).em,
                    color = when {
                        kpi.value == "—" -> sk.subText
                        kpi.critical -> Color(0xFFFF8A9B)
                        else -> sk.frost
                    },
                    maxLines = 1,
                )
            }

            Spacer(Modifier.height(5.dp))
            Text(
                kpi.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = sk.labelText,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.09.em,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (kpi.spark.size >= 2) {
                Spacer(Modifier.height(7.dp))
                Sparkline(
                    values = kpi.spark,
                    tint = kpi.tint,
                    endpointTint = sk.cyan,
                    height = 20.dp,
                )
            } else if (kpi.trend.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    buildString {
                        append(
                            when (kpi.trendDir) {
                                1 -> "▲ "
                                -1 -> "▼ "
                                else -> "· "
                            }
                        )
                        append(kpi.trend)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (kpi.trendDir) {
                        1 -> sk.good
                        -1 -> sk.crit
                        else -> sk.subText
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
        Band("Ready",            ready,       sk.aqua,  ""),
        Band("Ready with Prep",  readyPrep,   sk.cyan,  ""),
        Band("Needs Mentoring",  needsMentor, sk.warn,  ""),
        Band("Hold",             hold,        sk.crit,  ""),
    )

    Box(Modifier.fillMaxWidth().glassSurface()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_trend), null,
                    tint = sk.cyan, modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Delivery readiness",
                    style = MaterialTheme.typography.titleSmall,
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
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(band.color)
                        )
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
        RiskBand("High",   high,   sk.crit,  ""),
        RiskBand("Medium", medium, sk.warn,  ""),
        RiskBand("Low",    low,    sk.aqua,  ""),
    )

    Box(Modifier.fillMaxWidth().glassSurface()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_alert), null,
                    tint = sk.crit, modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Feedback risk",
                    style = MaterialTheme.typography.titleSmall,
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
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(band.color)
                        )
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

    Box(Modifier.fillMaxWidth().glassSurface()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconSlot(tint = sk.warn, size = 26.dp) {
                    Icon(
                        painterResource(R.drawable.ic_people), null,
                        tint = sk.warn, modifier = Modifier.size(14.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Capacity balance",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = sk.frost,
                    )
                    Text(
                        "Where the team sits against demand",
                        style = MaterialTheme.typography.labelSmall,
                        color = sk.labelText,
                        fontSize = 10.sp,
                    )
                }
                if (bench > 0) Chip("$bench on bench", sk.warn)
            }
            Spacer(Modifier.height(16.dp))
            // A single segmented bar: at phone width, comparing segment lengths
            // is far easier than comparing four separate stat blocks, and the
            // whole/part relationship is the actual question being asked.
            DistributionBar(
                slices = listOf(
                    Slice("Stretched", stretched, sk.crit),
                    Slice("Balanced", balanced, sk.sky),
                    Slice("Light", light, sk.warn),
                    Slice("Bench", bench, sk.aqua),
                )
            )
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
                Column(Modifier.weight(1f)) {
                    Text(
                        if (utils.isEmpty()) "—" else "${utils.average().toInt()}%",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = (-0.03).em,
                        color = sk.frost,
                    )
                    Text(
                        "TEAM AVERAGE",
                        style = MaterialTheme.typography.labelSmall,
                        color = sk.labelText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.11.em,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            DistributionBar(slices = capacitySlices.filter { it.value > 0 }.ifEmpty { capacitySlices })
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
            DistributionBar(slices = deployment.filter { it.value > 0 }.ifEmpty { deployment })
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
            AnalyticsCard(
                "Utilisation trend",
                "Team average over the last ${trend.size} months · healthy corridor 70–85%",
            ) {
                // The corridor is the point: a manager needs to know whether the
                // team is inside the healthy band, not the exact percentage.
                CorridorBars(
                    values = trend.map { it.value },
                    labels = trend.map { it.label },
                    height = 88.dp,
                )
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

    Box(Modifier.fillMaxWidth().glassSurface()) {
        Column(Modifier.padding(16.dp)) {
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
                    "Trending toward overload".uppercase(),
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
    val sk = MaterialTheme.skill
    Box(Modifier.fillMaxWidth().glassSurface()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = sk.frost,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = sk.labelText,
                fontSize = 10.sp,
            )
            Spacer(Modifier.height(14.dp))
            body()
        }
    }
}
