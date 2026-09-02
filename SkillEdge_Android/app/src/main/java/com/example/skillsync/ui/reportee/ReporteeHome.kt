package com.example.skillsync.ui.reportee

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.skillsync.ReporteeTab
import com.example.skillsync.theme.AuroraBackground
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.skill
import com.example.skillsync.theme.SkillCard
import com.example.skillsync.ui.components.str
import com.example.skillsync.ui.trainer.Trainer360Screen

/**
 * The whole app a reportee sees: their own profile, the unallocated demand that
 * matches their skills, and the updates feed (skill-request outcomes). No team
 * roster, no other trainers, no manager consoles.
 */
@Composable
fun ReporteeHome(
    email: String,
    tab: String,
    onTabChange: (String) -> Unit,
    onLogout: () -> Unit,
    viewModel: ReporteeViewModel = viewModel(),
) {
    LaunchedEffect(email) { viewModel.load() }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground()
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.skill.cardBg) {
                    ReporteeNavItem(tab, ReporteeTab.ME, "Me", onTabChange)
                    ReporteeNavItem(tab, ReporteeTab.DEMAND, "Demand", onTabChange)
                    ReporteeNavItem(tab, ReporteeTab.UPDATES, "Updates", onTabChange)
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    ReporteeTab.DEMAND -> DemandTab(viewModel) { onTabChange(ReporteeTab.ME) }
                    ReporteeTab.UPDATES -> UpdatesTab(viewModel, onLogout)
                    else -> Trainer360Screen(
                        trainerEmail = email,
                        trainerName = email.substringBefore("@"),
                        managerEmail = email,
                        selfView = true,
                        onBack = onLogout,
                    )
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ReporteeNavItem(
    current: String,
    key: String,
    label: String,
    onTabChange: (String) -> Unit,
) {
    NavigationBarItem(
        selected = current == key,
        onClick = { onTabChange(key) },
        icon = {
            Text(
                label.take(1),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        label = { Text(label) },
    )
}

@Composable
private fun DemandTab(viewModel: ReporteeViewModel, onGoToProfile: () -> Unit) {
    val rows by viewModel.demand.collectAsState()
    val loading by viewModel.demandLoading.collectAsState()
    val error by viewModel.demandError.collectAsState()

    when {
        loading -> Center { CircularProgressIndicator(color = MaterialTheme.skill.brand) }
        error != null -> Center { Text(error!!, color = MaterialTheme.skill.warn) }
        rows.isEmpty() -> Center {
            Text(
                "No unallocated batches match your current skills.",
                color = MaterialTheme.skill.subText,
            )
        }
        else -> LazyColumn(
            Modifier.fillMaxSize().padding(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            item {
                Text(
                    "OPEN BATCHES YOU CAN TEACH",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.skill.labelText,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(rows) { row ->
                SkillCard(
                    Modifier.fillMaxWidth(),
                ) {
                    Text(
                        row.str("course_name"),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.skill.bodyText,
                    )
                    Text(
                        listOf(
                            row.str("start_date"),
                            row.str("location"),
                            "match ${row.str("skill_match_pct")}%",
                        ).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.skill.subText,
                    )
                    androidx.compose.material3.TextButton(
                        onClick = onGoToProfile,
                    ) { Text("Mark my skill (in Me → Development plan)") }
                }
            }
        }
    }
}

@Composable
private fun UpdatesTab(viewModel: ReporteeViewModel, onLogout: () -> Unit) {
    val updates by viewModel.updates.collectAsState()
    LazyColumn(
        Modifier.fillMaxSize().padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        if (updates.isEmpty()) {
            item { Text("Nothing new.", color = MaterialTheme.skill.subText) }
        }
        items(updates) { n ->
            SkillCard(Modifier.fillMaxWidth()) {
                Text(
                    n.str("title"),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.skill.bodyText,
                )
                Text(
                    n.str("message"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.skill.subText,
                )
            }
        }
        item {
            androidx.compose.material3.TextButton(onClick = onLogout) {
                Text("Sign out", color = MaterialTheme.skill.warn)
            }
        }
    }
}

@Composable
private fun Center(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(Space.xl), contentAlignment = Alignment.Center) { content() }
}
