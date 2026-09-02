package com.example.skillsync.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.skillsync.R
import com.example.skillsync.theme.Figure
import com.example.skillsync.theme.FigureSize
import com.example.skillsync.theme.Layout
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.SectionHeading
import com.example.skillsync.theme.Severity
import com.example.skillsync.theme.SkillCard
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.Surface0
import com.example.skillsync.theme.ToneChip
import com.example.skillsync.theme.accentGlass
import com.example.skillsync.theme.glassSurface
import com.example.skillsync.theme.heroSurface
import com.example.skillsync.theme.pressable
import com.example.skillsync.theme.rememberCriticalPulse
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.components.*
import com.example.skillsync.util.NotifyEvent

/**
 * The manager's briefing — the whole of the Today surface.
 *
 * Built to answer six questions in descending urgency, and nothing else above
 * the fold (see `AI/DESIGN_VISION_V2_2026_08_11.md` §7.1 and §10):
 *
 *  1. Are we healthy?   → readiness ring + one sentence. The only large element.
 *  2. What is on fire?  → up to three critical cards, each with its action.
 *  3. What is moving?   → four KPI tiles, sparkline and delta where a baseline
 *                         genuinely exists.
 *  4. Where is slack?   → bench / optimal / stretched, interpretation as headline.
 *  5. What is coming?   → unallocated count, how much is international, one CTA.
 *  6. What else?        → collapsed Explore.
 *
 * Identity and the notification bell are folded into the hero rather than
 * sitting in a separate header row above it, so the first screenful is entirely
 * information and none of it is chrome.
 *
 * Nothing here derives a new number. Every value, drill and caption comes from
 * the same payload fields the previous layout used.
 */
@Composable
fun ManagerCommandCentre(
    email: String,
    profile: Map<String, Any>?,
    kpis: Map<*, *>?, capKpis: Map<*, *>?, capabilityLoading: Boolean,
    ops: List<Map<*, *>>, states: List<Map<*, *>>, batches: List<Map<*, *>>,
    demand: List<Map<*, *>>, capTrainers: List<Map<*, *>>,
    actions: List<Map<String, Any>>,
    recentNotifications: List<NotifyEvent> = emptyList(),
    fromCache: Boolean, cachedAt: Long,
    onDrill: (Drill) -> Unit,
    onTrainerClick: (String, String) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenDemand: () -> Unit,
    onOpenWeeklyReport: () -> Unit = {},
    onOpenHrReport: () -> Unit = {},
    onOpenPriorities: () -> Unit = {},
    onOpenAccounts: () -> Unit = {},
    onOpenCopilot: () -> Unit = {},
    onOpenDelivery: () -> Unit = {},
    onOpenPipelineRadar: () -> Unit = {},
    onOpenDeliveryCompliance: () -> Unit = {},
    onOpenCapacityRunway: () -> Unit = {},
    onOpenViberAutomation: () -> Unit = {},
    onOpenSkillRequests: () -> Unit = {},
    pendingSkillRequests: Int = 0,
    onBatchClick: (String) -> Unit = {},
    /**
     * Real calendar availability per trainer email. Named explicitly because
     * `readiness` in this function is already the team capability score.
     */
    calendarReadiness: Map<String, Map<String, Any>> = emptyMap(),
) {
    val sk = MaterialTheme.skill
    val openActions = actions.filter { it.str("lifecycle_state").ifBlank { "open" } !in setOf("closed", "resolved") }
    val available = states.filter { it.str("current_status") == "free" }
    val deployed = states.count { it.str("current_status") != "free" }

    // Real availability from the RMS calendar, where it has loaded. `free` above
    // is an RMS status flag, not a statement about the dates ahead: someone
    // marked free can still be on approved leave next week. These counts are
    // used only when the calendar actually answered, so the dashboard never
    // downgrades to a worse signal than it had before.
    fun days(row: Map<String, Any>, key: String) = (row[key] as? Number)?.toInt() ?: 0
    val onLeave = calendarReadiness.values.count { days(it, "leave_days") > 0 }
    val committed = calendarReadiness.values.count {
        days(it, "leave_days") == 0 && days(it, "confirmed_days") > 0
    }
    val clear = calendarReadiness.values.count {
        it["verified"] == true && days(it, "leave_days") == 0 && days(it, "confirmed_days") == 0
    }
    val calendarKnown = calendarReadiness.isNotEmpty()
    val overloaded = ops.filter { it.intOrNull("current_utilization")?.let { u -> u > 85 } == true }
    val active = batches.filter { it.str("engagement_state") == "current" }
    val upcoming = batches.filter { it.str("engagement_state") == "upcoming" }
    val gaps = capTrainers.sumOf { it.obj("certification")?.int("gap_count") ?: 0 }
    val readiness = capKpis?.intOrNull("team_readiness_score") ?: kpis?.intOrNull("team_readiness_score")
    val utilisation = kpis?.intOrNull("avg_team_utilization")
    val coverage = capKpis?.intOrNull("avg_trainer_coverage_pct") ?: kpis?.intOrNull("cert_coverage_pct")
    val atRisk = ops.filter { it.str("feedback_risk").equals("High", true) }

    val utilHistory = remember(kpis) {
        (kpis?.get("utilization_history") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }.orEmpty()
    }
    // The one real month-over-month baseline the payload carries. Nothing else
    // in the response has history, so nothing else claims a trend.
    val utilDelta = if (utilHistory.size >= 2) {
        utilHistory.last() - utilHistory[utilHistory.lastIndex - 1]
    } else null

    var showNotifications by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {

        // ── 1 · Are we healthy? ─────────────────────────────────────────────
        BriefingHero(
            email = email,
            profile = profile,
            readiness = readiness,
            utilisation = utilisation,
            utilDelta = utilDelta,
            team = ops.size,
            deployed = deployed,
            atRisk = atRisk.size,
            unallocated = demand.size,
            openActions = openActions.size + recentNotifications.size,
            fromCache = fromCache,
            cachedAt = cachedAt,
            onOpenProfile = onOpenProfile,
            onOpenNotifications = { showNotifications = !showNotifications },
        )

        AnimatedVisibility(visible = showNotifications) {
            NotificationCenter(
                actions = actions,
                events = recentNotifications,
                onTrainerTap = onTrainerClick,
                onDemandTap = { demandId ->
                    if (demandId.isNotBlank()) onBatchClick(demandId)
                    else onOpenDemand()
                },
            )
        }

        // ── 1b · This Week — the ranked board of what needs the manager ─────
        ThisWeekCard(email = email, onOpen = onOpenPriorities)

        // ── 2 · What is on fire? ────────────────────────────────────────────
        val alerts = buildAlerts(atRisk, overloaded, demand, openActions, gaps, onDrill, onOpenDemand)
        if (alerts.isNotEmpty()) {
            SectionHeading(
                "Needs you today",
                alerts.first().headline,
                trailing = if (alerts.size > 3) "${alerts.size} total" else null,
            )
            AttentionStrip(alerts.take(3))
        }

        // ── 3 · What is moving? ─────────────────────────────────────────────
        SectionHeading("Pulse", trailing = if (capabilityLoading) "refreshing" else null)
        PulseRow(
            listOf(
                PulseTileData(
                    "Strength", ops.size.toString(),
                    baseline = "$deployed deployed · ${available.size} free",
                    tint = sk.sky, drill = drill("Team strength", ops),
                ),
                PulseTileData(
                    "Utilisation", utilisation?.let { "$it%" } ?: "—",
                    baseline = "${kpis?.intOrNull("utilization_sample") ?: 0} of ${ops.size} measured",
                    tint = utilisationColour(utilisation, sk),
                    series = utilHistory,
                    delta = utilDelta?.let { d -> (if (d >= 0) "▲ +$d" else "▼ $d") + " vs last month" },
                    deltaGood = (utilDelta ?: 0) >= 0,
                    drill = drill("Team utilisation", ops, true),
                ),
                PulseTileData(
                    "Cert coverage", coverage?.let { "$it%" } ?: "—",
                    baseline = if (gaps > 0) "$gaps ${plural(gaps, "gap", "gaps")} open" else "no open gaps",
                    tint = if (gaps == 0) sk.good else sk.warn,
                ),
                PulseTileData(
                    "At risk", atRisk.size.toString(),
                    baseline = if (atRisk.isEmpty()) "no feedback flags" else "high feedback risk",
                    tint = if (atRisk.isEmpty()) sk.good else sk.crit,
                    drill = if (atRisk.isEmpty()) null else drill("At risk", atRisk),
                ),
            ),
            onDrill,
        )

        // ── 4 · Where is the slack? ─────────────────────────────────────────
        CapacityBalance(ops, demand.size, available.size, onDrill)

        // ── 4b · Who is actually free ───────────────────────────────────────
        if (calendarKnown) {
            CalendarAvailability(onLeave, committed, clear, calendarReadiness.size)
        }

        // ── 5 · What is coming? ─────────────────────────────────────────────
        DemandGlance(demand, active.size, upcoming.size, onOpenDemand)

        // ── Delivery Pulse Glance ───────────────────────────────────────────
        DeliveryPulseGlance(
            activeCount = active.size,
            upcomingCount = upcoming.size,
            onLeaveCount = onLeave,
            onOpenDelivery = onOpenDelivery,
        )

        // ── Executive Command Deck (8-Tile Bento Grid) ─────────────────────
        SectionHeading("Executive Operations", "Real-time intelligence dispatch & strategic consoles")
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Row 1: Priorities & Pre-Demand Radar
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenPriorities() },
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0x281D4ED8),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x6638BDF8)),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x3338BDF8)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_calendar),
                                contentDescription = "This Week Priorities",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Column {
                            Text("This Week", fontWeight = FontWeight.Bold, color = sk.bodyText, fontSize = 12.sp)
                            Text("Urgent Actions ↗", color = Color(0xFF38BDF8), fontSize = 10.sp)
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenPipelineRadar() },
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0x284F46E5),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66818CF8)),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x33818CF8)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_trend),
                                contentDescription = "Pre-Demand Pipeline Radar",
                                tint = Color(0xFF818CF8),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Column {
                            Text("Pipeline Radar", fontWeight = FontWeight.Bold, color = sk.bodyText, fontSize = 12.sp)
                            Text("Signed SC Radar ↗", color = Color(0xFF818CF8), fontSize = 10.sp)
                        }
                    }
                }
            }

            // Row 2: Live Delivery Sentinel & Weekly Standpoint
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenDeliveryCompliance() },
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0x28059669),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x6634D399)),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x3334D399)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_check),
                                contentDescription = "Delivery Compliance Sentinel",
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Column {
                            Text("Live Sentinel", fontWeight = FontWeight.Bold, color = sk.bodyText, fontSize = 12.sp)
                            Text("Daily Recording Audit ↗", color = Color(0xFF34D399), fontSize = 10.sp)
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenWeeklyReport() },
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0x280284C7),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x6638BDF8)),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x3338BDF8)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_copy),
                                contentDescription = "Weekly Standpoint Note",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Column {
                            Text("Weekly Standpoint", fontWeight = FontWeight.Bold, color = sk.bodyText, fontSize = 12.sp)
                            Text("AI Mind Dispatch ↗", color = Color(0xFF38BDF8), fontSize = 10.sp)
                        }
                    }
                }
            }

            // Row 3: HR Monthly Review & Capacity Runway
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenHrReport() },
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0x28D97706),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66FBBF24)),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x33FBBF24)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_certificate),
                                contentDescription = "HR Monthly Review",
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Column {
                            Text("HR Monthly Review", fontWeight = FontWeight.Bold, color = sk.bodyText, fontSize = 12.sp)
                            Text("TI Score Breakdown ↗", color = Color(0xFFFBBF24), fontSize = 10.sp)
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenCapacityRunway() },
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0x28E11D48),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66FB7185)),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x33FB7185)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_trend),
                                contentDescription = "Capacity Runway",
                                tint = Color(0xFFFB7185),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Column {
                            Text("Capacity Runway", fontWeight = FontWeight.Bold, color = sk.bodyText, fontSize = 12.sp)
                            Text("8-Wk Demand Gap ↗", color = Color(0xFFFB7185), fontSize = 10.sp)
                        }
                    }
                }
            }

            // Row 4: Customer Accounts & Team Copilot AI
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenAccounts() },
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0x287C3AED),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66A78BFA)),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x33A78BFA)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_inbox),
                                contentDescription = "Customer Accounts",
                                tint = Color(0xFFA78BFA),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Column {
                            Text("Accounts Book", fontWeight = FontWeight.Bold, color = sk.bodyText, fontSize = 12.sp)
                            Text("Client Concentration ↗", color = Color(0xFFA78BFA), fontSize = 10.sp)
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenCopilot() },
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0x280891B2),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x6622D3EE)),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x3322D3EE)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_alert),
                                contentDescription = "Team Copilot AI",
                                tint = Color(0xFF22D3EE),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Column {
                            Text("Team Copilot AI", fontWeight = FontWeight.Bold, color = sk.bodyText, fontSize = 12.sp)
                            Text("Ask Team Agent ↗", color = Color(0xFF22D3EE), fontSize = 10.sp)
                        }
                    }
                }
            }

            // Row 5: Viber Automation & Delivery Operations
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenViberAutomation() },
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0x286366F1),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66818CF8)),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x33818CF8)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_forward),
                                contentDescription = "Viber Background Automation",
                                tint = Color(0xFF818CF8),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Column {
                            Text("Viber Automation", fontWeight = FontWeight.Bold, color = sk.bodyText, fontSize = 12.sp)
                            Text("Auto-Dispatch Queue ↗", color = Color(0xFF818CF8), fontSize = 10.sp)
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenDelivery() },
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0x280D9488),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x662DD4BF)),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x332DD4BF)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_calendar),
                                contentDescription = "Delivery Operations",
                                tint = Color(0xFF2DD4BF),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Column {
                            Text("Delivery Ops", fontWeight = FontWeight.Bold, color = sk.bodyText, fontSize = 12.sp)
                            Text("Full Calendar ↗", color = Color(0xFF2DD4BF), fontSize = 10.sp)
                        }
                    }
                }
            }

            // Row 6: Skill approval queue
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenSkillRequests() },
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0x28F59E0B),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66FBBF24)),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x33FBBF24)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_check),
                                contentDescription = "Skill Requests",
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Column {
                            Text("Skill Requests", fontWeight = FontWeight.Bold, color = sk.bodyText, fontSize = 12.sp)
                            Text(
                                if (pendingSkillRequests > 0) "$pendingSkillRequests awaiting approval ↗"
                                else "Reportee level requests ↗",
                                color = Color(0xFFFBBF24), fontSize = 10.sp,
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
            }
        }

        // ── 5b · Who is carrying the work ───────────────────────────────────
        TopPerformersPanel(ops, capTrainers, onTrainerClick)

        // ── 5b2 · Certification health ──────────────────────────────────────
        CertificationBand(coverage = coverage, gaps = gaps, loading = capabilityLoading)

        // ── 5d · The agent ──────────────────────────────────────────────────
        AgentCta(onOpenCopilot)

        // ── 6 · Everything else ─────────────────────────────────────────────
        ExploreSection(
            kpis = kpis, capKpis = capKpis, capabilityLoading = capabilityLoading,
            ops = ops, upcoming = upcoming, capTrainers = capTrainers, gaps = gaps,
            openActions = openActions, atRisk = atRisk, utilHistory = utilHistory,
            onDrill = onDrill, onTrainerClick = onTrainerClick,
        )
    }
}

private fun plural(n: Int, one: String, many: String) = if (n == 1) one else many

/** Prominent entry point to the "This Week" priority board. */
@Composable
private fun ThisWeekCard(email: String, onOpen: () -> Unit) {
    val sk = MaterialTheme.skill
    val openCount = remember(email) {
        com.example.skillsync.ui.report.PrioritiesViewModel.cachedOpenCount(email)
    }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onOpen() },
        shape = RoundedCornerShape(Radii.card),
        color = Color(0x281D4ED8),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x6638BDF8)),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x3338BDF8)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_calendar),
                    contentDescription = "This Week",
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text("This Week", fontWeight = FontWeight.Bold, color = sk.bodyText, fontSize = 14.sp)
                Text(
                    if (openCount > 0) "$openCount ${plural(openCount, "item", "items")} need you — ranked ↗"
                    else "What needs you — ranked ↗",
                    color = sk.sky,
                    fontSize = 11.sp,
                )
            }
            if (openCount > 0) {
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF38BDF8))
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                ) {
                    Text("$openCount", color = Color(0xFF0B1220), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Freshness stamp, or null when the payload carries no write time at all. */
private fun freshnessAge(epochMillis: Long): String? =
    if (epochMillis <= 0L) null else relativeAge(epochMillis)

// ── 1 · Briefing hero ───────────────────────────────────────────────────────

/**
 * Identity, freshness, the readiness ring and the one-sentence brief.
 *
 * This replaces the old `ProfileHeader` + `CommandHero` + KPI-grid stack: three
 * separate blocks that between them spent most of the first screenful on chrome
 * and repeated the same figures twice.
 */
@Composable
private fun BriefingHero(
    email: String,
    profile: Map<String, Any>?,
    readiness: Int?, utilisation: Int?, utilDelta: Int?,
    team: Int, deployed: Int, atRisk: Int, unallocated: Int, openActions: Int,
    fromCache: Boolean, cachedAt: Long,
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
) {
    val sk = MaterialTheme.skill
    val name = profile?.str("name").orEmpty()
        .ifBlank { email.substringBefore("@").replace(".", " ").replaceFirstChar { it.uppercase() } }
    val age = freshnessAge(cachedAt)

    Column(
        Modifier
            .fillMaxWidth()
            .heroSurface()
            .padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        // Identity row — folded in with high-end executive styling
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(Radii.chip))
                    .border(1.5.dp, Color(0x6638BDF8), RoundedCornerShape(Radii.chip))
                    .clickable(onClick = onOpenProfile),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Avatar(name = name, photoUrl = profile?.str("photo_url").orEmpty(), size = 42.dp)
            }
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = sk.heroText,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        age == null -> "DELIVERY MANAGER · LIVE"
                        fromCache -> "OFFLINE COPY · $age".uppercase()
                        else -> "LIVE · UPDATED $age".uppercase()
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.04.em
                    ),
                    color = if (fromCache) sk.warn else Color(0xFF38BDF8),
                    maxLines = 1,
                )
            }
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(1.dp, Color(0x4038BDF8), RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenNotifications),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_alert),
                    contentDescription = "Alerts",
                    tint = Color.White,
                    modifier = Modifier.size(19.dp),
                )
                if (openActions > 0) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                            .defaultMinSize(minWidth = 17.dp, minHeight = 17.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444))
                            .border(2.dp, Surface0, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$openActions",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 3.dp),
                        )
                    }
                }
            }
        }

        // The readiness reading — executive hero display
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0x20000000))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "TEAM READINESS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.05.em
                    ),
                    color = Color(0xFF93C5FD)
                )
                Text(
                    readiness?.let { "$it%" } ?: "—",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontFeatureSettings = "tnum",
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    color = Color.White,
                )
                if (utilDelta != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            (if (utilDelta >= 0) "▲ +$utilDelta%" else "▼ $utilDelta%") + " utilisation vs last month",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (utilDelta >= 0) Color(0xFF34D399) else sk.warn,
                        )
                    }
                }
            }
            ReadinessRing(readiness, utilisation, size = 92.dp)
        }

        // The brief itself — high-contrast executive summary pills
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0x26000000),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x2093C5FD)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                buildList {
                    add("$deployed of $team deployed")
                    utilisation?.let {
                        add("utilisation $it%" + when {
                            utilDelta == null -> ""
                            utilDelta > 0 -> " and rising"
                            utilDelta < 0 -> " and falling"
                            else -> " and flat"
                        })
                    }
                    if (atRisk > 0) add("$atRisk ${plural(atRisk, "trainer", "trainers")} at risk")
                    if (unallocated > 0) add("$unallocated ${plural(unallocated, "demand", "demands")} unallocated")
                    if (atRisk == 0 && unallocated == 0) add("all deliveries covered")
                }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = Color(0xFFDBEAFE),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

// ── 2 · Attention strip ─────────────────────────────────────────────────────

private data class Alert(
    val severity: Severity,
    val headline: String,
    val detail: String,
    val actionLabel: String,
    val onAction: () -> Unit,
)

/**
 * Triage, ordered by [Severity.weight] so a feedback incident always outranks
 * an unallocated batch regardless of the order rows arrived in.
 *
 * [actionLabel] is the recommended next step, and it is the card's primary
 * button — the point of the strip is that a manager can act from it, not merely
 * read it.
 */
private fun buildAlerts(
    atRisk: List<Map<*, *>>,
    overloaded: List<Map<*, *>>,
    demand: List<Map<*, *>>,
    openActions: List<Map<String, Any>>,
    gaps: Int,
    onDrill: (Drill) -> Unit,
    onOpenDemand: () -> Unit,
): List<Alert> = buildList {
    if (atRisk.isNotEmpty()) {
        val d = drill("High feedback risk", atRisk)
        add(
            Alert(
                Severity.Critical,
                "${atRisk.size} ${plural(atRisk.size, "trainer is", "trainers are")} at high feedback risk",
                // The recommendation RMS itself returned, where it returned one.
                atRisk.firstOrNull()?.str("recommended_action")?.takeIf { it.isNotBlank() }
                    ?: atRisk.take(3).joinToString(" · ") { it.str("trainer_name") },
                "Review trainers",
            ) { onDrill(d) }
        )
    }
    if (overloaded.isNotEmpty()) {
        val d = drill("Overloaded", overloaded, true)
        add(
            Alert(
                Severity.Warning,
                "${overloaded.size} ${plural(overloaded.size, "trainer is", "trainers are")} over 85% utilised",
                "Rebalance upcoming work before assigning anything new.",
                "Rebalance",
            ) { onDrill(d) }
        )
    }
    if (demand.isNotEmpty()) {
        val intl = demand.count { it.str("delivery_mode").uppercase() in setOf("FMAT", "ILT") }
        add(
            Alert(
                Severity.Warning,
                "${demand.size} ${plural(demand.size, "batch has", "batches have")} no owner",
                if (intl > 0) "$intl ${plural(intl, "is", "are")} FMAT or ILT — the highest-value work."
                else demand.take(3).joinToString(" · ") { it.str("course_name").ifBlank { it.str("demand_id") } },
                "Allocate",
                onOpenDemand,
            )
        )
    }
    if (gaps > 0) {
        add(
            Alert(
                Severity.Watch,
                "$gaps certification ${plural(gaps, "gap", "gaps")} across the team",
                "Gaps block allocation to accredited demand.",
                "See coverage",
            ) { }
        )
    }
    if (openActions.isNotEmpty()) {
        val d = actionDrill(openActions)
        add(
            Alert(
                Severity.Info,
                "${openActions.size} open manager ${plural(openActions.size, "action", "actions")}",
                openActions.take(2).joinToString(" · ") { it.str("title").ifBlank { "Manager action" } },
                "Open queue",
            ) { onDrill(d) }
        )
    }
}.sortedBy { it.severity.weight }

/**
 * A horizontal strip rather than a vertical stack: three critical items stacked
 * vertically pushed the pulse row off the first screen, and the whole point of
 * the layout is that health and triage are visible together.
 */
@Composable
private fun AttentionStrip(alerts: List<Alert>) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        contentPadding = PaddingValues(end = Space.sm),
    ) {
        items(alerts, key = { it.headline }) { alert ->
            val tint = alert.severity.tint()
            val pulse = rememberCriticalPulse(alert.severity == Severity.Critical)
            Column(
                Modifier
                    .width(272.dp)
                    .accentGlass(tint, strong = alert.severity == Severity.Critical)
                    .pressable(alert.onAction)
                    .padding(Space.lg),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .alpha(pulse)
                            .background(tint, CircleShape)
                    )
                    Spacer(Modifier.width(Space.sm))
                    ToneChip(alert.severity.label, tint)
                }
                Text(
                    alert.headline,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.skill.bodyText,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    alert.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.skill.subText,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                FilledTonalButton(
                    onClick = alert.onAction,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radii.chip),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = tint.copy(alpha = 0.18f),
                        contentColor = tint,
                    ),
                ) { Text(alert.actionLabel, style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

// ── 3 · Pulse row ───────────────────────────────────────────────────────────

private data class PulseTileData(
    val label: String,
    val value: String,
    val baseline: String,
    val tint: Color,
    val series: List<Int> = emptyList(),
    val delta: String? = null,
    val deltaGood: Boolean = true,
    val drill: Drill? = null,
)

@Composable
private fun PulseRow(items: List<PulseTileData>, onDrill: (Drill) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        items.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                row.forEach { item -> key(item.label) { PulseTile(item, Modifier.weight(1f), onDrill) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * Value, then evidence.
 *
 * A sparkline and a delta are drawn only where the payload actually carries a
 * history — today that is utilisation alone. The other three tiles state their
 * baseline in words ("8 of 10 measured", "3 gaps open") rather than showing an
 * invented trend line, because a fabricated sparkline is worse than none.
 */
@Composable
private fun PulseTile(item: PulseTileData, modifier: Modifier, onDrill: (Drill) -> Unit) {
    val sk = MaterialTheme.skill
    val target = item.drill
    Column(
        modifier
            .height(132.dp)
            .glassSurface(RoundedCornerShape(Radii.kpi))
            .then(if (target != null) Modifier.pressable { onDrill(target) } else Modifier)
            .padding(Space.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(item.tint, RoundedCornerShape(3.dp)))
            Spacer(Modifier.width(Space.sm))
            Text(
                item.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = sk.labelText,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(Space.xs))
        Text(
            item.value,
            style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
            color = item.tint,
        )
        Text(
            item.delta ?: item.baseline,
            style = MaterialTheme.typography.bodySmall,
            color = if (item.delta != null) (if (item.deltaGood) sk.aqua else sk.warn) else sk.subText,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        if (item.series.size >= 2) {
            Sparkline(item.series, item.tint, endpointTint = sk.cyan, height = 24.dp)
        }
    }
}

// ── 4 · Capacity balance ────────────────────────────────────────────────────

/**
 * Bench / optimal / stretched, straight off `capacity_bucket`, with the reading
 * of the bar as the section headline. The bar is evidence for a claim the
 * manager has already absorbed, not a puzzle to solve.
 */
@Composable
private fun CapacityBalance(
    ops: List<Map<*, *>>,
    unallocated: Int,
    free: Int,
    onDrill: (Drill) -> Unit,
) {
    val sk = MaterialTheme.skill
    val bench = ops.filter { it.str("capacity_bucket") == "On Bench" }
    val stretched = ops.filter { it.str("capacity_bucket") == "Stretched" }
    val optimal = ops.filter { it !in bench && it !in stretched }

    val headline = when {
        bench.isNotEmpty() && unallocated > 0 ->
            "${bench.size} on bench while $unallocated ${plural(unallocated, "batch is", "batches are")} unallocated — the gap is coverage, not headcount."
        stretched.isNotEmpty() && free > 0 ->
            "${stretched.size} stretched while ${free} sit free — the work is unevenly spread."
        stretched.isNotEmpty() ->
            "${stretched.size} ${plural(stretched.size, "trainer is", "trainers are")} carrying more than their share."
        bench.isNotEmpty() -> "${bench.size} on bench and no demand waiting."
        else -> "The whole team is inside a healthy load band."
    }

    SectionHeading("Capacity balance", headline)
    SkillCard(Modifier.fillMaxWidth()) {
        DistributionBar(
            listOf(
                Slice("Bench", bench.size, sk.warn),
                Slice("Optimal", optimal.size, sk.good),
                Slice("Stretched", stretched.size, sk.crit),
            )
        )
        Text(
            "Workload bands. Availability is separate — see below.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.skill.labelText,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            listOf("Bench" to bench, "Optimal" to optimal, "Stretched" to stretched).forEach { (label, rows) ->
                Figure(
                    value = rows.size.toString(),
                    label = label,
                    size = FigureSize.Small,
                    modifier = Modifier
                        .weight(1f)
                        .then(if (rows.isEmpty()) Modifier else Modifier.pressable { onDrill(drill(label, rows)) }),
                )
            }
        }
    }
}

/**
 * Availability read from the RMS calendar rather than from a status flag.
 *
 * Kept distinct from the capacity bands above, which measure workload. The two
 * answer different questions and conflating them is the error this layer was
 * built to remove: a trainer inside a healthy workload band can still be on
 * approved leave for the whole of next week.
 */
@Composable
private fun CalendarAvailability(onLeave: Int, committed: Int, clear: Int, checked: Int) {
    val sk = MaterialTheme.skill
    SectionHeading(
        "Who is actually free",
        when {
            onLeave > 0 -> "$onLeave on leave in the next 90 days, $clear with nothing booked."
            clear == checked -> "Nobody has leave or commitments booked."
            else -> "$clear of $checked have nothing booked."
        },
        trailing = "$checked checked",
    )
    SkillCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            Figure(clear.toString(), "Clear", FigureSize.Small, Modifier.weight(1f), sk.aqua)
            Figure(committed.toString(), "Committed", FigureSize.Small, Modifier.weight(1f), sk.sky)
            Figure(onLeave.toString(), "On leave", FigureSize.Small, Modifier.weight(1f),
                if (onLeave == 0) sk.good else sk.warn)
        }
        Text(
            "From approved leave and confirmed bookings in RMS, not from utilisation.",
            style = MaterialTheme.typography.bodySmall, color = sk.labelText,
        )
    }
}

// ── 5 · Demand at a glance ──────────────────────────────────────────────────

/**
 * How much work is waiting, how much of it is the high-value international
 * kind, and one way through to act on it.
 */
@Composable
private fun DemandGlance(
    demand: List<Map<*, *>>,
    active: Int,
    upcoming: Int,
    onOpenDemand: () -> Unit,
) {
    val sk = MaterialTheme.skill
    val international = demand.count { it.str("delivery_mode").uppercase() in setOf("FMAT", "ILT") }

    SectionHeading(
        "Demand",
        if (demand.isEmpty()) "Nothing is waiting for an owner."
        else if (international > 0)
            "$international of ${demand.size} unallocated ${plural(demand.size, "batch is", "batches are")} FMAT or ILT — review those first."
        else "${demand.size} unallocated ${plural(demand.size, "batch", "batches")}, none international.",
    )
    SkillCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
            Figure(
                demand.size.toString(), "Unallocated", FigureSize.Medium, Modifier.weight(1f),
                if (demand.isEmpty()) sk.good else sk.crit,
            )
            Figure(
                international.toString(), "International", FigureSize.Medium, Modifier.weight(1f),
                if (international > 0) sk.cyan else sk.subText,
            )
            Figure(active.toString(), "Active", FigureSize.Medium, Modifier.weight(1f), sk.aqua)
            Figure(upcoming.toString(), "Upcoming", FigureSize.Medium, Modifier.weight(1f), sk.sky)
        }
        FilledTonalButton(
            onClick = onOpenDemand,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radii.chip),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = sk.brand.copy(alpha = 0.20f),
                contentColor = sk.ice,
            ),
        ) {
            Text(
                if (demand.isEmpty()) "Open demand pipeline" else "Allocate ${demand.size} open ${plural(demand.size, "batch", "batches")}",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/**
 * Route into the weekly copy and send messages. Sits in the briefing rather
 * than in a menu because it is a Monday morning job, and the dashboard is
 * where the manager already is on a Monday morning.
 */
@Composable
private fun WeeklyReportCta(onOpen: () -> Unit) {
    val sk = MaterialTheme.skill
    Row(
        Modifier
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(Radii.card))
            .pressable(onOpen)
            .padding(Space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Weekly report",
                style = MaterialTheme.typography.titleMedium,
                color = sk.bodyText,
            )
            Text(
                "One message for the team, one for each reportee. Ready to copy.",
                style = MaterialTheme.typography.bodySmall,
                color = sk.subText,
            )
        }
        Text("Open", style = MaterialTheme.typography.labelMedium, color = sk.sky)
    }
}

/** Route into the HR monthly performance report. */
@Composable
private fun HrReportCta(onOpen: () -> Unit) {
    val sk = MaterialTheme.skill
    Row(
        Modifier
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(Radii.card))
            .pressable(onOpen)
            .padding(Space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "HR Monthly Report",
                style = MaterialTheme.typography.titleMedium,
                color = sk.bodyText,
            )
            Text(
                "Qubits scores, utilisation, feedback and cert gaps — per reportee.",
                style = MaterialTheme.typography.bodySmall,
                color = sk.subText,
            )
        }
        Text("Open", style = MaterialTheme.typography.labelMedium, color = sk.sky)
    }
}

/** Route into the delivery agent. */
@Composable
private fun AgentCta(onOpen: () -> Unit) {
    val sk = MaterialTheme.skill
    Row(
        Modifier
            .fillMaxWidth()
            .accentGlass(sk.cyan)
            .pressable(onOpen)
            .padding(Space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Ask the delivery agent",
                style = MaterialTheme.typography.titleMedium,
                color = sk.bodyText,
            )
            Text(
                "Who needs attention, who is free, what to do next.",
                style = MaterialTheme.typography.bodySmall,
                color = sk.subText,
            )
        }
        Text("Open", style = MaterialTheme.typography.labelMedium, color = sk.cyan)
    }
}

// ── 5b2 · Certification band ────────────────────────────────────────────────

/**
 * Always-visible cert health strip — coverage bar + gap count.
 * The full donut/bar chart lives in ExploreSection for those who want the detail.
 */
@Composable
private fun CertificationBand(coverage: Int?, gaps: Int, loading: Boolean) {
    val sk = MaterialTheme.skill
    if (coverage == null && gaps == 0 && !loading) return

    SectionHeading(
        "Certification",
        when {
            loading -> "Refreshing capability data"
            gaps == 0 -> "No open gaps — fully covered"
            else -> "$gaps ${if (gaps == 1) "gap requires" else "gaps require"} follow-up"
        },
    )
    SkillCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = sk.brand, trackColor = sk.track)
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        coverage?.let { "$it% covered" } ?: "Coverage unknown",
                        style = MaterialTheme.typography.titleSmall,
                        color = when {
                            coverage == null -> sk.subText
                            coverage >= 80 -> sk.good
                            coverage >= 60 -> sk.warn
                            else -> sk.crit
                        },
                    )
                    Text(
                        if (gaps == 0) "No open gaps" else "$gaps ${if (gaps == 1) "gap" else "gaps"} open",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (gaps == 0) sk.good else sk.warn,
                    )
                }
                if (coverage != null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(sk.track),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(coverage / 100f)
                                .fillMaxHeight()
                                .background(
                                    when {
                                        coverage >= 80 -> sk.good
                                        coverage >= 60 -> sk.warn
                                        else -> sk.crit
                                    },
                                ),
                        )
                    }
                }
            }
        }
    }
}

// ── 6 · Explore ─────────────────────────────────────────────────────────────

/**
 * Reference material, not decisions. Collapsed by default so the briefing above
 * stays a briefing, expandable for the manager who wants the evidence.
 */
@Composable
private fun ExploreSection(
    kpis: Map<*, *>?, capKpis: Map<*, *>?, capabilityLoading: Boolean,
    ops: List<Map<*, *>>, upcoming: List<Map<*, *>>, capTrainers: List<Map<*, *>>,
    gaps: Int, openActions: List<Map<String, Any>>, atRisk: List<Map<*, *>>,
    utilHistory: List<Int>,
    onDrill: (Drill) -> Unit, onTrainerClick: (String, String) -> Unit,
) {
    val sk = MaterialTheme.skill
    var open by rememberSaveable { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(Radii.card))
            .pressable { open = !open }
            .padding(Space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Explore the detail",
            style = MaterialTheme.typography.titleMedium,
            color = sk.bodyText,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (open) "Hide" else "Show",
            style = MaterialTheme.typography.labelMedium,
            color = sk.sky,
        )
    }

    AnimatedVisibility(open) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {

            // Target corridor drawn behind the bars, so "healthy" is legible
            // without knowing that 70-85% is the band you are aiming for.
            if (utilHistory.size >= 2) {
                val last = utilHistory.last()
                SectionHeading(
                    "Utilisation",
                    when {
                        last in 70..85 -> "Inside the 70–85% target corridor."
                        last > 85 -> "Above the target corridor — the team is running hot."
                        else -> "Below the target corridor — there is unsold capacity."
                    },
                )
                SkillCard(Modifier.fillMaxWidth()) {
                    CorridorBars(
                        values = utilHistory,
                        labels = utilHistory.indices.map { i ->
                            if (i == utilHistory.lastIndex) "NOW" else "M${i + 1}"
                        },
                    )
                }
            }

            val coverage = capKpis?.intOrNull("avg_trainer_coverage_pct") ?: kpis?.intOrNull("cert_coverage_pct")
            val ready = capTrainers.count { it.int("readiness_score") >= 75 }
            val developing = capTrainers.count { it.int("readiness_score") in 50..74 }
            val blocked = (capTrainers.size - ready - developing).coerceAtLeast(0)
            SectionHeading(
                "Certification",
                if (capabilityLoading) "Refreshing capability data"
                else "$gaps ${plural(gaps, "gap requires", "gaps require")} follow-up",
            )
            SkillCard(Modifier.fillMaxWidth()) {
                val slices = listOf(
                    Slice("Covered", coverage ?: 0, sk.good),
                    Slice("Gap", if (coverage == null) 0 else 100 - coverage, sk.warn),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DonutChart(slices, coverage?.let { "$it%" } ?: "—", "coverage", size = 92.dp)
                    Spacer(Modifier.width(Space.lg))
                    ChartLegend(slices, Modifier.weight(1f))
                }
                BarChart(
                    listOf(
                        BarDatum("Ready", ready, sk.good),
                        BarDatum("Develop", developing, sk.warn),
                        BarDatum("Blocked", blocked, sk.crit),
                    ),
                    height = 92.dp,
                )
            }

            SectionHeading("Upcoming delivery", "Next scheduled commitments")
            SkillCard(Modifier.fillMaxWidth()) {
                if (upcoming.isEmpty()) {
                    Text(
                        "No upcoming deliveries returned by RMS.",
                        style = MaterialTheme.typography.bodySmall, color = sk.subText,
                    )
                } else upcoming.take(5).forEach { b ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .pressable { onDrill(batchDrill("Upcoming delivery", listOf(b))) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            b.str("start_at").takeLast(5).ifBlank { "TBC" },
                            style = MaterialTheme.typography.labelMedium,
                            color = sk.sky,
                            modifier = Modifier.width(54.dp),
                        )
                        Text(
                            b.str("course_name"),
                            style = MaterialTheme.typography.bodySmall,
                            color = sk.bodyText,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            SectionHeading(
                "Action centre",
                "${openActions.size} open across six management themes",
                trailing = if (atRisk.isEmpty()) null else "${atRisk.size} at risk",
            )
            SkillCard(Modifier.fillMaxWidth()) {
                BarChart(
                    listOf("Certification", "Underutilised", "Overallocated", "Unallocated", "Readiness", "Feedback")
                        .map { category ->
                            BarDatum(
                                category.take(5),
                                openActions.count {
                                    (it.str("category") + " " + it.str("title") + " " + it.str("detail"))
                                        .contains(category, true)
                                },
                                sk.warn,
                            )
                        },
                    height = 100.dp,
                )
                openActions.take(3).forEach { ActionPreview(it) }
                if (openActions.isNotEmpty()) {
                    TextButton(
                        onClick = { onDrill(actionDrill(openActions)) },
                        modifier = Modifier.align(Alignment.End),
                    ) { Text("Review all ${openActions.size}") }
                }
            }
        }
    }
}

private fun utilisationColour(value: Int?, sk: com.example.skillsync.theme.SkillColors): Color = when {
    value == null -> sk.subText
    value > 85 -> sk.crit
    value >= 55 -> sk.good
    value >= 30 -> sk.warn
    else -> sk.sky
}

@Composable
private fun TopPerformersPanel(
    ops: List<Map<*, *>>,
    capTrainers: List<Map<*, *>>,
    onTrainerClick: (String, String) -> Unit,
) {
    val sk = MaterialTheme.skill
    val capMap = remember(capTrainers) { capTrainers.associateBy { it.str("trainer_email").lowercase() } }
    val top = remember(ops) {
        ops.filter { (it.intOrNull("current_utilization") ?: 0) > 0 }
            .sortedByDescending { it.int("current_utilization") }
            .take(5)
    }
    if (top.isEmpty()) return
    SectionHeading("Top performers", "Carrying delivery, ranked by measured utilisation")
    SkillCard(Modifier.fillMaxWidth()) {
        top.forEachIndexed { index, trainer ->
            val util = trainer.int("current_utilization")
            val email = trainer.str("official_email")
            Row(
                Modifier
                    .fillMaxWidth()
                    .pressable { onTrainerClick(email, trainer.str("trainer_name")) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = sk.labelText,
                    modifier = Modifier.width(20.dp),
                )
                Avatar(trainer.str("trainer_name"), capMap[email.lowercase()]?.str("photo_url"), 32.dp)
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        trainer.str("trainer_name"),
                        style = MaterialTheme.typography.titleSmall,
                        color = sk.bodyText,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        trainer.str("capacity_bucket").ifBlank { "Measured delivery load" },
                        style = MaterialTheme.typography.bodySmall,
                        color = sk.subText, maxLines = 1,
                    )
                }
                Text(
                    "$util%",
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    color = utilisationColour(util, sk),
                )
            }
        }
    }
}

@Composable
private fun ActionPreview(a: Map<String, Any>) {
    val sk = MaterialTheme.skill
    val high = a.str("priority").equals("high", true)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(if (high) sk.crit else sk.warn, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(Space.sm))
        Column(Modifier.weight(1f)) {
            Text(
                a.str("title").ifBlank { "Manager action" },
                style = MaterialTheme.typography.bodyMedium,
                color = sk.bodyText, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                a.str("trainer_name"),
                style = MaterialTheme.typography.bodySmall,
                color = sk.subText, maxLines = 1,
            )
        }
    }
}

@Composable
private fun DeliveryPulseGlance(
    activeCount: Int,
    upcomingCount: Int,
    onLeaveCount: Int,
    onOpenDelivery: () -> Unit,
) {
    val sk = MaterialTheme.skill
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenDelivery() },
        shape = RoundedCornerShape(16.dp),
        color = sk.cardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, sk.cardBorder),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(sk.sky.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_calendar),
                            contentDescription = null,
                            tint = sk.sky,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Column {
                        Text(
                            "Delivery Schedule",
                            style = MaterialTheme.typography.titleMedium,
                            color = sk.bodyText,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Deliveries, Mocks, Webinars & Leaves",
                            style = MaterialTheme.typography.labelSmall,
                            color = sk.subText,
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = sk.sky.copy(alpha = 0.12f),
                ) {
                    Row(
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "Full Calendar",
                            style = MaterialTheme.typography.labelSmall,
                            color = sk.sky,
                            fontWeight = FontWeight.Bold,
                        )
                        Icon(
                            painterResource(R.drawable.ic_chevron),
                            contentDescription = null,
                            tint = sk.sky,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = sk.surface2.copy(alpha = 0.6f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, sk.glassBorder),
                ) {
                    Column(
                        Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(sk.good))
                            Text(
                                "DELIVERING",
                                style = MaterialTheme.typography.labelSmall,
                                color = sk.good,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            "$activeCount live",
                            style = MaterialTheme.typography.titleSmall,
                            color = sk.bodyText,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = sk.surface2.copy(alpha = 0.6f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, sk.glassBorder),
                ) {
                    Column(
                        Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            "UPCOMING",
                            style = MaterialTheme.typography.labelSmall,
                            color = sk.sky,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "$upcomingCount batches",
                            style = MaterialTheme.typography.titleSmall,
                            color = sk.bodyText,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = sk.surface2.copy(alpha = 0.6f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, sk.glassBorder),
                ) {
                    Column(
                        Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            "LEAVES",
                            style = MaterialTheme.typography.labelSmall,
                            color = sk.warn,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "$onLeaveCount on PTO",
                            style = MaterialTheme.typography.titleSmall,
                            color = sk.bodyText,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

private fun drill(title: String, rows: List<Map<*, *>>, util: Boolean = false) = Drill(title, "Tap a trainer for detail", rows.map { DrillRow(it.str("trainer_name").ifBlank { it.str("official_email") }, if (util) "${it.intOrNull("current_utilization")?.let { u -> "$u%" } ?: "not measured"} • ${it.str("capacity_bucket")}" else listOf(it.str("capacity_bucket"), it.str("recommended_action")).filter { s -> s.isNotBlank() }.joinToString(" • "), it.str("official_email").ifBlank { it.str("trainer_email") }) })
private fun batchDrill(title: String, rows: List<Map<*, *>>) = Drill(title, "Delivery schedule", rows.map { DrillRow(it.str("course_name").ifBlank { "Unnamed course" }, listOf(it.str("trainer_name"), it.str("delivery_mode"), it.str("start_at")).filter(String::isNotBlank).joinToString(" • ")) })
private fun actionDrill(rows: List<Map<String, Any>>) = Drill("Action centre", "Open decisions requiring manager attention", rows.map { DrillRow(it.str("title").ifBlank { "Manager action" }, listOf(it.str("trainer_name"), it.str("category"), it.str("priority")).filter(String::isNotBlank).joinToString(" • "), it.str("trainer_email").takeIf(String::isNotBlank)) })
