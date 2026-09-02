package com.example.skillsync.ui.report

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.skillsync.R
import com.example.skillsync.theme.AuroraBackground
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.skill
import com.example.skillsync.theme.SkillCard
import com.example.skillsync.ui.components.str

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

    Box(Modifier.fillMaxSize()) {
        AuroraBackground()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Skill Requests", fontWeight = FontWeight.Bold, color = sk.bodyText,
                                style = MaterialTheme.typography.titleLarge)
                            Text("Reportee skill levels above 4 await your approval",
                                color = sk.sky, style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_back), "Back", tint = sk.ice)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when {
                    loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator(color = sk.brand)
                    }
                    error != null -> Box(Modifier.fillMaxSize().padding(Space.xl), Alignment.Center) {
                        Text(error!!, color = sk.warn)
                    }
                    requests.isEmpty() -> Box(Modifier.fillMaxSize().padding(Space.xl), Alignment.Center) {
                        Text("No pending skill requests.", color = sk.subText)
                    }
                    else -> LazyColumn(
                        Modifier.fillMaxSize().padding(Space.lg),
                        verticalArrangement = Arrangement.spacedBy(Space.md),
                    ) {
                        items(requests) { req ->
                            SkillCard(Modifier.fillMaxWidth()) {
                                Text(
                                    req.str("reportee_email").substringBefore("@"),
                                    style = MaterialTheme.typography.titleSmall, color = sk.bodyText,
                                )
                                Text(
                                    "Wants level ${req.str("requested_level")} · " +
                                        (req.str("course_name").ifBlank { "course ${req.str("course_id")}" }),
                                    style = MaterialTheme.typography.bodySmall, color = sk.subText,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                                    Button(
                                        onClick = {
                                            vm.resolve(req.str("id"), approve = true) { _, m -> toastR(context, m) }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = sk.brand),
                                    ) { Text("Approve") }
                                    OutlinedButton(onClick = {
                                        vm.resolve(req.str("id"), approve = false) { _, m -> toastR(context, m) }
                                    }) { Text("Deny") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun toastR(context: android.content.Context, message: String) =
    android.widget.Toast.makeText(context.applicationContext, message, android.widget.Toast.LENGTH_SHORT).show()
