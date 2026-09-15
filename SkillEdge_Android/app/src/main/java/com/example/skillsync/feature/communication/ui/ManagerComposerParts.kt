package com.example.skillsync.feature.communication.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.skillsync.theme.skill

/**
 * The manager communication model, made visible in every composer:
 *
 *   ① Verified context  +  ② Manager instruction (optional)  →  ③ Generated message
 *
 * Verified context is read-only and always used; the instruction may steer
 * tone or focus but never replaces the facts. These parts are shared by the
 * main Communication screen and every in-flow composer dialog so the model
 * reads the same wherever the manager starts a message.
 */
object ComposerTints {
    val context = Color(0xFF34D399)      // verified — emerald
    val instruction = Color(0xFF60A5FA)  // manager — blue
    val generated = Color(0xFFA78BFA)    // output — violet
}

/** The one-line visual equation shown at the top of every composer. */
@Composable
fun ComposerModelStrip(modifier: Modifier = Modifier) {
    val sk = MaterialTheme.skill
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(sk.surface1)
            .border(1.dp, sk.cardBorder, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModelPill("1", "Verified context", ComposerTints.context, Modifier.weight(1f))
        Text("+", color = sk.labelText, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 4.dp))
        ModelPill("2", "Instruction", ComposerTints.instruction, Modifier.weight(1f))
        Text("→", color = sk.labelText, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 4.dp))
        ModelPill("3", "Message", ComposerTints.generated, Modifier.weight(1f))
    }
}

@Composable
private fun ModelPill(n: String, label: String, tint: Color, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(8.dp)).background(tint.copy(alpha = 0.14f))
            .border(1.dp, tint.copy(alpha = 0.45f), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(16.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
            Text(n, fontSize = 9.sp, color = Color(0xFF0B1220), fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 11.sp, color = tint, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A numbered composer stage: coloured rail, step number, title, hint, content. */
@Composable
fun ComposerStep(
    number: Int,
    title: String,
    hint: String,
    tint: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sk = MaterialTheme.skill
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(shape)
            .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.10f), sk.surface1)))
            .border(1.dp, tint.copy(alpha = 0.40f), shape),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(tint))
        Column(Modifier.weight(1f).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
                    Text("$number", fontSize = 12.sp, color = Color(0xFF0B1220), fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title.uppercase(), style = MaterialTheme.typography.titleSmall, color = sk.frost, fontWeight = FontWeight.Black, letterSpacing = 0.06.em)
                    Text(hint, style = MaterialTheme.typography.labelSmall, color = sk.subText)
                }
            }
            content()
        }
    }
}

/** Read-only fact rows for stage ①. Values are shown, never edited. */
@Composable
fun VerifiedContextRows(facts: List<Pair<String, String>>, emptyNote: String = "No linked record — only facts the engine can verify are used.") {
    val sk = MaterialTheme.skill
    val shown = facts.filter { it.second.isNotBlank() }
    if (shown.isEmpty()) {
        Text(emptyNote, style = MaterialTheme.typography.bodySmall, color = sk.subText)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        shown.forEach { (label, value) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✓", color = ComposerTints.context, fontWeight = FontWeight.Black, fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                Text(label.uppercase(), fontSize = 10.sp, color = sk.labelText, fontWeight = FontWeight.Bold, letterSpacing = 0.05.em, modifier = Modifier.width(92.dp))
                Text(value, style = MaterialTheme.typography.bodySmall, color = sk.frost, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Stage ② input. Always labelled the same way in every composer. */
@Composable
fun ManagerInstructionField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "Optional — tone, focus or one point to add. Facts are never overridden.",
    minLines: Int = 2,
) {
    val sk = MaterialTheme.skill
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Manager instruction (optional)") },
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodySmall) },
        minLines = minLines,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = sk.frost),
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ComposerTints.instruction,
            unfocusedBorderColor = ComposerTints.instruction.copy(alpha = 0.35f),
            focusedLabelColor = ComposerTints.instruction,
            cursorColor = ComposerTints.instruction,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Stage ③ read-only message preview surface. */
@Composable
fun GeneratedMessageBox(text: String, modifier: Modifier = Modifier) {
    val sk = MaterialTheme.skill
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF0B1220))
            .border(1.dp, ComposerTints.generated.copy(alpha = 0.35f), RoundedCornerShape(10.dp)).padding(12.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = sk.frost)
    }
}

/** Compact numbered stage label for composers embedded inside existing cards. */
@Composable
fun ComposerStageLabel(number: Int, title: String, tint: Color, note: String? = null) {
    val sk = MaterialTheme.skill
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(18.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
            Text("$number", fontSize = 10.sp, color = Color(0xFF0B1220), fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(8.dp))
        Text(title.uppercase(), fontSize = 11.sp, color = tint, fontWeight = FontWeight.Black, letterSpacing = 0.06.em)
        if (note != null) {
            Spacer(Modifier.width(6.dp))
            Text(note, style = MaterialTheme.typography.labelSmall, color = sk.subText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
