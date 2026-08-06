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
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "${data["name"] ?: "Dashboard Overview"}",
                style = MaterialTheme.typography.headlineMedium
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        val metrics = data["metrics"] as? Map<String, Any>
        if (metrics != null) {
            item {
                Text("Key Metrics", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
            }
            items(metrics.entries.toList()) { metric ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(metric.key, style = MaterialTheme.typography.bodyLarge)
                        Text(metric.value.toString(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        val alerts = data["alerts"] as? List<String>
        if (!alerts.isNullOrEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Action Alerts", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
            }
            items(alerts) { alert ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = alert,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}
