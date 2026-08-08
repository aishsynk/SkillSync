package com.example.skillsync.ui.main

import androidx.compose.foundation.background
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
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.accentGlass
import com.example.skillsync.theme.glassSurface
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.components.*
import com.example.skillsync.ui.batch.MarkState
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
    people: List<Pair<String, String>> = emptyList(),
    markState: MarkState = MarkState.Idle,
    courseSearchResults: List<Map<String, Any>> = emptyList(),
    courseSearchLoading: Boolean = false,
    onSearchCourses: (String) -> Unit = {},
    onAssign: (String, List<Pair<String, String>>, Int, String) -> Unit = { _, _, _, _ -> },
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
            Button(onClick = { assignmentCourse = null; showAssignment = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Assign skill by course name")
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
                        SelectChip(s.label, sort == s) { sort = s }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Deliberately not just "Single owner" — that is also the badge
                    // printed on the cards below, and two controls reading the same
                    // is ambiguous whether you are filtering or looking at a label.
                    SelectChip("Single owner only", singleOnly) { singleOnly = !singleOnly }
                    SelectChip("Nobody certified", uncertifiedOnly) { uncertifiedOnly = !uncertifiedOnly }
                    SelectChip("Future skill", futureOnly) { futureOnly = !futureOnly }
                    vendors.forEach { v ->
                        SelectChip(v, vendor == v) { vendor = if (vendor == v) null else v }
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
            Appear(i) { CourseCard(c, onTrainerClick) { assignmentCourse = c; showAssignment = true } }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    if (showAssignment) {
        SkillAssignmentDialog(
            initialCourse = assignmentCourse,
            people = people,
            results = courseSearchResults,
            searching = courseSearchLoading,
            markState = markState,
            onSearch = onSearchCourses,
            onDismiss = { showAssignment = false; onClearMark() },
            onAssign = onAssign,
        )
    }
}

@Composable
private fun CatalogueSummary(kpis: Map<*, *>?, courses: List<Map<*, *>>) {
    val sk = MaterialTheme.skill
    val single = kpis?.int("single_owner_courses") ?: courses.count { it.str("coverage") == "single" }
    val certifiable = courses.count { it.str("exam_code").isNotBlank() }
    val uncovered = courses.count { it.str("exam_code").isNotBlank() && it.int("certified_count") == 0 }

    Box(Modifier.fillMaxWidth().glassSurface()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_book), null, tint = sk.indigo, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text("Course catalogue", style = MaterialTheme.typography.titleLarge, color = sk.bodyText)
            }
            Text(
                "Everything your team is on record as able to deliver",
                style = MaterialTheme.typography.labelSmall, color = sk.subText,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CatalogueFigure("Courses", "${courses.size}", sk.indigo)
                CatalogueFigure("Single owner", "$single", if (single > 0) sk.amber else sk.green)
                CatalogueFigure("Exam-linked", "$certifiable", sk.blue)
                CatalogueFigure("Uncertified", "$uncovered", if (uncovered > 0) sk.red else sk.green)
            }
            if (single > 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "$single course${if (single == 1) "" else "s"} rest on one trainer — losing them " +
                        "means losing the course.",
                    style = MaterialTheme.typography.labelSmall, color = sk.amber, fontSize = 9.5.sp,
                )
            }
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
            color = MaterialTheme.skill.subText, fontSize = 9.sp,
        )
    }
}

@Composable
private fun CourseCard(course: Map<*, *>, onTrainerClick: (String, String) -> Unit, onTransfer: () -> Unit) {
    val sk = MaterialTheme.skill
    val owners = course.list("owners")
    val single = course.str("coverage") == "single"
    val examCode = course.str("exam_code")
    val certified = course.int("certified_count")
    val bestQ = course.int("best_qubits")
    var expanded by remember { mutableStateOf(false) }

    val qTint = when {
        bestQ >= 85 -> sk.green
        bestQ >= 60 -> sk.teal
        bestQ > 0 -> sk.amber
        else -> sk.subText
    }

    Box(
        Modifier
            .fillMaxWidth()
            .accentGlass(if (single) sk.warn else sk.sky, RoundedCornerShape(Radii.card))
            .clickable { expanded = !expanded },
    ) {
        Row {
            // Left rail encodes delivery risk at a glance: a course only one
            // person can teach is a single point of failure for that course.
            Box(
                Modifier.width(3.dp).fillMaxHeight()
                    .background(if (single) sk.warn else sk.sky)
            )
            Column(Modifier.padding(start = 11.dp, top = 11.dp, end = 12.dp, bottom = 11.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            course.str("course"),
                            style = MaterialTheme.typography.titleSmall,
                            color = sk.bodyText, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(3.dp))
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            if (examCode.isNotBlank()) Tag(examCode, sk.blue)
                            course.str("vendor").takeIf { it.isNotBlank() }?.let { Tag(it, sk.indigo) }
                            if (course.bool("future_skill")) Tag("Future skill", sk.amber)
                            if (single) Tag("Single owner", sk.amber)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Surface(color = qTint.copy(alpha = 0.14f), shape = RoundedCornerShape(10.dp)) {
                        Text(
                            "Q$bestQ",
                            style = MaterialTheme.typography.labelMedium, color = qTint,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }

                // Certification mapping — only shown where an exam actually exists.
                if (examCode.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(7.dp))
                            .background(
                                (if (certified > 0) sk.green else sk.red).copy(alpha = 0.09f)
                            )
                            .padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_certificate), null,
                            tint = if (certified > 0) sk.green else sk.red,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            course.str("certification").ifBlank { examCode },
                            style = MaterialTheme.typography.labelSmall,
                            color = sk.bodyText, modifier = Modifier.weight(1f), maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "$certified/${owners.size} certified",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (certified > 0) sk.green else sk.red,
                            fontSize = 9.sp,
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
                                    style = MaterialTheme.typography.bodySmall, color = sk.bodyText,
                                    maxLines = 1,
                                )
                                Text(
                                    listOfNotNull(
                                        o.str("skill_level").takeIf { it.isNotBlank() }?.let { "Level $it" },
                                        o.int("delivered").takeIf { it > 0 }?.let { "$it delivered" },
                                        if (o.bool("approved")) "approved" else null,
                                    ).joinToString(" · ").ifBlank { "no delivery history" },
                                    style = MaterialTheme.typography.labelSmall, color = sk.subText,
                                    fontSize = 9.sp,
                                )
                            }
                            if (examCode.isNotBlank()) {
                                Tag(
                                    if (o.bool("certified")) "Certified" else "Not certified",
                                    if (o.bool("certified")) sk.green else sk.red,
                                )
                                Spacer(Modifier.width(5.dp))
                            }
                            Text(
                                "Q${o.int("qubits_score")}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold, color = sk.subText,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onTransfer, modifier = Modifier.fillMaxWidth()) {
                        Text("Assign to Team Members")
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
    markState: MarkState,
    onSearch: (String) -> Unit,
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

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(if (initialCourse == null) "Assign skill" else "Transfer skill") },
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
                selectedCourse?.let { Tag("Selected: ${it.str("course_name")}", sk.teal) }
                if (selectedCourse == null && results.isNotEmpty()) {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 130.dp)) {
                        itemsIndexed(results) { _, course ->
                            Row(
                                Modifier.fillMaxWidth().clickable { selectedCourse = course; query = course.str("course_name") }.padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(course.str("course_name"), style = MaterialTheme.typography.bodySmall, color = sk.bodyText, modifier = Modifier.weight(1f), maxLines = 2)
                                Text("Select", style = MaterialTheme.typography.labelSmall, color = sk.teal)
                            }
                        }
                    }
                }
                Text("TEAM MEMBERS (${selectedPeople.size} SELECTED)", style = MaterialTheme.typography.labelSmall, color = sk.labelText, fontWeight = FontWeight.Bold)
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
                            Text(person.first, style = MaterialTheme.typography.bodySmall, color = sk.bodyText)
                        }
                    }
                }
                if (visiblePeople.isEmpty()) {
                    Text("No team member matches this search.", style = MaterialTheme.typography.labelSmall, color = sk.subText)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Skill level", style = MaterialTheme.typography.labelSmall, color = sk.subText)
                    Slider(value = level.toFloat(), onValueChange = { level = it.toInt().coerceIn(1, 10) }, valueRange = 1f..10f, steps = 8, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                    Text("$level", color = sk.teal, fontWeight = FontWeight.Bold)
                }
                Text("Effective $date · This writes to RMS for every selected trainer.", style = MaterialTheme.typography.labelSmall, color = sk.subText)
                when (markState) {
                    is MarkState.Done -> Text(markState.message, color = sk.green, style = MaterialTheme.typography.bodySmall)
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

@Composable
private fun Tag(text: String, tint: Color) {
    Surface(color = tint.copy(alpha = 0.13f), shape = RoundedCornerShape(6.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall, color = tint, fontSize = 9.sp,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
