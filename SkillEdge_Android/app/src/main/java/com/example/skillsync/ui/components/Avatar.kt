package com.example.skillsync.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.example.skillsync.theme.skill

/** Two-letter monogram: "Abhinav   Samant" -> "AS". */
fun initialsOf(name: String): String =
    name.split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.take(1).uppercase() }
        .ifBlank { "?" }

/**
 * Profile photo with an initials fallback.
 *
 * Most trainers have no photo on file — RMS returns the literal string "None" —
 * and of those that do, the URL can 404. Both cases fall back to the monogram
 * rather than leaving a hole, and the tint is derived from the name so the same
 * person is always the same colour across screens.
 */
@Composable
fun Avatar(
    name: String,
    photoUrl: String?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val sk = MaterialTheme.skill
    val palette = listOf(sk.teal, sk.blue, sk.indigo, sk.amber, sk.green)
    val tint = remember(name, sk) {
        val h = name.hashCode()
        palette[(if (h == Int.MIN_VALUE) 0 else kotlin.math.abs(h)) % palette.size]
    }
    var failed by remember(photoUrl) { mutableStateOf(false) }
    val showPhoto = !photoUrl.isNullOrBlank() && !failed

    val base = modifier.size(size).clip(CircleShape)
    Box(
        if (showPhoto) base
        else base.background(
            Brush.linearGradient(listOf(tint.copy(alpha = 0.26f), tint.copy(alpha = 0.10f)))
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (showPhoto) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(photoUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                modifier = Modifier.size(size).clip(CircleShape),
            )
        } else {
            Text(
                initialsOf(name),
                color = tint,
                fontWeight = FontWeight.ExtraBold,
                // Scales with the circle so one composable covers 28dp rows and
                // the 64dp dashboard header.
                fontSize = (size.value * 0.36f).sp,
            )
        }
    }
}
