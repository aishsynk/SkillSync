package com.example.skillsync.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.skillsync.R
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.SkillColors
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.skill
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * "New trainer ramp" — one card per reportee who joined in the last 12 months:
 * tenure, ramp stage, batches delivered, days to first batch, learner rating,
 * and the one deterministic next step. Insight only, never an allocation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RampScreen(
    managerEmail: String,
    onOpenTrainer: (email: String, name: String) -> Unit,
    onBack: () -> Unit,
    vm: RampViewModel = viewModel(),
) {
    val sk = MaterialTheme.skill
    val context = LocalContext.current

    LaunchedEffect(managerEmail) { if (managerEmail.isNotBlank()) vm.init(managerEmail, context) }

    val state by vm.state.collectAsState()
    val refreshing by vm.refreshing.collectAsState()

    Scaffold(
        containerColor = sk.pageBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("New trainer ramp", fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            "Joined in the last 12 months",
                            color = Color.White.copy(alpha = 0.78f),
                            fontSize = 13.sp,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_back), "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = sk.heroBg),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is RampState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = sk.brand)
                }

                is RampState.Error -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Could not load the ramp", color = sk.warn, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(s.message, color = sk.subText, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { vm.refresh() }, colors = ButtonDefaults.buttonColors(containerColor = sk.brand)) {
                        Text("Retry")
                    }
                }

                is RampState.Success -> PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = { vm.refresh() },
                ) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item { SummaryLine(s.summary, sk) }
                        if (s.trainers.isEmpty()) {
                            item {
                                Text(
                                    s.summary.note.ifBlank { "No trainers joined in the last 12 months." },
                                    color = sk.subText,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                        items(s.trainers.size) { i ->
                            RampCard(s.trainers[i], sk, onOpenTrainer)
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

private fun humanDate(iso: String): String = try {
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK))
} catch (_: Exception) {
    iso
}

private fun stageColor(stage: String, stalled: Boolean, sk: SkillColors): Color = when {
    stalled -> sk.amber
    stage == "established" -> sk.good
    stage == "first-deliveries" -> sk.sky
    else -> sk.subText
}

private fun stageLabel(stage: String, stalled: Boolean): String = when {
    stalled -> "stalled"
    else -> stage
}

@Composable
private fun SummaryLine(sum: RampSummary, sk: SkillColors) {
    val avg = sum.avgDaysToFirstBatch
    Surface(
        shape = RoundedCornerShape(Radii.card),
        color = sk.cardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, sk.cardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "${sum.newCount} new " + (if (sum.newCount == 1) "trainer" else "trainers") +
                    (if (sum.stalledCount > 0) " · ${sum.stalledCount} stalled" else ""),
                color = sk.bodyText,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (avg != null) "Average $avg days from joining to first batch"
                else "No first batch delivered yet across the group",
                color = sk.subText,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun RampCard(t: RampTrainer, sk: SkillColors, onOpenTrainer: (String, String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(Radii.card),
        color = sk.cardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, sk.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (t.email.isNotBlank())
                    Modifier.clickable { onOpenTrainer(t.email, t.name) }
                else Modifier
            ),
    ) {
        Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    t.name.ifBlank { t.email },
                    color = sk.bodyText,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val c = stageColor(t.rampStage, t.stalled, sk)
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(c.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(stageLabel(t.rampStage, t.stalled), color = c, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                "${t.tenureMonths} " + (if (t.tenureMonths == 1) "month" else "months") +
                    " in · joined ${humanDate(t.doj)}",
                color = sk.subText,
                fontSize = 11.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Stat("batches", t.batchesDelivered.toString(), sk)
                Stat(
                    "to 1st batch",
                    t.daysToFirstBatch?.let { "${it}d" } ?: "—",
                    sk,
                )
                Stat(
                    "rating",
                    t.avgLearnerRating?.let { "$it (${t.ratingSample})" } ?: "—",
                    sk,
                )
                Stat("util", t.currentUtilization?.let { "$it%" } ?: "—", sk)
            }
            Text(t.nextStep, color = sk.bodyText, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, sk: SkillColors) {
    Column {
        Text(value, color = sk.bodyText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Text(label, color = sk.subText, fontSize = 9.sp)
    }
}
