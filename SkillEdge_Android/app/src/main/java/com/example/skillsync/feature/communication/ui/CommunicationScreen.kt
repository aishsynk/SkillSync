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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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

/**
 * The manager message composer. Laid out as the communication model itself:
 * ① Verified context + ② Manager instruction (optional) → ③ Generated message.
 * Recipient and purpose sit above as addressing; the three numbered stages are
 * the same [ComposerStep] parts every in-flow composer dialog uses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunicationScreen(
    managerEmail: String,
    relatedEntityId: String = "",
    relatedEntityType: String = "",
    initialRecipientType: String = "",
    initialRecipientName: String = "",
    initialPurpose: String = "",
    onBack: () -> Unit,
    viewModel: CommunicationViewModel = viewModel(),
) {
    val ui by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current
    val shareContext = androidx.compose.ui.platform.LocalContext.current
    val ink = Color(0xFF0B1220)

    LaunchedEffect(relatedEntityId, initialRecipientType, initialRecipientName, initialPurpose) {
        if (relatedEntityId.isNotBlank()) {
            viewModel.setRelated(relatedEntityType, relatedEntityId)
        }
        if (initialRecipientType.isNotBlank() || initialRecipientName.isNotBlank() || initialPurpose.isNotBlank()) {
            viewModel.setInitial(initialRecipientType, initialRecipientName, initialPurpose)
        }
        viewModel.loadHistory(managerEmail)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AuroraBackground()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("MESSAGE COMPOSER", color = Color.White, fontWeight = FontWeight.Black) },
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
                item { ComposerModelStrip() }
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("ADDRESSED TO", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.7f))
                            OutlinedTextField(
                                value = ui.recipientName,
                                onValueChange = viewModel::setRecipientName,
                                label = { Text("Recipient name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.weight(1f)) {
                                    DropdownField("Recipient type", ui.recipientType, RECIPIENT_TYPES, viewModel::setRecipientType)
                                }
                                Box(Modifier.weight(1f)) {
                                    DropdownField("Purpose", ui.purpose, PURPOSES, viewModel::setPurpose)
                                }
                            }
                            OutlinedTextField(
                                value = ui.recipientRelationship,
                                onValueChange = viewModel::setRecipientRelationship,
                                label = { Text("Relationship (manager, external, peer...)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                item {
                    ComposerStep(
                        number = 1,
                        title = "Verified context",
                        hint = "Read from RMS on the server. Always used — never edited or guessed.",
                        tint = ComposerTints.context,
                    ) {
                        VerifiedContextRows(
                            listOf(
                                "Linked record" to if (ui.relatedEntityId.isNotBlank()) "${ui.relatedEntityType} #${ui.relatedEntityId}" else "",
                                "Purpose" to ui.purpose.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                                "Recipient" to listOf(ui.recipientName, ui.recipientType.lowercase()).filter { it.isNotBlank() }.joinToString(" · "),
                            ),
                        )
                        ui.result?.selectedFacts?.takeIf { it.isNotEmpty() }?.let { facts ->
                            Text(
                                "Facts used: ${facts.joinToString(", ")}",
                                style = MaterialTheme.typography.labelSmall, color = ComposerTints.context,
                            )
                        }
                    }
                }
                item {
                    ComposerStep(
                        number = 2,
                        title = "Manager instruction",
                        hint = "Optional. Steers tone or focus; cannot override verified context.",
                        tint = ComposerTints.instruction,
                    ) {
                        ManagerInstructionField(ui.managerInstruction, viewModel::setManagerInstruction, minLines = 3)
                    }
                }
                item {
                    Button(
                        onClick = { viewModel.generate(managerEmail) },
                        enabled = !ui.loading,
                        colors = ButtonDefaults.buttonColors(containerColor = ComposerTints.generated, contentColor = ink),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        if (ui.loading) {
                            CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp, color = ink)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (ui.result == null) "① + ②  →  GENERATE MESSAGE" else "REGENERATE MESSAGE", fontWeight = FontWeight.Black)
                    }
                }
                item {
                    ComposerStep(
                        number = 3,
                        title = "Generated message",
                        hint = "Built from ① and ②. Review, then copy or share — nothing is sent automatically.",
                        tint = ComposerTints.generated,
                    ) {
                        val result = ui.result
                        when {
                            result == null -> Text(
                                "Nothing generated yet.",
                                style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f),
                            )
                            !result.requiresCommunication || result.text == "NO_MEANINGFUL_MESSAGE" -> {
                                Text("NO MESSAGE NEEDED", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF7CE38B))
                                Text(
                                    result.noMessageReason ?: "Operations are steady. Suppressing unnecessary broadcast noise.",
                                    style = MaterialTheme.typography.bodyMedium, color = Color.White,
                                )
                                if (result.rejectedFacts.isNotEmpty()) {
                                    Text("Suppressed: ${result.rejectedFacts.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                                }
                            }
                            else -> {
                                GeneratedMessageBox(result.text)
                                Text(
                                    "Tone: ${result.tone}  ·  ${result.text.length} / $MAX_LENGTH chars  ·  ${result.generationMode}",
                                    style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f),
                                )
                                if (result.rejectedFacts.isNotEmpty()) {
                                    Text("Suppressed: ${result.rejectedFacts.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                                }
                            }
                        }
                        if (result != null) {
                            if (result.validation.passed) {
                                Text("✓ Passes house-style validation", color = Color(0xFF7CE38B), style = MaterialTheme.typography.labelMedium)
                            } else {
                                result.validation.issues.forEach { issue ->
                                    Text("! $issue", color = Color(0xFFFFB4A9), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(result.text))
                                        viewModel.save(managerEmail, "COPIED")
                                    },
                                    modifier = Modifier.weight(1f),
                                ) { Text("COPY") }
                                Button(
                                    onClick = {
                                        // The native share sheet only hands the text to another
                                        // app (Teams, Viber, whatever the manager picks) — it is
                                        // not delivery confirmation, so this is SHARED_EXTERNALLY,
                                        // never SENT.
                                        val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(android.content.Intent.EXTRA_TEXT, result.text)
                                        }
                                        shareContext.startActivity(android.content.Intent.createChooser(sendIntent, null))
                                        viewModel.save(managerEmail, "SHARED_EXTERNALLY")
                                    },
                                    modifier = Modifier.weight(1f),
                                ) { Text("SHARE") }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { viewModel.save(managerEmail, "DRAFT") }, modifier = Modifier.weight(1f)) { Text("SAVE DRAFT") }
                                OutlinedButton(onClick = { viewModel.clearResult() }, modifier = Modifier.weight(1f)) { Text("CLEAR") }
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
