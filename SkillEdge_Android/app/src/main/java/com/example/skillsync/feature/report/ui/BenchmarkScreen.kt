package com.example.skillsync.ui.report

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.skillsync.R
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.SkillColors
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.skill
import kotlin.math.abs

/**
 * "How your team compares" — each metric shows the team value big, the baseline
 * muted, a horizontal bar for the gap, and a verdict chip. The `baseline_source`
 * sits as a caption under the title because being honest about what the
 * comparison is against is the whole point of the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchmarkScreen(
    managerEmail: String,
    onBack: () -> Unit,
    vm: BenchmarkViewModel = viewModel(),
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
                        Text("How your team compares", fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            "Team health vs an honest baseline",
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
                is BenchmarkState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = sk.brand)
                }

                is BenchmarkState.Error -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Could not load the comparison", color = sk.warn, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(s.message, color = sk.subText, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { vm.refresh() }, colors = ButtonDefaults.buttonColors(containerColor = sk.brand)) {
                        Text("Retry")
                    }
                }

                is BenchmarkState.Success -> PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = { vm.refresh() },
                ) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item { HeadlineCard(s, sk) }
                        items(s.metrics.size) { i -> MetricRow(s.metrics[i], sk) }
                        item {
                            Text(
                                "Baseline: ${s.baselineSource}",
                                color = sk.subText,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

private fun fmt(v: Double?): String {
    if (v == null) return "n/a"
    return if (v == v.toLong().toDouble()) v.toLong().toString() else String.format("%.1f", v)
}

@Composable
private fun HeadlineCard(s: BenchmarkState.Success, sk: SkillColors) {
    Surface(
        shape = RoundedCornerShape(Radii.card),
        color = sk.cardBg,
        border = BorderStroke(1.dp, sk.cardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                s.headline,
                color = sk.bodyText,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "${s.aheadCount} ahead · ${s.behindCount} behind",
                color = sk.subText,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun MetricRow(m: BenchmarkMetric, sk: SkillColors) {
    val (chipBg, chipText, chipLabel) = when (m.verdict) {
        "ahead" -> Triple(sk.good.copy(alpha = 0.15f), sk.good, "ahead")
        "behind" -> Triple(sk.amber.copy(alpha = 0.18f), sk.amber, "behind")
        "on_par" -> Triple(sk.subText.copy(alpha = 0.15f), sk.subText, "on par")
        else -> Triple(sk.subText.copy(alpha = 0.12f), sk.subText, "no data")
    }
    val unitSuffix = if (m.unit == "/5") "" else m.unit

    Surface(
        shape = RoundedCornerShape(Radii.card),
        color = sk.cardBg,
        border = BorderStroke(1.dp, sk.cardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(m.label, color = sk.bodyText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp)).background(chipBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(chipLabel, color = chipText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${fmt(m.teamValue)}$unitSuffix",
                    color = sk.bodyText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                )
                Text(
                    "vs ${fmt(m.baselineValue)}$unitSuffix baseline",
                    color = sk.subText,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
            GapBar(m, sk, chipText)
            m.gap?.let {
                val betterWord = if (m.direction == "higher_better") {
                    if (it >= 0) "above" else "below"
                } else {
                    if (it <= 0) "under" else "over"
                }
                Text(
                    "${fmt(abs(it))}$unitSuffix $betterWord the baseline",
                    color = sk.subText,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun GapBar(m: BenchmarkMetric, sk: SkillColors, accent: Color) {
    val team = m.teamValue ?: 0.0
    val base = m.baselineValue ?: 0.0
    val scale = maxOf(team, base, 1.0)
    Box(
        Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(sk.cardBorder),
    ) {
        Box(
            Modifier.fillMaxWidth((team / scale).coerceIn(0.0, 1.0).toFloat())
                .fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(accent),
        )
        // baseline marker
        Box(
            Modifier.fillMaxWidth((base / scale).coerceIn(0.0, 1.0).toFloat())
                .fillMaxHeight(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(Modifier.width(2.dp).fillMaxHeight().background(sk.bodyText))
        }
    }
}
