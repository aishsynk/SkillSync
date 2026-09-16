package com.example.skillsync.feature.training.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.skillsync.R
import com.example.skillsync.theme.AuroraBackground
import com.example.skillsync.theme.IconSlot
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.StatusBarIcons
import com.example.skillsync.theme.accentGlass
import com.example.skillsync.theme.glassSurface
import com.example.skillsync.theme.skill
import com.example.skillsync.core.ui.*
import com.example.skillsync.feature.home.CourseCurriculumSheet
import kotlinx.coroutines.launch
import androidx.compose.material3.Text

/**
 * Everything known about one unallocated batch, plus the four actions a manager
 * takes from here: read the outline, claim it themselves, claim it for a
 * reportee, or message the team about it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchDetailScreen(
    batch: Map<*, *>,
    managerEmail: String,
    reportees: List<Pair<String, String>>,   // name to email
    markState: MarkState,
    operationalContext: com.example.skillsync.core.network.DemandContextResponse? = null,
    operationalContextLoading: Boolean = false,
    operationalContextError: String? = null,
    gatedCandidates: com.example.skillsync.core.network.AllocationCandidatesResponse? = null,
    gatedCandidatesLoading: Boolean = false,
    gatedCandidatesUnverified: String? = null,
    onMarkSkill: (courseId: String, trainerEmail: String, level: Int, date: String, who: String) -> Unit,
    /** One course, one or more trainers, reported honestly on partial failure. */
    onMarkSkillMany: (courseId: String, trainers: List<Pair<String, String>>, level: Int, date: String) -> Unit = { _, _, _, _ -> },
    onClearMark: () -> Unit,
    onBack: () -> Unit,
) {
    val sk = MaterialTheme.skill
    val context = LocalContext.current
    StatusBarIcons(lightIcons = true)

    var showMine by remember { mutableStateOf(false) }
    var showReportee by remember { mutableStateOf(false) }
    var showCurriculumSheet by remember { mutableStateOf(false) }
    var showNetworkSheet by remember { mutableStateOf(false) }
    var showEligibilitySheet by remember { mutableStateOf(false) }
    var shareTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showMessagePreview by remember { mutableStateOf(false) }
    // Per-row "Mark" from the team-skill panel: preselect that reportee and,
    // where known, open the dialog at the assignment's required level.
    var markFor by remember { mutableStateOf<Pair<String, String>?>(null) }
    var markForLevel by remember { mutableStateOf<Int?>(null) }

    val courseId = batch.str("course_id")
    val courseName = batch.str("course_name")
    val relevance = batch.int("relevance")
    val candidates = batch.list("candidates")
    val teamSkill = batch.list("team_skill")
    val requiredLevel = batch.str("assignment_level")

    val effectiveToc = operationalContext?.course?.contentUrl?.takeIf { it.isNotBlank() }
        ?: batch.str("toc_url").takeIf { it.isNotBlank() }
        ?: batch.str("course_url").takeIf { it.isNotBlank() }
        ?: ""
    val shareBatch = remember(batch, effectiveToc) {
        BatchShare.Batch(
            courseName = courseName,
            startDate = batch.str("start_date").longDate(),
            endDate = batch.str("end_date").longDate(),
            sessionTime = batch.str("session_time"),
            days = batch.intOrNull("days"),
            deliveryMode = batch.str("delivery_mode"),
            language = batch.str("language"),
            participants = batch.intOrNull("participants")?.toString().orEmpty(),
            location = batch.str("location"),
            vendor = batch.str("customer"),
            reference = batch.str("demand_id"),
            assignmentLevel = batch.str("assignment_level"),
            tocUrl = effectiveToc,
        )
    }

    // The broadcast wording is composed server-side (api/data/batch-message) so
    // it can change without an app release. Fetched lazily per recipient; the
    // local BatchShare builder is the offline fallback only.
    val scope = rememberCoroutineScope()
    val batchRepository = remember { com.example.skillsync.core.data.BatchRepository() }
    val serverMsg = remember { mutableStateMapOf<String, Pair<String, String>>() }
    fun recipientKey(target: Pair<String, String>?) = target?.first ?: "Team"
    fun ensureServerMessage(target: Pair<String, String>?) {
        val key = recipientKey(target)
        val demandId = batch.str("demand_id")
        if (demandId.isBlank() || serverMsg.containsKey(key)) return
        scope.launch {
            try {
                val r = batchRepository.batchMessage(demandId, if (key == "Team") null else key)
                val plain = (r["plain"] as? String).orEmpty()
                val html = (r["html"] as? String).orEmpty()
                if (plain.isNotBlank()) serverMsg[key] = plain to html
            } catch (_: Exception) { /* fall back to local */ }
        }
    }

    fun messageFor(target: Pair<String, String>?): String =
        serverMsg[recipientKey(target)]?.first
            ?: BatchShare.composeMessage(shareBatch, recipient = recipientKey(target))

    fun htmlFor(target: Pair<String, String>?): String =
        serverMsg[recipientKey(target)]?.second
            ?: BatchShare.htmlMessage(shareBatch, recipient = recipientKey(target))

    // Confirm or explain the RMS write, then reset so the dialog can reopen.
    // Material's default snackbar was the only surface in the app that did not
    // use the design tokens, and it gave a confirmed write, an unconfirmed one
    // and an outright failure the same neutral styling.
    val notify = com.example.skillsync.core.ui.LocalNotify.current
    LaunchedEffect(markState) {
        when (markState) {
            is MarkState.Done -> {
                notify.success("Skill saved to RMS", markState.message)
                onClearMark()
            }
            is MarkState.Unconfirmed -> {
                notify.warn("Saved, but not confirmed", markState.message)
                onClearMark()
            }
            is MarkState.Failed -> {
                notify.error("Not saved", markState.message)
                onClearMark()
            }
            else -> Unit
        }
    }

    val (coverageLabel, coverageTint, coverageIcon) = coverageStyle(batch.str("coverage_status"))
    val risk = batch.str("assignment_risk")
    val riskTint = when (risk) { "High" -> sk.crit; "Medium" -> sk.warn; else -> sk.aqua }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                com.example.skillsync.theme.SkillSyncTopBar(
                    title = "Demand Detail",
                    subtitle = "Ref ${batch.str("demand_id")}".takeIf { batch.str("demand_id").isNotBlank() },
                    onBack = onBack,
                )
            },
        ) { pv ->
            Column(
                Modifier.fillMaxSize().padding(pv).verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Headline: course, coverage, and the three business-priority
                // figures a manager needs before reading anything else — no
                // separate cards, one glass block.
                Box(Modifier.fillMaxWidth().glassSurface()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                courseName.ifBlank { "Course not specified" },
                                style = MaterialTheme.typography.titleLarge, color = sk.frost,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(10.dp))
                            IconSlot(tint = coverageTint, size = 34.dp) {
                                Icon(painterResource(coverageIcon), null, tint = coverageTint, modifier = Modifier.size(17.dp))
                            }
                        }
                        Text(
                            coverageLabel, style = MaterialTheme.typography.labelSmall,
                            color = coverageTint, fontWeight = FontWeight.Bold,
                        )
                        if (batch.str("customer").isNotBlank()) {
                            Text(
                                batch.str("customer"),
                                style = MaterialTheme.typography.bodyMedium, color = sk.subText,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            batch.str("start_date").takeIf { it.isNotBlank() }?.daysUntil()?.let { d ->
                                Chip(
                                    when {
                                        d < 0 -> "In progress"
                                        d == 0 -> "Starts today"
                                        d == 1 -> "Starts in 1d"
                                        else -> "Starts in ${d}d"
                                    },
                                    if (d in 0..3) sk.warn else sk.sky,
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            if (batch.str("demand_id").isNotBlank()) { Chip("Ref ${batch.str("demand_id")}", sk.subText); Spacer(Modifier.width(6.dp)) }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (batch.bool("is_fast_track") || operationalContext?.course?.isFastTrack == true) { Chip("Fast-Track (No Exam)", sk.aqua); Spacer(Modifier.width(6.dp)) }
                            if (batch.bool("is_priority")) { Chip("Priority", sk.teal); Spacer(Modifier.width(6.dp)) }
                            if (batch.str("tentative").equals("Yes", true)) { Chip("Tentative", sk.amber); Spacer(Modifier.width(6.dp)) }
                            if (batch.str("third_party").equals("Yes", true)) { Chip("Third party", sk.indigo) }
                        }
                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = sk.cardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            DetailStat("Mode", batch.str("delivery_mode").ifBlank { "ILO" }, sk.indigo)
                            DetailStat("Priority", "${batch.intOrNull("priority_score") ?: 0}", sk.teal)
                            DetailStat("Risk", risk.ifBlank { "—" }, riskTint)
                            DetailStat("Coverage", "$relevance%", relevanceColor(relevance))
                        }
                    }
                }

                // ── Team skill on this course ───────────────────────────────
                // The heart of Demand: which of my reportees already hold this
                // skill, at what level, and who is below the assignment level.
                TeamSkillPanel(
                    rows = teamSkill,
                    requiredLevel = requiredLevel,
                    canManageTeam = com.example.skillsync.core.data.SessionManager.canManageTeam(),
                    onMark = { name, email ->
                        markFor = name to email
                        markForLevel = requiredLevel.toIntOrNull()
                        showReportee = true
                    },
                    onMarkMine = { showMine = true },
                    onMarkTeam = { showReportee = true },
                )

                // The full eligibility check — leave, client exclusions,
                // confirmed bookings, skill floor and visa. The demand board
                // cannot afford these per-trainer calls, so this is where the
                // non-overridable gates are actually applied.
                Spacer(Modifier.height(14.dp))
                GatedCandidatesSection(
                    response = gatedCandidates,
                    loading = gatedCandidatesLoading,
                    unverified = gatedCandidatesUnverified,
                )

                // Koenig's algorithm allocates; the manager cannot. This opens
                // the per-trainer blocker list with the fixes the manager IS
                // allowed to make (record a skill, and hints for the rest).
                if (batch.str("demand_id").isNotBlank()) {
                    OutlinedButton(
                        onClick = { showEligibilitySheet = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            "Why my team isn't eligible",
                            style = MaterialTheme.typography.labelMedium,
                            color = sk.amber, fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Box(Modifier.fillMaxWidth().glassSurface()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Operational verification", fontWeight = FontWeight.SemiBold, color = sk.frost)
                            val label = when {
                                operationalContextLoading -> "Checking"
                                operationalContext?.confidence == "verified" -> "Verified"
                                else -> "Partial"
                            }
                            Text(label, style = MaterialTheme.typography.labelSmall, color = if (label == "Verified") sk.aqua else sk.amber)
                        }
                        when {
                            operationalContextLoading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                            operationalContext != null -> {
                                val course = operationalContext.course
                                val confirmations = operationalContext.salesConfirmations
                                Text(
                                    if (course.verified) "RMS course status: ${course.status.ifBlank { "Verified" }}" else "Course status could not be verified",
                                    style = MaterialTheme.typography.bodySmall, color = sk.bodyText,
                                )
                                Text(
                                    if (confirmations.verified) "${confirmations.count} sales confirmation${if (confirmations.count == 1) "" else "s"} linked" else "Sales confirmation link could not be verified",
                                    style = MaterialTheme.typography.bodySmall, color = sk.subText,
                                )
                            }
                            operationalContextError != null -> Text(operationalContextError, style = MaterialTheme.typography.bodySmall, color = sk.warn)
                        }
                    }
                }

                // Start -> End on its own row, kept apart from the requirement
                // and context facts below so the schedule reads at a glance.
                Box(Modifier.fillMaxWidth().glassSurface()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("START", style = MaterialTheme.typography.labelSmall, color = sk.ice, fontWeight = FontWeight.Bold)
                                Text(batch.str("start_date").takeIf { it.isNotBlank() }?.shortDate() ?: "—", style = MaterialTheme.typography.titleSmall, color = sk.frost, fontWeight = FontWeight.SemiBold)
                            }
                            Icon(painterResource(R.drawable.ic_chevron), null, tint = sk.subText, modifier = Modifier.size(16.dp))
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text("END", style = MaterialTheme.typography.labelSmall, color = sk.ice, fontWeight = FontWeight.Bold)
                                Text(batch.str("end_date").takeIf { it.isNotBlank() }?.shortDate() ?: "—", style = MaterialTheme.typography.titleSmall, color = sk.frost, fontWeight = FontWeight.SemiBold)
                            }
                            batch.intOrNull("days")?.let {
                                Spacer(Modifier.width(14.dp))
                                Chip("${it}d", sk.sky)
                            }
                        }
                        // The raw `schedule` blob is deliberately not rendered.
                        // RMS repeats the same window once per delivery day
                        // ("24 Aug / 09:00-17:00 / 25 Aug / 09:00-17:00 / ..."),
                        // so printing it restated the dates already shown above
                        // and the daily time already shown in the grid. The
                        // window is extracted once, server-side, as
                        // `session_time`.
                    }
                }

                // Demand requirements — what the delivery itself needs.
                SectionCard("Demand requirements") {
                    FactGrid(
                        listOf(
                            "Mode" to batch.str("delivery_mode"),
                            "Assignment level" to batch.str("assignment_level"),
                            "Participants" to (batch.intOrNull("participants")?.toString() ?: ""),
                            "Daily time" to batch.str("session_time"),
                            "Language" to batch.str("language"),
                            "Courseware" to batch.str("courseware"),
                        )
                    )
                }

                // Customer / learner context — who this delivery is for.
                SectionCard("Customer & learner context") {
                    FactGrid(
                        listOf(
                            "Vendor" to batch.str("customer"),
                            "Location" to batch.str("location"),
                            "Allocation for" to batch.str("allocation_for"),
                            "Course id" to courseId,
                            "Student card" to batch.str("scid"),
                        )
                    )
                }

                // Remarks — freeform text, kept in its own card so it never
                // gets truncated to fit a two-column grid cell.
                batch.str("remarks").takeIf { it.isNotBlank() }?.let { remarks ->
                    SectionCard("Remarks") {
                        Text(remarks, style = MaterialTheme.typography.bodySmall, color = sk.frost)
                    }
                }

                // ── Official Courseware & Materials Hub ──────────────────────────────
                val contentPdf = operationalContext?.course?.contentUrl.orEmpty()
                val activeVer = operationalContext?.course?.latestVersion.orEmpty()
                val isFastTrack = operationalContext?.course?.isFastTrack ?: batch.bool("is_fast_track")

                Box(Modifier.fillMaxWidth().glassSurface()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Courseware & Curriculum",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = sk.frost,
                            )
                            if (isFastTrack) {
                                Surface(
                                    color = sk.teal.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, sk.teal.copy(alpha = 0.4f)),
                                ) {
                                    Text(
                                        "Fast-Track (No Exam)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = sk.teal,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    )
                                }
                            }
                        }

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                if (activeVer.isNotBlank()) {
                                    Text(
                                        "Version: $activeVer",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = sk.aqua,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                } else {
                                    Text(
                                        "Standard Koenig Syllabus",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = sk.bodyText,
                                    )
                                }
                                Text(
                                    if (contentPdf.isNotBlank()) "Official slide deck & trainer notes ready" else "Syllabus verified in RMS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = sk.subText,
                                )
                            }

                            if (contentPdf.isNotBlank()) {
                                Surface(
                                    onClick = {
                                        try {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(contentPdf))
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    },
                                    color = Color(0xFF0284C7),
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            "Slides PDF",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = sk.frost,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }

                        if (courseName.isNotBlank()) {
                            OutlinedButton(
                                onClick = { showCurriculumSheet = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text("View curriculum", style = MaterialTheme.typography.labelMedium, color = sk.blue, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // ── Live Class Participant Roster (Key 208) ──────────────────────────
                val paxList = operationalContext?.participantsRoster?.students.orEmpty()
                if (paxList.isNotEmpty()) {
                    Box(Modifier.fillMaxWidth().glassSurface()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text("Enrolled Participants", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = sk.frost)
                                    Surface(
                                        color = Color(0xFF0284C7).copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(6.dp),
                                    ) {
                                        Text(
                                            "${paxList.size} STUDENTS",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF38BDF8),
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }

                            paxList.forEach { student ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(sk.cardBg.copy(alpha = 0.7f))
                                        .border(0.5.dp, sk.cardBorder.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    // Initials Avatar
                                    val initials = student.name.split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString("").ifBlank { "ST" }
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF0284C7).copy(alpha = 0.25f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(initials, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }

                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text(
                                            student.name.ifBlank { "Participant" },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = sk.bodyText,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        if (student.email.isNotBlank()) {
                                            Text(
                                                student.email,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF38BDF8),
                                                modifier = Modifier.clickable {
                                                    try {
                                                        val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                                                            data = android.net.Uri.parse("mailto:${student.email}")
                                                        }
                                                        context.startActivity(intent)
                                                    } catch (_: Exception) {}
                                                },
                                            )
                                        }
                                    }

                                    if (student.company.isNotBlank()) {
                                        Surface(
                                            color = sk.surface3,
                                            shape = RoundedCornerShape(4.dp),
                                        ) {
                                            Text(
                                                student.company,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = sk.subText,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Team Match — reportees only, never learners/participants
                // (those are the separate "Enrolled Participants" section above).
                Box(Modifier.fillMaxWidth().glassSurface()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Team Match", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold, color = sk.frost,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Ranked by skill fit, readiness, availability and language — includes you",
                            style = MaterialTheme.typography.labelSmall, color = sk.labelText,
                        )
                        Spacer(Modifier.height(10.dp))
                        if (candidates.isEmpty()) {
                            // Never a dead end: no mapped trainer is exactly the
                            // moment marking a skill becomes the next action.
                            Text(
                                "No mapped trainer found",
                                style = MaterialTheme.typography.bodyMedium, color = sk.bodyText,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "No one on your team currently holds this skill at the level the assignment needs.",
                                style = MaterialTheme.typography.labelSmall, color = sk.subText,
                            )
                            Spacer(Modifier.height(10.dp))
                        } else {
                            candidates.forEachIndexed { i, c ->
                                if (i > 0) Spacer(Modifier.height(8.dp))
                                TeamMatchRow(
                                    candidate = c,
                                    onMessage = {
                                        shareTarget = c.str("trainer_name") to c.str("trainer_email")
                                        showMessagePreview = true
                                    },
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                        // Same two actions regardless of state — Team Match is
                        // one component family whether it has zero or many
                        // ranked trainers.
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            if (com.example.skillsync.core.data.SessionManager.canManageTeam()) {
                                FilledTonalButton(
                                    onClick = { showReportee = true },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(Radii.chip),
                                ) { Text("Mark team skills") }
                            }
                            if (courseName.isNotBlank()) {
                                OutlinedButton(
                                    onClick = { showNetworkSheet = true },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(Radii.chip),
                                ) { Text("Search wider network", color = sk.cyan) }
                            }
                        }
                    }
                }

                // Communication — message the team about this demand, not tied
                // to any specific trainer (per-trainer messaging lives inline
                // on that trainer's Team Match row above).
                OutlinedButton(
                    onClick = { shareTarget = null; showMessagePreview = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Message the team", style = MaterialTheme.typography.labelMedium, color = sk.green, fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showCurriculumSheet && courseName.isNotBlank()) {
        CourseCurriculumSheet(
            courseName = courseName,
            courseId = courseId,
            onDismiss = { showCurriculumSheet = false },
        )
    }

    if (showNetworkSheet && courseName.isNotBlank()) {
        NetworkStaffingSheet(
            courseName = courseName,
            onDismiss = { showNetworkSheet = false },
        )
    }

    if (showEligibilitySheet) {
        EligibilitySheet(
            managerEmail = managerEmail,
            demandId = batch.str("demand_id"),
            courseId = courseId,
            courseName = courseName,
            markState = markState,
            onMarkSkill = onMarkSkill,
            onDismiss = { showEligibilitySheet = false },
        )
    }

    LaunchedEffect(showMessagePreview, shareTarget) {
        if (showMessagePreview) ensureServerMessage(shareTarget)
    }

    if (showMessagePreview) {
        MessagePreviewDialog(
            message = messageFor(shareTarget),
            recipient = shareTarget?.first,
            batch = shareBatch,
            onDismiss = { showMessagePreview = false },
            onCopy = { text ->
                // Only pass the HTML variant when the text is untouched; once it
                // has been edited the two would disagree and the rich paste would
                // silently drop the manager's changes.
                val html = if (text == messageFor(shareTarget)) htmlFor(shareTarget) else null
                BatchShare.copyMessage(context, text, html)
                showMessagePreview = false
            },
            onShare = { text ->
                BatchShare.shareAnywhere(context, text)
                showMessagePreview = false
            },
        )
    }

    if (showMine) {
        MarkSkillSheet(
            title = "Mark my skill",
            courseName = courseName,
            requiredLevel = "",
            people = null,
            working = markState is MarkState.Working,
            onDismiss = { showMine = false },
            onConfirmMine = { level, date ->
                onMarkSkill(courseId, managerEmail, level, date, "you")
                showMine = false
            },
        )
    }

    if (showReportee) {
        MarkSkillSheet(
            title = "Mark team skill",
            courseName = courseName,
            requiredLevel = requiredLevel,
            people = reportees,
            working = markState is MarkState.Working,
            initialSelected = markFor,
            initialLevel = markForLevel,
            onDismiss = { showReportee = false; markFor = null; markForLevel = null },
            onConfirmMany = { who, level, date ->
                if (who.size == 1) {
                    val (name, email) = who.first()
                    if (email.isNotBlank()) onMarkSkill(courseId, email, level, date, name)
                } else if (who.isNotEmpty()) {
                    onMarkSkillMany(courseId, who, level, date)
                }
                showReportee = false; markFor = null; markForLevel = null
            },
        )
    }
}

// ── Pieces ────────────────────────────────────────────────────────────────────

/**
 * Shows the exact text before it leaves the app, and lets the manager edit it.
 *
 * Backed by CommunicationContextPolicy (AVAILABLE DATA != MESSAGE CONTENT).
 * Supports manager intent input to guide the context drafting.
 */
@Composable
private fun MessagePreviewDialog(
    message: String,
    recipient: String?,
    batch: BatchShare.Batch,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
) {
    val sk = MaterialTheme.skill
    var text by remember(message) { mutableStateOf(message) }
    var managerIntent by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { onCopy(text) }, shape = RoundedCornerShape(10.dp)) {
                Icon(painterResource(R.drawable.ic_check), null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(7.dp))
                Text("Copy", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = { onShare(text) }) { Text("Share") }
            }
        },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (recipient != null) "Message $recipient" else "Message the team",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    "${text.length} of 1000 characters · commercial IDs and private notes filtered",
                    style = MaterialTheme.typography.labelSmall, color = sk.subText,
                )
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                com.example.skillsync.feature.communication.ui.ComposerModelStrip()
                com.example.skillsync.feature.communication.ui.ComposerStep(
                    1, "Verified context", "From the batch record — always included.",
                    com.example.skillsync.feature.communication.ui.ComposerTints.context,
                ) {
                    com.example.skillsync.feature.communication.ui.VerifiedContextRows(
                        listOf(
                            "Course" to batch.courseName,
                            "Dates" to listOf(batch.startDate, batch.endDate).filter { it.isNotBlank() }.joinToString(" → "),
                            "Mode" to listOf(batch.deliveryMode, batch.language).filter { it.isNotBlank() }.joinToString(" · "),
                            "Participants" to batch.participants,
                            "Level" to batch.assignmentLevel,
                        ),
                    )
                }
                com.example.skillsync.feature.communication.ui.ComposerStep(
                    2, "Manager instruction", "Optional — added on top of the facts.",
                    com.example.skillsync.feature.communication.ui.ComposerTints.instruction,
                ) {
                    com.example.skillsync.feature.communication.ui.ManagerInstructionField(
                        value = managerIntent,
                        onValueChange = { instruction ->
                            managerIntent = instruction
                            text = BatchShare.composeWithIntent(
                                batch = batch,
                                recipient = recipient ?: "Team",
                                myMessage = instruction,
                            )
                        },
                        placeholder = "e.g. Urgent requirement, please confirm if available",
                        minLines = 1,
                    )
                }
                com.example.skillsync.feature.communication.ui.ComposerStep(
                    3, "Generated message", "Editable before you copy or share.",
                    com.example.skillsync.feature.communication.ui.ComposerTints.generated,
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        textStyle = MaterialTheme.typography.bodySmall,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 300.dp),
                    )
                }
            }
        },
    )
}

/**
 * One deterministic label per candidate, derived only from the same fields the
 * backend already computed (backend.py `build_candidates`: `dnc_flag`,
 * `blocked`, `meets_required_level`, `availability_status`,
 * `certification_covered`). Compose never re-derives eligibility or
 * suitability itself — a hard block always outranks a high match score so a
 * DNC or below-level trainer can never read as recommended.
 */
private data class CandidateState(val label: String, val tint: Color, val reason: String)

private fun candidateState(c: Map<*, *>, sk: com.example.skillsync.theme.SkillColors): CandidateState {
    val isDnc = c.bool("dnc_flag")
    val isBlocked = c.bool("blocked")
    val meetsLevel = c["meets_required_level"]
    val availability = c.str("availability_status")
    val certCovered = c.bool("certification_covered")
    val heldLvl = c.str("held_skill_level")
    val reqLvl = c.str("required_skill_level")

    return when {
        isDnc -> CandidateState("BLOCKED", sk.crit, "Client exclusion (DNC) — cannot be allocated to this account")
        isBlocked -> CandidateState("BLOCKED", sk.crit, "Blocked on recent feedback or a confirmed conflict")
        meetsLevel == false -> CandidateState(
            "NOT ELIGIBLE", sk.crit,
            "Below required level" + if (heldLvl.isNotBlank() || reqLvl.isNotBlank()) " — holds L${heldLvl.ifBlank { "0" }}, needs L${reqLvl.ifBlank { "?" }}" else "",
        )
        availability == "conflict" -> CandidateState("CONFLICT", sk.warn, "Known schedule conflict in this window")
        availability == "unverified" || availability.isBlank() -> CandidateState("NEEDS REVIEW", sk.amber, "Availability not yet confirmed")
        !certCovered && reqLvl.isNotBlank() -> CandidateState("NEEDS REVIEW", sk.amber, "Certification for this course is not on file")
        else -> CandidateState("RECOMMENDED", sk.aqua, "Meets level, available, and covers the required certification")
    }
}

/**
 * One trainer's fit for this demand: identity, recommendation state, skill
 * fit, recorded level, availability, certification and the one reason behind
 * the state — so a hard-ineligible trainer never reads as recommended just
 * because their suitability score is high.
 */
@Composable
private fun TeamMatchRow(candidate: Map<*, *>, onMessage: () -> Unit) {
    val sk = MaterialTheme.skill
    val state = candidateState(candidate, sk)
    val isDnc = candidate.bool("dnc_flag")
    val isClientReq = candidate.bool("client_requested")
    val match = candidate.int("match")
    val heldLvl = candidate.str("held_skill_level")
    val reqLvl = candidate.str("required_skill_level")
    val availability = candidate.str("availability_status")
    val certCovered = candidate.bool("certification_covered")
    val hardBlocked = state.label == "BLOCKED" || state.label == "NOT ELIGIBLE"

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(sk.cardBg.copy(alpha = 0.6f))
            .border(1.dp, state.tint.copy(alpha = if (hardBlocked) 0.35f else 0.2f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    candidate.str("trainer_name"),
                    style = MaterialTheme.typography.titleSmall, color = sk.frost, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "via ${candidate.str("via_course")}",
                    style = MaterialTheme.typography.labelSmall, color = sk.subText,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Chip(state.label, state.tint)
        }

        Text(state.reason, style = MaterialTheme.typography.bodySmall, color = sk.bodyText)

        // Skill fit / suitability is shown, but a hard eligibility failure
        // keeps its own tint rather than borrowing the (possibly high) match
        // colour — a 92% match must not look positive when it is DNC-blocked.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MatchTag(
                if (heldLvl.isNotBlank() || reqLvl.isNotBlank()) "L${heldLvl.ifBlank { "0" }}/${reqLvl.ifBlank { "—" }}" else "Level —",
                if (hardBlocked) sk.subText else if (candidate["meets_required_level"] == true) sk.aqua else sk.warn,
            )
            MatchTag(
                when (availability) {
                    "available" -> "Available"
                    "conflict" -> "Conflict"
                    "unverified", "" -> "Unconfirmed"
                    else -> availability.replaceFirstChar { it.uppercase() }
                },
                if (hardBlocked) sk.subText else when (availability) { "available" -> sk.aqua; "conflict" -> sk.crit; else -> sk.amber },
            )
            MatchTag(
                if (certCovered) "Certified" else "Not certified",
                if (hardBlocked) sk.subText else if (certCovered) sk.aqua else sk.amber,
            )
            if (!hardBlocked) MatchTag("$match% fit", relevanceColor(match))
        }

        if (isClientReq && !isDnc) {
            Text("Client-requested trainer", style = MaterialTheme.typography.labelSmall, color = sk.amber, fontWeight = FontWeight.Bold)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onMessage) { Text("Message", color = sk.sky) }
        }
    }
}

@Composable
private fun MatchTag(text: String, tint: Color) {
    Surface(color = tint.copy(alpha = 0.14f), shape = RoundedCornerShape(6.dp)) {
        Text(
            text, style = MaterialTheme.typography.labelSmall, color = tint, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

/**
 * "Who on my team holds this skill" — the panel a delivery manager opens Demand
 * for. Every reportee, sorted eligible → holds-but-below-level → no skill, each
 * with a one-tap Mark that pre-fills the assignment's required level.
 */
@Composable
private fun TeamSkillPanel(
    rows: List<Map<*, *>>,
    requiredLevel: String,
    canManageTeam: Boolean,
    onMark: (name: String, email: String) -> Unit,
    onMarkMine: () -> Unit,
    onMarkTeam: () -> Unit,
) {
    val sk = MaterialTheme.skill
    if (rows.isEmpty()) {
        Box(Modifier.fillMaxWidth().glassSurface()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Team skill on this course", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = sk.frost)
                Text(
                    "No skill records found for your team on this course yet.",
                    style = MaterialTheme.typography.labelSmall, color = sk.subText,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onMarkMine, modifier = Modifier.weight(1f), shape = RoundedCornerShape(Radii.chip)) { Text("Mark my skill") }
                    if (canManageTeam) {
                        OutlinedButton(onClick = onMarkTeam, modifier = Modifier.weight(1f), shape = RoundedCornerShape(Radii.chip)) { Text("Mark team skills") }
                    }
                }
            }
        }
        return
    }
    val reqN = requiredLevel.toIntOrNull()
    val eligible = rows.count { it["meets_required"] == true }
    Box(Modifier.fillMaxWidth().glassSurface()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Team skill on this course", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = sk.frost)
                if (requiredLevel.isNotBlank()) Chip("Needs L$requiredLevel", sk.amber)
            }
            Text(
                if (reqN != null) "$eligible of ${rows.size} meet level $reqN or above"
                else "${rows.count { it["has_skill"] == true }} of ${rows.size} hold this course",
                style = MaterialTheme.typography.labelSmall, color = sk.labelText,
            )
            Spacer(Modifier.height(2.dp))
            rows.forEach { r ->
                val name = r.str("trainer_name")
                val email = r.str("trainer_email")
                val held = r.str("held_skill_level")
                val hasSkill = r["has_skill"] == true
                val meets = r["meets_required"]
                val (tint, tag) = when {
                    meets == true -> sk.aqua to "Eligible · L$held"
                    hasSkill && meets == false -> sk.warn to "Below level · L$held"
                    hasSkill -> sk.sky to "Holds · L$held"
                    else -> sk.subText to "No skill on file"
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(tint))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.bodySmall, color = sk.bodyText, fontWeight = FontWeight.SemiBold)
                        Text(tag, style = MaterialTheme.typography.labelSmall, color = tint)
                    }
                    if (meets != true) {
                        TextButton(onClick = { onMark(name, email) }) {
                            Text(if (hasSkill) "Raise" else "Mark", color = sk.sky)
                        }
                    }
                }
            }
            Text(
                "Marking writes a verified skill to RMS at the level you set. Preference still goes to certified trainers, then a quality mock.",
                style = MaterialTheme.typography.labelSmall, color = sk.subText,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onMarkMine, modifier = Modifier.weight(1f), shape = RoundedCornerShape(Radii.chip)) { Text("Mark my skill") }
                if (canManageTeam) {
                    OutlinedButton(onClick = onMarkTeam, modifier = Modifier.weight(1f), shape = RoundedCornerShape(Radii.chip)) { Text("Mark team skills") }
                }
            }
        }
    }
}

/** One titled glass card, used to separate distinct fact groups (requirements,
 * customer context, remarks) instead of stacking them into one dense block. */
@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    val sk = MaterialTheme.skill
    Box(Modifier.fillMaxWidth().glassSurface()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = sk.frost)
            content()
        }
    }
}

@Composable
private fun DetailStat(label: String, value: String, tint: Color) {
    Column {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = tint)
        Text(
            label.uppercase(), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.skill.labelText,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Facts laid out two per row, so the detail page reads at a glance instead of
 * as one long column the manager has to scroll.
 *
 * Blank values are dropped before pairing, so an absent field closes the gap
 * rather than leaving a hole in the grid. A value long enough to be truncated
 * in half-width takes a full row instead.
 */
@Composable
private fun FactGrid(facts: List<Pair<String, String>>) {
    val present = facts.filter { it.second.isNotBlank() && it.second != "—" }
    if (present.isEmpty()) return

    // Greedy pairing: walk the list and pair two short facts, or emit one long
    // fact on its own row.
    val rows = remember(present) {
        val out = mutableListOf<List<Pair<String, String>>>()
        var i = 0
        while (i < present.size) {
            val a = present[i]
            val aLong = a.second.length > 28
            val b = present.getOrNull(i + 1)
            if (!aLong && b != null && b.second.length <= 28) {
                out.add(listOf(a, b)); i += 2
            } else {
                out.add(listOf(a)); i += 1
            }
        }
        out
    }

    Column(Modifier.fillMaxWidth()) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                row.forEach { (label, value) ->
                    Column(Modifier.weight(1f).padding(end = 10.dp)) {
                        Text(
                            label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.skill.ice,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            value,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.skill.frost,
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Skips itself when the value is blank, so the block never shows empty rows. */
@Composable
private fun Fact(label: String, value: String) {
    if (value.isBlank() || value == "—") return
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.skill.labelText, modifier = Modifier.width(104.dp),
        )
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.skill.frost)
    }
}

@Composable
private fun Chip(text: String, tint: Color) {
    Surface(color = tint.copy(alpha = 0.16f), shape = RoundedCornerShape(10.dp)) {
        Text(
            text, style = MaterialTheme.typography.labelSmall, color = tint,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
