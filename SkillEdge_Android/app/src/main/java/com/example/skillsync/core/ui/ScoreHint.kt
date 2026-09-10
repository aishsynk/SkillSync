package com.example.skillsync.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.skillsync.data.SessionManager
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.glassSurface
import com.example.skillsync.theme.skill

/**
 * Score literacy. The first time a manager meets a score, it carries one line
 * explaining what it means and where the line is. Tap "Got it" and it is gone
 * for good (persisted per [key]). No tour, no modal — the meaning arrives with
 * the number and then gets out of the way.
 */
@Composable
fun ScoreHint(
    key: String,
    text: String,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(!SessionManager.isHintDismissed(key)) }
    val sk = MaterialTheme.skill
    AnimatedVisibility(visible = visible, exit = shrinkVertically()) {
        Row(
            modifier
                .clip(RoundedCornerShape(10.dp))
                .glassSurface(RoundedCornerShape(10.dp))
                .padding(horizontal = Space.md, vertical = Space.sm),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = sk.subText,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Space.md))
            Text(
                "GOT IT",
                style = MaterialTheme.typography.labelSmall,
                color = sk.brass,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .pressable {
                        SessionManager.dismissHint(key)
                        visible = false
                    }
                    .padding(horizontal = Space.sm, vertical = 4.dp),
            )
        }
    }
}

/** The canonical explainer strings, so the wording is defined once. */
object ScoreHints {
    const val READINESS = "readiness"
    const val RISK = "risk"

    fun readiness() =
        "Team readiness blends certification, availability and delivery quality. " +
            "Above 75 is strong; below 50 needs a plan."

    fun risk() =
        "Risk is driven by recent negative feedback and delivery incidents. " +
            "Low is clear; High means review before allocating."
}
