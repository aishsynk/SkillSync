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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.skillsync.R
import com.example.skillsync.feature.communication.ui.MorningNoteAction
import com.example.skillsync.feature.communication.ui.MorningNoteState
import com.example.skillsync.feature.communication.ui.MorningNoteViewModel
import com.example.skillsync.feature.communication.ui.viberAnnotated
import com.example.skillsync.feature.training.ui.BatchShare
import com.example.skillsync.theme.IconSlot
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.pressable
import com.example.skillsync.theme.skill

/**
 * Morning Note — the manager's daily weekday greeting for Teams/Viber,
 * composed by Communication Intelligence (MORNING_TEAM_GREETING). Not shown
 * at all on Saturday or Sunday. Copy and Share hand over the greeting alone.
 */
@Composable
fun MorningNoteCard(email: String, viewModel: MorningNoteViewModel = viewModel()) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Re-checked on every resume, so an app left open overnight picks up the next weekday.
    LaunchedEffect(email) {
        viewModel.start(context, email)
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.refreshForToday() }
    }
    val state by viewModel.state.collectAsState()
    if (state.isWeekend) return
    MorningNoteContent(
        state = state,
        onRegenerate = viewModel::regenerate,
        onCopy = {
            BatchShare.copyMessage(context, viewModel.payload())
            viewModel.record(MorningNoteAction.COPY)
        },
        onShare = {
            BatchShare.shareAnywhere(context, viewModel.payload())
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
            .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.09f), sk.surface1)))
            .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
            .padding(start = Space.md, end = Space.sm, top = 12.dp, bottom = 2.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconSlot(tint = tint, size = 26.dp) {
                Icon(painterResource(R.drawable.ic_sun), contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(Space.sm))
            Text("MORNING NOTE", style = MaterialTheme.typography.labelMedium, color = sk.frost, fontWeight = FontWeight.Bold, letterSpacing = 0.08.em)
            Spacer(Modifier.width(6.dp))
            Text(
                state.weekday.name,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.06.em),
                color = tint, fontWeight = FontWeight.SemiBold,
            )
        }

        Box(Modifier.padding(end = Space.sm)) {
            when {
                state.loading && state.text.isBlank() -> Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = tint)
                    Spacer(Modifier.width(Space.sm))
                    Text("Composing today's note…", style = MaterialTheme.typography.bodySmall, color = sk.subText)
                }
                state.text.isBlank() -> Text("Couldn't compose a note right now.", style = MaterialTheme.typography.bodyMedium, color = sk.subText)
                // Preview only: paragraph break tightened to one line to keep the card compact.
                else -> Text(viberAnnotated(state.text.replace("\n\n", "\n")), style = MaterialTheme.typography.bodyMedium, color = sk.bodyText)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            NoteAction(R.drawable.ic_refresh, "Regenerate", sk.subText, enabled = !state.loading, onClick = onRegenerate)
            Spacer(Modifier.weight(1f))
            NoteAction(R.drawable.ic_copy, "Copy", sk.sky, enabled = state.text.isNotBlank() && !state.loading, onClick = onCopy)
            NoteAction(R.drawable.ic_share, "Share", sk.sky, enabled = state.text.isNotBlank() && !state.loading, onClick = onShare)
        }
    }
}

/** Compact icon + label action: 44dp tall touch target, content-width. */
@Composable
private fun NoteAction(icon: Int, label: String, tint: Color, enabled: Boolean, onClick: () -> Unit) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(if (enabled) Modifier.pressable(onClick) else Modifier)
            .semantics { role = Role.Button; contentDescription = "$label morning note" }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint.copy(alpha = alpha), modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint.copy(alpha = alpha), fontWeight = FontWeight.SemiBold)
    }
}
