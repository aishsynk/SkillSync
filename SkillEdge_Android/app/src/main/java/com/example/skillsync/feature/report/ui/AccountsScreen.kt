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
import androidx.compose.ui.unit.em
import com.example.skillsync.core.ui.DistributionBar
import com.example.skillsync.core.ui.Slice
import com.example.skillsync.theme.SkillColors
import com.example.skillsync.theme.SkillSyncEmptyState
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.accentGlass
import com.example.skillsync.theme.glassSurface
import com.example.skillsync.theme.skill
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.material3.Text

/**
 * "Accounts" — the manager's team seen through the customers they deliver for.
 * A concentration panel first (counted facts, a proportional bar of delivered
 * batches, and the concentration conclusion), then one expandable card per
 * account: delivered / upcoming / open demand / trainers, course list and next
 * start. Every percentage states its numerator, denominator and window. The
 * top account is accented when it took over half the delivery. Insight only —
 * managers cannot allocate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    managerEmail: String,
    onOpenTrainer: (email: String, name: String) -> Unit,
    onBack: () -> Unit,
    vm: AccountsViewModel = viewModel(),
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
                        Text("Accounts", fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            "Who your team delivers for",
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
                is AccountsState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = sk.brand)
                }

                is AccountsState.Error -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Could not load the account book", color = sk.warn, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(s.message, color = sk.subText, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { vm.refresh() }, colors = ButtonDefaults.buttonColors(containerColor = sk.brand)) {
                        Text("Retry")
                    }
                }

                is AccountsState.Success -> PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = { vm.refresh() },
                ) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item { ConcentrationPanel(s, sk) }
                        if (s.accounts.isEmpty()) {
                            item {
                                SkillSyncEmptyState(
                                    title = "No accounts on the book",
                                    description = "Nothing your team delivered, has scheduled, or has open " +
                                        "demand for carries a customer account yet.",
                                    iconRes = R.drawable.ic_globe,
                                )
                            }
                        }
                        items(s.accounts.size) { i ->
                            val a = s.accounts[i]
                            val isTop = a.name == s.summary.topAccount && s.summary.topAccountShare > 50.0
                            AccountCard(a, isTop, s.summary, sk, onOpenTrainer)
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
 * Concentration intelligence, not a sentence. Four counted facts, then a real
 * proportional bar of delivered batches by account, then the concentration
 * conclusion — each percentage stated with its numerator, denominator and
 * window, so "58%" can never be read as "58% of everything, forever".
 */
@Composable
private fun ConcentrationPanel(s: AccountsState.Success, sk: SkillColors) {
    val sum = s.summary
    val past = if (sum.pastDays > 0) "last ${sum.pastDays} days" else "trailing window"
    val forward = if (sum.forwardDays > 0) "next ${sum.forwardDays} days" else "forward window"
    val openDemand = s.accounts.sumOf { it.openDemandBatches }
    val upcoming = s.accounts.sumOf { it.batchesUpcoming }

    // Top five by delivered batches, with the remainder collected honestly
    // rather than dropped, so the bar always sums to the stated denominator.
    val ranked = s.accounts.filter { it.batchesDelivered > 0 }.sortedByDescending { it.batchesDelivered }
    val palette = listOf(sk.blue, sk.teal, sk.indigo, sk.amber, sk.green)
    val slices = ranked.take(5).mapIndexed { i, a -> Slice(a.name, a.batchesDelivered, palette[i % palette.size]) }
        .let { head ->
            val rest = ranked.drop(5).sumOf { it.batchesDelivered }
            if (rest > 0) head + Slice("Other", rest, sk.subText) else head
        }

    Column(
        Modifier.fillMaxWidth().glassSurface().padding(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Text(
            "DELIVERY CONCENTRATION",
            style = MaterialTheme.typography.labelSmall,
            color = sk.labelText, fontWeight = FontWeight.Bold, letterSpacing = 0.08.em,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ConcentrationStat("Accounts", sum.accountCount.toString(), "on the book", sk)
            ConcentrationStat("Delivered", sum.teamBatchesDelivered.toString(), past, sk)
            ConcentrationStat("Upcoming", upcoming.toString(), forward, sk)
            ConcentrationStat(
                "Open demand", openDemand.toString(), "unallocated", sk,
                tint = if (openDemand > 0) sk.amber else null,
            )
        }

        if (slices.isNotEmpty() && sum.teamBatchesDelivered > 0) {
            DistributionBar(slices)
            Text(
                "Share of the ${sum.teamBatchesDelivered} batches your team delivered in the $past.",
                style = MaterialTheme.typography.labelSmall, color = sk.labelText,
            )
        } else {
            Text(
                "No batches were delivered in the $past, so no delivery share can be calculated.",
                style = MaterialTheme.typography.bodySmall, color = sk.subText,
            )
        }

        if (sum.topAccount.isNotBlank() && sum.teamBatchesDelivered > 0) {
            val concentrated = sum.topAccountShare > 50.0
            val tint = if (concentrated) sk.crit else sk.sky
            Column(
                Modifier.fillMaxWidth().accentGlass(tint).padding(Space.md),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    if (concentrated) "Concentrated on one account" else "Delivery is spread across accounts",
                    style = MaterialTheme.typography.titleSmall,
                    color = tint, fontWeight = FontWeight.Bold,
                )
                Text(
                    "${sum.topAccount} took ${sum.topAccountBatches} of ${sum.teamBatchesDelivered} " +
                        "delivered batches in the $past (${fmtPct(sum.topAccountShare)}).",
                    style = MaterialTheme.typography.bodySmall, color = sk.bodyText,
                )
                if (concentrated) {
                    Text(
                        "Losing or pausing this account would idle more than half your delivery.",
                        style = MaterialTheme.typography.labelSmall, color = sk.subText,
                    )
                }
            }
        }

        if (sum.unspecifiedBatches > 0) {
            Text(
                "${sum.unspecifiedBatches} ${if (sum.unspecifiedBatches == 1) "batch carries" else "batches carry"} " +
                    "no account on record and " +
                    (if (sum.unspecifiedBatches == 1) "is" else "are") +
                    " excluded from the shares above.",
                style = MaterialTheme.typography.labelSmall, color = sk.subText,
            )
        }
    }
}

/** One counted fact with its basis printed underneath, never a bare number. */
@Composable
private fun ConcentrationStat(
    label: String,
    value: String,
    basis: String,
    sk: SkillColors,
    tint: Color? = null,
) {
    Column(horizontalAlignment = Alignment.Start, modifier = Modifier.width(78.dp)) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            color = tint ?: sk.bodyText, fontWeight = FontWeight.Bold,
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = sk.labelText, maxLines = 1)
        Text(
            basis,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = sk.subText, maxLines = 2,
        )
    }
}

private fun fmtPct(v: Double): String =
    if (v == v.toLong().toDouble()) "${v.toLong()}%" else "${v}%"

@Composable
private fun AccountCard(
    a: AccountRow,
    isTop: Boolean,
    summary: AccountsSummary,
    sk: SkillColors,
    onOpenTrainer: (String, String) -> Unit,
) {
    var expanded by remember(a.name) { mutableStateOf(false) }
    val accent = when {
        isTop -> sk.crit
        a.openDemandBatches > 0 -> sk.amber
        else -> sk.cardBorder
    }
    Column(
        Modifier
            .fillMaxWidth()
            .let { if (accent == sk.cardBorder) it.glassSurface() else it.accentGlass(accent) }
            .clickable { expanded = !expanded }
            .padding(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                a.name,
                color = sk.bodyText,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (a.avgLearnerRating != null) {
                Text(
                    "Rating ${a.avgLearnerRating}",
                    color = sk.good,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            CountStat(a.batchesDelivered.toString(), "delivered", sk)
            CountStat(a.batchesUpcoming.toString(), "upcoming", sk)
            CountStat(
                a.openDemandBatches.toString(), "open demand", sk,
                emphasise = a.openDemandBatches > 0,
            )
            CountStat(a.trainers.size.toString(), "trainers", sk)
        }
        // The warning names the fraction it is based on; a bare "over half"
        // would leave the manager guessing over half of what, and when.
        if (isTop && summary.teamBatchesDelivered > 0) {
            Text(
                "Concentration risk: ${a.batchesDelivered} of ${summary.teamBatchesDelivered} " +
                    "delivered batches in the " +
                    (if (summary.pastDays > 0) "last ${summary.pastDays} days" else "trailing window") +
                    " were for this account.",
                color = sk.crit,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        val nextLine = buildList {
            if (a.nextStartDate.isNotBlank()) add("next start ${humanDate(a.nextStartDate)}")
            if (a.lastDeliveryDate.isNotBlank()) add("last delivery ${humanDate(a.lastDeliveryDate)}")
        }.joinToString(" · ")
        if (nextLine.isNotBlank()) {
            Text(nextLine, color = sk.subText, fontSize = 11.sp)
        }

        if (expanded) {
            if (a.trainers.isNotEmpty()) {
                Text("Trainers", color = sk.labelText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                FlowChips(a.trainers, sk) { name -> onOpenTrainer("", name) }
            }
            if (a.courses.isNotEmpty()) {
                Text("Courses", color = sk.labelText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                a.courses.forEach { c ->
                    Text("• $c", color = sk.bodyText, fontSize = 12.sp)
                }
            }
            if (a.participantsDelivered > 0) {
                Text(
                    "${a.participantsDelivered} learners delivered in the " +
                        (if (summary.pastDays > 0) "last ${summary.pastDays} days" else "trailing window"),
                    color = sk.subText,
                    fontSize = 11.sp,
                )
            }
        } else {
            Text(
                if (a.courses.isEmpty()) "Tap for detail"
                else "${a.courses.size} ${if (a.courses.size == 1) "course" else "courses"} · tap for detail",
                color = sk.sky,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun CountStat(value: String, label: String, sk: SkillColors, emphasise: Boolean = false) {
    Column {
        Text(
            value,
            color = if (emphasise) sk.amber else sk.bodyText,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
        Text(label, color = sk.subText, fontSize = 10.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowChips(items: List<String>, sk: SkillColors, onClick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { name ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(sk.sky.copy(alpha = 0.15f))
                    .clickable { onClick(name) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(name, color = sk.sky, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
