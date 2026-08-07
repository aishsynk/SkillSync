package com.example.skillsync.ui.trainer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.skillsync.R
import com.example.skillsync.theme.StatusBarIcons
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.components.*
import com.example.skillsync.ui.main.relativeAge
import com.example.skillsync.ui.main.projectNextUtilization

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Trainer360Screen(
    trainerEmail: String,
    trainerName: String,
    managerEmail: String = "",
    onBack: () -> Unit,
    viewModel: Trainer360ViewModel = viewModel(),
) {
    LaunchedEffect(trainerEmail, managerEmail) { viewModel.load(trainerEmail, managerEmail) }
    RefreshOnResume(key = trainerEmail) { viewModel.refresh(trainerEmail, managerEmail) }

    val state by viewModel.state.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    StatusBarIcons(lightIcons = true)

    Scaffold(
        containerColor = MaterialTheme.skill.pageBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            trainerName.ifBlank { "Trainer" },
                            fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text("Trainer 360", fontSize = 9.5.sp, color = Color.White.copy(alpha = 0.78f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_back),
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
    ) { pv ->
        Box(Modifier.fillMaxSize().padding(pv)) {
            when (val s = state) {
                is Trainer360State.Loading -> Column(
                    Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ShimmerBox(height = 150.dp, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                    repeat(4) {
                        ShimmerBox(height = 92.dp, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
                    }
                }
                is Trainer360State.Error -> Column(
                    Modifier.fillMaxSize().padding(28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        s.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.skill.subText,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.refresh(trainerEmail, managerEmail) },
                        shape = RoundedCornerShape(10.dp),
                    ) { Text("Try again") }
                }
                is Trainer360State.Success -> Column(Modifier.fillMaxSize()) {
                    if (s.fromCache) {
                        Row(
                            Modifier.fillMaxWidth()
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(vertical = 4.dp, horizontal = 16.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                "Offline — showing data from ${relativeAge(s.cachedAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                    PullToRefreshBox(
                        isRefreshing = refreshing,
                        onRefresh = { viewModel.refresh(trainerEmail, managerEmail) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Trainer360Content(s.data)
                    }
                }
            }
        }
    }
}

@Composable
internal fun Trainer360Content(data: Map<String, Any>) {
    val sk = MaterialTheme.skill
    val identity = data.obj("identity")
    val metrics  = data.obj("metrics")
    val util     = data.obj("utilization")
    val cap      = data.obj("capability")
    val certs    = data.obj("certifications")
    val delivery = data.obj("delivery")
    val feedback = data.obj("feedback")
    val avail    = data.obj("availability")
    // Delivery readiness — the trainer-360 endpoint already returns these in [metrics]
    // as readiness_score, risk_score, readiness_bucket. Surface them in a dedicated section.
    val deliveryReadiness = data.obj("delivery_readiness") ?: metrics

    val series = util?.list("series").orEmpty()
    val courses = cap?.list("courses").orEmpty()
    val assignments = delivery?.list("assignments").orEmpty()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Appear(0) { IdentityCard(identity, util, cap, certs) } }
        item { Appear(1) { PersonalDetails(identity) } }
        item { Appear(2) { UtilisationSection(util, series, delivery) } }
        item { Appear(3) { CapabilityMetrics(metrics) } }
        item { Appear(4) { DeliveryReadinessSection(deliveryReadiness, feedback) } }
        item { Appear(5) { RiskSection(metrics, feedback) } }
        item { Appear(6) { CertificationSection(certs) } }
        item { Appear(7) { CapabilitySection(cap, courses) } }
        item { Appear(8) { DeliverySection(delivery, assignments) } }
        item { Appear(9) { FeedbackSection(feedback) } }
        item { Appear(10) { SPOFAndActionsSection(cap, metrics) } }
        item { Appear(11) { AvailabilitySection(avail) } }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

// ── Identity ──────────────────────────────────────────────────────────────────

@Composable
private fun IdentityCard(
    identity: Map<*, *>?,
    util: Map<*, *>?,
    cap: Map<*, *>?,
    certs: Map<*, *>?,
) {
    val sk = MaterialTheme.skill
    val name = identity?.str("name").orEmpty().ifBlank { "Trainer" }
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = sk.heroBg),
    ) {
        Column(
            Modifier
                .background(Brush.linearGradient(listOf(sk.heroBg, sk.heroBgAlt)))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(name, identity?.str("photo_url"), 58.dp)
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold, color = sk.heroText,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    identity?.str("designation")?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = sk.heroMuted)
                    }
                    Text(
                        identity?.str("email").orEmpty(),
                        style = MaterialTheme.typography.labelSmall, color = sk.heroMuted,
                        fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HeroFigure(
                    "Utilisation",
                    if (util?.bool("available") == true) "${util.int("current")}%" else "—",
                    sk.teal,
                )
                HeroFigure("Peak", "${util?.int("peak") ?: 0}%", sk.blue)
                HeroFigure("Courses", "${cap?.int("total_courses") ?: 0}", sk.indigo)
                HeroFigure("Certs", "${certs?.int("count") ?: 0}", sk.amber)
            }
        }
    }
}

@Composable
private fun PersonalDetails(identity: Map<*, *>?) {
    val sk = MaterialTheme.skill
    val languages = identity?.list("languages").orEmpty()
    val clients = identity?.strings("clients").orEmpty()

    SectionCard("Personal details", null) {
        DetailRow("Email", identity?.str("email").orEmpty())
        DetailRow("Employee code", identity?.str("emp_code").orEmpty())
        DetailRow("Trainer id", identity?.str("trainer_id").orEmpty())
        // Designation is already the subtitle under the name in the header card;
        // repeating it here just makes the table longer.
        val reportsTo = identity?.str("reports_to").orEmpty()
        DetailRow(
            "Reports to",
            if (reportsTo.isBlank()) ""
            else reportsTo + if (identity?.bool("direct_report") == true) " (direct)" else " (indirect)",
        )
        DetailRow("Joined", identity?.str("date_of_joining").orEmpty().longDate())
        DetailRow(
            "Experience",
            (identity?.get("tenure_years") as? Number)?.let { "%.1f years at Koenig".format(it.toFloat()) }
                .orEmpty(),
        )
        if (identity?.bool("trainer_plus") == true) DetailRow("Trainer Plus", "Yes")
        DetailRow(
            "Languages",
            languages.joinToString(", ") { "${it.str("language")} (${it.str("level")})" },
        )
        DetailRow("Clients delivered for", clients.size.takeIf { it > 0 }?.toString().orEmpty())

        identity?.str("summary")?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = sk.bodyText)
        }
        if (clients.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Recent clients",
                style = MaterialTheme.typography.labelSmall, color = sk.subText,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            FlowChips(clients.take(10), sk.blue)
        }
        if (identity?.bool("has_resume") != true) {
            EmptyNote("RMS holds no resume record for this trainer, so photo, certifications and experience are unavailable.")
        }
    }
}

// ── Utilisation ───────────────────────────────────────────────────────────────

@Composable
private fun UtilisationSection(
    util: Map<*, *>?,
    series: List<Map<*, *>>,
    delivery: Map<*, *>?,
) {
    val sk = MaterialTheme.skill
    val available = util?.bool("available") == true
    val bench = util?.intOrNull("bench_months")

    SectionCard(
        "Utilisation",
        if (available) "${series.size} months on record" else "RMS returned no utilisation",
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Figure("Current", if (available) "${util.int("current")}%" else "—", sk.teal)
            Figure("Peak", "${util?.int("peak") ?: 0}%", sk.blue)
            Figure("Active batches", "${delivery?.int("current") ?: 0}", sk.indigo)
            Figure("Upcoming", "${util?.int("upcoming_load") ?: 0}", sk.amber)
            Figure(
                "Bench",
                // 0 means "working this month"; the count is months since the
                // most recent month with any load.
                bench?.let { if (it == 0) "none" else "$it mo" } ?: "—",
                if ((bench ?: 0) > 2) sk.red else sk.green,
            )
        }
        if (series.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            TrendChart(
                points = series.map { TrendPoint(it.str("month").take(3), it.int("utilization")) },
                tint = sk.teal,
                height = 100.dp,
            )
            val projection = remember(series) {
                projectNextUtilization(series.map { it.int("utilization") })
            }
            if (projection != null) {
                Spacer(Modifier.height(8.dp))
                val tint = when (projection.direction) {
                    "Rising"  -> if (projection.projected >= 90) sk.red else sk.teal
                    "Falling" -> if (projection.projected <= 25) sk.amber else sk.teal
                    else      -> sk.subText
                }
                Text(
                    "Next month (trend projection): ${projection.projected}% · ${projection.direction}",
                    style = MaterialTheme.typography.labelSmall,
                    color = tint, fontSize = 9.sp,
                )
            }
        } else {
            EmptyNote("No utilisation series returned — this is missing data, not zero utilisation.")
        }
    }
}

// ── Capability metrics ────────────────────────────────────────────────────────

@Composable
private fun CapabilityMetrics(metrics: Map<*, *>?) {
    val sk = MaterialTheme.skill
    val readiness = metrics?.intOrNull("readiness_score")
    val risk = metrics?.intOrNull("risk_score")
    val match = metrics?.intOrNull("skill_match_pct")
    val rank = metrics?.intOrNull("team_rank")
    val teamSize = metrics?.int("team_size") ?: 0

    SectionCard("Capability metrics", "Computed from Qubits, catalogue depth and capacity") {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GaugeChart(
                value = readiness,
                label = metrics?.str("readiness_bucket").orEmpty().ifBlank { "readiness" },
                tint = when (metrics?.str("readiness_bucket")) {
                    "Ready" -> sk.green
                    "Developing" -> sk.amber
                    "Needs support" -> sk.red
                    else -> sk.subText
                },
                size = 92.dp,
            )
            GaugeChart(
                value = risk,
                label = metrics?.str("risk_level").orEmpty().ifBlank { "risk" },
                tint = when (metrics?.str("risk_level")) {
                    "High" -> sk.red
                    "Medium" -> sk.amber
                    "Low" -> sk.green
                    else -> sk.subText
                },
                size = 92.dp,
            )
            GaugeChart(
                value = match,
                label = "skill match",
                tint = when {
                    match == null -> sk.subText
                    match >= 80 -> sk.green
                    match >= 50 -> sk.amber
                    else -> sk.red
                },
                size = 92.dp,
            )
            Column(
                Modifier.height(92.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    // Ranking one person against nobody is not a ranking.
                    if (rank != null && teamSize > 1) "#$rank" else "—",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold, color = sk.indigo,
                )
                Text(
                    if (teamSize > 1) "of $teamSize in team" else "team ranking",
                    style = MaterialTheme.typography.labelSmall, color = sk.subText, fontSize = 9.sp,
                )
            }
        }
        if (risk == null) {
            Spacer(Modifier.height(6.dp))
            EmptyNote("Risk is Unknown: RMS returned no feedback, incident or utilisation signal. That is an absence of evidence, not a clean record.")
        }
    }
}

// ── Delivery Readiness (Phase 4 Stream 1) ───────────────────────────────────────

/**
 * Delivery Readiness section — the single source of truth for deployment decisions.
 *
 * Reads from [metrics] (readiness_score, readiness_bucket, risk_score, risk_level)
 * and [feedback] (negative_total) to surface:
 *   • Readiness score with animated gauge + label
 *   • Capacity status
 *   • Delivery strengths and constraints
 *   • Actionable recommendations for the manager
 *
 * All data comes from the trainer-360 endpoint — no extra API call.
 */
@Composable
private fun DeliveryReadinessSection(
    metrics: Map<*, *>?,
    feedback: Map<*, *>?,
) {
    val sk = MaterialTheme.skill

    // Readiness score — use delivery_readiness_score if available (Phase 4 backend),
    // otherwise fall back to readiness_score from capability metrics.
    val score        = metrics?.intOrNull("delivery_readiness_score")
        ?: metrics?.intOrNull("readiness_score")
    val label        = metrics?.str("delivery_readiness_label").orEmpty()
        .ifBlank {
            when {
                score == null    -> ""
                score >= 80      -> "Ready"
                score >= 65      -> "Ready with Prep"
                score >= 45      -> "Needs Mentoring"
                else             -> "Hold"
            }
        }
    val capacity     = metrics?.str("delivery_capacity_status").orEmpty()
        .ifBlank { metrics?.str("capacity_bucket").orEmpty() }
    val riskLevel    = metrics?.str("delivery_risk_level").orEmpty()
        .ifBlank { metrics?.str("risk_level").orEmpty() }
    val strengths    = metrics?.list("delivery_strengths").orEmpty()
        .map { it.str("value").ifBlank { it.toString() } }.filter { it.isNotBlank() }
    val constraints  = metrics?.list("delivery_constraints").orEmpty()
        .map { it.str("value").ifBlank { it.toString() } }.filter { it.isNotBlank() }
    val recs         = metrics?.list("delivery_recommendations").orEmpty()
    val confidence   = metrics?.intOrNull("delivery_confidence")

    val (labelColor, labelEmoji) = when (label) {
        "Ready"            -> sk.green  to "🟢"
        "Ready with Prep"  -> sk.teal   to "🟡"
        "Needs Mentoring" -> sk.amber  to "🟠"
        "Hold"             -> sk.red    to "🔴"
        else               -> sk.subText to "⌓"
    }
    val capacityColor = when (capacity) {
        "Overloaded"   -> sk.red
        "Balanced"     -> sk.teal
        "Underutilized" -> sk.green
        else           -> sk.subText
    }
    val riskColor = when (riskLevel) {
        "High"   -> sk.red
        "Medium" -> sk.amber
        "Low"    -> sk.green
        else     -> sk.subText
    }

    SectionCard(
        "Delivery Readiness",
        "Manager deployment decision support",
    ) {
        // Header row — score gauge + label badges
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GaugeChart(
                value = score,
                label = label.ifBlank { "readiness" },
                tint = labelColor,
                size = 92.dp,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (label.isNotBlank()) {
                    Surface(
                        color = labelColor.copy(alpha = 0.14f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            "$labelEmoji $label",
                            style = MaterialTheme.typography.titleSmall,
                            color = labelColor,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
                if (capacity.isNotBlank()) {
                    Surface(
                        color = capacityColor.copy(alpha = 0.12f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            capacity,
                            style = MaterialTheme.typography.labelSmall,
                            color = capacityColor,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        )
                    }
                }
                if (riskLevel.isNotBlank()) {
                    Surface(
                        color = riskColor.copy(alpha = 0.12f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            "Risk: $riskLevel",
                            style = MaterialTheme.typography.labelSmall,
                            color = riskColor,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        )
                    }
                }
                confidence?.let {
                    Text(
                        "Confidence: $it%",
                        style = MaterialTheme.typography.labelSmall,
                        color = sk.subText, fontSize = 9.sp,
                    )
                }
            }
        }

        // Strengths + Constraints
        if (strengths.isNotEmpty() || constraints.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = sk.cardBorder)
            Spacer(Modifier.height(10.dp))
            if (strengths.isNotEmpty()) {
                Label("Delivery Strengths")
                Spacer(Modifier.height(6.dp))
                strengths.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_check), null,
                            tint = sk.green, modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(s, style = MaterialTheme.typography.bodySmall, color = sk.bodyText)
                    }
                }
            }
            if (constraints.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Label("Constraints")
                Spacer(Modifier.height(6.dp))
                constraints.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_alert), null,
                            tint = sk.amber, modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(c, style = MaterialTheme.typography.bodySmall, color = sk.bodyText)
                    }
                }
            }
        }

        // Recommendations — the most actionable part
        if (recs.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = sk.cardBorder)
            Spacer(Modifier.height(10.dp))
            Label("Recommendations")
            Spacer(Modifier.height(8.dp))
            recs.take(4).forEach { rec ->
                val priority = rec.str("priority")
                val priorityColor = when (priority) {
                    "High"   -> sk.red
                    "Medium" -> sk.amber
                    else     -> sk.teal
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .background(priorityColor.copy(alpha = 0.06f))
                        .padding(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            color = priorityColor.copy(alpha = 0.16f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                priority.ifBlank { rec.str("recommendation_type").ifBlank { "Action" } },
                                style = MaterialTheme.typography.labelSmall,
                                color = priorityColor, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                fontSize = 8.5.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        Text(
                            rec.str("title"),
                            style = MaterialTheme.typography.titleSmall,
                            color = sk.bodyText,
                        )
                    }
                    rec.str("reason").takeIf { it.isNotBlank() }?.let { reason ->
                        Spacer(Modifier.height(4.dp))
                        Text(reason, style = MaterialTheme.typography.bodySmall, color = sk.subText)
                    }
                    rec.str("manager_action").takeIf { it.isNotBlank() }?.let { action ->
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painterResource(R.drawable.ic_flag), null,
                                tint = priorityColor, modifier = Modifier.size(12.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                action,
                                style = MaterialTheme.typography.labelSmall,
                                color = priorityColor,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }

        if (score == null && label.isBlank()) {
            EmptyNote(
                "Delivery readiness is computed from Qubit scores, assignment history, " +
                "capacity and quality signals. Data will appear once the trainer has RMS records."
            )
        }
    }
}

// ── Certifications and the gap analysis ───────────────────────────────────────

@Composable
private fun CertificationSection(certs: Map<*, *>?) {
    val sk = MaterialTheme.skill
    val held = certs?.list("held").orEmpty()
    val missing = certs?.list("missing").orEmpty()
    val recommended = certs?.list("recommended").orEmpty()
    val accreditations = certs?.strings("accreditations").orEmpty()
    val coverage = certs?.intOrNull("coverage_pct")

    SectionCard(
        "Certifications",
        "${held.size} held · ${missing.size} gap${if (missing.size == 1) "" else "s"}" +
            (coverage?.let { " · $it% of taught tracks covered" } ?: ""),
    ) {
        // Accreditation is the right to teach a vendor's material and is a
        // different thing from having passed that vendor's exams. Showing them in
        // one list is what made "1 certification" look wrong next to nine badges.
        if (accreditations.isNotEmpty()) {
            Label("Teaching accreditation")
            FlowChips(accreditations, sk.teal)
            Spacer(Modifier.height(12.dp))
        }

        Label("Certifications held")
        if (held.isEmpty()) {
            EmptyNote("No exam certifications on the RMS resume record.")
        } else {
            held.forEach { c ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_certificate), null,
                        tint = sk.green, modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        c.str("name"),
                        style = MaterialTheme.typography.bodySmall, color = sk.bodyText,
                        modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    c.str("code").takeIf { it.isNotBlank() }?.let { CodeChip(it, sk.green) }
                }
            }
        }

        if (missing.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_gap), null, tint = sk.red, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Label("Missing — teaches the course, holds no certification")
            }
            Spacer(Modifier.height(4.dp))
            missing.forEach { m ->
                val high = m.str("priority") == "high"
                Row(
                    Modifier.fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background((if (high) sk.red else sk.amber).copy(alpha = 0.08f))
                        .padding(horizontal = 9.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CodeChip(m.str("code"), if (high) sk.red else sk.amber)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            m.str("name"),
                            style = MaterialTheme.typography.bodySmall, color = sk.bodyText,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "teaches ${m.str("because")}" +
                                (m.int("delivered").takeIf { it > 0 }?.let { " · $it delivered" } ?: ""),
                            style = MaterialTheme.typography.labelSmall, color = sk.subText,
                            fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (high) {
                        Text(
                            "HIGH", style = MaterialTheme.typography.labelSmall,
                            color = sk.red, fontWeight = FontWeight.Bold, fontSize = 8.sp,
                        )
                    }
                }
            }
        }

        if (recommended.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Label("Recommended next")
            Spacer(Modifier.height(4.dp))
            recommended.forEach { r ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CodeChip(r.str("code"), sk.blue)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            r.str("name"),
                            style = MaterialTheme.typography.bodySmall, color = sk.bodyText,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        r.str("because").takeIf { it.isNotBlank() }?.let {
                            Text(
                                "natural next step from $it",
                                style = MaterialTheme.typography.labelSmall,
                                color = sk.subText, fontSize = 9.sp,
                            )
                        }
                    }
                }
            }
        }

        if (missing.isEmpty() && held.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_check), null, tint = sk.green, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Certified for every exam-linked course they teach.",
                    style = MaterialTheme.typography.labelSmall, color = sk.green,
                )
            }
        }
    }
}

// ── Capability, delivery, feedback, availability ──────────────────────────────

@Composable
private fun CapabilitySection(cap: Map<*, *>?, courses: List<Map<*, *>>) {
    val sk = MaterialTheme.skill
    var expanded by remember { mutableStateOf(false) }
    val shown = if (expanded) courses else courses.take(12)

    SectionCard(
        "Capability",
        "${cap?.int("approved_courses") ?: 0} approved · avg Qubits ${cap?.int("avg_qubits") ?: 0} · " +
            "${cap?.int("future_skills") ?: 0} future skills",
    ) {
        if (courses.isEmpty()) {
            EmptyNote("RMS returned no course capability for this trainer.")
        } else {
            shown.forEach { CourseRow(it) }
            if (courses.size > 12) {
                Text(
                    if (expanded) "Show fewer" else "Show all ${courses.size} courses",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable { expanded = !expanded },
                )
            }
        }
    }
}

@Composable
private fun DeliverySection(delivery: Map<*, *>?, assignments: List<Map<*, *>>) {
    SectionCard(
        "Delivery",
        "${delivery?.int("total") ?: 0} assignments · ${delivery?.int("upcoming") ?: 0} upcoming",
    ) {
        if (assignments.isEmpty()) EmptyNote("No assignments in the last 12 months.")
        else assignments.take(10).forEach { AssignmentRow(it) }
    }
}

@Composable
private fun RiskSection(metrics: Map<*, *>?, feedback: Map<*, *>?) {
    val sk = MaterialTheme.skill
    val riskScore = metrics?.intOrNull("risk_score")
    val riskLevel = metrics?.str("risk_level").orEmpty()
    val negCount = feedback?.int("negative_total") ?: 0
    val hrNeg = feedback?.int("hr_negative") ?: 0

    val (riskColor, riskEmoji) = when (riskLevel) {
        "High"   -> sk.red    to "🔴"
        "Medium" -> sk.amber  to "🟡"
        "Low"    -> sk.green  to "🟢"
        else     -> sk.subText to "⌓"
    }

    SectionCard("Feedback Risk", "Incidents and HR flags") {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (riskScore != null) {
                GaugeChart(
                    value = riskScore,
                    label = riskLevel.ifBlank { "risk" },
                    tint = riskColor,
                    size = 92.dp,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (riskLevel.isNotBlank()) {
                    Surface(
                        color = riskColor.copy(alpha = 0.14f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            "$riskEmoji $riskLevel Risk",
                            style = MaterialTheme.typography.titleSmall,
                            color = riskColor,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Figure("Negative", "$negCount", if (negCount > 0) sk.red else sk.green)
                    Figure("HR Issues", "$hrNeg", if (hrNeg > 0) sk.red else sk.subText)
                }
            }
        }

        // Risk summary
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = sk.cardBorder)
        Spacer(Modifier.height(10.dp))
        Label("Risk Indicators")
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RiskIndicator(
                "Feedback Issues",
                negCount > 0,
                negCount,
                if (negCount > 2) sk.red else if (negCount > 0) sk.amber else sk.green,
                Modifier.weight(1f),
            )
            RiskIndicator(
                "HR Flags",
                hrNeg > 0,
                hrNeg,
                if (hrNeg > 0) sk.red else sk.green,
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RiskIndicator(
    label: String,
    hasRisk: Boolean,
    count: Int,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val sk = MaterialTheme.skill
    Surface(
        modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.09f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        color = tint.copy(alpha = 0.09f),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (hasRisk) "⚠️ $count" else "✓",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = tint,
                fontSize = 18.sp,
            )
            Text(label, style = MaterialTheme.typography.labelSmall, color = sk.subText, fontSize = 8.5.sp)
        }
    }
}

@Composable
private fun FeedbackSection(feedback: Map<*, *>?) {
    val sk = MaterialTheme.skill
    SectionCard("Feedback & incidents", null) {
        val neg = feedback?.int("negative_total") ?: 0
        val hrP = feedback?.int("hr_positive") ?: 0
        val hrN = feedback?.int("hr_negative") ?: 0
        val details = feedback?.list("negative_details").orEmpty()
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Figure("Negative", "$neg", if (neg > 0) sk.red else sk.green)
            Figure("HR positive", "$hrP", sk.green)
            Figure("HR negative", "$hrN", if (hrN > 0) sk.red else sk.subText)
        }
        if (details.isEmpty() && neg == 0 && hrP == 0 && hrN == 0) {
            Spacer(Modifier.height(8.dp))
            EmptyNote("RMS returned no feedback records — this is an absence of data, not a clean record.")
        }
        details.take(5).forEach { d ->
            Spacer(Modifier.height(8.dp))
            Column {
                Text(
                    d.str("feedback_question").ifBlank { "Feedback" },
                    style = MaterialTheme.typography.bodySmall, color = sk.bodyText,
                )
                Text(
                    listOf(d.str("client_name"), d.str("dates"), d.str("delivery_mode"))
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = sk.subText,
                )
            }
        }

        // Per-question responses — positive and negative both, unlike the
        // negative-only list above. New field; empty until RMS confirms it.
        val responses = feedback?.list("responses").orEmpty()
        if (responses.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = sk.cardBorder)
            Spacer(Modifier.height(8.dp))
            Label("Recent Feedback")
            responses.take(5).forEach { r ->
                Spacer(Modifier.height(8.dp))
                Column {
                    Text(
                        r.str("question").ifBlank { "Feedback" },
                        style = MaterialTheme.typography.bodySmall, color = sk.bodyText,
                    )
                    if (r.str("answer").isNotBlank()) {
                        Text(
                            r.str("answer"),
                            style = MaterialTheme.typography.labelSmall, color = sk.subText,
                        )
                    }
                    if (r.str("date").isNotBlank()) {
                        Text(
                            r.str("date"),
                            style = MaterialTheme.typography.labelSmall, color = sk.subText, fontSize = 9.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SPOFAndActionsSection(cap: Map<*, *>?, metrics: Map<*, *>?) {
    val sk = MaterialTheme.skill
    val courses = cap?.list("courses").orEmpty()
    val readinessScore = metrics?.intOrNull("delivery_readiness_score")
        ?: metrics?.intOrNull("readiness_score") ?: 0

    // Identify courses where this trainer might be the only expert
    val criticalCourses = courses.filter { course ->
        val level = course.str("skill_level").lowercase()
        level.contains("advanced") || level.contains("architect") || level.contains("expert")
    }.take(3)

    SectionCard("Strategic Impact & Actions", "Succession planning & key-person risk") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // SPOF Risk Indicator
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (criticalCourses.isNotEmpty()) sk.red.copy(alpha = 0.09f) else sk.green.copy(alpha = 0.09f))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        if (criticalCourses.isNotEmpty()) "⚠️ Critical Skill Owner" else "✓ Good Coverage",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (criticalCourses.isNotEmpty()) sk.red else sk.green,
                    )
                    Text(
                        "${criticalCourses.size} advanced courses",
                        style = MaterialTheme.typography.labelSmall,
                        color = sk.subText, fontSize = 9.sp,
                    )
                }
                if (criticalCourses.isNotEmpty()) {
                    Surface(
                        Modifier.weight(1f),
                        color = sk.red.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            "Plan succession",
                            style = MaterialTheme.typography.labelSmall,
                            color = sk.red,
                            fontWeight = FontWeight.Bold, fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            if (criticalCourses.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = sk.cardBorder)
                Spacer(Modifier.height(8.dp))
                Label("Critical Courses (Advanced Level)")
                Spacer(Modifier.height(6.dp))
                criticalCourses.forEach { course ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_alert), null,
                            tint = sk.red, modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            course.str("course_name"),
                            style = MaterialTheme.typography.bodySmall,
                            color = sk.bodyText,
                        )
                    }
                }
            }

            // Recommended Actions
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = sk.cardBorder)
            Spacer(Modifier.height(8.dp))
            Label("Recommended Actions")
            Spacer(Modifier.height(8.dp))
            val actions = buildList {
                if (criticalCourses.isNotEmpty()) {
                    add("Develop backup trainer for advanced courses")
                }
                if (readinessScore >= 80) {
                    add("Consider for mentoring / knowledge transfer")
                }
                add("Quarterly skill validation & career planning")
            }
            actions.forEach { action ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(sk.teal.copy(alpha = 0.06f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_check), null,
                        tint = sk.teal, modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(action, style = MaterialTheme.typography.bodySmall, color = sk.bodyText)
                }
            }
        }
    }
}

@Composable
private fun AvailabilitySection(avail: Map<*, *>?) {
    val sk = MaterialTheme.skill
    SectionCard("Availability", null) {
        val off = avail?.obj("off_dates")
        if (off == null || off.isEmpty()) {
            EmptyNote("RMS exposes no leave or absence endpoint, and no off-dates are recorded for this trainer.")
        } else {
            off.forEach { (k, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(
                        k.toString().replace('_', ' ').replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall, color = sk.subText,
                        modifier = Modifier.weight(1f),
                    )
                    Text(v.toString(), style = MaterialTheme.typography.labelSmall, color = sk.bodyText)
                }
            }
        }
    }
}

// ── Pieces ────────────────────────────────────────────────────────────────────

@Composable
private fun HeroFigure(label: String, value: String, tint: Color) {
    Column {
        Text(
            value, style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold, color = tint,
        )
        Text(
            label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.skill.heroMuted, fontSize = 9.sp,
        )
    }
}

@Composable
private fun Figure(label: String, value: String, tint: Color) {
    Column {
        Text(
            value, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = tint,
        )
        Text(
            label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.skill.subText, fontSize = 9.sp,
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.skill.subText,
        fontWeight = FontWeight.Bold, fontSize = 9.sp,
    )
}

@Composable
private fun CodeChip(code: String, tint: Color) {
    Surface(color = tint.copy(alpha = 0.16f), shape = RoundedCornerShape(6.dp)) {
        Text(
            code,
            style = MaterialTheme.typography.labelSmall, color = tint,
            fontWeight = FontWeight.Bold, fontSize = 9.5.sp,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

/** Skips itself when the value is blank, so the card never shows empty rows. */
@Composable
private fun DetailRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.skill.subText, modifier = Modifier.width(118.dp),
        )
        Text(
            value, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.skill.bodyText, modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SectionCard(title: String, subtitle: String?, body: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.skill.cardBg),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.skill.bodyText)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.skill.subText)
            }
            Spacer(Modifier.height(10.dp))
            body()
        }
    }
}

@Composable
private fun CourseRow(c: Map<*, *>) {
    val sk = MaterialTheme.skill
    val q = c.int("qubits_score")
    val tint = when {
        q >= 85 -> sk.green
        q >= 60 -> sk.teal
        q > 0 -> sk.amber
        else -> sk.subText
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                c.str("course"),
                style = MaterialTheme.typography.bodySmall,
                color = sk.bodyText, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    c.str("vendor").takeIf { it.isNotBlank() },
                    c.str("skill_level").takeIf { it.isNotBlank() }?.let { "L$it" },
                    c.int("delivered").takeIf { it > 0 }?.let { "$it delivered" },
                    if (c.bool("future_skill")) "future skill" else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, color = sk.subText, maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (c.bool("approved")) {
            Icon(
                painterResource(R.drawable.ic_check), null,
                tint = sk.green, modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Surface(color = tint.copy(alpha = 0.14f), shape = RoundedCornerShape(10.dp)) {
            Text(
                "Q$q",
                style = MaterialTheme.typography.labelSmall, color = tint,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun AssignmentRow(a: Map<*, *>) {
    val sk = MaterialTheme.skill
    val state = a.str("state")
    val tint = when (state) {
        "current" -> sk.teal
        "upcoming" -> sk.blue
        else -> sk.subText
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(tint))
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                a.str("course"),
                style = MaterialTheme.typography.bodySmall,
                color = sk.bodyText, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    a.str("start_at").takeIf { it.isNotBlank() }?.shortDate(),
                    a.str("mode").takeIf { it.isNotBlank() },
                    a.intOrNull("participants")?.takeIf { it > 0 }?.let { "$it pax" },
                    a.str("location").takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, color = sk.subText, maxLines = 1,
            )
            // Roster is only fetched for the current + next assignment (see
            // backend), so this is populated for at most two rows here.
            val roster = a.list("participants")
            if (roster.isNotEmpty()) {
                Text(
                    "With: " + roster.take(3).joinToString(", ") { it.str("name") } +
                        (if (roster.size > 3) " +${roster.size - 3} more" else ""),
                    style = MaterialTheme.typography.labelSmall, color = sk.subText,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Surface(color = tint.copy(alpha = 0.14f), shape = RoundedCornerShape(10.dp)) {
            Text(
                state.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall, color = tint,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun FlowChips(items: List<String>, tint: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach {
                    Surface(color = tint.copy(alpha = 0.13f), shape = RoundedCornerShape(8.dp)) {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall, color = tint,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 9.5.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.skill.subText)
}
