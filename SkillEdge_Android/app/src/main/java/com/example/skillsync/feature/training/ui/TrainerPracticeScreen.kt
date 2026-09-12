package com.example.skillsync.feature.training.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.skillsync.core.network.RetrofitClient
import com.example.skillsync.core.ui.pressable
import com.example.skillsync.core.ui.rows
import com.example.skillsync.core.ui.str
import com.example.skillsync.core.ui.strings
import com.example.skillsync.theme.AuroraBackground
import com.example.skillsync.theme.Severity
import com.example.skillsync.theme.SkillSyncCard
import com.example.skillsync.theme.SkillSyncEmptyState
import com.example.skillsync.theme.SkillSyncInfoBanner
import com.example.skillsync.theme.SkillSyncLoadingState
import com.example.skillsync.theme.SkillSyncTopBar
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.editorialRule
import com.example.skillsync.theme.skill
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TrainerPracticeViewModel : ViewModel() {
    private val _feedback = MutableStateFlow<List<Map<*, *>>>(emptyList())
    val feedback: StateFlow<List<Map<*, *>>> = _feedback
    private val _recordings = MutableStateFlow<List<Map<*, *>>>(emptyList())
    val recordings: StateFlow<List<Map<*, *>>> = _recordings
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    fun load(email: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                _feedback.value = RetrofitClient.instance.trainerFeedbackLog(email).rows("entries")
            } catch (_: Exception) {}
            try {
                _recordings.value = RetrofitClient.instance.trainerRecordings(email).rows("recordings")
            } catch (_: Exception) {}
            _loading.value = false
        }
    }
}

/**
 * A trainer's own practice record — every learner comment as a dated log, and
 * the download links to their delivered sessions. Reachable from Trainer 360
 * (manager or self).
 */
@Composable
fun TrainerPracticeScreen(
    email: String,
    title: String,
    onBack: () -> Unit,
    vm: TrainerPracticeViewModel = viewModel(),
) {
    val sk = MaterialTheme.skill
    LaunchedEffect(email) { vm.load(email) }
    val feedback by vm.feedback.collectAsState()
    val recordings by vm.recordings.collectAsState()
    val loading by vm.loading.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    val uri = LocalUriHandler.current

    Box(Modifier.fillMaxSize()) {
        AuroraBackground()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                SkillSyncTopBar(
                    title = title.ifBlank { "Practice Record" },
                    subtitle = "Learner Voice & Session Recordings",
                    onBack = onBack,
                )
            },
        ) { pad ->
            Column(Modifier.padding(pad).fillMaxSize()) {
                SkillSyncInfoBanner(
                    title = "Learner Voice Context",
                    message = "Qualitative learner feedback and session recordings reflect delivery experience. They inform coaching conversations and do not automatically alter verified readiness ratings.",
                    severity = Severity.Info,
                    modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm),
                )

                TabRow(
                    selectedTabIndex = tab,
                    containerColor = Color.Transparent,
                    contentColor = sk.cyan,
                ) {
                    Tab(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        text = {
                            Text(
                                "Learner comments (${feedback.size})",
                                color = if (tab == 0) sk.frost else sk.labelText,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                    )
                    Tab(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        text = {
                            Text(
                                "Recordings (${recordings.size})",
                                color = if (tab == 1) sk.frost else sk.labelText,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                    )
                }

                if (loading && feedback.isEmpty() && recordings.isEmpty()) {
                    SkillSyncLoadingState()
                } else if (tab == 0) {
                    if (feedback.isEmpty()) {
                        SkillSyncEmptyState(
                            title = "No Comments",
                            description = "No learner feedback comments recorded for this trainer yet.",
                        )
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize().padding(horizontal = Space.lg),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                top = Space.md, bottom = Space.xxl,
                            ),
                        ) {
                            items(feedback) { e ->
                                val concern = e.str("kind") == "concern"
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = Space.md)
                                        .editorialRule(),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            e.str("date"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (concern) sk.warn else sk.labelText,
                                        )
                                        if (e.str("rating").isNotBlank()) {
                                            Text(
                                                "  ·  ${e.str("rating")}/5",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = sk.cyan,
                                            )
                                        }
                                        if (concern) {
                                            Text(
                                                "  ·  CONCERN",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = sk.warn,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                    if (e.str("question").isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            e.str("question"),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = sk.subText,
                                        )
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "“${e.str("answer")}”",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = sk.bodyText,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    if (recordings.isEmpty()) {
                        SkillSyncEmptyState(
                            title = "No Recordings",
                            description = "No delivered session recordings found for the past year.",
                        )
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize().padding(horizontal = Space.lg),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                top = Space.md, bottom = Space.xxl,
                            ),
                        ) {
                            items(recordings) { r ->
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = Space.md)
                                        .editorialRule(),
                                ) {
                                    Text(
                                        r.str("course"),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = sk.bodyText,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        listOf(r.str("start_date"), r.str("vendor"))
                                            .filter { it.isNotBlank() }.joinToString("  ·  "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = sk.subText,
                                    )
                                    r.strings("links").forEachIndexed { i, link ->
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            "Open recording ${if (r.strings("links").size > 1) "#${i + 1}" else ""}".trim(),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = sk.brand,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.pressable { runCatching { uri.openUri(link) } },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
