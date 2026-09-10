package com.example.skillsync.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.skillsync.theme.NumericInline
import com.example.skillsync.theme.frostedGlass
import com.example.skillsync.theme.skill

enum class PulseTone { Calm, Watch, Critical }

/**
 * The Pulse — a stadium capsule docked top-centre on every primary screen,
 * carrying the one number that matters right now. It is the app's heartbeat: it
 * breathes while data is refreshing, it takes on the colour of the most urgent
 * thing on screen, and a tap sends you back to the full briefing.
 *
 * Borrowed from the Dynamic Island / Live Activity idea and re-pointed at
 * delivery: "72" when the org is healthy, "3 need you" when it isn't.
 */
@Composable
fun Pulse(
    value: String,
    label: String,
    tone: PulseTone,
    refreshing: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sk = MaterialTheme.skill
    val accent = when (tone) {
        PulseTone.Calm -> sk.brand
        PulseTone.Watch -> sk.warn
        PulseTone.Critical -> sk.crit
    }

    // Breathe while refreshing — a slow 1.0 → 1.03 scale, nothing louder.
    val breath = rememberInfiniteTransition(label = "pulse-breath")
    val scale by breath.animateFloat(
        initialValue = 1f,
        targetValue = if (refreshing) 1.035f else 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "breath",
    )

    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(999.dp))
            .frostedGlass(RoundedCornerShape(999.dp), blurRadius = 14.dp)
            .pressable(onClick = onTap)
            .drawBehind {
                // a faint accent halo, stronger while live
                drawRoundRect(
                    color = accent.copy(alpha = if (refreshing) 0.22f else 0.12f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
                )
            }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .drawBehind { drawCircle(accent) },
        )
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                (fadeIn(tween(220)) togetherWith fadeOut(tween(140)))
            },
            label = "pulse-value",
        ) { v ->
            Text(
                v,
                style = NumericInline.copy(
                    fontSize = MaterialTheme.typography.titleMedium.fontSize,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = sk.bodyText,
                maxLines = 1,
            )
        }
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = sk.labelText,
            maxLines = 1,
        )
    }
}
