package com.example.skillsync.feature.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp
import com.example.skillsync.R
import com.example.skillsync.core.data.ManagerRepository
import com.example.skillsync.core.ui.*
import com.example.skillsync.feature.communication.engine.CommunicationContextFilter
import com.example.skillsync.feature.communication.engine.CommunicationPurpose
import com.example.skillsync.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseCurriculumSheet(
    courseName: String,
    courseId: String = "",
    course: Map<*, *>? = null,
    onDismiss: () -> Unit,
) {
    val sk = MaterialTheme.skill
    val context = LocalContext.current
    val repository = remember { ManagerRepository() }

    var loading by remember { mutableStateOf(true) }
    var curriculumData by remember { mutableStateOf<Map<String, Any>?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showPrepDialog by remember { mutableStateOf(false) }

    LaunchedEffect(courseName, courseId) {
        loading = true
        try {
            val res = repository.courseCurriculum(courseName = courseName, courseId = courseId)
            curriculumData = res
        } catch (_: Exception) {
            curriculumData = null
        } finally {
            loading = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = sk.cardBg,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.md, vertical = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        courseName.ifBlank { "Course Curriculum" },
                        style = MaterialTheme.typography.titleMedium,
                        color = sk.frost,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Space.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (courseId.isNotBlank()) {
                            ToneChip(text = "Code: $courseId", tint = sk.cyan)
                        }
                        val activeVersion = curriculumData?.str("latest_version").orEmpty()
                        if (activeVersion.isNotBlank()) {
                            ToneChip(text = "RMS v$activeVersion", tint = sk.teal)
                        }
                    }
                }
                IconButton(onClick = onDismiss) {
                    Text("Done", style = MaterialTheme.typography.labelMedium, color = sk.brand, fontWeight = FontWeight.Bold)
                }
            }

            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = sk.brand)
                }
            } else {
                val data = curriculumData
                val modules = data?.list("modules") ?: emptyList()
                val contentUrls = data?.list("content_resources") ?: emptyList()
                val schedules = data?.list("public_schedule_dates")?.map { it.toString() } ?: emptyList()
                val syllabusUrl = data?.str("syllabus_url").orEmpty()
                val officialPdf = data?.str("official_courseware_url").orEmpty()

                // Quick Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (officialPdf.isNotBlank()) {
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(officialPdf))
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = sk.cyan, contentColor = sk.cardBg),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Text("Slides PDF", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (syllabusUrl.isNotBlank()) {
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(syllabusUrl))
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = sk.brand, contentColor = sk.frost),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Text("Syllabus", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (contentUrls.isNotEmpty() && officialPdf.isBlank()) {
                        Button(
                            onClick = {
                                val firstUrl = contentUrls.firstOrNull()?.str("url").orEmpty()
                                if (firstUrl.isNotBlank()) {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(firstUrl))
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = sk.cyan, contentColor = sk.cardBg),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Text("Course Lab", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Sub-Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = sk.brand,
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Modules (${modules.size})", style = MaterialTheme.typography.labelMedium) },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Capability & Readiness", style = MaterialTheme.typography.labelMedium) },
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Public Schedules (${schedules.size})", style = MaterialTheme.typography.labelMedium) },
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { Text("Resources (${contentUrls.size})", style = MaterialTheme.typography.labelMedium) },
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    when (selectedTab) {
                        0 -> {
                            if (modules.isEmpty()) {
                                item {
                                    Text(
                                        "Detailed chapter breakdown is currently syncing from RMS catalogue.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = sk.subText,
                                        modifier = Modifier.padding(16.dp),
                                    )
                                }
                            } else {
                                items(modules) { mod ->
                                    SkillSyncCard(Modifier.fillMaxWidth()) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                "Module ${mod.int("module_no")}: ${mod.str("title")}",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = sk.frost,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.weight(1f),
                                            )
                                            ToneChip(
                                                text = "${mod.int("duration_hours")} hrs",
                                                tint = sk.cyan,
                                            )
                                        }
                                        val topics = mod.str("topics")
                                        if (topics.isNotBlank()) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                topics,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = sk.bodyText,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        1 -> {
                            // Capability & Readiness
                            item {
                                val examCode = course?.str("exam_code").orEmpty().ifBlank { courseId }
                                val certName = course?.str("certification").orEmpty()
                                val owners = course?.list("owners").orEmpty()
                                val single = course?.str("coverage") == "single"
                                val certifiedCount = course?.int("certified_count") ?: owners.count { it.bool("certified") }

                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (examCode.isNotBlank()) {
                                        SkillSyncCard(Modifier.fillMaxWidth()) {
                                            Row(
                                                Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    "Certification Requirement",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = sk.frost,
                                                )
                                                ToneChip(
                                                    text = if (certifiedCount > 0) "$certifiedCount/${owners.size} certified" else "Uncertified",
                                                    tint = if (certifiedCount > 0) sk.good else sk.warn,
                                                )
                                            }
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                if (certName.isNotBlank()) "$examCode: $certName" else "Exam Track: $examCode",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = sk.bodyText,
                                            )
                                        }
                                    }

                                    if (single) {
                                        SkillSyncCard(Modifier.fillMaxWidth(), severity = Severity.Warning) {
                                            Text(
                                                "Single Point of Failure",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = sk.warn,
                                            )
                                            Text(
                                                "Only 1 trainer is currently on record to deliver this course. Delivery coverage is vulnerable.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = sk.bodyText,
                                            )
                                        }
                                    }

                                    SkillSyncCard(Modifier.fillMaxWidth()) {
                                        Text(
                                            "Team Delivery Capability",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = sk.frost,
                                        )
                                        Spacer(Modifier.height(6.dp))

                                        if (owners.isEmpty()) {
                                            Text(
                                                "No assigned delivery trainers recorded on this course.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = sk.subText,
                                            )
                                        } else {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                owners.forEach { o ->
                                                    Row(
                                                        Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                            modifier = Modifier.weight(1f),
                                                        ) {
                                                            Avatar(o.str("trainer_name"), o.str("photo_url"), 28.dp)
                                                            Column {
                                                                Text(
                                                                    o.str("trainer_name"),
                                                                    style = MaterialTheme.typography.bodyMedium,
                                                                    color = sk.frost,
                                                                    fontWeight = FontWeight.Medium,
                                                                )
                                                                Text(
                                                                    "Level ${o.str("skill_level").ifBlank { "Unrated" }}",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = sk.subText,
                                                                )
                                                            }
                                                        }
                                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                            if (o.bool("certified")) {
                                                                ToneChip(text = "CERTIFIED", tint = sk.good)
                                                            }
                                                            val del = o.int("delivered")
                                                            if (del > 0) {
                                                                ToneChip(text = "$del DELIVERED", tint = sk.cyan)
                                                            }
                                                            if (!o.bool("certified") && del == 0) {
                                                                ToneChip(text = "Insufficient evidence", tint = sk.warn)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Action
                                    Button(
                                        onClick = { showPrepDialog = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = sk.brand, contentColor = sk.frost),
                                    ) {
                                        Text("Request Trainer Preparation", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        2 -> {
                            if (schedules.isEmpty()) {
                                item {
                                    Text(
                                        "No public schedule batches published for this course yet.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = sk.subText,
                                        modifier = Modifier.padding(16.dp),
                                    )
                                }
                            } else {
                                items(schedules) { dateStr ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(sk.cardBorder.copy(alpha = 0.3f))
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(painterResource(R.drawable.ic_check), null, tint = sk.good, modifier = Modifier.size(16.dp))
                                            Text(dateStr, style = MaterialTheme.typography.bodyMedium, color = sk.frost, fontWeight = FontWeight.Medium)
                                        }
                                        ToneChip(text = "Open for enrollment", tint = sk.good)
                                    }
                                }
                            }
                        }
                        else -> {
                            if (contentUrls.isEmpty()) {
                                item {
                                    Text(
                                        "Official lab links and slides available upon corporate batch confirmation.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = sk.subText,
                                        modifier = Modifier.padding(16.dp),
                                    )
                                }
                            } else {
                                items(contentUrls) { res ->
                                    val url = res.str("url")
                                    SkillSyncCard(Modifier.fillMaxWidth().clickable {
                                        if (url.isNotBlank()) {
                                            try {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                            } catch (_: Exception) {}
                                        }
                                    }) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(res.str("title").ifBlank { "Official Resource" }, style = MaterialTheme.typography.bodyMedium, color = sk.cyan, fontWeight = FontWeight.Bold)
                                                Text(url, style = MaterialTheme.typography.labelSmall, color = sk.subText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            ToneChip(text = "Open", tint = sk.brand)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showPrepDialog) {
        PreparationRequestDialog(
            courseName = courseName,
            courseCode = courseId,
            owners = course?.list("owners").orEmpty(),
            onDismiss = { showPrepDialog = false },
        )
    }
}

@Composable
private fun PreparationRequestDialog(
    courseName: String,
    courseCode: String,
    owners: List<Map<*, *>>,
    onDismiss: () -> Unit,
) {
    val sk = MaterialTheme.skill
    val context = LocalContext.current
    var selectedTrainer by remember {
        mutableStateOf(owners.firstOrNull()?.str("trainer_name") ?: "Trainer")
    }
    var managerIntent by remember { mutableStateOf("") }

    val composedMessage = remember(selectedTrainer, managerIntent) {
        val sanitized = CommunicationContextFilter.sanitize(
            CommunicationPurpose.COURSE_PREPARATION_REQUEST,
            mapOf(
                "course_title" to courseName,
                "course_code" to courseCode,
                "trainer_name" to selectedTrainer,
                "intent" to managerIntent,
            ),
        )
        buildString {
            append("Hi $selectedTrainer,\n\n")
            if (managerIntent.isNotBlank()) {
                append("$managerIntent\n\n")
            } else {
                append("Please review the curriculum and prepare lab delivery readiness for $courseName")
                if (courseCode.isNotBlank()) append(" ($courseCode)")
                append(".\n\n")
            }
            append("Official courseware and module breakdown are available in SkillSync.")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Request Course Preparation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = sk.frost)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (owners.size > 1) {
                    Text("Select Trainer:", style = MaterialTheme.typography.labelSmall, color = sk.subText)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        owners.take(3).forEach { o ->
                            val name = o.str("trainer_name")
                            ToneChip(
                                text = name,
                                tint = if (selectedTrainer == name) sk.brand else sk.subText,
                                solid = selectedTrainer == name,
                                modifier = Modifier.pressable { selectedTrainer = name },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = managerIntent,
                    onValueChange = { managerIntent = it },
                    label = { Text("Manager instruction (optional)") },
                    placeholder = { Text("e.g. please review labs before next week's enterprise delivery") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    textStyle = MaterialTheme.typography.bodySmall,
                )

                SkillSyncCard(
                    modifier = Modifier.fillMaxWidth(),
                    severity = Severity.Info,
                ) {
                    Text(
                        "Policy: COURSE_PREPARATION_REQUEST",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = sk.brand,
                    )
                    Text(
                        "Internal pricing, private trainer notes, and commercial margins are automatically stripped.",
                        style = MaterialTheme.typography.bodySmall,
                        color = sk.subText,
                    )
                }

                Text("PREVIEW:", style = MaterialTheme.typography.labelSmall, color = sk.labelText, fontWeight = FontWeight.Bold)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = sk.cardBg,
                    modifier = Modifier.fillMaxWidth().border(1.dp, sk.cardBorder, RoundedCornerShape(8.dp)),
                ) {
                    Text(
                        composedMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = sk.bodyText,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Preparation Request", composedMessage))
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = sk.brand, contentColor = sk.frost),
            ) {
                Text("Copy Message")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = sk.subText)
            }
        },
    )
}
