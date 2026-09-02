package com.example.skillsync.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * V4 motion — three springs carry the whole app. Everything is a spring you can
 * catch mid-flight; nothing is a fixed-duration tween you have to wait out.
 *
 *   snappy — nav, toggles, tab and segment changes. Quick, barely any overshoot.
 *   gentle — sheets, cards, section reveals. A little give so it feels physical.
 *   flow   — the collapsing brief, container transforms. Critically damped, meant
 *            to track a finger rather than perform.
 */
object SkillMotion {
    fun <T> snappy() = spring<T>(
        dampingRatio = 0.82f,
        stiffness = Spring.StiffnessMedium,
    )

    fun <T> gentle() = spring<T>(
        dampingRatio = 0.78f,
        stiffness = Spring.StiffnessMediumLow,
    )

    fun <T> flow() = spring<T>(
        dampingRatio = 1f,
        stiffness = Spring.StiffnessMedium,
    )

    fun <T> press() = spring<T>(
        dampingRatio = 0.9f,
        stiffness = Spring.StiffnessHigh,
    )
}
