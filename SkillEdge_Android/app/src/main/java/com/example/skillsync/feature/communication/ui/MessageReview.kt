package com.example.skillsync.feature.communication.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.skillsync.R
import com.example.skillsync.theme.pressable
import com.example.skillsync.theme.skill

/**
 * Renders Viber/WhatsApp markers (*bold*, _italic_, ~strike~) for display only.
 *
 * Preview rendering and the copied payload are separate concerns: the manager
 * sees formatted text, while Copy/Share hand over the raw marked-up string that
 * Teams and Viber understand.
 */
fun viberAnnotated(raw: String): AnnotatedString = buildAnnotatedString {
    // Word-boundary anchored so snake_case identifiers, emails and course codes
    // are not mistaken for markers (a_variable_name stays as typed).
    val marker = Regex("""(?<![A-Za-z0-9])([*_~])([^*_~\n]+)\1(?![A-Za-z0-9])""")
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

/**
 * Review surface for a generated manager message: formatted preview, an Edit
 * toggle that exposes the raw marked-up text, and Regenerate / Copy / Share.
 *
 * [onShare] must be wired to the Android share sheet and recorded as
 * SHARED_EXTERNALLY — never as SENT, which requires confirmed transport.
 */
@Composable
fun MessageReviewCard(
    text: String,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    provenanceNote: String? = null,
    onTextChange: ((String) -> Unit)? = null,
    onRegenerate: (() -> Unit)? = null,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    val sk = MaterialTheme.skill
    var editing by remember(text) { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(sk.surface1)
            .border(1.dp, Color.White.copy(alpha = 0.07f), shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "MESSAGE",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.08.em),
                color = sk.labelText, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
            )
            if (busy) CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 2.dp, color = sk.sky)
        }

        if (editing && onTextChange != null) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = sk.sky, unfocusedBorderColor = sk.cardBorder,
                    focusedTextColor = sk.bodyText, unfocusedTextColor = sk.bodyText, cursorColor = sk.sky,
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 320.dp),
            )
            Text(
                "Editing the raw text. *bold*, _italic_ and ~strike~ are the markers Teams and Viber understand.",
                style = MaterialTheme.typography.labelSmall, color = sk.subText,
            )
        } else {
            SelectionContainer {
                // Formatted for reading; the markers stay in the copied payload.
                Text(viberAnnotated(text), style = MaterialTheme.typography.bodyMedium, color = sk.bodyText)
            }
        }

        if (provenanceNote != null) {
            Text(provenanceNote, style = MaterialTheme.typography.labelSmall, color = sk.subText)
        }

        val hasRegenerate = onRegenerate != null
        val hasEdit = onTextChange != null
        // Four actions (Regenerate + Edit + Copy + Share) don't fit on one row
        // at 360dp without squeezing the last one to near-zero width, which
        // wrapped "Share" character-by-character. A 2x2 grid keeps every
        // label and touch target full-size instead of shrinking text.
        if (hasRegenerate && hasEdit) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    ReviewAction(
                        R.drawable.ic_refresh, "Regenerate", sk.subText, enabled = !busy,
                        onClick = onRegenerate!!, modifier = Modifier.weight(1f),
                    )
                    ReviewAction(
                        R.drawable.ic_gap, if (editing) "Done" else "Edit", sk.subText, enabled = !busy,
                        onClick = { editing = !editing }, modifier = Modifier.weight(1f),
                    )
                }
                Row(Modifier.fillMaxWidth()) {
                    ReviewAction(
                        R.drawable.ic_copy, "Copy", sk.sky, enabled = text.isNotBlank() && !busy,
                        onClick = onCopy, modifier = Modifier.weight(1f),
                    )
                    ReviewAction(
                        R.drawable.ic_share, "Share", sk.sky, enabled = text.isNotBlank() && !busy,
                        onClick = onShare, modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            // Three or fewer actions fit naturally on one row.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasRegenerate) {
                    ReviewAction(R.drawable.ic_refresh, "Regenerate", sk.subText, enabled = !busy, onClick = onRegenerate!!)
                }
                if (hasEdit) {
                    ReviewAction(
                        R.drawable.ic_gap, if (editing) "Done" else "Edit", sk.subText, enabled = !busy,
                        onClick = { editing = !editing },
                    )
                }
                Spacer(Modifier.weight(1f))
                ReviewAction(R.drawable.ic_copy, "Copy", sk.sky, enabled = text.isNotBlank() && !busy, onClick = onCopy)
                ReviewAction(R.drawable.ic_share, "Share", sk.sky, enabled = text.isNotBlank() && !busy, onClick = onShare)
            }
        }
    }
}

@Composable
private fun ReviewAction(
    icon: Int,
    label: String,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(if (enabled) Modifier.pressable(onClick) else Modifier)
            .semantics { role = Role.Button; contentDescription = "$label message" }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint.copy(alpha = alpha), modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            label, style = MaterialTheme.typography.labelMedium, color = tint.copy(alpha = alpha),
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}
