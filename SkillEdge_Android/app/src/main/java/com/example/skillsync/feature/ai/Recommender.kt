package com.example.skillsync.ai

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What should this trainer, or this manager, do next.
 *
 * Every recommendation is a scored [Suggestion] carrying the evidence it was
 * derived from, because a manager will not act on a number they cannot audit.
 * The score is a weighted sum of signals, and the weights are learned from
 * whether the manager accepted or dismissed the same kind of suggestion before
 * (see [Weights]).
 *
 * This is deterministic, tool-based reasoning over RMS facts. It is not a
 * language model and does not pretend to be: it recognises a bounded set of
 * situations extremely well and says so plainly when a question falls outside
 * them. That is a deliberate trade — an honest narrow agent beats a fluent one
 * that invents delivery data.
 */

/** The kinds of thing the agent can propose. Also the learning key. */
enum class SuggestionKind(val label: String) {
    ADDRESS_FEEDBACK("Address feedback risk"),
    CLOSE_CERT_GAP("Close a certification gap"),
    REBALANCE_LOAD("Rebalance workload"),
    ALLOCATE_TO_DEMAND("Allocate to open demand"),
    BUILD_BENCH_SKILL("Use bench time to build a skill"),
    CLOSE_OPEN_ACTIONS("Close open actions"),
    RECOGNISE("Recognise strong delivery"),
    COVER_KEY_PERSON_RISK("Cover single owner course"),
}

data class Suggestion(
    val kind: SuggestionKind,
    val subject: String,
    val subjectEmail: String = "",
    val headline: String,
    val rationale: String,
    /** Raw signal strength before weighting, 0 to 100. */
    val signal: Int,
    /** Weighted, learned priority. Higher sorts first. */
    val score: Int,
    /** Every fact this rests on, so the manager can check the reasoning. */
    val evidence: List<String>,
)

/**
 * Learned weights, one per [SuggestionKind].
 *
 * Starts neutral at 1.0. Accepting a suggestion nudges its weight up, dismissing
 * nudges it down, both clamped to a band so no single kind can collapse to zero
 * or dominate the ranking. Weights are renormalised to a mean of 1.0 after each
 * update, which keeps the scale stable however many events arrive.
 *
 * [version] increments on every update so a stored model can be told apart from
 * a fresh one, and [events] records how much evidence the weights rest on —
 * without which a manager cannot tell a trained model from an untouched default.
 */
data class Weights(
    val values: Map<String, Double> = SuggestionKind.entries.associate { it.name to 1.0 },
    val version: Int = 0,
    val events: Int = 0,
) {
    fun weightFor(kind: SuggestionKind): Double = values[kind.name] ?: 1.0

    fun record(kind: SuggestionKind, accepted: Boolean): Weights {
        val step = if (accepted) LEARNING_RATE else -LEARNING_RATE
        val updated = values.toMutableMap()
        val current = updated[kind.name] ?: 1.0
        updated[kind.name] = (current + step).coerceIn(MIN_WEIGHT, MAX_WEIGHT)

        // Renormalise to mean 1.0 so the absolute scale does not drift upward
        // just because the manager accepts a lot.
        val mean = updated.values.average()
        val normalised =
            if (mean > 0.0) updated.mapValues { (_, v) -> (v / mean).coerceIn(MIN_WEIGHT, MAX_WEIGHT) }
            else updated

        return copy(values = normalised, version = version + 1, events = events + 1)
    }

    /** True once there is enough evidence for the weights to mean anything. */
    val isTrained: Boolean get() = events >= MIN_EVENTS_TO_TRUST

    companion object {
        const val LEARNING_RATE = 0.08
        const val MIN_WEIGHT = 0.4
        const val MAX_WEIGHT = 2.0
        const val MIN_EVENTS_TO_TRUST = 5
    }
}

object Recommender {

    /** Everything worth doing about one trainer, most important first. */
    fun forTrainer(t: TrainerFact, weights: Weights = Weights()): List<Suggestion> {
        val out = mutableListOf<Suggestion>()

        if (t.isFlagged) {
            out += build(
                SuggestionKind.ADDRESS_FEEDBACK, t, weights, signal = 95,
                headline = "Meet ${t.name.substringBefore(" ")} about delivery feedback",
                rationale = "A high feedback risk left alone tends to repeat on the next batch, so this is worth a conversation before the next allocation rather than after it.",
                evidence = buildList {
                    add("Feedback risk: ${t.feedbackRisk}")
                    if (t.deliveryRisk.isNotBlank()) add("Delivery risk: ${t.deliveryRisk}")
                    if (t.currentCourse.isNotBlank()) add("Currently on ${t.currentCourse}")
                },
            )
        }

        if (t.certGaps.isNotEmpty()) {
            out += build(
                SuggestionKind.CLOSE_CERT_GAP, t, weights,
                signal = (60 + t.certGaps.size * 8).coerceAtMost(90),
                headline = "Book ${t.certGaps.first()} for ${t.name.substringBefore(" ")}",
                rationale = "The course is already being taught without the matching certification on record, which blocks allocation to accredited demand.",
                evidence = buildList {
                    add("${t.certGaps.size} certification gap${if (t.certGaps.size == 1) "" else "s"}: ${t.certGaps.take(3).joinToString(", ")}")
                    t.coveragePct?.let { add("Certification coverage: $it percent") }
                    if (t.teachableCourses.isNotEmpty()) add("Teaches ${t.teachableCourses.size} courses")
                },
            )
        }

        if (t.isStretched) {
            out += build(
                SuggestionKind.REBALANCE_LOAD, t, weights, signal = 80,
                headline = "Move work off ${t.name.substringBefore(" ")}",
                rationale = "Sustained load above the target band is where delivery quality and feedback scores start to slip, and it is cheaper to rebalance now than to recover a bad batch.",
                evidence = buildList {
                    t.utilisation?.let { add("Utilisation: $it percent") }
                    add("Capacity: ${t.capacityBucket}")
                    t.utilisationTrend?.let {
                        add("Trend: ${if (it >= 0) "up" else "down"} ${abs(it)} points month over month")
                    }
                },
            )
        }

        if (t.isBenched || (t.utilisation != null && t.utilisation < 40)) {
            val best = t.candidateFor.firstOrNull()
            if (best != null) {
                out += build(
                    SuggestionKind.ALLOCATE_TO_DEMAND, t, weights,
                    signal = (55 + best.relevance / 3).coerceAtMost(88),
                    headline = "Put ${t.name.substringBefore(" ")} on ${best.courseName}",
                    rationale = "Available capacity and an open batch they already rank for, so this closes two problems with one allocation.",
                    evidence = buildList {
                        add("Fit score: ${best.relevance}")
                        t.utilisation?.let { add("Utilisation: $it percent") }
                        if (best.startDate.isNotBlank()) add("Starts ${best.startDate}")
                        if (best.isInternational) add("International delivery: ${best.deliveryMode}")
                    },
                )
            } else {
                out += build(
                    SuggestionKind.BUILD_BENCH_SKILL, t, weights, signal = 55,
                    headline = "Align ${t.name.substringBefore(" ")} to high-demand peer skills while on bench",
                    rationale = "Bench time converts into billable delivery only if pointed at cross-domain skills (Cloud, AI, Kubernetes) where active corporate pipeline is concentrated.",
                    evidence = buildList {
                        t.utilisation?.let { add("Current utilisation: $it percent") }
                        add("Domain peer target: 80%+ utilisation with adjacent tech certifications")
                        if (t.certGaps.isNotEmpty()) add("Open gap to close: ${t.certGaps.first()}")
                        add("Top cross-skilling tracks: Kubernetes (CKA), Microsoft Fabric, Azure Solutions (AZ-305)")
                    },
                )
            }
        }

        if (t.openActions > 0) {
            out += build(
                SuggestionKind.CLOSE_OPEN_ACTIONS, t, weights,
                signal = (35 + t.openActions * 5).coerceAtMost(70),
                headline = "Clear ${t.openActions} open item${if (t.openActions == 1) "" else "s"} on ${t.name.substringBefore(" ")}",
                rationale = "Open items age into escalations, and an inbox that never empties stops being read.",
                evidence = buildList {
                    add("${t.openActions} open manager action${if (t.openActions == 1) "" else "s"}")
                    if (t.recommendedAction.isNotBlank()) add("RMS suggests: ${t.recommendedAction}")
                },
            )
        }

        // Recognition is a real management action, and the only positive signal
        // in the set. Without it the agent only ever brings bad news.
        if (!t.isFlagged && t.certGaps.isEmpty() && t.utilisation != null && t.utilisation in 70..85) {
            out += build(
                SuggestionKind.RECOGNISE, t, weights, signal = 40,
                headline = "Acknowledge ${t.name.substringBefore(" ")}'s delivery",
                rationale = "Consistent delivery inside the target band with no open issues, which is easy to overlook precisely because nothing is going wrong.",
                evidence = buildList {
                    add("Utilisation: ${t.utilisation} percent, inside the 70 to 85 band")
                    t.readiness?.let { add("Readiness: $it") }
                    if (t.certsHeld.isNotEmpty()) add("${t.certsHeld.size} certifications held")
                },
            )
        }

        return out.sortedByDescending { it.score }
    }

    /** The manager's own queue, across the whole team. */
    fun forTeam(team: TeamFact, weights: Weights = Weights()): List<Suggestion> {
        val perTrainer = team.trainers.flatMap { forTrainer(it, weights) }

        val org = mutableListOf<Suggestion>()

        // A course only one person can teach is an outage waiting to happen.
        val owners = mutableMapOf<String, MutableList<String>>()
        team.trainers.forEach { t ->
            t.teachableCourses.forEach { c -> owners.getOrPut(c) { mutableListOf() }.add(t.name) }
        }
        owners.filter { it.value.size == 1 }.entries.take(3).forEach { (course, holders) ->
            val weight = weights.weightFor(SuggestionKind.COVER_KEY_PERSON_RISK)
            val signal = 65
            org += Suggestion(
                kind = SuggestionKind.COVER_KEY_PERSON_RISK,
                subject = course,
                headline = "Only ${holders.first()} can teach $course",
                rationale = "A single owner means any leave, illness or exit takes the course offline, and cross training takes longer than the notice you usually get.",
                signal = signal,
                score = (signal * weight).roundToInt(),
                evidence = listOf("Sole owner: ${holders.first()}", "Team size: ${team.trainers.size}"),
            )
        }

        return (perTrainer + org).sortedByDescending { it.score }
    }

    private fun build(
        kind: SuggestionKind,
        t: TrainerFact,
        weights: Weights,
        signal: Int,
        headline: String,
        rationale: String,
        evidence: List<String>,
    ) = Suggestion(
        kind = kind,
        subject = t.name,
        subjectEmail = t.email,
        headline = headline,
        rationale = rationale,
        signal = signal,
        score = (signal * weights.weightFor(kind)).roundToInt(),
        evidence = evidence,
    )
}
