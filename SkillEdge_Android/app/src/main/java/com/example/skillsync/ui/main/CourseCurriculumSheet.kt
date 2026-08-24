package com.example.skillsync.ui.main

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.example.skillsync.data.api.RetrofitClient
import com.example.skillsync.theme.SkillCard
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.components.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseCurriculumSheet(
    courseName: String,
    courseId: String = "",
    onDismiss: () -> Unit,
) {
    val sk = MaterialTheme.skill
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var curriculumData by remember { mutableStateOf<Map<String, Any>?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(courseName, courseId) {
        loading = true
        try {
            val res = RetrofitClient.instance.getCourseCurriculum(courseName = courseName, courseId = courseId)
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
                        color = sk.bodyText,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (courseId.isNotBlank()) {
                        Text("Course Code: $courseId", style = MaterialTheme.typography.labelSmall, color = sk.cyan)
                    }
                    val activeVersion = curriculumData?.str("latest_version").orEmpty()
                    if (activeVersion.isNotBlank()) {
                        Text("🏷️ Active RMS Version: $activeVersion", style = MaterialTheme.typography.labelSmall, color = sk.aqua, fontWeight = FontWeight.Bold)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Text("✕", color = sk.subText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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
                            colors = ButtonDefaults.buttonColors(containerColor = sk.cyan),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Text("Slides PDF ↗", style = MaterialTheme.typography.labelMedium, color = Color.Black, fontWeight = FontWeight.Bold)
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
                            colors = ButtonDefaults.buttonColors(containerColor = sk.indigo),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Text("Syllabus ↗", style = MaterialTheme.typography.labelMedium, color = Color.White)
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
                            colors = ButtonDefaults.buttonColors(containerColor = sk.cyan),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Text("Course Lab ↗", style = MaterialTheme.typography.labelMedium, color = Color.Black)
                        }
                    }
                }

                // Sub-Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = sk.sky,
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Modules (${modules.size})", style = MaterialTheme.typography.labelMedium) },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Public Schedules (${schedules.size})", style = MaterialTheme.typography.labelMedium) },
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Resources (${contentUrls.size})", style = MaterialTheme.typography.labelMedium) },
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
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
                                    SkillCard(Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(
                                                Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    "Module ${mod.int("module_no")}: ${mod.str("title")}",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = sk.bodyText,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.weight(1f),
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = sk.sky.copy(alpha = 0.12f),
                                                ) {
                                                    Text(
                                                        "${mod.int("duration_hours")} hrs",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = sk.sky,
                                                        fontSize = 10.sp,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    )
                                                }
                                            }
                                            val topics = mod.str("topics")
                                            if (topics.isNotBlank()) {
                                                Text(
                                                    topics,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = sk.subText,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        1 -> {
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
                                            Text(dateStr, style = MaterialTheme.typography.bodyMedium, color = sk.bodyText, fontWeight = FontWeight.Medium)
                                        }
                                        Surface(shape = RoundedCornerShape(4.dp), color = sk.good.copy(alpha = 0.14f)) {
                                            Text("Open for enrollment", style = MaterialTheme.typography.labelSmall, color = sk.good, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
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
                                    SkillCard(Modifier.fillMaxWidth().clickable {
                                        if (url.isNotBlank()) {
                                            try {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                            } catch (_: Exception) {}
                                        }
                                    }) {
                                        Row(
                                            Modifier.padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(res.str("title").ifBlank { "Official Resource" }, style = MaterialTheme.typography.bodyMedium, color = sk.cyan, fontWeight = FontWeight.Bold)
                                                Text(url, style = MaterialTheme.typography.labelSmall, color = sk.subText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            Text("Open ↗", style = MaterialTheme.typography.labelSmall, color = sk.sky)
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
}
