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
 * "Capacity Runway" — the manager's forward view. A bar per week (incoming
 * demand height against a capacity marker; shortfall weeks tinted), a plain
 * summary line, then the ranked "start these upskills now" list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapacityRunwayScreen(
    managerEmail: String,
    onOpenTrainer: (email: String, name: String) -> Unit,
    onBack: () -> Unit,
    vm: CapacityRunwayViewModel = viewModel(),
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
                        Text("Capacity Runway", fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            "Next 8 weeks — demand vs capacity",
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
                is RunwayState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = sk.brand)
                }

                is RunwayState.Error -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Could not load the runway", color = sk.warn, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(s.message, color = sk.subText, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { vm.refresh() }, colors = ButtonDefaults.buttonColors(containerColor = sk.brand)) {
                        Text("Retry")
                    }
                }

                is RunwayState.Success -> PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = { vm.refresh() },
                ) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item { SummaryLine(s, sk) }
                        item { WeekBars(s.weeks, sk) }
                        if (s.upskilling.isNotEmpty()) {
                            item {
                                Text(
                                    "Start these upskills now",
                                    color = sk.bodyText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            items(s.upskilling.size) { i ->
                                UpskillCard(s.upskilling[i], sk, onOpenTrainer)
                            }
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

private fun humanDate(iso: String): String = try {
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("d MMM", Locale.UK))
} catch (_: Exception) {
    iso
}

@Composable
private fun SummaryLine(s: RunwayState.Success, sk: SkillColors) {
    val sum = s.summary
    val tightest = if (sum.worstWeek.isNotBlank()) humanDate(sum.worstWeek) else "none"
    Surface(
        shape = RoundedCornerShape(Radii.card),
        color = sk.cardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, sk.cardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "You can cover ${sum.totalCoverable} of ${sum.totalDemand} " +
                    (if (sum.totalDemand == 1) "batch" else "batches") +
                    " over the next 8 weeks; the tightest week is $tightest.",
                color = sk.bodyText,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "${sum.trainerDaysAvailable} trainer-days free · ${sum.trainerDaysDemanded} trainer-days of demand",
                color = sk.subText,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun WeekBars(weeks: List<RunwayWeek>, sk: SkillColors) {
    if (weeks.isEmpty()) return
    val maxVal = maxOf(1, weeks.maxOf { maxOf(it.demandBatches, it.teamAvailable) })
    val chartHeight = 120.dp

    Surface(
        shape = RoundedCornerShape(Radii.card),
        color = sk.cardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, sk.cardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Space.md)) {
            Row(
                Modifier.fillMaxWidth().height(chartHeight),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                weeks.forEach { w ->
                    val demandFrac = w.demandBatches.toFloat() / maxVal
                    val capFrac = w.teamAvailable.toFloat() / maxVal
                    val barColor = when {
                        w.gap >= 2 -> sk.red
                        w.gap == 1 -> sk.amber
                        else -> sk.brand
                    }
                    Box(
                        Modifier.weight(1f).fillMaxHeight(),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        // demand bar
                        Box(
                            Modifier
                                .fillMaxWidth(0.72f)
                                .fillMaxHeight(demandFrac.coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(barColor),
                        )
                        // capacity marker
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(bottom = chartHeight * capFrac.coerceIn(0f, 1f))
                                .height(2.dp)
                                .background(sk.good),
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                weeks.forEach { w ->
                    Text(
                        humanDate(w.weekStart),
                        color = sk.subText,
                        fontSize = 9.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LegendDot(sk.brand, "demand", sk)
                LegendDot(sk.good, "capacity", sk)
                LegendDot(sk.amber, "short", sk)
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String, sk: SkillColors) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(label, color = sk.subText, fontSize = 10.sp)
    }
}

@Composable
private fun UpskillCard(u: RunwayUpskill, sk: SkillColors, onOpenTrainer: (String, String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(Radii.card),
        color = sk.cardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, sk.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (u.nearestTrainer.isNotBlank())
                    Modifier.clickable { onOpenTrainer(u.nearestTrainer, u.nearestTrainerName) }
                else Modifier
            ),
    ) {
        Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (u.examCode.isNotBlank()) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(sk.sky.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(u.examCode, color = sk.sky, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    "opens ${u.opensBatches} ${if (u.opensBatches == 1) "batch" else "batches"}",
                    color = sk.subText,
                    fontSize = 11.sp,
                )
            }
            Text(
                u.course,
                color = sk.bodyText,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(u.why, color = sk.subText, fontSize = 12.sp)
            if (u.nearestTrainerName.isNotBlank()) {
                Text("closest: ${u.nearestTrainerName}", color = sk.sky, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
