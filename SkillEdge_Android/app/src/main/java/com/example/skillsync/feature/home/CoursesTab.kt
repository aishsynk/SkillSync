package com.example.skillsync.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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
import com.example.skillsync.core.ui.*
import com.example.skillsync.feature.training.data.CourseIntelligence
import com.example.skillsync.feature.training.ui.MarkState
import com.example.skillsync.theme.*
import java.util.Calendar

private enum class CourseSort(val label: String) {
    COVERAGE("Coverage"), QUBITS("Qubits"), DELIVERED("Delivered"), NAME("Name")
}

/**
 * The catalogue the team can actually teach, assembled from every trainer's RMS
 * capability rows.
 *
 * The question this screen answers is delivery risk, not curriculum: how many
 * people can cover each course, whether any of them hold the matching
 * certification, and which courses rest on a single person.
 */
@Composable
internal fun CoursesTab(
    capability: Map<String, Any>?,
    loading: Boolean,
    onTrainerClick: (String, String) -> Unit,
    certIntel: Map<String, Any>? = null,
    people: List<Pair<String, String>> = emptyList(),
    markState: MarkState = MarkState.Idle,
    courseSearchResults: List<Map<String, Any>> = emptyList(),
    courseSearchLoading: Boolean = false,
    courseIntelligence: CourseIntelligence? = null,
    courseIntelligenceLoading: Boolean = false,
    onSearchCourses: (String) -> Unit = {},
    onLoadCourseIntelligence: (String) -> Unit = {},
    onAssign: (String, List<Pair<String, String>>, Int, String) -> Unit = { _, _, _, _ -> },
    /** §7.6: one skill to many reportees, with per-row outcomes. */
    onBulkAssign: (courseId: String, rows: List<Pair<String, Int>>) -> Unit = { _, _ -> },
    bulkWorking: Boolean = false,
    bulkResults: List<com.example.skillsync.feature.home.SkillWriteResult>? = null,
    onClearMark: () -> Unit = {},
) {
    val sk = MaterialTheme.skill

    if (capability == null) {
        if (loading) {
            Column(
                Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ShimmerBox(height = 50.dp, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                repeat(5) {
                    ShimmerBox(height = 104.dp, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyStateCard("Course capability could not be loaded from RMS.")
            }
        }
        return
    }

    val courses = capability.rows("courses")
    val kpis = capability.obj("kpis")

    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(CourseSort.COVERAGE) }
    var singleOnly by remember { mutableStateOf(false) }
    var uncertifiedOnly by remember { mutableStateOf(false) }
    var futureOnly by remember { mutableStateOf(false) }
    var vendor by remember { mutableStateOf<String?>(null) }
    var assignmentCourse by remember { mutableStateOf<Map<*, *>?>(null) }
    var showAssignment by remember { mutableStateOf(false) }
    var curriculumCourse by remember { mutableStateOf<Map<*, *>?>(null) }

    val vendors = remember(courses) {
        courses.map { it.str("vendor") }.filter { it.isNotBlank() }.distinct().sorted()
    }

    val shown = remember(courses, query, sort, singleOnly, uncertifiedOnly, futureOnly, vendor) {
        val q = query.trim().lowercase()
        courses.filter { c ->
            val matchesQuery = q.isBlank() ||
                c.str("course").lowercase().contains(q) ||
                c.str("vendor").lowercase().contains(q) ||
                c.str("exam_code").lowercase().contains(q) ||
                c.str("certification").lowercase().contains(q) ||
                c.list("owners").any { o -> o.str("trainer_name").lowercase().contains(q) }
            val matchesSingle = !singleOnly || c.str("coverage") == "single"
            // Only meaningful for courses that map to an exam at all.
            val matchesCert = !uncertifiedOnly ||
                (c.str("exam_code").isNotBlank() && c.int("certified_count") == 0)
            val matchesFuture = !futureOnly || c.bool("future_skill")
            val matchesVendor = vendor == null || c.str("vendor") == vendor
            matchesQuery && matchesSingle && matchesCert && matchesFuture && matchesVendor
        }.let { list ->
            when (sort) {
                CourseSort.COVERAGE -> list.sortedWith(
                    compareByDescending<Map<*, *>> { it.int("owner_count") }
                        .thenByDescending { it.int("best_qubits") }
                )
                CourseSort.QUBITS -> list.sortedByDescending { it.int("best_qubits") }
                CourseSort.DELIVERED -> list.sortedByDescending { it.int("delivered_total") }
                CourseSort.NAME -> list.sortedBy { it.str("course") }
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            CatalogueSummary(kpis, courses)
            Spacer(Modifier.height(8.dp))
            CapabilityPortfolio(capability.obj("portfolio"))
            Spacer(Modifier.height(8.dp))
            CertificationPriorities(certIntel)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { assignmentCourse = null; showAssignment = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = sk.brand, contentColor = sk.frost),
            ) {
                Text("Assign skill by course name", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }

        item {
            Column {
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search course, exam code, vendor or trainer",
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CourseSort.entries.forEach { s ->
                        ToneChip(
                            text = s.label,
                            tint = if (sort == s) sk.brand else sk.subText,
                            solid = sort == s,
                            modifier = Modifier.pressable { sort = s },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ToneChip(
                        text = "Single owner only",
                        tint = if (singleOnly) sk.warn else sk.subText,
                        solid = singleOnly,
                        modifier = Modifier.pressable { singleOnly = !singleOnly },
                    )
                    ToneChip(
                        text = "Nobody certified",
                        tint = if (uncertifiedOnly) sk.red else sk.subText,
                        solid = uncertifiedOnly,
                        modifier = Modifier.pressable { uncertifiedOnly = !uncertifiedOnly },
                    )
                    ToneChip(
                        text = "Future skill",
                        tint = if (futureOnly) sk.amber else sk.subText,
                        solid = futureOnly,
                        modifier = Modifier.pressable { futureOnly = !futureOnly },
                    )
                    vendors.forEach { v ->
                        ToneChip(
                            text = v,
                            tint = if (vendor == v) sk.cyan else sk.subText,
                            solid = vendor == v,
                            modifier = Modifier.pressable { vendor = if (vendor == v) null else v },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "${shown.size} of ${courses.size} courses",
                    style = MaterialTheme.typography.labelSmall, color = sk.subText,
                )
            }
        }

        if (shown.isEmpty()) {
            item {
                EmptyStateCard(
                    if (courses.isEmpty()) "RMS returned no course capability for this team."
                    else "No course matches these filters."
                )
            }
        }
        itemsIndexed(shown) { i, c ->
            Appear(i) {
                CourseCard(
                    course = c,
                    onTrainerClick = onTrainerClick,
                    onTransfer = { assignmentCourse = c; showAssignment = true },
                    onInspectCurriculum = { curriculumCourse = c },
                )
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    curriculumCourse?.let { c ->
        CourseCurriculumSheet(
            courseName = c.str("course"),
            courseId = c.str("course_id").ifBlank { c.str("exam_code") },
            course = c,
            onDismiss = { curriculumCourse = null },
        )
    }

    if (showAssignment) {
        val course = assignmentCourse
        val courseId = course?.str("course_id").orEmpty()
        val courseTitle = course?.str("course").orEmpty()

        if (courseId.isNotBlank()) {
            val holders = (course ?: emptyMap<String, Any>()).list("trainers")
                .associate { it.str("trainer_email").lowercase() to it.intOrNull("skill_level") }

            SkillAssignFlow(
                courseName = courseTitle,
                candidates = people.map { (name, email) ->
                    SkillCandidate(
                        name = name,
                        email = email,
                        alreadyHas = holders.containsKey(email.lowercase()),
                        currentLevel = holders[email.lowercase()],
                    )
                },
                working = bulkWorking,
                results = bulkResults,
                onAssign = { selected, level ->
                    onBulkAssign(courseId, selected.map { it.email to level })
                },
                onDismiss = { showAssignment = false; onClearMark() },
            )
        } else {
            SkillAssignmentDialog(
                initialCourse = assignmentCourse,
                people = people,
                results = courseSearchResults,
                searching = courseSearchLoading,
                intelligence = courseIntelligence,
                intelligenceLoading = courseIntelligenceLoading,
                markState = markState,
                onSearch = onSearchCourses,
                onLoadIntelligence = onLoadCourseIntelligence,
                onDismiss = { showAssignment = false; onClearMark() },
                onAssign = onAssign,
            )
        }
    }
}

/**
 * Compact certification priorities: which exams the open demand board is
 * waiting on, and the nearest held-cert expiry (or the honest note when RMS
 * exposes no expiry dates).
 */
@Composable
private fun CertificationPriorities(certIntel: Map<*, *>?) {
    if (certIntel == null || certIntel["loading"] == true) return
    val sk = MaterialTheme.skill
    val demandLed = certIntel.list("demand_led")
    val expiring = certIntel.list("expiring")
    if (demandLed.isEmpty() && expiring.isEmpty() && certIntel.str("note").isBlank()) return

    SkillSyncCard(Modifier.fillMaxWidth()) {
        Text("Certification priorities", style = MaterialTheme.typography.titleMedium, color = sk.frost, fontWeight = FontWeight.Bold)
        Text("Which certifications the open demand board is waiting on", style = MaterialTheme.typography.bodySmall, color = sk.subText)

        if (demandLed.isEmpty()) {
            Text("No open batch currently maps to a known certification exam.", style = MaterialTheme.typography.bodySmall, color = sk.subText)
        } else {
            demandLed.take(3).forEach { d ->
                val batches = d.int("opens_batches")
                val missing = d.int("trainers_missing")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToneChip(text = d.str("exam_code").ifBlank { d.str("cert_name") }, tint = sk.cyan)
                    Text(
                        "unlocks $batches open batch${if (batches == 1) "" else "es"}" +
                            if (missing > 0) " · $missing trainer${if (missing == 1) "" else "s"} missing it" else "",
                        style = MaterialTheme.typography.labelSmall, color = sk.bodyText,
                    )
                }
            }
        }

        HorizontalDivider(color = sk.cardBorder)
        val nearest = expiring.minByOrNull { it.int("days_left") }
        if (nearest != null) {
            val days = nearest.int("days_left")
            Text(
                "Nearest expiry: ${nearest.str("cert")} (${nearest.str("trainer_name")}) in $days day${if (days == 1) "" else "s"}",
                style = MaterialTheme.typography.labelSmall,
                color = if (days <= 30) sk.red else sk.amber, fontWeight = FontWeight.Bold,
            )
        } else {
            Text(
                certIntel.str("note").ifBlank { "No held certifications are approaching expiry." },
                style = MaterialTheme.typography.bodySmall, color = sk.subText,
            )
        }
    }
}

@Composable
private fun CapabilityPortfolio(portfolio: Map<*, *>?) {
    if (portfolio == null) return
    val sk = MaterialTheme.skill
    val summary = portfolio.obj("summary")
    val vendors = portfolio.list("vendor_coverage").take(5)
    val priorities = portfolio.list("priorities")
    val confidence = portfolio.obj("confidence")
    val health = summary?.str("portfolio_health").orEmpty()
    val healthTint = when (health) {
        "healthy" -> sk.good
        "needs_attention" -> sk.amber
        "high_risk" -> sk.warn
        else -> sk.subText
    }

    SkillSyncCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Capability portfolio", style = MaterialTheme.typography.titleMedium, color = sk.frost, fontWeight = FontWeight.Bold)
                Text("Where delivery depth needs a manager decision", style = MaterialTheme.typography.bodySmall, color = sk.subText)
            }
            ToneChip(text = health.replace('_', ' ').ifBlank { "Unknown" }, tint = healthTint)
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            CatalogueFigure("Ready", "${summary?.int("ready_trainers") ?: 0}/${summary?.int("team_size") ?: 0}", sk.good)
            CatalogueFigure("Single owner", "${summary?.int("single_owner_courses") ?: 0}", sk.amber)
            CatalogueFigure("Cert exposed", "${summary?.int("certification_exposed_courses") ?: 0}", sk.red)
            CatalogueFigure("Future", "${summary?.int("future_skill_courses") ?: 0}", sk.brand)
        }

        if (vendors.isNotEmpty()) {
            HorizontalDivider(color = sk.cardBorder)
            Text("Coverage by vendor", style = MaterialTheme.typography.titleSmall, color = sk.frost, fontWeight = FontWeight.Bold)
            vendors.forEach { row ->
                val pct = row.int("coverage_pct").coerceIn(0, 100)
                val tint = when { pct >= 75 -> sk.good; pct >= 50 -> sk.amber; else -> sk.red }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row {
                        Text(row.str("vendor"), style = MaterialTheme.typography.labelSmall, color = sk.bodyText, modifier = Modifier.weight(1f), maxLines = 1)
                        Text("$pct% depth · ${row.int("single_owner")} single · ${row.int("certification_exposed")} exposed", style = MaterialTheme.typography.labelSmall, color = tint)
                    }
                    LinearProgressIndicator(
                        progress = { pct / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(Radii.chip)),
                        color = tint,
                        trackColor = sk.cardBorder,
                    )
                }
            }
        }

        priorities.firstOrNull()?.let { priority ->
            Surface(color = sk.amber.copy(alpha = 0.10f), shape = RoundedCornerShape(Radii.chip)) {
                Text(
                    "Next decision: ${priority.str("label")} (${priority.int("count")})",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelSmall, color = sk.amber, fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(
            if (confidence?.str("status") == "verified") "Verified from current RMS capability evidence"
            else confidence?.str("note").orEmpty().ifBlank { "Capability evidence is incomplete" },
            style = MaterialTheme.typography.labelSmall, color = sk.subText,
        )
    }
}

@Composable
private fun CatalogueSummary(kpis: Map<*, *>?, courses: List<Map<*, *>>) {
    val sk = MaterialTheme.skill
    val single = kpis?.int("single_owner_courses") ?: courses.count { it.str("coverage") == "single" }
    val certifiable = courses.count { it.str("exam_code").isNotBlank() }
    val uncovered = courses.count { it.str("exam_code").isNotBlank() && it.int("certified_count") == 0 }

    SkillSyncCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_book), null, tint = sk.brand, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Course catalogue", style = MaterialTheme.typography.titleLarge, color = sk.frost, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "Everything your team is on record as able to deliver",
            style = MaterialTheme.typography.bodySmall, color = sk.subText,
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            CatalogueFigure("Courses", "${courses.size}", sk.cyan)
            CatalogueFigure("Single owner", "$single", if (single > 0) sk.amber else sk.good)
            CatalogueFigure("Exam-linked", "$certifiable", sk.brand)
            CatalogueFigure("Uncertified", "$uncovered", if (uncovered > 0) sk.red else sk.good)
        }
        if (single > 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                "$single course${if (single == 1) "" else "s"} rest on one trainer — losing them " +
                    "means losing the course.",
                style = MaterialTheme.typography.bodySmall, color = sk.amber,
            )
        }
    }
}

@Composable
private fun CatalogueFigure(label: String, value: String, tint: Color) {
    Column {
        Text(
            value, style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold, color = tint,
        )
        Text(
            label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.skill.subText,
        )
    }
}

@Composable
private fun CourseCard(
    course: Map<*, *>,
    onTrainerClick: (String, String) -> Unit,
    onTransfer: () -> Unit,
    onInspectCurriculum: () -> Unit = {},
) {
    val sk = MaterialTheme.skill
    val owners = course.list("owners")
    val single = course.str("coverage") == "single"
    val examCode = course.str("exam_code")
    val certified = course.int("certified_count")
    val bestQ = course.int("best_qubits")
    var expanded by remember { mutableStateOf(false) }

    val qTint = when {
        bestQ >= 85 -> sk.good
        bestQ >= 60 -> sk.cyan
        bestQ > 0 -> sk.amber
        else -> sk.subText
    }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.card))
            .background(sk.cardBg)
            .border(1.dp, sk.cardBorder, RoundedCornerShape(Radii.card))
            .clickable { expanded = !expanded },
    ) {
        Row {
            // Left rail encodes delivery risk at a glance: a course only one
            // person can teach is a single point of failure for that course.
            Box(
                Modifier.width(3.dp).fillMaxHeight()
                    .background(if (single) sk.warn else sk.cyan)
            )
            Column(Modifier.padding(start = 11.dp, top = 11.dp, end = 12.dp, bottom = 11.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            course.str("course"),
                            style = MaterialTheme.typography.titleSmall,
                            color = sk.frost, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            if (examCode.isNotBlank()) ToneChip(text = examCode, tint = sk.cyan)
                            course.str("vendor").takeIf { it.isNotBlank() }?.let { ToneChip(text = it, tint = sk.brand) }
                            if (course.bool("future_skill")) ToneChip(text = "Future skill", tint = sk.amber)
                            if (single) ToneChip(text = "Single owner", tint = sk.warn)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    ToneChip(
                        text = "Q$bestQ",
                        tint = qTint,
                        solid = true,
                    )
                }

                // Certification mapping — only shown where an exam actually exists.
                if (examCode.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(Radii.chip))
                            .background(
                                (if (certified > 0) sk.good else sk.red).copy(alpha = 0.09f)
                            )
                            .padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_certificate), null,
                            tint = if (certified > 0) sk.good else sk.red,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            course.str("certification").ifBlank { examCode },
                            style = MaterialTheme.typography.labelSmall,
                            color = sk.frost, modifier = Modifier.weight(1f), maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "$certified/${owners.size} certified",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (certified > 0) sk.good else sk.red,
                        )
                    }
                }

                Spacer(Modifier.height(9.dp))
                HorizontalDivider(color = sk.cardBorder)
                Spacer(Modifier.height(8.dp))

                // Ownership
                Row(verticalAlignment = Alignment.CenterVertically) {
                    owners.take(4).forEach { o ->
                        Avatar(o.str("trainer_name"), o.str("photo_url"), 24.dp)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        if (owners.size == 1) "1 trainer can deliver"
                        else "${owners.size} trainers can deliver",
                        style = MaterialTheme.typography.labelSmall, color = sk.subText,
                        modifier = Modifier.weight(1f),
                    )
                    course.int("delivered_total").takeIf { it > 0 }?.let {
                        Text(
                            "$it delivered",
                            style = MaterialTheme.typography.labelSmall, color = sk.subText,
                        )
                    }
                    Icon(
                        painterResource(R.drawable.ic_chevron), null, tint = sk.subText,
                        modifier = Modifier.size(14.dp).padding(start = 4.dp),
                    )
                }

                if (expanded) {
                    Spacer(Modifier.height(8.dp))
                    owners.forEach { o ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    onTrainerClick(o.str("trainer_email"), o.str("trainer_name"))
                                }
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(o.str("trainer_name"), o.str("photo_url"), 26.dp)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    o.str("trainer_name"),
                                    style = MaterialTheme.typography.bodySmall, color = sk.frost,
                                    maxLines = 1,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    listOfNotNull(
                                        o.str("skill_level").takeIf { it.isNotBlank() }?.let { "Level $it" },
                                        o.int("delivered").takeIf { it > 0 }?.let { "$it delivered" },
                                        if (o.bool("approved")) "approved" else null,
                                    ).joinToString(" · ").ifBlank { "no delivery history" },
                                    style = MaterialTheme.typography.labelSmall, color = sk.subText,
                                )
                            }
                            if (examCode.isNotBlank()) {
                                ToneChip(
                                    text = if (o.bool("certified")) "Certified" else "Not certified",
                                    tint = if (o.bool("certified")) sk.good else sk.warn,
                                )
                                Spacer(Modifier.width(5.dp))
                            }
                            val del = o.int("delivered")
                            if (del > 0) {
                                ToneChip(text = "$del DELIVERED", tint = sk.cyan)
                                Spacer(Modifier.width(5.dp))
                            }
                            ToneChip(
                                text = "Q${o.int("qubits_score")}",
                                tint = sk.subText,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onInspectCurriculum,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = sk.brand, contentColor = sk.frost),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Text("Curriculum & Labs", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = onTransfer,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Text("Assign Skill", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SkillAssignmentDialog(
    initialCourse: Map<*, *>?,
    people: List<Pair<String, String>>,
    results: List<Map<String, Any>>,
    searching: Boolean,
    intelligence: CourseIntelligence?,
    intelligenceLoading: Boolean,
    markState: MarkState,
    onSearch: (String) -> Unit,
    onLoadIntelligence: (String) -> Unit,
    onDismiss: () -> Unit,
    onAssign: (String, List<Pair<String, String>>, Int, String) -> Unit,
) {
    val sk = MaterialTheme.skill
    var query by remember(initialCourse) { mutableStateOf(initialCourse?.str("course").orEmpty()) }
    var selectedCourse by remember(initialCourse) {
        mutableStateOf<Map<*, *>?>(
            initialCourse?.let {
                mapOf("course_id" to it.str("course_id"), "course_name" to it.str("course"))
            }?.takeIf { it.str("course_id").isNotBlank() }
        )
    }
    val selectedPeople = remember { mutableStateListOf<Pair<String, String>>() }
    var trainerQuery by remember { mutableStateOf("") }
    var level by remember { mutableStateOf(4) }
    val today = remember { Calendar.getInstance() }
    val date = remember { "%04d-%02d-%02d".format(today.get(Calendar.YEAR), today.get(Calendar.MONTH) + 1, today.get(Calendar.DAY_OF_MONTH)) }
    val working = markState is MarkState.Working

    LaunchedEffect(initialCourse) {
        initialCourse?.str("course")?.takeIf { it.isNotBlank() }?.let(onLoadIntelligence)
    }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(if (initialCourse == null) "Assign skill" else "Transfer skill", color = sk.frost, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Search the full RMS catalogue, then select one or more trainers.", style = MaterialTheme.typography.bodySmall, color = sk.subText)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it; selectedCourse = null },
                        label = { Text("Skill or course name") }, singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onSearch(query) }, enabled = query.trim().length >= 2 && !searching) {
                        Text(if (searching) "Searching…" else "Search")
                    }
                }
                selectedCourse?.let { ToneChip(text = "Selected: ${it.str("course_name")}", tint = sk.cyan) }
                if (selectedCourse == null && results.isNotEmpty()) {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 130.dp)) {
                        itemsIndexed(results) { _, course ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    selectedCourse = course
                                    query = course.str("course_name")
                                    onLoadIntelligence(query)
                                }.padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(course.str("course_name"), style = MaterialTheme.typography.bodySmall, color = sk.frost, maxLines = 2)
                                    Text(
                                        listOfNotNull(
                                            course.str("vendor").takeIf { it.isNotBlank() },
                                            course["duration_days"]?.toString()?.takeIf { it.isNotBlank() }?.let { "$it days" },
                                            course.str("course_code").takeIf { it.isNotBlank() },
                                        ).joinToString(" · ").ifBlank { "RMS catalogue" },
                                        style = MaterialTheme.typography.labelSmall, color = sk.subText,
                                    )
                                }
                                Text("Select", style = MaterialTheme.typography.labelSmall, color = sk.cyan, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                if (selectedCourse != null) {
                    if (intelligenceLoading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    } else when (val info = intelligence) {
                        is CourseIntelligence.Unverified -> {
                            Surface(color = sk.warn.copy(alpha = 0.10f), shape = RoundedCornerShape(Radii.chip)) {
                                Column(Modifier.fillMaxWidth().padding(9.dp)) {
                                    Text(
                                        "Schedule not verified",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = sk.warn, fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        info.note.ifBlank { "Could not confirm this course with RMS right now." },
                                        style = MaterialTheme.typography.labelSmall, color = sk.subText,
                                    )
                                }
                            }
                        }
                        is CourseIntelligence.Verified -> {
                            Surface(color = sk.cyan.copy(alpha = 0.09f), shape = RoundedCornerShape(Radii.chip)) {
                                Column(Modifier.fillMaxWidth().padding(9.dp)) {
                                    Text(
                                        listOfNotNull(
                                            info.vendor.takeIf { it.isNotBlank() },
                                            info.durationDays?.let { "$it days" },
                                        ).joinToString(" · ").ifBlank { "Verified RMS course" },
                                        style = MaterialTheme.typography.labelSmall, color = sk.frost,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        if (info.scheduleDates.isNotEmpty()) "Next public schedule: ${info.scheduleDates.first()}"
                                        else info.note.ifBlank { "No public schedule is currently returned by RMS." },
                                        style = MaterialTheme.typography.labelSmall, color = sk.subText,
                                    )
                                }
                            }
                        }
                        null -> Unit
                    }
                }
                Text("TEAM MEMBERS (${selectedPeople.size} SELECTED)", style = MaterialTheme.typography.labelSmall, color = sk.labelText, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = {
                            people.forEach { if (it !in selectedPeople) selectedPeople.add(it) }
                        },
                        enabled = people.isNotEmpty() && selectedPeople.size < people.size && !working,
                    ) { Text("Select all") }
                    TextButton(
                        onClick = { selectedPeople.clear() },
                        enabled = selectedPeople.isNotEmpty() && !working,
                    ) { Text("Clear") }
                }
                OutlinedTextField(
                    value = trainerQuery,
                    onValueChange = { trainerQuery = it },
                    label = { Text("Search and select trainers") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val visiblePeople = people.filter {
                    trainerQuery.isBlank() || it.first.contains(trainerQuery, ignoreCase = true) ||
                        it.second.contains(trainerQuery, ignoreCase = true)
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 150.dp)) {
                    itemsIndexed(visiblePeople) { _, person ->
                        val checked = person in selectedPeople
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                if (checked) selectedPeople.remove(person) else selectedPeople.add(person)
                            }.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = { yes -> if (yes) selectedPeople.add(person) else selectedPeople.remove(person) })
                            Text(person.first, style = MaterialTheme.typography.bodySmall, color = sk.frost)
                        }
                    }
                }
                if (visiblePeople.isEmpty()) {
                    Text("No team member matches this search.", style = MaterialTheme.typography.labelSmall, color = sk.subText)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Skill level", style = MaterialTheme.typography.labelSmall, color = sk.subText)
                    Slider(value = level.toFloat(), onValueChange = { level = it.toInt().coerceIn(1, 10) }, valueRange = 1f..10f, steps = 8, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                    Text("$level", color = sk.cyan, fontWeight = FontWeight.Bold)
                }
                Text("Effective $date · This writes to RMS for every selected trainer.", style = MaterialTheme.typography.labelSmall, color = sk.subText)
                when (markState) {
                    is MarkState.Done -> Text(markState.message, color = sk.good, style = MaterialTheme.typography.bodySmall)
                    is MarkState.Unconfirmed -> Text(markState.message, color = sk.amber, style = MaterialTheme.typography.bodySmall)
                    is MarkState.Failed -> Text(markState.message, color = sk.red, style = MaterialTheme.typography.bodySmall)
                    else -> Unit
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedCourse?.str("course_id")?.let { onAssign(it, selectedPeople.toList(), level, date) } },
                enabled = !working && selectedCourse != null && selectedPeople.isNotEmpty(),
            ) { if (working) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Mark skill for ${selectedPeople.size}") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !working) { Text(if (markState is MarkState.Idle) "Cancel" else "Close") } },
    )
}
