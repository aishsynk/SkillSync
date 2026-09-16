package com.example.skillsync.feature.viber.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.skillsync.R
import com.example.skillsync.core.storage.ViberConfig
import com.example.skillsync.core.storage.ViberOutboxItem
import com.example.skillsync.theme.AuroraBackground
import com.example.skillsync.theme.LocalSkillColors
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.SkillColors
import com.example.skillsync.theme.SkillSyncEmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViberAutomationScreen(
    managerEmail: String,
    onBack: () -> Unit,
    viewModel: ViberAutomationViewModel = viewModel(),
) {
    val context = LocalContext.current
    val sk = LocalSkillColors.current
    val uiState by viewModel.uiState.collectAsState()
    var showSettingsDialog by remember { mutableStateOf(false) }
    var previewItem by remember { mutableStateOf<ViberOutboxItem?>(null) }

    LaunchedEffect(managerEmail) {
        viewModel.load(managerEmail)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AuroraBackground()

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "VIBER DISPATCH CENTRE",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    fontFamily = FontFamily.Monospace,
                                ),
                                color = Color.White,
                            )
                            Text(
                                "Prepared drafts, rules and hand-off history",
                                style = MaterialTheme.typography.bodySmall,
                                color = sk.sky,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painterResource(R.drawable.ic_back),
                                contentDescription = "Back",
                                tint = Color.White,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(
                                painterResource(R.drawable.ic_inbox),
                                contentDescription = "Settings",
                                tint = sk.sky,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Banner message
                uiState.bannerMessage?.let { msg ->
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = sk.brand.copy(alpha = 0.25f)),
                            border = BorderStroke(1.dp, sk.sky),
                            shape = RoundedCornerShape(Radii.card),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(msg, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                                IconButton(onClick = { viewModel.clearBanner() }, modifier = Modifier.size(24.dp)) {
                                    Icon(painterResource(R.drawable.ic_check), null, tint = Color.White)
                                }
                            }
                        }
                    }
                }

                // AUTOMATION STATUS
                item {
                    DispatchSectionLabel("AUTOMATION STATUS", sk)
                }
                item {
                    ViberOverviewCockpit(
                        waitingCount = uiState.items.count { it.status in ViberOutboxItem.AWAITING_HANDOFF },
                        failedCount = uiState.items.count { it.status == ViberOutboxItem.STATUS_FAILED },
                        sharedCount = uiState.items.count { it.status == ViberOutboxItem.STATUS_SHARED_EXTERNALLY },
                        sentCount = uiState.items.count { it.status == ViberOutboxItem.STATUS_SENT },
                        hasConfirmedTransport = uiState.config.dispatchMode == ViberConfig.MODE_BOT_API &&
                            uiState.config.viberBotToken.isNotBlank(),
                        isSendingAll = uiState.isSendingAll,
                        onTransmitAll = { viewModel.transmitAllViaBot(context) },
                        onShareNext = { viewModel.shareNextDraft(context) },
                        onClearHandedOff = { viewModel.clearHandedOff() },
                        sk = sk,
                    )
                }

                // RULES
                item {
                    DispatchSectionLabel("RULES", sk)
                }
                item {
                    AutomationRulesCard(
                        config = uiState.config,
                        onConfigChanged = { viewModel.updateConfig(it) },
                        onOpenAccessibilitySettings = {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                })
                            }
                        },
                        isAccessibilityEnabled = uiState.isAccessibilityEnabled,
                        sk = sk,
                    )
                }

                // OUTBOX — only what still needs the manager's hand.
                val waiting = uiState.items.filter {
                    it.status in ViberOutboxItem.AWAITING_HANDOFF || it.status == ViberOutboxItem.STATUS_FAILED
                }
                val history = uiState.items.filterNot { it in waiting }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DispatchSectionLabel("OUTBOX (${waiting.size})", sk)
                        if (uiState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = sk.sky, strokeWidth = 2.dp)
                        }
                    }
                }

                if (waiting.isEmpty()) {
                    item {
                        SkillSyncEmptyState(
                            title = "No drafts waiting",
                            description = "Unallocated demand, weekly standpoints and compliance nudges are " +
                                "prepared here by the rules above. Nothing is waiting for you right now.",
                            iconRes = R.drawable.ic_inbox,
                        )
                    }
                } else {
                    items(waiting, key = { it.id }) { item ->
                        ViberOutboxItemCard(
                            item = item,
                            onPreview = { previewItem = item },
                            onShare = { viewModel.shareItem(context, item) },
                            onCopy = {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cb?.setPrimaryClip(ClipData.newPlainText("Viber Message", item.messageText))
                            },
                            sk = sk,
                        )
                    }
                }

                // HISTORY — handed off already, with the honest distinction kept.
                if (history.isNotEmpty()) {
                    item { DispatchSectionLabel("HISTORY (${history.size})", sk) }
                    items(history, key = { "h_" + it.id }) { item ->
                        ViberOutboxItemCard(
                            item = item,
                            onPreview = { previewItem = item },
                            onShare = { viewModel.shareItem(context, item) },
                            onCopy = {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cb?.setPrimaryClip(ClipData.newPlainText("Viber Message", item.messageText))
                            },
                            sk = sk,
                        )
                    }
                }
            }
        }

        // Preview Message Dialog
        previewItem?.let { item ->
            AlertDialog(
                onDismissRequest = { previewItem = null },
                containerColor = sk.surface2,
                title = {
                    Text("Draft preview", color = Color.White, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column {
                        Text("To: ${item.recipientName} (${item.recipientEmail})", style = MaterialTheme.typography.labelSmall, color = sk.sky)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            item.messageText,
                            style = MaterialTheme.typography.bodySmall,
                            color = sk.bodyText,
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.shareItem(context, item)
                            previewItem = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = sk.brand),
                    ) {
                        Text("Share to Viber")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { previewItem = null }) {
                        Text("Close", color = sk.subText)
                    }
                },
            )
        }

        // Configuration Sheet Dialog
        if (showSettingsDialog) {
            ViberConfigDialog(
                config = uiState.config,
                onSave = {
                    viewModel.updateConfig(it)
                    showSettingsDialog = false
                },
                onDismiss = { showSettingsDialog = false },
                sk = sk,
            )
        }
    }
}

/** Uppercase section label; the dispatch centre's only structural furniture. */
@Composable
private fun DispatchSectionLabel(text: String, sk: SkillColors) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            fontFamily = FontFamily.Monospace,
        ),
        color = sk.labelText,
    )
}

/**
 * What the automation can and did do. The primary action is derived from the
 * configured transport rather than assumed: with a bot token the app can
 * transmit and Viber confirms it; without one the only truthful action is to
 * hand one draft at a time to the Viber share sheet.
 */
@Composable
private fun ViberOverviewCockpit(
    waitingCount: Int,
    failedCount: Int,
    sharedCount: Int,
    sentCount: Int,
    hasConfirmedTransport: Boolean,
    isSendingAll: Boolean,
    onTransmitAll: () -> Unit,
    onShareNext: () -> Unit,
    onClearHandedOff: () -> Unit,
    sk: SkillColors,
) {
    val pendingCount = waitingCount
    Card(
        colors = CardDefaults.cardColors(containerColor = sk.surface2),
        border = BorderStroke(1.dp, sk.glassBorder),
        shape = RoundedCornerShape(Radii.card),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("TRANSPORT", style = MaterialTheme.typography.labelSmall, color = sk.labelText)
                    Text(
                        if (hasConfirmedTransport) "Viber bot API" else "Share sheet only",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (hasConfirmedTransport) "Delivery is confirmed by Viber."
                        else "The app prepares drafts; you send them inside Viber.",
                        style = MaterialTheme.typography.labelSmall,
                        color = sk.subText,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (pendingCount > 0) Color(0xFFF59E0B) else Color(0xFF10B981))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (pendingCount > 0) "$pendingCount waiting" else "Nothing waiting",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (pendingCount > 0) Color(0xFFF59E0B) else Color(0xFF10B981),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Three counted states, each labelled with what actually
                // happened. "Sent" is only ever the bot-confirmed count.
                DispatchCount("READY", waitingCount - failedCount, Color(0xFFF59E0B), sk, Modifier.weight(1f))
                DispatchCount("SHARED", sharedCount, sk.sky, sk, Modifier.weight(1f))
                // A "sent" column would read as a permanent zero without a
                // transport that can confirm one, so it is only shown when the
                // bot API can actually produce that state.
                if (hasConfirmedTransport) {
                    DispatchCount("SENT", sentCount, Color(0xFF10B981), sk, Modifier.weight(1f))
                } else {
                    DispatchCount("FAILED", failedCount, Color(0xFFF87171), sk, Modifier.weight(1f))
                }
            }
            if (failedCount > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "$failedCount failed and can be shared again below.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFF87171),
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = if (hasConfirmedTransport) onTransmitAll else onShareNext,
                    enabled = !isSendingAll && pendingCount > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = sk.brand,
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(Radii.chip),
                    modifier = Modifier.weight(1f),
                ) {
                    if (isSendingAll) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Transmitting…")
                    } else {
                        Icon(
                            painterResource(
                                if (hasConfirmedTransport) R.drawable.ic_forward else R.drawable.ic_share
                            ),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (hasConfirmedTransport) "Transmit all" else "Share next",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                OutlinedButton(
                    onClick = onClearHandedOff,
                    shape = RoundedCornerShape(Radii.chip),
                    border = BorderStroke(1.dp, sk.glassBorder),
                ) {
                    Text("Clear history", color = sk.subText)
                }
            }
        }
    }
}

/** One outbox count with the state it counts, in vector-only styling. */
@Composable
private fun DispatchCount(
    label: String,
    value: Int,
    tint: Color,
    sk: SkillColors,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Radii.chip))
            .background(Color(0xFF0F172A))
            .padding(12.dp),
    ) {
        Column {
            Text(
                label, style = MaterialTheme.typography.labelSmall, color = sk.subText,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                "$value",
                style = MaterialTheme.typography.titleLarge,
                color = tint,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun AutomationRulesCard(
    config: ViberConfig,
    onConfigChanged: (ViberConfig) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    isAccessibilityEnabled: Boolean,
    sk: SkillColors,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = sk.surface2),
        border = BorderStroke(1.dp, sk.glassBorder),
        shape = RoundedCornerShape(Radii.card),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("WHAT GETS PREPARED", style = MaterialTheme.typography.labelSmall, color = sk.labelText)
            Spacer(Modifier.height(2.dp))
            Text(
                "These rules decide what the app drafts for you. They never send anything on their own.",
                style = MaterialTheme.typography.labelSmall,
                color = sk.subText,
            )
            Spacer(Modifier.height(12.dp))

            // Switch 1: Demand
            RuleSwitchRow(
                title = "Auto-Draft Unallocated Demand",
                subtitle = "Matches new client demand to certified reportees and prepares a candidate note for you to share",
                checked = config.autoSendDemand,
                onCheckedChange = { onConfigChanged(config.copy(autoSendDemand = it)) },
                sk = sk,
            )

            Spacer(Modifier.height(8.dp))

            // Switch 2: Weekly
            RuleSwitchRow(
                title = "Prepare Weekly Standpoints",
                subtitle = "Drafts a Monday delivery standpoint note for each active reportee",
                checked = config.autoSendWeekly,
                onCheckedChange = { onConfigChanged(config.copy(autoSendWeekly = it)) },
                sk = sk,
            )

            Spacer(Modifier.height(8.dp))

            // Switch 3: Nudges
            RuleSwitchRow(
                title = "Prepare Compliance Nudges",
                subtitle = "Drafts a nudge for the delivering instructor when a session recording is missing",
                checked = config.autoSendNudges,
                onCheckedChange = { onConfigChanged(config.copy(autoSendNudges = it)) },
                sk = sk,
            )

            Spacer(Modifier.height(16.dp))

            // Dispatch Mode Segment
            Text("TRANSPORT", style = MaterialTheme.typography.labelSmall, color = sk.labelText)
            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip(
                    title = "Bot API",
                    selected = config.dispatchMode == ViberConfig.MODE_BOT_API,
                    onClick = { onConfigChanged(config.copy(dispatchMode = ViberConfig.MODE_BOT_API)) },
                    modifier = Modifier.weight(1f),
                    sk = sk,
                )
                // One share option, not two: the withdrawn accessibility mode
                // now behaves exactly like the Intent path, so offering both as
                // separate transports implied a capability that does not exist.
                ModeChip(
                    title = "Share sheet",
                    selected = config.dispatchMode != ViberConfig.MODE_BOT_API,
                    onClick = { onConfigChanged(config.copy(dispatchMode = ViberConfig.MODE_INTENT_NOTIFICATION)) },
                    modifier = Modifier.weight(1f),
                    sk = sk,
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                if (config.dispatchMode == ViberConfig.MODE_BOT_API)
                    "With a bot token configured, messages are transmitted and Viber confirms delivery."
                else
                    "Both share options open Viber with the draft prefilled. You choose the chat and send it there, so the app records them as shared, never as sent.",
                style = MaterialTheme.typography.labelSmall,
                color = sk.subText,
            )

        }
    }
}

@Composable
private fun RuleSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    sk: SkillColors,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = sk.subText)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = sk.brand,
                uncheckedThumbColor = sk.subText,
                uncheckedTrackColor = Color(0xFF1E293B),
            ),
        )
    }
}

@Composable
private fun ModeChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sk: SkillColors,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Radii.chip))
            .background(if (selected) sk.brand.copy(alpha = 0.85f) else Color(0xFF0F172A))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
            color = if (selected) Color.White else sk.subText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ViberOutboxItemCard(
    item: ViberOutboxItem,
    onPreview: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    sk: SkillColors,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = sk.surface2),
        border = BorderStroke(
            1.dp,
            when (item.status) {
                ViberOutboxItem.STATUS_SENT -> Color(0x5510B981)
                ViberOutboxItem.STATUS_FAILED -> Color(0x55EF4444)
                else -> Color(0x55F59E0B)
            }
        ),
        shape = RoundedCornerShape(Radii.card),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when (item.category) {
                                    ViberOutboxItem.CAT_DEMAND -> Color(0xFF3B82F6)
                                    ViberOutboxItem.CAT_WEEKLY -> Color(0xFF8B5CF6)
                                    ViberOutboxItem.CAT_DELIVERY -> Color(0xFFEF4444)
                                    else -> sk.brand
                                }.copy(alpha = 0.2f)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            item.category.replace("_", " "),
                            style = MaterialTheme.typography.labelSmall,
                            color = when (item.category) {
                                ViberOutboxItem.CAT_DEMAND -> Color(0xFF60A5FA)
                                ViberOutboxItem.CAT_WEEKLY -> Color(0xFFA78BFA)
                                ViberOutboxItem.CAT_DELIVERY -> Color(0xFFF87171)
                                else -> sk.sky
                            },
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // Status Badge
                Text(
                    ViberOutboxItem.label(item.status),
                    style = MaterialTheme.typography.labelSmall,
                    color = when (item.status) {
                        ViberOutboxItem.STATUS_SENT -> Color(0xFF10B981)
                        ViberOutboxItem.STATUS_FAILED -> Color(0xFFEF4444)
                        else -> Color(0xFFF59E0B)
                    },
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                item.recipientName,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            if (item.courseName.isNotBlank()) {
                Text(
                    item.courseName,
                    style = MaterialTheme.typography.bodySmall,
                    color = sk.sky,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(6.dp))

            Text(
                item.messageText,
                style = MaterialTheme.typography.bodySmall,
                color = sk.bodyText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCopy) {
                    Text("Copy", color = sk.subText)
                }
                Spacer(Modifier.width(6.dp))
                TextButton(onClick = onPreview) {
                    Text("Preview", color = sk.sky)
                }
                Spacer(Modifier.width(6.dp))
                Button(
                    onClick = onShare,
                    colors = ButtonDefaults.buttonColors(containerColor = sk.brand),
                    shape = RoundedCornerShape(Radii.chip),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_share),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (item.status == ViberOutboxItem.STATUS_SENT ||
                            item.status == ViberOutboxItem.STATUS_SHARED_EXTERNALLY
                        ) "Share again" else "Share"
                    )
                }
            }
        }
    }
}

@Composable
private fun ViberConfigDialog(
    config: ViberConfig,
    onSave: (ViberConfig) -> Unit,
    onDismiss: () -> Unit,
    sk: SkillColors,
) {
    var botToken by remember { mutableStateOf(config.viberBotToken) }
    var webhookUrl by remember { mutableStateOf(config.webhookUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = sk.surface2,
        title = {
            Text("Viber Bot API Settings", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "A Viber Public Account bot token lets the backend transmit messages and record a confirmed delivery. Without one, the app can only prepare drafts for you to share.",
                    style = MaterialTheme.typography.bodySmall,
                    color = sk.subText,
                )

                OutlinedTextField(
                    value = botToken,
                    onValueChange = { botToken = it },
                    label = { Text("Viber Bot Auth Token") },
                    placeholder = { Text("e.g. 50a12...-12345...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radii.chip),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = sk.brand,
                        unfocusedBorderColor = sk.glassBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    ),
                )

                OutlinedTextField(
                    value = webhookUrl,
                    onValueChange = { webhookUrl = it },
                    label = { Text("Corporate Webhook URL (Optional)") },
                    placeholder = { Text("https://api.koenig.com/viber/dispatch") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radii.chip),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = sk.brand,
                        unfocusedBorderColor = sk.glassBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    ),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(config.copy(viberBotToken = botToken, webhookUrl = webhookUrl))
                },
                colors = ButtonDefaults.buttonColors(containerColor = sk.brand),
            ) {
                Text("Save Configuration")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = sk.subText)
            }
        },
    )
}
