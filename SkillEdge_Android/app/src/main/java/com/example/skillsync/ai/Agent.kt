package com.example.skillsync.ai

/**
 * The question answering half of the agent.
 *
 * A question is routed to an [Intent] by pattern, the matching tool reads the
 * fact base, and the answer is composed from what was actually found. Every
 * answer carries its evidence and its confidence, and an unmatched question is
 * refused rather than guessed at.
 *
 * That refusal is the important part. Without a language model this agent can
 * only answer what it has tools for, and a system that produces fluent text for
 * a question it did not understand is worse than one that says so: the manager
 * cannot tell the two apart, and delivery decisions get made on invented data.
 */

enum class Intent {
    WHO_NEEDS_ATTENTION,
    WHO_IS_AVAILABLE,
    WHO_CAN_TEACH,
    TRAINER_STATUS,
    TRAINER_NEXT_STEP,
    TEAM_HEALTH,
    DEMAND_COVERAGE,
    CERTIFICATION_GAPS,
    KEY_PERSON_RISK,
    FUTURE_TRENDS,
    UNKNOWN,
}

enum class Confidence { HIGH, MEDIUM, LOW }

data class Answer(
    val intent: Intent,
    val headline: String,
    val detail: String,
    val evidence: List<String> = emptyList(),
    val suggestions: List<Suggestion> = emptyList(),
    val confidence: Confidence = Confidence.HIGH,
    /** Set when the agent could not answer, explaining what it would need. */
    val unmet: String? = null,
)

object Agent {

    /** Questions offered in the UI, so a manager is not made to guess the syntax. */
    fun starters(team: TeamFact): List<String> = buildList {
        add("Who needs my attention?")
        if (team.free.isNotEmpty()) add("Who is available right now?")
        if (team.unallocated.isNotEmpty()) add("Can we cover the open demand?")
        if (team.totalCertGaps > 0) add("Where are our certification gaps?")
        add("How is the team doing?")
        add("What is our key person risk?")
        add("What does next week look like?")
        team.flagged.firstOrNull()?.let { add("What should I do about ${it.name}?") }
        team.trainers.firstOrNull()?.let { add("How is ${it.name} doing?") }
    }

    fun ask(question: String, team: TeamFact, weights: Weights = Weights()): Answer {
        val q = question.trim().lowercase()
        if (q.isBlank()) return unknown(question)

        val named = team.find(extractName(q, team))
        val intent = classify(q, named != null)

        return when (intent) {
            Intent.WHO_NEEDS_ATTENTION -> whoNeedsAttention(team, weights)
            Intent.WHO_IS_AVAILABLE -> whoIsAvailable(team)
            Intent.WHO_CAN_TEACH -> whoCanTeach(q, team)
            Intent.TRAINER_STATUS -> trainerStatus(named, team, weights)
            Intent.TRAINER_NEXT_STEP -> trainerNextStep(named, weights)
            Intent.TEAM_HEALTH -> teamHealth(team)
            Intent.DEMAND_COVERAGE -> demandCoverage(team)
            Intent.CERTIFICATION_GAPS -> certificationGaps(team)
            Intent.KEY_PERSON_RISK -> keyPersonRisk(team, weights)
            Intent.FUTURE_TRENDS -> futureOutlook(team)
            Intent.UNKNOWN -> unknown(question)
        }
    }

    // ── Routing ─────────────────────────────────────────────────────────────

    internal fun classify(q: String, hasName: Boolean): Intent = when {
        // A named trainer plus an action word is a "what do I do" question; a
        // named trainer alone is a status question.
        hasName && q.containsAny("what should", "what do i do", "next step", "recommend", "suggest", "action") ->
            Intent.TRAINER_NEXT_STEP
        hasName && q.containsAny("how is", "status", "doing", "tell me about", "about") ->
            Intent.TRAINER_STATUS

        q.containsAny("who can teach", "who knows", "who is certified", "qualified for", "who could take") ->
            Intent.WHO_CAN_TEACH
        q.containsAny("needs my attention", "need attention", "who needs", "at risk", "worried", "problem") ->
            Intent.WHO_NEEDS_ATTENTION
        q.containsAny("available", "free", "on bench", "bench", "spare capacity", "who is idle") ->
            Intent.WHO_IS_AVAILABLE
        q.containsAny("cover", "demand", "unallocated", "open batch", "pipeline", "allocate") ->
            Intent.DEMAND_COVERAGE
        q.containsAny("certification", "certified", "cert gap", "accreditation", "gaps") ->
            Intent.CERTIFICATION_GAPS
        q.containsAny("key person", "single owner", "only one", "bus factor", "sole") ->
            Intent.KEY_PERSON_RISK
        q.containsAny("team", "how are we", "overall", "health", "utilisation", "utilization") ->
            Intent.TEAM_HEALTH
        q.containsAny("next week", "coming week", "coming weeks", "future", "forecast", "predict",
            "outlook", "what will", "trend", "what's coming", "whats coming", "ahead") ->
            Intent.FUTURE_TRENDS

        hasName -> Intent.TRAINER_STATUS
        else -> Intent.UNKNOWN
    }

    /** Pulls the longest trainer name mentioned, so "How is Priya Sharma?" resolves. */
    private fun extractName(q: String, team: TeamFact): String {
        val hit = team.trainers
            .filter { t ->
                val full = t.name.lowercase()
                val first = full.substringBefore(" ")
                q.contains(full) || (first.length >= 3 && q.contains(first))
            }
            .maxByOrNull { it.name.length }
        return hit?.name ?: ""
    }

    // ── Tools ───────────────────────────────────────────────────────────────

    private fun whoNeedsAttention(team: TeamFact, weights: Weights): Answer {
        val ranked = Recommender.forTeam(team, weights).take(3)
        if (ranked.isEmpty()) {
            return Answer(
                Intent.WHO_NEEDS_ATTENTION,
                "Nobody needs escalation today.",
                "No feedback flags, no certification gaps and nobody outside the load band. The useful thing you could do is recognise it rather than look for a problem.",
                evidence = listOf("${team.trainers.size} reportees reviewed"),
            )
        }
        return Answer(
            Intent.WHO_NEEDS_ATTENTION,
            "${ranked.size} thing${if (ranked.size == 1) "" else "s"} worth your time today.",
            ranked.joinToString(" ") { it.headline + "." },
            evidence = buildList {
                if (team.flagged.isNotEmpty()) add("${team.flagged.size} carrying a feedback flag")
                if (team.stretched.isNotEmpty()) add("${team.stretched.size} above the load band")
                if (team.totalCertGaps > 0) add("${team.totalCertGaps} certification gaps open")
                if (team.unallocated.isNotEmpty()) add("${team.unallocated.size} batches unallocated")
            },
            suggestions = ranked,
        )
    }

    private fun whoIsAvailable(team: TeamFact): Answer {
        val free = (team.free + team.benched).distinctBy { it.email }
        if (free.isEmpty()) {
            return Answer(
                Intent.WHO_IS_AVAILABLE,
                "Nobody is free right now.",
                "Every reportee is either delivering or committed. If something urgent lands, the realistic options are to move a batch or to look outside the team.",
                evidence = listOf("${team.trainers.size} reportees, none marked free"),
                confidence = Confidence.HIGH,
            )
        }
        return Answer(
            Intent.WHO_IS_AVAILABLE,
            "${free.size} available: ${free.joinToString(", ") { it.name }}.",
            free.joinToString(" ") { t ->
                val util = t.utilisation?.let { "$it percent utilised" } ?: "utilisation not measured"
                "${t.name} is $util."
            },
            evidence = free.map { t ->
                "${t.name}: ${t.capacityBucket.ifBlank { t.currentStatus.ifBlank { "status not reported" } }}"
            },
        )
    }

    private fun whoCanTeach(q: String, team: TeamFact): Answer {
        // Match the question against every course the team can actually teach.
        val courses = team.trainers.flatMap { it.teachableCourses }.distinct()
        val course = courses
            .filter { it.isNotBlank() && q.contains(it.lowercase()) }
            .maxByOrNull { it.length }
            ?: courses.firstOrNull { c ->
                c.split(" ", "-").any { token -> token.length >= 4 && q.contains(token.lowercase()) }
            }

        if (course == null) {
            return Answer(
                Intent.WHO_CAN_TEACH,
                "I could not match that to a course this team teaches.",
                "Name the course as it appears in the catalogue and I will list who can take it, with their certification and availability.",
                evidence = listOf("${courses.size} distinct courses known across the team"),
                confidence = Confidence.LOW,
                unmet = if (courses.isEmpty())
                    "Capability data has not loaded yet, so I do not know who teaches what."
                else null,
            )
        }

        val able = team.trainers.filter { course in it.teachableCourses }
        val certified = able.filter { t -> t.certGaps.none { it.equals(course, true) } }
        return Answer(
            Intent.WHO_CAN_TEACH,
            "${able.size} can teach $course.",
            able.joinToString(" ") { t ->
                val state = when {
                    t.isFree || t.isBenched -> "available"
                    t.isStretched -> "already stretched"
                    else -> "currently committed"
                }
                "${t.name} is $state."
            },
            evidence = buildList {
                add("Certified for it: ${certified.size} of ${able.size}")
                able.take(4).forEach { t ->
                    add("${t.name}: ${t.utilisation?.let { "$it percent" } ?: "utilisation not measured"}")
                }
            },
        )
    }

    private fun trainerStatus(t: TrainerFact?, team: TeamFact, weights: Weights): Answer {
        if (t == null) return unknownPerson(team)
        val next = Recommender.forTrainer(t, weights)
        val headline = when {
            t.isFlagged -> "${t.name} needs a conversation this week."
            t.isStretched -> "${t.name} is carrying too much."
            t.isBenched -> "${t.name} is available and underused."
            t.certGaps.isNotEmpty() -> "${t.name} is delivering ahead of their certification."
            else -> "${t.name} is in good shape."
        }
        return Answer(
            Intent.TRAINER_STATUS,
            headline,
            buildString {
                t.utilisation?.let { append("Utilisation is $it percent") }
                    ?: append("Utilisation has not been measured")
                t.utilisationTrend?.let {
                    append(", ${if (it >= 0) "up" else "down"} ${kotlin.math.abs(it)} points on last month")
                }
                append(". ")
                if (t.currentCourse.isNotBlank()) append("Currently on ${t.currentCourse}. ")
                if (t.nextCourse.isNotBlank()) append("Next up is ${t.nextCourse}. ")
                if (t.certGaps.isNotEmpty()) append("${t.certGaps.size} certification gap open. ")
            }.trim(),
            evidence = buildList {
                add("Capacity: ${t.capacityBucket.ifBlank { "not reported" }}")
                add("Feedback risk: ${t.feedbackRisk.ifBlank { "none recorded" }}")
                t.readiness?.let { add("Readiness: $it${t.readinessBucket.takeIf { b -> b.isNotBlank() }?.let { b -> " ($b)" } ?: ""}") }
                if (t.certsHeld.isNotEmpty()) add("${t.certsHeld.size} certifications held")
                if (t.candidateFor.isNotEmpty()) add("Ranked for ${t.candidateFor.size} open batches")
            },
            suggestions = next.take(2),
            confidence = if (t.utilisation == null || t.readiness == null) Confidence.MEDIUM else Confidence.HIGH,
        )
    }

    private fun trainerNextStep(t: TrainerFact?, weights: Weights): Answer {
        if (t == null) return Answer(
            Intent.TRAINER_NEXT_STEP,
            "Tell me who you mean.",
            "Name the trainer and I will give you the next step with the evidence behind it.",
            confidence = Confidence.LOW,
        )
        val next = Recommender.forTrainer(t, weights)
        if (next.isEmpty()) {
            return Answer(
                Intent.TRAINER_NEXT_STEP,
                "Nothing needs doing about ${t.name}.",
                "No flag, no gap, and the load is inside the band. Leaving them alone is the correct action.",
                evidence = listOf("Capacity: ${t.capacityBucket.ifBlank { "not reported" }}"),
            )
        }
        val top = next.first()
        return Answer(
            Intent.TRAINER_NEXT_STEP,
            top.headline,
            top.rationale,
            evidence = top.evidence,
            suggestions = next,
        )
    }

    private fun teamHealth(team: TeamFact): Answer {
        val util = team.avgUtilisation
        val band = when {
            util == null -> "not measured"
            util > 85 -> "above the target band"
            util in 70..85 -> "inside the target band"
            else -> "below the target band"
        }
        return Answer(
            Intent.TEAM_HEALTH,
            "Utilisation is $band${util?.let { " at $it percent" } ?: ""}.",
            buildString {
                append("${team.trainers.size} reportees, ${team.free.size} free and ${team.stretched.size} stretched. ")
                if (team.flagged.isNotEmpty()) append("${team.flagged.size} carrying a feedback flag. ")
                if (team.unallocated.isNotEmpty()) append("${team.unallocated.size} batches still need an owner. ")
                if (team.totalCertGaps > 0) append("${team.totalCertGaps} certification gaps open. ")
            }.trim(),
            evidence = buildList {
                team.readinessScore?.let { add("Team readiness: $it") }
                add("Active deliveries: ${team.activeDeliveries}")
                add("Upcoming deliveries: ${team.upcomingDeliveries}")
                if (team.utilisationHistory.size >= 2) {
                    val d = team.utilisationHistory.last() - team.utilisationHistory[team.utilisationHistory.lastIndex - 1]
                    add("Month over month: ${if (d >= 0) "up" else "down"} ${kotlin.math.abs(d)} points")
                }
            },
            confidence = if (util == null) Confidence.MEDIUM else Confidence.HIGH,
        )
    }

    private fun demandCoverage(team: TeamFact): Answer {
        val open = team.unallocated
        if (open.isEmpty()) return Answer(
            Intent.DEMAND_COVERAGE,
            "All visible demand is covered.",
            "Nothing is waiting for an owner right now.",
            evidence = listOf("Active deliveries: ${team.activeDeliveries}"),
        )
        val coverable = team.trainers.count { it.candidateFor.isNotEmpty() }
        val intl = team.internationalDemand
        return Answer(
            Intent.DEMAND_COVERAGE,
            "${open.size} batches open, ${intl.size} of them international.",
            buildString {
                append(
                    if (team.free.isEmpty())
                        "Nobody is currently free, so covering these means moving existing work. "
                    else "${team.free.size} free to allocate against them. "
                )
                if (intl.isNotEmpty()) append("The international work is the highest value and should be reviewed first. ")
            }.trim(),
            evidence = buildList {
                add("Trainers ranked against at least one batch: $coverable")
                intl.take(3).forEach { add("${it.courseName}: ${it.deliveryMode}${it.startDate.takeIf { d -> d.isNotBlank() }?.let { d -> ", starts $d" } ?: ""}") }
            },
            confidence = if (coverable == 0) Confidence.MEDIUM else Confidence.HIGH,
            unmet = if (coverable == 0)
                "The allocation desk has not loaded, so I cannot tell you who ranks for these batches yet."
            else null,
        )
    }

    private fun certificationGaps(team: TeamFact): Answer {
        val withGaps = team.trainers.filter { it.certGaps.isNotEmpty() }
        if (withGaps.isEmpty()) return Answer(
            Intent.CERTIFICATION_GAPS,
            "No open certification gaps.",
            "Everyone teaching a course holds the matching certification on record.",
            evidence = listOf("${team.trainers.size} reportees checked"),
        )
        return Answer(
            Intent.CERTIFICATION_GAPS,
            "${team.totalCertGaps} gaps across ${withGaps.size} people.",
            withGaps.take(3).joinToString(" ") { t ->
                "${t.name} is missing ${t.certGaps.first()}."
            },
            evidence = withGaps.take(5).map { "${it.name}: ${it.certGaps.size} gap${if (it.certGaps.size == 1) "" else "s"}" },
        )
    }

    private fun keyPersonRisk(team: TeamFact, weights: Weights): Answer {
        val risks = Recommender.forTeam(team, weights)
            .filter { it.kind == SuggestionKind.COVER_KEY_PERSON_RISK }
        if (risks.isEmpty()) return Answer(
            Intent.KEY_PERSON_RISK,
            "No single owner courses found.",
            "Every course the team teaches has more than one person who can take it.",
            evidence = listOf("${team.trainers.size} reportees checked"),
            confidence = if (team.trainers.any { it.teachableCourses.isNotEmpty() }) Confidence.HIGH else Confidence.LOW,
        )
        return Answer(
            Intent.KEY_PERSON_RISK,
            "${risks.size} course${if (risks.size == 1) "" else "s"} depend on one person.",
            risks.joinToString(" ") { it.headline + "." },
            evidence = risks.flatMap { it.evidence }.distinct(),
            suggestions = risks,
        )
    }

    private fun futureOutlook(team: TeamFact): Answer {
        val history = team.utilisationHistory
        val trendDir = when {
            history.size >= 2 -> {
                val delta = history.last() - history[history.lastIndex - 1]
                when {
                    delta >= 5 -> "climbing"
                    delta <= -5 -> "falling"
                    else -> "flat"
                }
            }
            else -> null
        }

        val benchCount = (team.free + team.benched).distinctBy { it.email }.size
        val openDemand = team.unallocated.size
        val certRisk = team.trainers.count { it.certGaps.isNotEmpty() }

        // Headline summarises the single biggest forward-looking pressure.
        val headline = when {
            openDemand > benchCount + 1 ->
                "$openDemand batches need owners but only $benchCount people are available — a capacity gap is building."
            trendDir == "climbing" && team.stretched.isNotEmpty() ->
                "Utilisation is climbing and ${team.stretched.size} people are already stretched — the team is at risk of overload."
            certRisk > 0 ->
                "$certRisk people are delivering without the matching certification — this blocks accredited work coming in."
            trendDir == "falling" && benchCount > 2 ->
                "Utilisation is falling with $benchCount people on bench — demand is softer than supply right now."
            else -> "No acute pressure visible, but the outlook has a few things worth watching."
        }

        return Answer(
            Intent.FUTURE_TRENDS,
            headline,
            buildString {
                trendDir?.let { append("Utilisation trend is $it. ") }
                if (openDemand > 0) append("$openDemand batches are unallocated — if this is not resolved soon, delivery dates are at risk. ")
                if (benchCount > 0) append("$benchCount people are available to absorb incoming demand. ")
                if (certRisk > 0) append("$certRisk people need to complete certifications before they can take accredited work arriving in the pipeline. ")
                if (team.flagged.isNotEmpty()) append("${team.flagged.size} people have open feedback flags that could affect delivery quality on their next batch. ")
                if (team.internationalDemand.isNotEmpty()) append("${team.internationalDemand.size} international batches are in the pipeline — these need accredited trainers and longer lead times to fill. ")
            }.trim(),
            evidence = buildList {
                trendDir?.let { add("Utilisation direction: $it") }
                add("Open demand vs bench: $openDemand batches, $benchCount available")
                add("Certification risk: $certRisk people delivering without cert")
                if (team.upcomingDeliveries > 0) add("Upcoming confirmed deliveries: ${team.upcomingDeliveries}")
                if (team.internationalDemand.isNotEmpty()) add("International pipeline: ${team.internationalDemand.size} batches")
            },
            confidence = when {
                history.isEmpty() -> Confidence.MEDIUM
                history.size < 3 -> Confidence.MEDIUM
                else -> Confidence.HIGH
            },
        )
    }

    // ── Refusals ────────────────────────────────────────────────────────────

    private fun unknownPerson(team: TeamFact) = Answer(
        Intent.TRAINER_STATUS,
        "I do not recognise that name.",
        "I can only answer about your ${team.trainers.size} reportees. Try their full name as it appears on the Team page.",
        confidence = Confidence.LOW,
    )

    private fun unknown(question: String) = Answer(
        Intent.UNKNOWN,
        "I cannot answer that one.",
        "I answer from RMS delivery data: who needs attention, who is free, who can teach a course, how someone is doing, what to do about them, team health, demand coverage, certification gaps, key person risk, and future outlook and trends.",
        confidence = Confidence.LOW,
        unmet = "\"$question\" did not match anything I have a tool for. I would rather say so than invent an answer.",
    )
}

private fun String.containsAny(vararg needles: String) = needles.any { this.contains(it) }
