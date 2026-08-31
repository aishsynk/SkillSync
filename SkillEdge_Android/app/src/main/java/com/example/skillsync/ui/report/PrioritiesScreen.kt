package com.example.skillsync.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.skillsync.R
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.SkillColors
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.skill

/**
 * "This Week" — the manager's ranked board of what needs them, driven by
 * `GET /api/v2/manager/priorities`. Summary strip of counts by kind, then the
 * items as severity-striped cards in the order the backend ranked them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrioritiesScreen(
    managerEmail: String,
    onOpenDemand: (String) -> Unit,
    onOpenTrainer: (email: String, name: String) -> Unit,
    onOpenActions: () -> Unit,
    onOpenRunway: () -> Unit = {},
    onOpenRamp: () -> Unit = {},
    onBack: () -> Unit,
    vm: PrioritiesViewModel = viewModel(),
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
                        Text("This Week", fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            "What needs you — ranked",
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
                actions = {
                    IconButton(onClick = onOpenRamp) {
                        Icon(painterResource(R.drawable.ic_people), "New trainer ramp", tint = Color.White)
                    }
                    IconButton(onClick = onOpenRunway) {
                        Icon(painterResource(R.drawable.ic_trend), "Capacity Runway", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = sk.heroBg),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is PrioritiesState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = sk.brand)
                }

                is PrioritiesState.Error -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Could not load this week", color = sk.warn, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(s.message, color = sk.subText, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { vm.refresh() }, colors = ButtonDefaults.buttonColors(containerColor = sk.brand)) {
                        Text("Retry")
                    }
                }

                is PrioritiesState.Success -> PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = { vm.refresh() },
                ) {
                    if (s.items.isEmpty()) {
                        Column(
                            Modifier.fillMaxSize().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text("Nothing needs you this week.", color = sk.bodyText, style = MaterialTheme.typography.titleSmall)
                        }
                        return@PullToRefreshBox
                    }
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item { SummaryStrip(s.counts, s.items.size, sk) }
                        items(s.items, key = { it.id.ifBlank { it.title } }) { item ->
                            PriorityCard(
                                item = item,
                                sk = sk,
                                onClick = {
                                    when (item.targetType) {
                                        "demand" -> onOpenDemand(item.targetId)
                                        "trainer" -> onOpenTrainer(item.targetId, item.targetName)
                                        else -> onOpenActions()
                                    }
                                },
                            )
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

private fun kindLabel(kind: String): String = when (kind) {
    "unstaffed_demand" -> "Unstaffed"
    "one_to_one" -> "1:1 due"
    "overload" -> "Overload"
    "cert_gap" -> "Cert gap"
    "action_overdue" -> "Overdue"
    else -> kind.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

@Composable
private fun severityColor(severity: String, sk: SkillColors): Color = when (severity) {
    "high" -> sk.red
    "medium" -> sk.amber
    else -> sk.subText
}

@Composable
private fun SummaryStrip(counts: Map<String, Int>, total: Int, sk: SkillColors) {
    val order = listOf("unstaffed_demand", "one_to_one", "overload", "cert_gap", "action_overdue")
    val entries = order.mapNotNull { k -> counts[k]?.takeIf { it > 0 }?.let { k to it } } +
        counts.filterKeys { it !in order }.filterValues { it > 0 }.toList()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "$total ${if (total == 1) "item" else "items"} open",
            color = sk.bodyText,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries) { (kind, n) ->
                Surface(
                    shape = RoundedCornerShape(Radii.chip),
                    color = sk.surface1,
                ) {
                    Text(
                        "${kindLabel(kind)} $n",
                        style = MaterialTheme.typography.labelSmall,
                        color = sk.subText,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PriorityCard(item: PriorityItem, sk: SkillColors, onClick: () -> Unit) {
    val stripe = severityColor(item.severity, sk)
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(Radii.card),
        color = sk.cardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, sk.cardBorder),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(stripe),
            )
            Row(
                Modifier.weight(1f).padding(Space.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(stripe.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                item.severity.replaceFirstChar { it.uppercase() },
                                color = stripe,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(kindLabel(item.kind), color = sk.subText, fontSize = 10.sp)
                        if (item.coverable) {
                            Text("· coverable", color = sk.good, fontSize = 10.sp)
                        }
                    }
                    Text(
                        item.title,
                        color = sk.bodyText,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.detail.isNotBlank()) {
                        Text(
                            item.detail,
                            color = sk.subText,
                            fontSize = 12.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (item.due.isNotBlank()) {
                        Text(
                            "Due ${item.due}",
                            color = sk.sky,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            textDecoration = TextDecoration.Underline,
                        )
                    }
                }
                Icon(
                    painterResource(R.drawable.ic_chevron),
                    contentDescription = null,
                    tint = sk.subText,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
