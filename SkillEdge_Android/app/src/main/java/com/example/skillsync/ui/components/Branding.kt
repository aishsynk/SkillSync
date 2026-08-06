package com.example.skillsync.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.skillsync.R
import com.example.skillsync.theme.skill

/**
 * The SkillSync brand mark. When [floating] it drifts and breathes very slightly —
 * enough to feel alive on the login screen without pulling focus.
 */
@Composable
fun SkillSyncLogo(
    size: Dp,
    modifier: Modifier = Modifier,
    floating: Boolean = false,
) {
    val t = rememberInfiniteTransition(label = "logo")
    val drift by t.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            tween(2800, easing = Motion.Standard),
            RepeatMode.Reverse,
        ),
        label = "drift",
    )
    val breathe by t.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(
            tween(3600, easing = Motion.Standard),
            RepeatMode.Reverse,
        ),
        label = "breathe",
    )

    Image(
        painter = painterResource(R.drawable.ic_logo),
        contentDescription = "SkillSync",
        modifier = modifier
            .size(size)
            .graphicsLayer {
                if (floating) {
                    translationY = drift
                    scaleX = breathe
                    scaleY = breathe
                }
            },
    )
}

/** Wordmark + tagline lockup used on the login screen. */
@Composable
fun SkillSyncWordmark(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = "SkillSync",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "MANAGER INTELLIGENCE",
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 2.4.sp,
            color = MaterialTheme.skill.subText,
        )
    }
}
