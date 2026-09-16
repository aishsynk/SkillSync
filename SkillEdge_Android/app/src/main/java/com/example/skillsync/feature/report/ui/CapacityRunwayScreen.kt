package com.example.skillsync.feature.report.ui

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
import com.example.skillsync.theme.ActionRow
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.SkillColors
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.accentGlass
import com.example.skillsync.theme.glassSurface
import com.example.skillsync.theme.pressable
import com.example.skillsync.theme.skill
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.material3.Text

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
            com.example.skillsync.theme.SkillSyncTopBar(
                title = "Capacity Runway",
                subtitle = "Next 8 weeks — demand vs capacity",
                onBack = onBack,
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
                        item { CoverageInsight(s, sk) }
                        item { CoverageExplanation(s, sk) }
                        item { WeekBars(s.weeks, sk) }
                        if (s.upskilling.isNotEmpty()) {
                            item {
                                Column(Modifier.padding(top = 4.dp)) {
                                    Text(
                                        "Start these upskills now",
                                        color = sk.bodyText, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                                    )
                                    Text(
                                        "Ranked by how many uncovered batches each one unlocks.",
                                        color = sk.subText, style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            items(s.upskilling.size) { i ->
                                UpskillCard(i + 1, s.upskilling[i], sk, onOpenTrainer)
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

/**
 * The four figures a planner reads first. Skill gaps counts the uncovered
 * batches the upskilling list would unlock, so it is real data, not a guess.
 */
@Composable
private fun CoverageInsight(s: RunwayState.Success, sk: SkillColors) {
    val sum = s.summary
    val skillGapBatches = s.upskilling.sumOf { it.opensBatches }
    Box(Modifier.fillMaxWidth().glassSurface()) {
        Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth()) {
                InsightCell("Coverage", "${sum.totalCoverable}/${sum.totalDemand}", if (sum.totalCoverable < sum.totalDemand) sk.amber else sk.good, sk, Modifier.weight(1f))
                InsightCell("Free capacity", "${sum.trainerDaysAvailable} days", sk.sky, sk, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                InsightCell("Demand", "${sum.trainerDaysDemanded} days", sk.brand, sk, Modifier.weight(1f))
                InsightCell(
                    "Skill gaps",
                    if (skillGapBatches > 0) "$skillGapBatches batches" else "none",
                    if (skillGapBatches > 0) sk.crit else sk.good, sk, Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun InsightCell(label: String, value: String, tint: Color, sk: SkillColors, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, color = tint, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(
            label.uppercase(), color = sk.labelText, fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The business story the old screen never told: free trainer-days can exceed
 * demanded trainer-days while batches stay uncovered, because capacity is only
 * usable if the right person is free at the right time with the right skill.
 * Every clause here is conditional on real data.
 */
@Composable
private fun CoverageExplanation(s: RunwayState.Success, sk: SkillColors) {
    val sum = s.summary
    val uncovered = (sum.totalDemand - sum.totalCoverable).coerceAtLeast(0)
    if (uncovered == 0) return
    val aggregateLooksFine = sum.trainerDaysAvailable >= sum.trainerDaysDemanded
    val skillGapBatches = s.upskilling.sumOf { it.opensBatches }
    val gapCourses = s.upskilling.take(3).map { it.course }
    val tightWeeks = s.weeks.count { it.gap > 0 }

    val reasons = buildList {
        if (skillGapBatches > 0) {
            add("skill: nobody on the team can teach " + gapCourses.joinToString(", ") +
                (if (s.upskilling.size > 3) " and others" else ""))
        }
        if (tightWeeks > 0) {
            add("timing: " + tightWeeks + (if (tightWeeks == 1) " week is" else " weeks are") + " short of capacity even where the skill exists")
        }
    }
    Box(Modifier.fillMaxWidth().accentGlass(if (aggregateLooksFine) sk.amber else sk.crit)) {
        Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (aggregateLooksFine) "Capacity exists, coverage does not" else "Capacity is short of demand",
                color = sk.frost, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall,
            )
            Text(
                if (aggregateLooksFine) {
                    "${sum.trainerDaysAvailable} trainer-days are free against ${sum.trainerDaysDemanded} trainer-days of demand, yet $uncovered " +
                        "${if (uncovered == 1) "batch cannot" else "batches cannot"} be covered. " +
                        "Aggregate trainer-days are not coverage."
                } else {
                    "${sum.trainerDaysAvailable} trainer-days are free against ${sum.trainerDaysDemanded} trainer-days of demand, and " +
                        "$uncovered ${if (uncovered == 1) "batch cannot" else "batches cannot"} be covered."
                },
                color = sk.bodyText, style = MaterialTheme.typography.bodyMedium,
            )
            reasons.forEach { reason ->
                Text("\u2022  " + reason, color = sk.subText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SummaryLine(s: RunwayState.Success, sk: SkillColors) {
    val sum = s.summary
    val tightest = if (sum.worstWeek.isNotBlank()) humanDate(sum.worstWeek) else "none"
    Box(Modifier.fillMaxWidth().glassSurface()) {
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

    Box(Modifier.fillMaxWidth().glassSurface()) {
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
                        // Demand bar. A week with no demand draws nothing at all
                        // rather than a meaningless zero-height stub.
                        if (w.demandBatches > 0) {
                            Box(
                                Modifier
                                    .fillMaxWidth(0.72f)
                                    .fillMaxHeight(demandFrac.coerceIn(0.04f, 1f))
                                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                    .background(barColor),
                            )
                        }
                        // Capacity line, only where capacity actually exists.
                        if (w.teamAvailable > 0) {
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
            }
            // Zero baseline, so bar heights are read against a real axis.
            Box(Modifier.fillMaxWidth().height(1.dp).background(sk.cardBorder))
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                weeks.forEach { w ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            humanDate(w.weekStart),
                            color = if (w.gap > 0) sk.amber else sk.subText,
                            fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 1,
                        )
                        if (w.gap > 0) {
                            Text(
                                "short ${w.gap}",
                                color = sk.crit, fontSize = 9.sp, maxLines = 1,
                            )
                        }
                    }
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
        Text(label, color = sk.subText, fontSize = 11.sp)
    }
}

/** The runway's own row family, on the same shared [ActionRow]/[accentGlass]
 * pair Today and This Week already use — an "opportunity" tint (sky), not a
 * severity one, since an upskill suggestion isn't an attention item. */
@Composable
private fun UpskillCard(rank: Int, u: RunwayUpskill, sk: SkillColors, onOpenTrainer: (String, String) -> Unit) {
    val metadata = buildString {
        if (u.examCode.isNotBlank()) append(u.examCode).append(" · ")
        append("opens ${u.opensBatches} ${if (u.opensBatches == 1) "batch" else "batches"}")
        if (u.nearestTrainerName.isNotBlank()) append(" · closest: ${u.nearestTrainerName}")
    }
    val clickable = u.nearestTrainer.isNotBlank()
    Box(
        Modifier
            .fillMaxWidth()
            .accentGlass(sk.sky)
            .then(if (clickable) Modifier.pressable { onOpenTrainer(u.nearestTrainer, u.nearestTrainerName) } else Modifier),
    ) {
        ActionRow(
            title = "$rank.  " + u.course,
            modifier = Modifier.padding(horizontal = Space.md),
            supportingText = u.why,
            metadata = metadata,
            tint = sk.sky,
        )
    }
}
