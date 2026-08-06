package com.example.skillsync.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
  onItemClick: (NavKey) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: MainScreenViewModel = viewModel()
) {
  val state by viewModel.uiState.collectAsState()

  Scaffold(
      topBar = {
          TopAppBar(
              title = { Text("SkillSync Dashboard") },
              colors = TopAppBarDefaults.topAppBarColors(
                  containerColor = MaterialTheme.colorScheme.primary,
                  titleContentColor = MaterialTheme.colorScheme.onPrimary
              )
          )
      }
  ) { paddingValues ->
      Box(modifier = modifier.fillMaxSize().padding(paddingValues)) {
          when (state) {
              is DashboardState.Loading -> {
                  CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
              }
              is DashboardState.Error -> {
                  Text(
                      text = (state as DashboardState.Error).message,
                      color = MaterialTheme.colorScheme.error,
                      modifier = Modifier.align(Alignment.Center)
                  )
              }
              is DashboardState.Success -> {
                  val data = (state as DashboardState.Success).intelligenceData
                  DashboardContent(data = data)
              }
          }
      }
  }
}

@Composable
fun DashboardContent(data: Map<String, Any>) {
    val manager = data["manager"] as? Map<*, *>
    val kpis = data["kpis"] as? Map<*, *>
    val trainers = data["trainers"] as? List<Map<*, *>>
    val actions = data["actions"] as? List<Map<*, *>>

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Header Section ---
        item {
            val name = manager?.get("name") as? String ?: "Manager"
            val role = manager?.get("role") as? String ?: "Dashboard"
            Text(
                text = "Welcome, $name",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = role,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        }

        // --- KPIs Section ---
        if (kpis != null) {
            item {
                Text("Key Performance Indicators", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KpiCard(
                        title = "Active Trainers",
                        value = "${(kpis["active_trainers"] as? Double)?.toInt() ?: kpis["active_trainers"] ?: 0}",
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        title = "Avg Utilization",
                        value = "${(kpis["avg_utilization"] as? Double)?.toInt() ?: kpis["avg_utilization"] ?: 0}%",
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KpiCard(
                        title = "Pending Actions",
                        value = "${(kpis["pending_actions"] as? Double)?.toInt() ?: kpis["pending_actions"] ?: 0}",
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        title = "Completion Rate",
                        value = "${(kpis["completion_rate"] as? Double)?.toInt() ?: kpis["completion_rate"] ?: 0}%",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // --- Pending Actions Section ---
        if (!actions.isNullOrEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Pending Actions", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
            }
            items(actions) { action ->
                val desc = action["description"] as? String ?: "Unknown Action"
                val priority = action["priority"] as? String ?: "Normal"
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (priority.equals("high", ignoreCase = true)) 
                            MaterialTheme.colorScheme.errorContainer 
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (priority.equals("high", ignoreCase = true)) 
                                MaterialTheme.colorScheme.onErrorContainer 
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Priority: ${priority.uppercase()}",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        // --- Trainers Section ---
        if (!trainers.isNullOrEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Trainer Roster", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
            }
            items(trainers) { trainer ->
                val name = trainer["name"] as? String ?: "Unknown"
                val status = trainer["status"] as? String ?: "Unknown"
                val utilization = (trainer["utilization"] as? Double)?.toInt() ?: trainer["utilization"] as? Int ?: 0
                val skills = (trainer["skills"] as? List<*>)?.joinToString(", ") ?: "No skills listed"
                
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = status, 
                                style = MaterialTheme.typography.labelMedium,
                                color = if (status.equals("Active", ignoreCase = true)) 
                                    MaterialTheme.colorScheme.primary 
                                else MaterialTheme.colorScheme.error
                            )
                        }
                        Text(
                            text = skills,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Utilization: $utilization%", style = MaterialTheme.typography.labelSmall)
                            Spacer(modifier = Modifier.width(8.dp))
                            LinearProgressIndicator(
                                progress = { utilization / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KpiCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text(text = title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
