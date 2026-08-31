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

/** The delivery agent: manager questions and next best actions. */
@Serializable data class Copilot(val email: String) : NavKey

/** Weekly copy-and-send messages: one for the team, one per reportee. */
@Serializable data class WeeklyReport(val email: String) : NavKey

/** Monthly performance snapshot for HR — one card per reportee. */
@Serializable data class HrReport(val email: String) : NavKey

/** "This Week" — the ranked board of what needs the manager. */
@Serializable data class Priorities(val email: String) : NavKey

/** "Capacity Runway" — forward 8-week demand vs team capacity, plus upskilling. */
@Serializable data class CapacityRunway(val email: String) : NavKey
@Serializable data class Ramp(val email: String) : NavKey

/** "Accounts" — the team's customer delivery book and concentration signal. */
@Serializable data class Accounts(val email: String) : NavKey

/** "How your team compares" — team health vs an honest, documented baseline. */
@Serializable data class Benchmark(val email: String) : NavKey

object HomeTab {
    const val DASHBOARD = "today"
    const val TEAM = "people"
    const val COURSES = "courses"
    const val DEMAND = "planning"
    const val ACTIONS = "actions"
    const val DELIVERY = "delivery"
    const val SEARCH = "search"
}
