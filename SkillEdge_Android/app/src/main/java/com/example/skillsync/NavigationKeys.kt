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

/** "Pre-Demand Pipeline Radar" — advance Service Confirmations with lead times and candidate matching. */
@Serializable data class PipelineRadar(val email: String) : NavKey

/** "Delivery Compliance Sentinel" — checks daily recording uploads for ongoing batches across reportees. */
@Serializable data class DeliveryCompliance(val email: String) : NavKey

/** "Viber Background Automation" — automated queue, outbox, and background dispatch console. */
@Serializable data class ViberAutomation(val email: String) : NavKey

/** "Skill Requests" — pending reportee skill-level elevation requests for a manager to approve/deny. */
@Serializable data class SkillRequests(val email: String) : NavKey

/** A trainer's practice record — learner comment log + session recordings. */
@Serializable data class TrainerPractice(val email: String, val name: String) : NavKey

/** Your own delivery schedule + leave bands — for anyone who also delivers. */
@Serializable data class MySchedule(val email: String) : NavKey

/** Reportee self-service shell: their own profile, matched demand, and updates. */
@Serializable data class ReporteeMain(val email: String, val tab: String = ReporteeTab.TODAY) : NavKey

object ReporteeTab {
    const val TODAY = "today"
    const val PROFILE = "profile"
    const val DEMAND = "demand"
    const val CALENDAR = "calendar"
}

object HomeTab {
    const val DASHBOARD = "today"
    const val TEAM = "people"
    const val COURSES = "courses"
    const val DEMAND = "planning"
    const val ACTIONS = "actions"
    const val DELIVERY = "delivery"
    const val SEARCH = "search"
}
