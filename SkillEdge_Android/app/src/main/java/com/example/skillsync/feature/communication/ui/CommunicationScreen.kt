package com.example.skillsync.feature.communication.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.skillsync.R
import com.example.skillsync.feature.communication.engine.MAX_LENGTH
import com.example.skillsync.feature.communication.engine.PURPOSES
import com.example.skillsync.feature.communication.engine.RECIPIENT_TYPES
import com.example.skillsync.theme.AuroraBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunicationScreen(
    managerEmail: String,
    relatedEntityId: String = "",
    relatedEntityType: String = "",
    onBack: () -> Unit,
    viewModel: CommunicationViewModel = viewModel(),
) {
    val ui by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(relatedEntityId) {
        if (relatedEntityId.isNotBlank()) {
            viewModel.setRelated(relatedEntityType, relatedEntityId)
        }
        viewModel.loadHistory(managerEmail)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AuroraBackground()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("COMMUNICATION INTELLIGENCE", color = Color.White, fontWeight = FontWeight.Black) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painterResource(R.drawable.ic_back),
                                contentDescription = "Back",
                                tint = Color.White,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    WarningCard(ui, relatedEntityId)
                }
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("RECIPIENT", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = ui.recipientName,
                                onValueChange = viewModel::setRecipientName,
                                label = { Text("Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            DropdownField(
                                label = "Recipient type",
                                value = ui.recipientType,
                                options = RECIPIENT_TYPES,
                                onSelect = viewModel::setRecipientType,
                            )
                            OutlinedTextField(
                                value = ui.recipientRelationship,
                                onValueChange = viewModel::setRecipientRelationship,
                                label = { Text("Relationship hint (manager, external, peer...)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("PURPOSE", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            DropdownField(
                                label = "Purpose",
                                value = ui.purpose,
                                options = PURPOSES,
                                onSelect = viewModel::setPurpose,
                            )
                        }
                    }
                }
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("INPUTS", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = ui.userMessage,
                                onValueChange = viewModel::setUserMessage,
                                label = { Text("User instruction (what to change: firmer, short, mention x...)") },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = ui.myMessage,
                                onValueChange = viewModel::setMyMessage,
                                label = { Text("My message (your words / semantic intent, Hinglish ok)") },
                                minLines = 3,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                item {
                    Button(
                        onClick = { viewModel.generate(managerEmail) },
                        enabled = !ui.loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (ui.loading) {
                            CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("GENERATE", fontWeight = FontWeight.Bold)
                    }
                }
                ui.result?.let { result ->
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("DRAFT", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                if (!result.requiresCommunication || result.text == "NO_MEANINGFUL_MESSAGE") {
                                    Text(
                                        "NO MEANINGFUL MESSAGE REQUIRED",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF7CE38B),
                                    )
                                    Text(
                                        result.noMessageReason ?: "Operations are steady. Suppressing unnecessary broadcast noise.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                    )
                                    if (result.rejectedFacts.isNotEmpty()) {
                                        Text("Suppressed unneeded metrics: ${result.rejectedFacts.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                                    }
                                } else {
                                    Text(
                                        result.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                    )
                                    Text("Purpose: ${result.purpose}  ·  Tone: ${result.tone}  ·  Engine: ${result.generationMode}", style = MaterialTheme.typography.labelSmall)
                                    if (result.selectedFacts.isNotEmpty()) {
                                        Text("Facts used: ${result.selectedFacts.joinToString(", ")}", style = MaterialTheme.typography.labelSmall)
                                    }
                                    if (result.rejectedFacts.isNotEmpty()) {
                                        Text("Suppressed metrics: ${result.rejectedFacts.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                                    }
                                }
                                if (result.validation.passed) {
                                    Text("✓ Passes house-style validation", color = Color(0xFF7CE38B))
                                } else {
                                    result.validation.issues.forEach { issue ->
                                        Text("! $issue", color = Color(0xFFFFB4A9))
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = {
                                        clipboard.setText(AnnotatedString(result.text))
                                        viewModel.save(managerEmail, "COPIED")
                                    }) { Text("COPY + SAVE") }
                                    OutlinedButton(onClick = { viewModel.save(managerEmail, "DRAFT") }) { Text("SAVE DRAFT") }
                                    OutlinedButton(onClick = { viewModel.clearResult() }) { Text("CLEAR") }
                                }
                                Text("Length: ${result.text.length} / $MAX_LENGTH", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                ui.error?.let { errorMessage ->
                    item { Text(errorMessage, color = Color(0xFFFFB4A9), style = MaterialTheme.typography.bodySmall) }
                }
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text("HISTORY (${ui.history.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                if (ui.historyLoading) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    CircularProgressIndicator(modifier = Modifier.width(14.dp).height(14.dp), strokeWidth = 2.dp)
                                }
                            }
                            if (ui.history.isEmpty() && !ui.historyLoading) {
                                Text("No saved messages yet.", color = Color.White)
                            }
                            ui.history.forEach { item ->
                                HistoryRow(item)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WarningCard(ui: CommunicationUiState, relatedEntityId: String) {
    val linked = ui.relatedEntityId.isNotBlank()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (linked) "Linked to ${ui.relatedEntityType} #${ui.relatedEntityId}. Verified context is used server-side only; never guessed."
                else "Drafts a professional Teams/Viber message. Only verifiable facts are used.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun HistoryRow(item: CommunicationHistoryItem) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        Text(
            "${item.recipient.ifBlank { item.recipientType }}  ·  ${item.purpose}  ·  [${item.status}]",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Text(item.message, style = MaterialTheme.typography.bodySmall, color = Color.White, maxLines = 4)
        Text(item.createdAt, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun DropdownField(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Column {
            OutlinedTextField(
                value = value,
                onValueChange = {},
                readOnly = true,
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().clickable { expanded = true },
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}