package com.example.skillsync.feature.report.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.skillsync.R
import com.example.skillsync.core.ui.Avatar
import com.example.skillsync.core.ui.ShimmerBox
import com.example.skillsync.core.ui.int
import com.example.skillsync.core.ui.shortDate
import com.example.skillsync.core.ui.str
import com.example.skillsync.theme.IconSlot
import com.example.skillsync.theme.Layout
import com.example.skillsync.theme.SkillColors
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.ToneChip
import com.example.skillsync.theme.AuroraBackground
import com.example.skillsync.theme.SkillSyncEmptyState
import com.example.skillsync.theme.SkillSyncErrorState
import com.example.skillsync.theme.accentGlass
import com.example.skillsync.theme.glassSurface
import com.example.skillsync.theme.pressable
import com.example.skillsync.theme.skill

/**
 * Approval workflow, not a report. Every request is one decision with the
 * evidence the backend actually holds beside it: who asked, for which course,
 * for what level, and when they asked. The store keeps no "current level" or
 * photo for a request, so neither is fabricated — the monogram stands in for a
 * photo and the level line states only the requested level.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillRequestsScreen(
    managerEmail: String,
    onBack: () -> Unit,
    vm: SkillRequestsViewModel = viewModel(),
) {
    val sk = MaterialTheme.skill
    val context = LocalContext.current
    LaunchedEffect(managerEmail) { vm.load() }

    val requests by vm.requests.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()

    val pending = requests.filter { it.str("status").ifBlank { "pending" } == "pending" }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "Skill Requests",
                                fontWeight = FontWeight.Bold, color = sk.bodyText,
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                "Reportee level elevations awaiting your decision",
                                color = sk.sky, style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_back), "Back", tint = sk.ice)
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.load() }) {
                            Icon(painterResource(R.drawable.ic_refresh), "Reload requests", tint = sk.ice)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
        ) { pad ->
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Layout.gutter, end = Layout.gutter,
                    top = Space.sm, bottom = Space.xl,
                ),
                verticalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                // The queue count is a fact in every state, including zero, so
                // the manager never has to infer "nothing pending" from a blank.
                item("queue") { PendingQueueBar(if (loading) null else pending.size, sk) }

                when {
                    loading -> item("skeleton") { SkillRequestSkeleton(sk) }

                    error != null -> item("error") {
                        SkillSyncErrorState(message = error!!, onRetry = { vm.load() })
                    }

                    pending.isEmpty() -> item("empty") {
                        SkillSyncEmptyState(
                            title = "No pending skill requests",
                            description = "Reportees ask for a course level above 4 from their own app. " +
                                "Anything they submit lands here for your approval or decline.",
                            iconRes = R.drawable.ic_award,
                        )
                    }

                    else -> items(pending, key = { it.str("id") }) { req ->
                        SkillRequestCard(
                            req = req,
                            sk = sk,
                            onApprove = {
                                vm.resolve(req.str("id"), approve = true) { _, m -> toastR(context, m) }
                            },
                            onDecline = {
                                vm.resolve(req.str("id"), approve = false) { _, m -> toastR(context, m) }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Pending count, with the label carrying the timeframe-free basis. */
@Composable
private fun PendingQueueBar(count: Int?, sk: SkillColors) {
    val tint = if ((count ?: 0) > 0) sk.warn else sk.good
    Row(
        Modifier.fillMaxWidth().glassSurface().padding(Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconSlot(tint, size = 30.dp) {
            Icon(painterResource(R.drawable.ic_inbox), null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(
                "PENDING",
                style = MaterialTheme.typography.labelSmall,
                color = sk.labelText, fontWeight = FontWeight.Bold, letterSpacing = 0.08.em,
            )
            Text(
                "Awaiting your approval",
                style = MaterialTheme.typography.bodySmall, color = sk.subText,
            )
        }
        Text(
            count?.toString() ?: "—",
            style = MaterialTheme.typography.headlineSmall,
            color = tint, fontWeight = FontWeight.Bold,
        )
    }
}

/** One decision: who, what, when, then Approve / Decline. */
@Composable
private fun SkillRequestCard(
    req: Map<*, *>,
    sk: SkillColors,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
) {
    val email = req.str("reportee_email")
    val name = email.substringBefore("@").replace('.', ' ')
        .split(" ").filter { it.isNotBlank() }
        .joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
        .ifBlank { email }
    val course = req.str("course_name").ifBlank { "Course ${req.str("course_id")}" }
    val level = req.int("requested_level")
    val from = req.str("from_date").shortDate()
    // created_at is a full ISO timestamp ("2026-09-14T09:12:00+00:00"), not a
    // bare date — shortDate() only understands "YYYY-MM-DD" and mangles the
    // rest, so the date portion is taken first.
    val asked = req.str("created_at").substringBefore("T").shortDate()

    Column(
        Modifier.fillMaxWidth().accentGlass(sk.sky).padding(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // No photo is stored against a request, so the monogram is the
            // honest representation rather than a placeholder silhouette.
            Avatar(name = name, photoUrl = null, size = 34.dp)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall,
                    color = sk.bodyText, fontWeight = FontWeight.SemiBold, maxLines = 1,
                )
                Text(
                    course,
                    style = MaterialTheme.typography.bodySmall, color = sk.subText, maxLines = 2,
                )
            }
            if (level > 0) ToneChip("LEVEL $level", sk.sky)
        }

        Text(
            buildString {
                append("Requested ")
                append(if (asked.isNotBlank()) asked else "recently")
                if (from.isNotBlank()) append(" · effective from $from")
            },
            style = MaterialTheme.typography.labelSmall, color = sk.labelText,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DecisionAction("Approve", R.drawable.ic_check, sk.good, Modifier.weight(1f), onApprove)
            DecisionAction("Decline", R.drawable.ic_flag, sk.crit, Modifier.weight(1f), onDecline)
        }
        Text(
            "Approving writes the level to RMS.",
            style = MaterialTheme.typography.labelSmall, color = sk.labelText,
        )
    }
}

/** Compact, equal-weight decision control. */
@Composable
private fun DecisionAction(
    label: String,
    iconRes: Int,
    tint: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.16f))
            .pressable(onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(painterResource(iconRes), null, tint = tint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = tint, fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Loading placeholder shaped like the request card it replaces. */
@Composable
private fun SkillRequestSkeleton(sk: SkillColors) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        repeat(3) {
            Column(
                Modifier.fillMaxWidth().glassSurface().padding(Space.md),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShimmerBox(width = 34.dp, height = 34.dp, shape = RoundedCornerShape(17.dp))
                    Spacer(Modifier.width(Space.md))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        ShimmerBox(height = 12.dp, modifier = Modifier.fillMaxWidth(0.45f))
                        ShimmerBox(height = 10.dp, modifier = Modifier.fillMaxWidth(0.7f))
                    }
                }
                ShimmerBox(height = 40.dp, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp))
            }
        }
    }
}

private fun toastR(context: android.content.Context, message: String) =
    android.widget.Toast.makeText(context.applicationContext, message, android.widget.Toast.LENGTH_SHORT).show()
