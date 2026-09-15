package com.example.skillsync.feature.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.skillsync.R
import com.example.skillsync.feature.communication.ui.MorningNoteAction
import com.example.skillsync.feature.communication.ui.MorningNoteState
import com.example.skillsync.feature.communication.ui.MorningNoteViewModel
import com.example.skillsync.feature.training.ui.BatchShare
import com.example.skillsync.theme.IconSlot
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.pressable
import com.example.skillsync.theme.skill

/**
 * Morning Note — the manager's weekday greeting for Teams/Viber, composed by
 * Communication Intelligence (MORNING_TEAM_GREETING). The preview renders the
 * Viber markers; Copy and Share hand over the raw marked-up text unchanged.
 */
@Composable
fun MorningNoteCard(email: String, viewModel: MorningNoteViewModel = viewModel()) {
    val context = LocalContext.current
    LaunchedEffect(email) { viewModel.start(context, email) }
    val state by viewModel.state.collectAsState()
    MorningNoteContent(
        state = state,
        onRegenerate = viewModel::regenerate,
        onCopy = {
            BatchShare.copyMessage(context, state.text)
            viewModel.record(MorningNoteAction.COPY)
        },
        onShare = {
            BatchShare.shareAnywhere(context, state.text)
            viewModel.record(MorningNoteAction.SHARE)
        },
    )
}

@Composable
internal fun MorningNoteContent(
    state: MorningNoteState,
    onRegenerate: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    val sk = MaterialTheme.skill
    val tint = sk.amber
    val shape = RoundedCornerShape(Radii.card)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.10f), sk.surface1)))
            .border(1.dp, tint.copy(alpha = 0.22f), shape)
            .padding(horizontal = Space.md, vertical = Space.md)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconSlot(tint = tint, size = 30.dp) {
                Icon(painterResource(R.drawable.ic_sun), contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(Space.sm))
            Column(Modifier.weight(1f)) {
                Text("MORNING NOTE", style = MaterialTheme.typography.labelMedium, color = sk.frost, fontWeight = FontWeight.Bold, letterSpacing = 0.08.em)
                Text(
                    state.weekday.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall, color = tint,
                )
            }
            Text("For Teams / Viber", style = MaterialTheme.typography.labelSmall, color = sk.subText)
        }

        when {
            state.isWeekend -> Text(
                "No morning note at the weekend — it returns on Monday.",
                style = MaterialTheme.typography.bodyMedium, color = sk.subText,
            )
            state.loading -> Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = tint)
                Spacer(Modifier.width(Space.sm))
                Text("Composing today's note…", style = MaterialTheme.typography.bodySmall, color = sk.subText)
            }
            state.text.isBlank() -> Text("Couldn't compose a note right now.", style = MaterialTheme.typography.bodyMedium, color = sk.subText)
            else -> Text(viberPreview(state.text), style = MaterialTheme.typography.bodyMedium, color = sk.bodyText)
        }

        if (!state.isWeekend) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                NoteAction(R.drawable.ic_refresh, "Regenerate", sk.subText, Modifier.weight(1f), enabled = !state.loading, onClick = onRegenerate)
                NoteAction(R.drawable.ic_copy, "Copy", sk.sky, Modifier.weight(1f), enabled = state.text.isNotBlank(), onClick = onCopy)
                NoteAction(R.drawable.ic_share, "Share", sk.sky, Modifier.weight(1f), enabled = state.text.isNotBlank(), onClick = onShare)
            }
        }
    }
}

@Composable
private fun NoteAction(icon: Int, label: String, tint: Color, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.08f * alpha))
            .then(if (enabled) Modifier.pressable(onClick) else Modifier)
            .semantics { role = Role.Button; contentDescription = "$label morning note" },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint.copy(alpha = alpha), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = tint.copy(alpha = alpha), fontWeight = FontWeight.SemiBold)
    }
}

/** Renders Viber/WhatsApp markers (*bold*, _italic_, ~strike~) for the preview only. */
internal fun viberPreview(raw: String): AnnotatedString = buildAnnotatedString {
    val marker = Regex("""([*_~])([^*_~\n]+)\1""")
    var last = 0
    marker.findAll(raw).forEach { m ->
        append(raw.substring(last, m.range.first))
        val style = when (m.groupValues[1]) {
            "*" -> SpanStyle(fontWeight = FontWeight.Bold)
            "_" -> SpanStyle(fontStyle = FontStyle.Italic)
            else -> SpanStyle(textDecoration = TextDecoration.LineThrough)
        }
        pushStyle(style); append(m.groupValues[2]); pop()
        last = m.range.last + 1
    }
    append(raw.substring(last))
}
