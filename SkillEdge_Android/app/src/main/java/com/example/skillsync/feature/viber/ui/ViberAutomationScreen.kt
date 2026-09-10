package com.example.skillsync.ui.report

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
import com.example.skillsync.data.cache.ViberConfig
import com.example.skillsync.data.cache.ViberOutboxItem
import com.example.skillsync.theme.AuroraBackground
import com.example.skillsync.theme.LocalSkillColors
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.SkillColors

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
                                "VIBER AUTOMATION",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    fontFamily = FontFamily.Monospace,
                                ),
                                color = Color.White,
                            )
                            Text(
                                "Background Queue & Auto-Dispatch",
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

                // Overview Cockpit Card
                item {
                    ViberOverviewCockpit(
                        pendingCount = uiState.items.count { it.status == ViberOutboxItem.STATUS_QUEUED || it.status == ViberOutboxItem.STATUS_FAILED },
                        sentCount = uiState.items.count { it.status == ViberOutboxItem.STATUS_SENT },
                        isSendingAll = uiState.isSendingAll,
                        onSendAll = { viewModel.sendAllNow(context) },
                        onClearSent = { viewModel.clearSent() },
                        sk = sk,
                    )
                }

                // Automation Rules Control Card
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

                // Outbox Section Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "DISPATCH OUTBOX (${uiState.items.size})",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = sk.labelText,
                        )
                        if (uiState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = sk.sky, strokeWidth = 2.dp)
                        }
                    }
                }

                if (uiState.items.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = sk.surface2),
                            border = BorderStroke(1.dp, sk.glassBorder),
                            shape = RoundedCornerShape(Radii.card),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_check),
                                    null,
                                    tint = sk.sky,
                                    modifier = Modifier.size(40.dp),
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "Outbox is Empty",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "New unallocated demand & weekly standpoints will appear here automatically.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = sk.subText,
                                )
                            }
                        }
                    }
                } else {
                    items(uiState.items, key = { it.id }) { item ->
                        ViberOutboxItemCard(
                            item = item,
                            onPreview = { previewItem = item },
                            onRetry = { viewModel.retryItem(context, item) },
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
                    Text("Viber Message Preview", color = Color.White, fontWeight = FontWeight.Bold)
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
                            viewModel.retryItem(context, item)
                            previewItem = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = sk.brand),
                    ) {
                        Text("Send to Viber Now")
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

@Composable
private fun ViberOverviewCockpit(
    pendingCount: Int,
    sentCount: Int,
    isSendingAll: Boolean,
    onSendAll: () -> Unit,
    onClearSent: () -> Unit,
    sk: SkillColors,
) {
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
                Column {
                    Text("AUTOMATION ENGINE", style = MaterialTheme.typography.labelSmall, color = sk.labelText)
                    Text("Live Viber Sentinel", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
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
                        if (pendingCount > 0) "$pendingCount Pending" else "All Delivered",
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
                // Pending Box
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Radii.chip))
                        .background(Color(0xFF0F172A))
                        .padding(12.dp),
                ) {
                    Column {
                        Text("QUEUED / PENDING", style = MaterialTheme.typography.labelSmall, color = sk.subText)
                        Text("$pendingCount", style = MaterialTheme.typography.titleLarge, color = Color(0xFFF59E0B), fontWeight = FontWeight.Black)
                    }
                }

                // Sent Box
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Radii.chip))
                        .background(Color(0xFF0F172A))
                        .padding(12.dp),
                ) {
                    Column {
                        Text("DELIVERED / SENT", style = MaterialTheme.typography.labelSmall, color = sk.subText)
                        Text("$sentCount", style = MaterialTheme.typography.titleLarge, color = Color(0xFF10B981), fontWeight = FontWeight.Black)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onSendAll,
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
                        Text("Dispatching...")
                    } else {
                        Text("🚀 Send All Queued Now", fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = onClearSent,
                    shape = RoundedCornerShape(Radii.chip),
                    border = BorderStroke(1.dp, sk.glassBorder),
                ) {
                    Text("Clear Sent", color = sk.subText)
                }
            }
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
            Text("AUTOMATION TRIGGERS", style = MaterialTheme.typography.labelSmall, color = sk.labelText)
            Spacer(Modifier.height(12.dp))

            // Switch 1: Demand
            RuleSwitchRow(
                title = "⚡ Auto-Send Unallocated Demand",
                subtitle = "Matches new client demand to certified reportees & drafts Viber candidate note",
                checked = config.autoSendDemand,
                onCheckedChange = { onConfigChanged(config.copy(autoSendDemand = it)) },
                sk = sk,
            )

            Spacer(Modifier.height(8.dp))

            // Switch 2: Weekly
            RuleSwitchRow(
                title = "📅 Auto-Send Weekly Standpoints",
                subtitle = "Monday 08:00 AM delivery standpoint notes for each active reportee",
                checked = config.autoSendWeekly,
                onCheckedChange = { onConfigChanged(config.copy(autoSendWeekly = it)) },
                sk = sk,
            )

            Spacer(Modifier.height(8.dp))

            // Switch 3: Nudges
            RuleSwitchRow(
                title = "🚨 Auto-Send Delivery Compliance Nudges",
                subtitle = "Flags missing session recordings & nudges delivering instructor",
                checked = config.autoSendNudges,
                onCheckedChange = { onConfigChanged(config.copy(autoSendNudges = it)) },
                sk = sk,
            )

            Spacer(Modifier.height(16.dp))

            // Dispatch Mode Segment
            Text("DISPATCH STRATEGY", style = MaterialTheme.typography.labelSmall, color = sk.labelText)
            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip(
                    title = "Bot REST API",
                    selected = config.dispatchMode == ViberConfig.MODE_BOT_API,
                    onClick = { onConfigChanged(config.copy(dispatchMode = ViberConfig.MODE_BOT_API)) },
                    modifier = Modifier.weight(1f),
                    sk = sk,
                )
                ModeChip(
                    title = "App Auto-Send",
                    selected = config.dispatchMode == ViberConfig.MODE_ACCESSIBILITY,
                    onClick = { onConfigChanged(config.copy(dispatchMode = ViberConfig.MODE_ACCESSIBILITY)) },
                    modifier = Modifier.weight(1f),
                    sk = sk,
                )
                ModeChip(
                    title = "1-Tap Share",
                    selected = config.dispatchMode == ViberConfig.MODE_INTENT_NOTIFICATION,
                    onClick = { onConfigChanged(config.copy(dispatchMode = ViberConfig.MODE_INTENT_NOTIFICATION)) },
                    modifier = Modifier.weight(1f),
                    sk = sk,
                )
            }

            if (config.dispatchMode == ViberConfig.MODE_ACCESSIBILITY && !isAccessibilityEnabled) {
                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF451A03)),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                    shape = RoundedCornerShape(Radii.chip),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Accessibility Service is OFF",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFCD34D),
                            fontWeight = FontWeight.Bold,
                        )
                        TextButton(onClick = onOpenAccessibilitySettings) {
                            Text("Enable Service", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
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
    onRetry: () -> Unit,
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
                    item.status,
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
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = sk.brand),
                    shape = RoundedCornerShape(Radii.chip),
                ) {
                    Text(if (item.status == ViberOutboxItem.STATUS_SENT) "Resend" else "Send")
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
                    "Configure your Viber Public Account / Bot Token for silent background cloud dispatch.",
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
