package com.example.skillsync.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Re-fetch when a screen becomes active, so the manager never reads stale numbers.
 *
 * Fires on ON_RESUME — which covers returning from another app, unlocking the
 * device, and coming back from a pushed detail screen — but never on the first
 * resume, because the screen's own initial load is already in flight and a second
 * identical request would only double the RMS traffic.
 *
 * [minIntervalMs] throttles it. Without a floor, flicking between tabs would fire
 * a full dashboard rebuild on every switch; the underlying RMS payload does not
 * change second to second, so anything inside the window is served from what is
 * already on screen.
 */
@Composable
fun RefreshOnResume(
    key: Any?,
    minIntervalMs: Long = 60_000L,
    onRefresh: () -> Unit,
) {
    val owner = LocalLifecycleOwner.current
    val refresh by rememberUpdatedState(onRefresh)
    // Seeded with "now" so the initial load counts as the most recent fetch.
    val lastRun = remember(key) { longArrayOf(System.currentTimeMillis()) }

    DisposableEffect(owner, key) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            val now = System.currentTimeMillis()
            if (now - lastRun[0] < minIntervalMs) return@LifecycleEventObserver
            lastRun[0] = now
            refresh()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
