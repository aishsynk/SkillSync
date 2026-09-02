package com.example.skillsync.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * V4 haptic vocabulary — seven signals, each mapped to a meaning, every one
 * paired with something visual. Compose only exposes two constants
 * (LongPress / TextHandleMove), so the finer distinctions map onto those with
 * intent-named entry points; a later pass can drop to `VibrationEffect`
 * compositions for the full palette. Honours the system haptic-off setting
 * automatically (the platform swallows the call).
 */
class SkillHaptics(private val h: HapticFeedback) {

    /** Tab / segment change — "you moved". */
    fun move() = h.performHapticFeedback(HapticFeedbackType.TextHandleMove)

    /** Card / control press — "you grabbed it". */
    fun press() = h.performHapticFeedback(HapticFeedbackType.TextHandleMove)

    /** A write to your own record saved — "saved". */
    fun saved() = h.performHapticFeedback(HapticFeedbackType.TextHandleMove)

    /** RMS confirmed the write — "it landed". */
    fun confirmed() = h.performHapticFeedback(HapticFeedbackType.LongPress)

    /** You are about to do something the app disagrees with — "stop and look". */
    fun objection() = h.performHapticFeedback(HapticFeedbackType.LongPress)

    /** Pull-to-refresh crossed the release threshold — "let go now". */
    fun threshold() = h.performHapticFeedback(HapticFeedbackType.TextHandleMove)

    /** A critical alert arrived — "look up". */
    fun alert() = h.performHapticFeedback(HapticFeedbackType.LongPress)
}

@Composable
fun rememberSkillHaptics(): SkillHaptics {
    val h = LocalHapticFeedback.current
    return SkillHaptics(h)
}
