package com.example.skillsync.ui.trainer

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.skillsync.R
import com.example.skillsync.data.api.RetrofitClient
import com.example.skillsync.theme.AuroraBackground
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.editorialRule
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.components.SectionHeader
import com.example.skillsync.ui.components.pressable
import com.example.skillsync.ui.components.rows
import com.example.skillsync.ui.components.str
import com.example.skillsync.ui.components.strings
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
 * (manager or self). The two things a trainer most wants and cannot easily get.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
                TopAppBar(
                    title = {
                        Column {
                            Text(title.ifBlank { "Practice record" },
                                style = MaterialTheme.typography.headlineSmall, color = sk.bodyText)
                            Text("LEARNER VOICE · SESSION RECORDINGS",
                                style = MaterialTheme.typography.labelSmall, color = sk.sky)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_back), "Back", tint = sk.ice)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
        ) { pad ->
            Column(Modifier.padding(pad).fillMaxSize()) {
                TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                    Tab(selected = tab == 0, onClick = { tab = 0 },
                        text = { Text("Learner comments (${feedback.size})") })
                    Tab(selected = tab == 1, onClick = { tab = 1 },
                        text = { Text("Recordings (${recordings.size})") })
                }
                if (loading && feedback.isEmpty() && recordings.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator(color = sk.brand)
                    }
                } else if (tab == 0) {
                    if (feedback.isEmpty()) {
                        Empty("No learner comments on record yet.")
                    } else LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = Space.xl),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            top = Space.md, bottom = Space.xxl),
                    ) {
                        items(feedback) { e ->
                            val concern = e.str("kind") == "concern"
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = Space.md).editorialRule(),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        e.str("date"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (concern) sk.warn else sk.labelText,
                                    )
                                    if (e.str("rating").isNotBlank()) {
                                        Spacer(Modifier.height(0.dp))
                                        Text("  ·  ${e.str("rating")}/5",
                                            style = MaterialTheme.typography.labelSmall, color = sk.aqua)
                                    }
                                    if (concern) {
                                        Text("  ·  CONCERN",
                                            style = MaterialTheme.typography.labelSmall, color = sk.warn)
                                    }
                                }
                                if (e.str("question").isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(e.str("question"),
                                        style = MaterialTheme.typography.labelMedium, color = sk.subText)
                                }
                                Spacer(Modifier.height(2.dp))
                                Text("“${e.str("answer")}”",
                                    style = MaterialTheme.typography.bodyLarge, color = sk.bodyText)
                            }
                        }
                    }
                } else {
                    if (recordings.isEmpty()) {
                        Empty("No session recordings found for the last year.")
                    } else LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = Space.xl),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            top = Space.md, bottom = Space.xxl),
                    ) {
                        items(recordings) { r ->
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = Space.md).editorialRule(),
                            ) {
                                Text(r.str("course"),
                                    style = MaterialTheme.typography.titleMedium, color = sk.bodyText)
                                Text(
                                    listOf(r.str("start_date"), r.str("vendor"))
                                        .filter { it.isNotBlank() }.joinToString("  ·  "),
                                    style = MaterialTheme.typography.bodySmall, color = sk.subText,
                                )
                                r.strings("links").forEachIndexed { i, link ->
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Open recording ${if (r.strings("links").size > 1) "#${i + 1}" else ""}".trim(),
                                        style = MaterialTheme.typography.labelMedium, color = sk.brand,
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

@Composable
private fun Empty(text: String) {
    Box(Modifier.fillMaxSize().padding(Space.xl), Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.skill.subText)
    }
}
