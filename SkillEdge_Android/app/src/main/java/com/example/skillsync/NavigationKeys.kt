package com.example.skillsync

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Login : NavKey

/** Tabbed shell. [tab] selects the destination inside the bottom bar. */
@Serializable data class Main(val email: String, val tab: String = HomeTab.DASHBOARD) : NavKey

/** Deep profile for a single trainer, pushed over the shell. */
@Serializable data class Trainer360(val email: String, val trainerEmail: String, val trainerName: String) : NavKey

/** Full detail + actions for one unallocated batch, keyed by its assignment id. */
@Serializable data class BatchDetail(val email: String, val demandId: String) : NavKey

object HomeTab {
    const val DASHBOARD = "dashboard"
    const val TEAM = "team"
    const val DEMAND = "demand"
    const val ACTIONS = "actions"
}
