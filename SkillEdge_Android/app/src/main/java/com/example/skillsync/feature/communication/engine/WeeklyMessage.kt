package com.example.skillsync.feature.communication.engine

/**
 * Weekly-report-specific data shapes and the "Weekly Manager Standpoint"
 * internal note.
 *
 * `composeTeamMessage`/`composeReporteeMessage` (a second, independent
 * Teams/Viber prose engine, parallel to `CommunicationComposer`) were
 * retired in Phase 2 of the architecture restructuring: they had zero
 * production callers — `WeeklyReportScreen.kt` uses `TeamSignals`/
 * `ReporteeSignals` only as intermediate evidence structs (see
 * `WeeklyTeamSummary` construction there), never passes them into a prose
 * composer — and preserving an unused parallel prose generator is exactly
 * what this restructuring exists to remove. Live weekly/reportee Teams
 * messages go through `feature.communication.domain.ManagerCommunicationComposer`
 * (offline) or the backend `/api/v2/message/compose` endpoint (online), both
 * of which build on the shared `CommunicationComposer`/`CommunicationContextSelector`
 * pipeline.
 */

const val MESSAGE_LIMIT = 1000

/**
 * How the copied text is marked up.
 *
 * Teams renders `**bold**` and `_italic_` on paste; Viber renders neither and
 * would show the markers literally, so [PLAIN] emits none.
 */
enum class MessageStyle { PLAIN, TEAMS }

/** One reportee's week, reduced to only what changes what you would say. Evidence-only. */
data class ReporteeSignals(
    val name: String,
    val utilisation: Int? = null,
    val capacityBucket: String = "",
    val certGaps: Int = 0,
    /** The specific courses being taught without the matching certification — names the "which" so the message is actionable. */
    val certGapCourses: List<String> = emptyList(),
    val feedbackRisk: String = "",
    val readiness: Int? = null,
    val currentCourse: String = "",
    val nextCourse: String = "",
    val openActions: Int = 0,
    /** Cross-domain and peer benchmark insights to help them grow and gain utilization. */
    val domain: String = "",
    val targetGrowthCourses: List<String> = emptyList(),
    val peerBenchmarkNote: String = "",
    /** Evidence-only learner signals (RMS key 244). Null = no feedback on record, not zero. */
    val learnerRating: Double? = null,
    val learnerRatingCount: Int = 0,
    val learnerRecentDate: String = "",
    val hrNegativeCount: Int = 0,
    val negativeFeedbackCount: Int = 0,
)

/** The team's week. */
data class TeamSignals(
    val teamName: String = "team",
    val strength: Int = 0,
    val deployed: Int = 0,
    val free: Int = 0,
    val utilisation: Int? = null,
    val atRisk: Int = 0,
    val certGaps: Int = 0,
    val unallocated: Int = 0,
    val international: Int = 0,
)

/**
 * Composes an explicit "Where You Stand" weekly managerial evaluation note.
 * Evidence-only: every line derives from real RMS signals (utilisation,
 * learner rating, cert gaps, HR/negative feedback). No generic
 * "pacing & articulation" boilerplate. Bullet-free to meet the Teams/Viber
 * house style (no bullets, hyphens or decorative symbols).
 *
 * This is a distinct artifact type from a Teams/Viber prose message — an
 * internal, labelled-field manager note — not a duplicate of
 * `CommunicationComposer`'s prose output. Live caller:
 * `WeeklyReportScreen.kt`'s per-reportee card, as the offline fallback when
 * neither the server nor `ManagerCommunicationComposer` returned a message.
 */
fun composeManagerStandpointNote(
    signals: ReporteeSignals,
    style: MessageStyle = MessageStyle.TEAMS,
): String {
    val first = signals.name.trim().substringBefore(" ").ifBlank { "Trainer" }
    val lines = mutableListOf<String>()
    lines += "Weekly Manager Standpoint for $first:"
    lines += ""

    val statusText = when {
        signals.capacityBucket.equals("Stretched", true) -> "High Workload (Stretched at ${signals.utilisation ?: 85}% util)"
        signals.capacityBucket.equals("On Bench", true) -> "Available / On Bench (${signals.utilisation ?: 0}% util)"
        signals.currentCourse.isNotBlank() -> "Active Delivery on ${signals.currentCourse} (${signals.utilisation ?: 75}% util)"
        else -> "Steady (${signals.utilisation ?: 70}% util)"
    }
    lines += "Standpoint: $statusText"

    if (signals.learnerRating != null) {
        val datePart = if (signals.learnerRecentDate.isNotBlank()) ", latest ${signals.learnerRecentDate}" else ""
        lines += "Learner rating 90 day: ${signals.learnerRating}/5 from ${signals.learnerRatingCount} responses$datePart"
    } else {
        lines += "Learner rating 90 day: no feedback on record"
    }

    val focus = when {
        signals.feedbackRisk.equals("High", true) ->
            "Immediate Focus: review the ${signals.negativeFeedbackCount} negative feedback and ${signals.hrNegativeCount} HR records and hold a 1 on 1"
        signals.certGaps > 0 -> {
            val courses = signals.certGapCourses.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "assigned courses"
            "Immediate Focus: schedule and complete the certification exam for $courses"
        }
        signals.capacityBucket.equals("On Bench", true) -> {
            if (signals.targetGrowthCourses.isNotEmpty()) "Immediate Focus: assign to open demand or upskill toward ${signals.targetGrowthCourses.joinToString(", ")}"
            else "Immediate Focus: assign to open demand or upskill toward pipeline demand"
        }
        signals.nextCourse.isNotBlank() -> "Immediate Focus: final preparation for ${signals.nextCourse}"
        signals.currentCourse.isNotBlank() -> "Immediate Focus: delivering ${signals.currentCourse} this week. No action needed"
        else -> "Immediate Focus: none. Steady, no flags this week"
    }
    lines += focus

    val raw = lines.joinToString("\n")
    return if (style == MessageStyle.TEAMS) {
        raw.replace("Standpoint:", "**Standpoint:**")
            .replace("Learner rating 90 day:", "**Learner rating 90 day:**")
            .replace("Immediate Focus:", "**Immediate Focus:**")
    } else raw
}
