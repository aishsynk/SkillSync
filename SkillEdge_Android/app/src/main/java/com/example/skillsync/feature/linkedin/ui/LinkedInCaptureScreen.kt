package com.example.skillsync.feature.linkedin.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.skillsync.R
import com.example.skillsync.feature.linkedin.engine.LinkedInAnalysis
import com.example.skillsync.feature.linkedin.engine.LinkedInCapture
import com.example.skillsync.feature.linkedin.engine.LinkedInLabels
import com.example.skillsync.navigation.LinkedInCaptureSource
import com.example.skillsync.theme.AuroraBackground
import com.example.skillsync.theme.Severity
import com.example.skillsync.theme.SkillSyncCard
import com.example.skillsync.theme.SkillSyncErrorState
import com.example.skillsync.theme.SkillSyncInfoBanner
import com.example.skillsync.theme.SkillSyncPrimaryButton
import com.example.skillsync.theme.SkillSyncSecondaryButton
import com.example.skillsync.theme.SkillSyncTopBar
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.ToneChip
import com.example.skillsync.theme.skill
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * LinkedIn Engagement Copilot — human-in-the-loop capture.
 *
 * Two cold entries: an `ACTION_SEND` share of a LinkedIn post, or the in-app
 * "Analyse a LinkedIn post" card on the Today brief. Either way the user first
 * sees an editable preview (they may trim the post, or paste the visible text
 * when only a link was shared), then the decision/comment result to copy.
 */
@Composable
fun LinkedInCaptureScreen(
    email: String,
    sharedText: String?,
    source: String,
    onBack: () -> Unit,
    viewModel: LinkedInCaptureViewModel = viewModel(),
) {
    val ui by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(source, sharedText) {
        if (source == LinkedInCaptureSource.SHARE_INTENT) {
            viewModel.startShare(sharedText)
        } else {
            viewModel.startPaste(sharedText)
        }
    }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                SkillSyncTopBar(
                    title = "LinkedIn Engagement Copilot",
                    subtitle = "Capture",
                    onBack = onBack,
                )
            },
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                val analysis = ui.result
                val error = ui.error
                when {
                    ui.analyzing -> LinkedInCaptureAnalyzingContent()
                    ui.stage == LinkedInCaptureStage.RESULT && analysis != null ->
                        LinkedInCaptureResultContent(
                            analysis = analysis,
                            copied = copied,
                            onCopy = {
                                clipboard.setText(AnnotatedString(analysis.comment.orEmpty()))
                                copied = true
                            },
                            onOpenOriginal = { openPostUrl(context, analysis.postUrl) },
                            onAnalyseAnother = {
                                copied = false
                                viewModel.reset()
                                viewModel.startPaste(null)
                            },
                            onDone = {
                                copied = false
                                onBack()
                            },
                        )
                    error != null ->
                        LinkedInCaptureErrorContent(
                            message = error,
                            onRetry = viewModel::retry,
                            onEdit = viewModel::backToEdit,
                        )
                    else ->
                        LinkedInCaptureEditingContent(
                            ui = ui,
                            onTextChange = viewModel::onTextChange,
                            onAuthorChange = viewModel::onAuthorChange,
                            onUrlChange = viewModel::onUrlChange,
                            onSubmit = viewModel::submit,
                        )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LinkedInCaptureEditingContent(
    ui: LinkedInCaptureUiState,
    onTextChange: (String) -> Unit,
    onAuthorChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        item {
            SkillSyncInfoBanner(
                title = "Paste the visible post text",
                message = "The decision engine reads the post's text, not its link. Paste what you see, trim anything personal, and analyse. Nothing is ever posted automatically.",
                severity = Severity.Info,
            )
        }
        if (ui.urlOnlyHint.isNotBlank()) {
            item {
                SkillSyncInfoBanner(
                    title = "Link-only capture",
                    message = ui.urlOnlyHint,
                    severity = Severity.Warning,
                )
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)),
                shape = MaterialTheme.shapes.large,
            ) {
                OutlinedTextField(
                    value = ui.rawText,
                    onValueChange = onTextChange,
                    label = { Text("POST TEXT") },
                    placeholder = { Text("Paste the post text here…") },
                    minLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Space.md),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                OutlinedTextField(
                    value = ui.authorName,
                    onValueChange = onAuthorChange,
                    label = { Text("Author (optional)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = ui.postUrl,
                    onValueChange = onUrlChange,
                    label = { Text("Post URL (optional)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            SkillSyncPrimaryButton(
                text = if (ui.captureMethod == LinkedInCapture.METHOD_SHARE_INTENT) {
                    "Analyse this shared post"
                } else {
                    "Analyse post"
                },
                onClick = onSubmit,
                enabled = ui.canSubmit,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Text(
                "You stay in control: the engine returns a recommendation, a reaction, and a comment template that you copy and post yourself.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.skill.subText,
                modifier = Modifier.padding(top = Space.xs),
            )
        }
    }
}

@Composable
internal fun LinkedInCaptureResultContent(
    analysis: LinkedInAnalysis,
    copied: Boolean,
    onCopy: () -> Unit,
    onOpenOriginal: () -> Unit,
    onAnalyseAnother: () -> Unit,
    onDone: () -> Unit,
) {
    val sk = MaterialTheme.skill
    val actionLabel = LinkedInLabels.action(analysis.action)
    val reactionLabel = LinkedInLabels.reaction(analysis.reaction ?: analysis.selectedReaction)
    val manualReview = analysis.requiresManualReview || analysis.action == LinkedInCapture.ACTION_MANUAL_REVIEW
    val sensitive = analysis.sensitivity.equals("HIGH", ignoreCase = true) ||
        analysis.sensitivity.equals("CRITICAL", ignoreCase = true)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                ToneChip(text = "RECOMMENDATION", tint = sk.sky)
                when (analysis.commentValidation) {
                    LinkedInCapture.COMMENT_VALIDATION_PASS -> ToneChip(text = "VALIDATED", tint = sk.good)
                    LinkedInCapture.COMMENT_VALIDATION_FAILED -> ToneChip(text = "NOT VALIDATED", tint = sk.warn)
                }
                Spacer(Modifier.weight(1f))
                analysis.textLength?.let { length ->
                    ToneChip(text = "$length chars", tint = sk.subText)
                }
            }
        }
        if (manualReview) {
            item {
                SkillSyncInfoBanner(
                    title = "Requires your review",
                    message = analysis.actionReason ?: "Flagged for manual review before you post anything.",
                    severity = Severity.Warning,
                )
            }
        }
        if (sensitive) {
            item {
                SkillSyncInfoBanner(
                    title = "Sensitive post",
                    message = "The post was flagged as ${analysis.sensitivity} — weigh visibility before engaging.",
                    severity = Severity.Watch,
                )
            }
        }
        item {
            SkillSyncCard {
                Text(
                    actionLabel,
                    style = MaterialTheme.typography.titleLarge,
                    color = sk.frost,
                    fontWeight = FontWeight.Bold,
                )
                analysis.actionReason?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = sk.bodyText,
                        modifier = Modifier.padding(top = Space.xs),
                    )
                }
                analysis.topics.takeIf { topics -> topics.isNotEmpty() }?.let { topics ->
                    Text(
                        "Topics: ${topics.joinToString(", ")} · ${analysis.postType ?: "post"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = sk.subText,
                        modifier = Modifier.padding(top = Space.sm),
                    )
                }
            }
        }
        if (analysis.requiresReaction) {
            item {
                SkillSyncCard {
                    Text(
                        "REACTION",
                        style = MaterialTheme.typography.labelSmall,
                        color = sk.sky,
                    )
                    Text(
                        "Give a $reactionLabel reaction",
                        style = MaterialTheme.typography.titleMedium,
                        color = sk.frost,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = Space.xs),
                    )
                    analysis.reactionReason?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = sk.bodyText,
                            modifier = Modifier.padding(top = Space.xs),
                        )
                    }
                }
            }
        }
        item {
            SkillSyncCard {
                Text(
                    "COMMENT",
                    style = MaterialTheme.typography.labelSmall,
                    color = sk.sky,
                )
                when {
                    analysis.shouldComment == true && !analysis.comment.isNullOrBlank() -> {
                        Text(
                            analysis.comment,
                            style = MaterialTheme.typography.bodyMedium,
                            color = sk.bodyText,
                            modifier = Modifier.padding(top = Space.xs),
                        )
                        analysis.commentStrategy?.takeIf { it.isNotBlank() }?.let {
                            ToneChip(
                                text = it.lowercase().replace("_", " "),
                                tint = sk.ice,
                                modifier = Modifier.padding(top = Space.sm),
                            )
                        }
                        Row(
                            modifier = Modifier.padding(top = Space.lg),
                            horizontalArrangement = Arrangement.spacedBy(Space.md),
                        ) {
                            SkillSyncPrimaryButton(
                                text = if (copied) "Copied" else "Copy comment",
                                onClick = onCopy,
                                leadingIcon = if (copied) null else R.drawable.ic_copy,
                                modifier = Modifier.weight(1f),
                            )
                            analysis.postUrl?.takeIf { it.isNotBlank() }?.let {
                                SkillSyncSecondaryButton(
                                    text = "Open post",
                                    onClick = onOpenOriginal,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    analysis.shouldComment == false -> {
                        Text(
                            "No comment recommended",
                            style = MaterialTheme.typography.titleMedium,
                            color = sk.frost,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = Space.xs),
                        )
                        analysis.commentReason?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = sk.bodyText,
                                modifier = Modifier.padding(top = Space.xs),
                            )
                        }
                    }
                    else -> {
                        Text(
                            "No comment generated for this post.",
                            style = MaterialTheme.typography.bodySmall,
                            color = sk.bodyText,
                            modifier = Modifier.padding(top = Space.xs),
                        )
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                SkillSyncSecondaryButton(
                    text = "Analyse another",
                    onClick = onAnalyseAnother,
                    modifier = Modifier.weight(1f),
                )
                SkillSyncPrimaryButton(
                    text = "Done",
                    onClick = onDone,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            val pieces = buildList {
                analysis.sourceApp?.takeIf { it.isNotBlank() }?.let { add(it.lowercase()) }
                analysis.language?.takeIf { it.isNotBlank() }?.let { add("lang $it") }
                analysis.confidence?.let { add("${(it * 100).roundToInt()}% confidence") }
            }
            Text(
                (listOfNotNull("capture ${analysis.captureId.orEmpty().take(8)}") + pieces)
                    .filter { it.isNotBlank() }
                    .joinToString("  ·  "),
                style = MaterialTheme.typography.labelSmall,
                color = sk.subText,
            )
        }
    }
}

@Composable
internal fun LinkedInCaptureAnalyzingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            CircularProgressIndicator(color = MaterialTheme.skill.aqua)
            Text(
                "Analysing this post…",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.skill.frost,
            )
            Text(
                "The engine scores relevance, relationship fit, risk and novelty before choosing an action.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.skill.subText,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LinkedInCaptureErrorContent(
    message: String,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        SkillSyncErrorState(message = message, onRetry = onRetry)
        SkillSyncSecondaryButton(
            text = "Edit post",
            onClick = onEdit,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun openPostUrl(context: Context, url: String?) {
    val target = url?.trim().orEmpty()
    if (target.isEmpty()) return
    val intent = Intent(Intent.ACTION_VIEW, target.toUri())
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        Toast.makeText(context, "No app can open this link.", Toast.LENGTH_SHORT).show()
    }
}