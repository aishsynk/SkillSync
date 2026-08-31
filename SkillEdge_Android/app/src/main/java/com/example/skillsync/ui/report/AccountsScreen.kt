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
 * "Accounts" — the manager's team seen through the customers they deliver for.
 * A top summary line, then one expandable card per account: batches delivered /
 * upcoming / open demand as three counts, trainer chips, course list, next
 * start. A red hairline marks the top account when it is over half the team's
 * delivery. Insight only — managers cannot allocate.
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
                        item { SummaryLine(s, sk) }
                        items(s.accounts.size) { i ->
                            val a = s.accounts[i]
                            val isTop = a.name == s.summary.topAccount && s.summary.topAccountShare > 50.0
                            AccountCard(a, isTop, sk, onOpenTrainer)
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
private fun SummaryLine(s: AccountsState.Success, sk: SkillColors) {
    val sum = s.summary
    val line = if (sum.topAccount.isNotBlank())
        "You deliver for ${sum.accountCount} ${if (sum.accountCount == 1) "account" else "accounts"}; " +
            "${sum.topAccount} is ${fmtPct(sum.topAccountShare)} of your team's batches."
    else
        "You deliver for ${sum.accountCount} ${if (sum.accountCount == 1) "account" else "accounts"}."
    Surface(
        shape = RoundedCornerShape(Radii.card),
        color = sk.cardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, sk.cardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                line,
                color = sk.bodyText,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (sum.unspecifiedBatches > 0) {
                Text(
                    "${sum.unspecifiedBatches} ${if (sum.unspecifiedBatches == 1) "batch has" else "batches have"} no account on record",
                    color = sk.subText,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

private fun fmtPct(v: Double): String =
    if (v == v.toLong().toDouble()) "${v.toLong()}%" else "${v}%"

@Composable
private fun AccountCard(
    a: AccountRow,
    isTop: Boolean,
    sk: SkillColors,
    onOpenTrainer: (String, String) -> Unit,
) {
    var expanded by remember(a.name) { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(Radii.card),
        color = sk.cardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, sk.cardBorder),
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
    ) {
        Column {
            if (isTop) {
                Box(Modifier.fillMaxWidth().height(2.dp).background(sk.red))
            }
            Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            "★ ${a.avgLearnerRating}",
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
                }
                if (isTop) {
                    Text(
                        "Over half of your team's delivery goes to this one account.",
                        color = sk.red,
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
                        Text("Trainers", color = sk.subText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        FlowChips(a.trainers, sk) { name -> onOpenTrainer("", name) }
                    }
                    if (a.courses.isNotEmpty()) {
                        Text("Courses", color = sk.subText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        a.courses.forEach { c ->
                            Text("• $c", color = sk.bodyText, fontSize = 12.sp)
                        }
                    }
                    if (a.participantsDelivered > 0) {
                        Text(
                            "${a.participantsDelivered} learners delivered in the trailing window",
                            color = sk.subText,
                            fontSize = 11.sp,
                        )
                    }
                } else {
                    Text(
                        if (a.trainers.isEmpty()) "Tap for detail"
                        else "${a.trainers.size} ${if (a.trainers.size == 1) "trainer" else "trainers"} · ${a.courses.size} ${if (a.courses.size == 1) "course" else "courses"} · tap for detail",
                        color = sk.sky,
                        fontSize = 11.sp,
                    )
                }
            }
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
