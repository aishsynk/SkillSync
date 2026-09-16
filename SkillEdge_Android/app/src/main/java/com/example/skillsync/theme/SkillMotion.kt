package com.example.skillsync.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * V4 motion — three springs carry the whole app. Everything is a spring you can
 * catch mid-flight; nothing is a fixed-duration tween you have to wait out.
 *
 * This is the ONE motion vocabulary for the whole app (Design V3 Phase 1
 * formalizes this, see `AI/DECISIONS.md` — the springs themselves are
 * unchanged, only their intended use is now explicit below). Do not
 * introduce ad-hoc `tween(180)`/`tween(300)` durations elsewhere; pick the
 * spring whose intent matches instead.
 */
object SkillMotion {
    /** Chips, toggles, segmented-control selection, small discrete-state
     * changes. Quick, barely any overshoot — the user shouldn't wait to see
     * their tap register. */
    fun <T> snappy() = spring<T>(
        dampingRatio = 0.82f,
        stiffness = Spring.StiffnessMedium,
    )

    /** Card/content entrance, section reveals, status-color changes. A
     * little give so it feels physical rather than mechanical. */
    fun <T> gentle() = spring<T>(
        dampingRatio = 0.78f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Larger layout/container transforms — the collapsing Today brief,
     * screen-level container transitions. Critically damped (no overshoot),
     * meant to track a continuous gesture/scroll rather than perform an
     * independent animation. */
    fun <T> flow() = spring<T>(
        dampingRatio = 1f,
        stiffness = Spring.StiffnessMedium,
    )

    /** Touch-down feedback only (`Modifier.pressable`) — never used for
     * anything that isn't a direct response to a press. */
    fun <T> press() = spring<T>(
        dampingRatio = 0.9f,
        stiffness = Spring.StiffnessHigh,
    )
}
