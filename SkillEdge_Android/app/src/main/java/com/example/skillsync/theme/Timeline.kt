package com.example.skillsync.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A generic event-timeline row: marker, connector, title/support/timestamp,
 * optional trailing content. Reserved for genuine chronological event feeds
 * (delivery lifecycle, automation runs, communication history, audit trail)
 * — not a substitute for an ordinary list where the items aren't events in
 * time. The theme layer knows nothing about what kind of event this is; a
 * screen supplies [tint] for status meaning and its own [content] for actions.
 *
 * Pass [isFirst]/[isLast] so the connector doesn't draw past the ends of the
 * feed the caller actually has.
 */
@Composable
fun TimelineItem(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String = "",
    timestamp: String = "",
    tint: Color? = null,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    content: (@Composable () -> Unit)? = null,
) {
    val sk = MaterialTheme.skill
    val markerColor = tint ?: sk.sky
    Row(modifier.fillMaxWidth()) {
        Column(Modifier.width(20.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .width(1.dp)
                    .height(if (isFirst) 6.dp else 14.dp)
                    .background(if (isFirst) Color.Transparent else sk.cardBorder),
            )
            Box(Modifier.size(8.dp).clip(CircleShape).background(markerColor))
            Box(
                Modifier
                    .width(1.dp)
                    .weight(1f)
                    .background(if (isLast) Color.Transparent else sk.cardBorder),
            )
        }
        Spacer(Modifier.width(Space.sm))
        Column(Modifier.padding(bottom = Space.md).wrapContentHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = sk.frost, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
                if (timestamp.isNotBlank()) {
                    Spacer(Modifier.width(Space.sm))
                    Text(timestamp, style = MaterialTheme.typography.labelSmall, color = sk.subText)
                }
            }
            if (supportingText.isNotBlank()) {
                Text(supportingText, style = MaterialTheme.typography.bodySmall, color = sk.subText)
            }
            if (content != null) {
                Spacer(Modifier.height(Space.xs))
                content()
            }
        }
    }
}
