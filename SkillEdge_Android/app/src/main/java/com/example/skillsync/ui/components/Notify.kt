package com.example.skillsync.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.skillsync.R
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.Severity
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.glassSurface
import com.example.skillsync.theme.skill
import kotlinx.coroutines.delay

/**
 * The app's one notification system.
 *
 * Before this the project surfaced feedback four different ways — a hardcoded
 * `TopBannerNotification` that used one icon and one colour for every message,
 * Material's default `Snackbar` on the batch screen, an inline error banner on
 * login, and raw `AlertDialog`s elsewhere. Same job, four looks, none of them
 * on the design tokens.
 *
 * Two surfaces replace all of it:
 *
 *  - [SkillToast] — transient confirmation and failure, queued and stacked,
 *    auto-dismissing with a visible time bar. The "toastr" role.
 *  - [SkillAlertDialog] — anything that needs a decision or must be read before
 *    the app moves on. The "sweetalert" role.
 *
 * Both take a [Severity] so a success and a failure can never be styled the
 * same way by accident, and both carry a redundant icon so the meaning survives
 * greyscale and colour-blindness.
 */

// ── Model ───────────────────────────────────────────────────────────────────

@Immutable
data class Toast(
    val id: Long,
    val severity: Severity,
    val title: String,
    val message: String? = null,
    val durationMillis: Long = if (severity == Severity.Critical) 6000L else 3500L,
    val action: ToastAction? = null,
)

@Immutable
data class ToastAction(val label: String, val onClick: () -> Unit)

/**
 * Queue owner. Held in a CompositionLocal so any screen can raise a toast
 * without threading a callback through every composable between it and the
 * scaffold — the plumbing is exactly why the old code grew four systems.
 */
@Stable
class NotifyState {
    private var nextId = 0L
    var toasts by mutableStateOf<List<Toast>>(emptyList())
        private set

    fun show(
        severity: Severity,
        title: String,
        message: String? = null,
        action: ToastAction? = null,
    ) {
        val toast = Toast(nextId++, severity, title, message, action = action)
        // Three on screen is the ceiling; past that the oldest is dropped so a
        // burst of failures cannot bury the app.
        toasts = (toasts + toast).takeLast(3)
    }

    fun success(title: String, message: String? = null) = show(Severity.Good, title, message)
    fun error(title: String, message: String? = null) = show(Severity.Critical, title, message)
    fun warn(title: String, message: String? = null) = show(Severity.Warning, title, message)
    fun info(title: String, message: String? = null) = show(Severity.Info, title, message)

    fun dismiss(id: Long) {
        toasts = toasts.filterNot { it.id == id }
    }
}

val LocalNotify = staticCompositionLocalOf { NotifyState() }

@Composable
fun rememberNotifyState(): NotifyState = remember { NotifyState() }

// ── Toast host ──────────────────────────────────────────────────────────────

/**
 * Renders the queue. Mounted once at the app root, above every screen, so a
 * toast raised during navigation is not torn down with the screen that raised it.
 */
@Composable
fun ToastHost(state: NotifyState, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        state.toasts.forEach { toast ->
            key(toast.id) {
                ToastCard(toast) { state.dismiss(toast.id) }
            }
        }
    }
}

@Composable
private fun ToastCard(toast: Toast, onDismiss: () -> Unit) {
    val sk = MaterialTheme.skill
    val tint = toast.severity.tint()
    var visible by remember { mutableStateOf(false) }

    // Elapsed fraction drives the time bar, so the reader can see how long they
    // have left rather than being surprised by the disappearance.
    val progress = remember { Animatable(0f) }
    LaunchedEffect(toast.id) {
        visible = true
        progress.animateTo(1f, tween(toast.durationMillis.toInt(), easing = LinearEasing))
        visible = false
        delay(220)
        onDismiss()
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(280)) { -it } + fadeIn(tween(220)),
        exit = slideOutVertically(tween(200)) { -it } + fadeOut(tween(180)),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .glassSurface(RoundedCornerShape(Radii.card))
                .border(1.dp, tint.copy(alpha = 0.45f), RoundedCornerShape(Radii.card))
                .pointerInput(toast.id) {
                    detectVerticalDragGestures { _, dy ->
                        // Swipe up to dismiss — the direction it arrived from.
                        if (dy < -6f) { visible = false }
                    }
                }
                .clickable { visible = false },
        ) {
            Row(
                Modifier.padding(Space.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(Radii.icon))
                        .background(tint.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(toast.severity.iconRes()),
                        contentDescription = toast.severity.label,
                        tint = tint,
                        modifier = Modifier.size(17.dp),
                    )
                }
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        toast.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = sk.bodyText,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    if (!toast.message.isNullOrBlank()) {
                        Text(
                            toast.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = sk.subText,
                            maxLines = 3, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (toast.action != null) {
                    TextButton(onClick = { toast.action.onClick(); visible = false }) {
                        Text(
                            toast.action.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = tint,
                        )
                    }
                }
            }
            // Time remaining.
            Box(
                Modifier
                    .fillMaxWidth(1f - progress.value)
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(listOf(tint, tint.copy(alpha = 0.35f)))
                    )
            )
        }
    }
}

// ── Alert dialog ────────────────────────────────────────────────────────────

/**
 * The decision surface: a severity medallion, a question, and two clear ways
 * out. Used for logout, destructive confirmations and any failure the manager
 * must acknowledge before continuing.
 */
@Composable
fun SkillAlertDialog(
    severity: Severity,
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String? = "Cancel",
    destructive: Boolean = false,
) {
    val sk = MaterialTheme.skill
    val tint = if (destructive) sk.crit else severity.tint()
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.88f,
        animationSpec = tween(220),
        label = "dialogScale",
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .padding(Space.xl)
                .widthIn(max = 420.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .glassSurface(RoundedCornerShape(Radii.hero))
                .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(Radii.hero))
                .padding(Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.16f))
                    .border(1.dp, tint.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(severity.iconRes()),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(28.dp),
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = sk.bodyText,
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = sk.subText,
            )
            Spacer(Modifier.height(Space.xs))
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(Radii.chip),
                colors = ButtonDefaults.buttonColors(
                    containerColor = tint,
                    contentColor = if (destructive) Color.White else sk.navy,
                ),
            ) {
                Text(confirmLabel, style = MaterialTheme.typography.labelLarge)
            }
            if (dismissLabel != null) {
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        dismissLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = sk.subText,
                    )
                }
            }
        }
    }
}

/** One icon per severity, so meaning is never carried by colour alone. */
private fun Severity.iconRes(): Int = when (this) {
    Severity.Good -> R.drawable.ic_check
    Severity.Critical, Severity.Warning, Severity.Watch -> R.drawable.ic_alert
    Severity.Info -> R.drawable.ic_flag
}
