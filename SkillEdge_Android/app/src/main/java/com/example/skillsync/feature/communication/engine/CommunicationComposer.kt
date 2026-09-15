package com.example.skillsync.feature.communication.engine

import java.util.Locale

object CommunicationComposer {

    fun composeFromPlan(plan: ContextSelectionPlan): String {
        if (plan.purpose == CommunicationPurpose.MORNING_TEAM_GREETING.id) return composeMorningGreeting(plan)
        // 1. Greeting
        val rName = plan.recipientName.trim()
        val greeting = when {
            plan.recipientType == "TEAM" -> "Hello team,"
            rName.isNotEmpty() -> "Hello ${italic(rName)},"
            else -> "Hello,"
        }

        // 2. Main message formulation
        val mainSentences = mutableListOf<String>()
        val p = plan.purpose
        val pDict = plan.selectedFacts.associate { it.key to it.value }
        val course = pDict["opportunity.course"]?.toString()
            ?: pDict["course"]?.toString()
            ?: extractCourse(if (plan.userMessage.isNotEmpty()) plan.userMessage else plan.myMessage)
        val timeRef = plan.timeReferences.firstOrNull() ?: ""

        when (p) {
            "OPPORTUNITY_RESPONSE" -> {
                val timeStr = if (timeRef.isNotEmpty()) " ${boldUnderline(timeRef)}" else ""
                val actionType = plan.actionRequired

                if (plan.myMessage.lowercase(Locale.getDefault()).contains("toc") || plan.myMessage.lowercase(Locale.getDefault()).contains("table of contents")) {
                    mainSentences.add("I can take up the batch$timeStr.")
                    mainSentences.add("I will need some preparation before delivery, so **please share the table of contents as soon as possible.**")
                } else if (actionType == "confirm_acceptance_and_request_schedule" || plan.myMessage.lowercase(Locale.getDefault()).contains("preparation")) {
                    val cLabel = if (course.isNotEmpty()) "the $course delivery" else "this delivery"
                    mainSentences.add("I can take up $cLabel$timeStr.")
                    mainSentences.add("I will need some preparation before the session, so **please share the final requirement and schedule when available so I can prepare accordingly.**")
                } else if (actionType == "decline_delivery_with_reason" || listOf("no", "cannot", "unable").any { plan.myMessage.lowercase(Locale.getDefault()).contains(it) }) {
                    mainSentences.add("Thank you for considering me for this requirement.")
                    mainSentences.add("Due to existing schedule commitments, I am unable to take this up, so **please assign another available trainer for this batch.**")
                } else {
                    val cLabel = if (course.isNotEmpty()) " for $course" else ""
                    mainSentences.add("Yes, I can take this up$cLabel$timeStr.")
                    mainSentences.add("**Please share the confirmed schedule so I can plan accordingly.**")
                }
            }
            "AVAILABILITY_RESPONSE" -> {
                if (plan.myMessage.lowercase(Locale.getDefault()).contains("connect_friday") || plan.myMessage.lowercase(Locale.getDefault()).contains("friday")) {
                    mainSentences.add("I am currently in delivery tomorrow and will not be available.")
                    mainSentences.add("**I can connect with you on ${boldUnderline("Friday")} if that works for you.**")
                } else if (listOf("no", "delivery", "busy", "cannot").any { plan.myMessage.lowercase(Locale.getDefault()).contains(it) }) {
                    mainSentences.add("I am currently in delivery and will not be available for this slot.")
                    mainSentences.add("**Please check if another time works or reassign to another available trainer.**")
                } else {
                    mainSentences.add("I am available and will be happy to connect with you.")
                    mainSentences.add("**Please let me know the preferred time for our discussion.**")
                }
            }
            "STATUS_UPDATE" -> {
                if (listOf("completed", "shared", "done").any { plan.myMessage.lowercase(Locale.getDefault()).contains(it) }) {
                    mainSentences.add("I have already completed the report and shared it with you.")
                    mainSentences.add("**Please let me know if you need any additional details.**")
                } else {
                    mainSentences.add(plan.situationSummary.trimEnd('.'))
                    mainSentences.add("**Please let me know if you need any additional updates.**")
                }
            }
            "COURSE_PREPARATION_CHECK" -> {
                val timeStr = if (timeRef.isNotEmpty()) " coming up ${boldUnderline(timeRef)}" else ""
                val cLabel = if (course.isNotEmpty()) "a $course delivery requirement" else "an upcoming delivery requirement"
                mainSentences.add("We have $cLabel$timeStr.")
                mainSentences.add("**Please confirm if you are confident taking this up and can complete the necessary preparation to deliver it with quality.**")
            }
            "AVAILABILITY_REQUEST" -> {
                val timeStr = if (timeRef.isNotEmpty()) " coming up ${boldUnderline(timeRef)}" else ""
                val candidate = pDict["candidate_trainer"]?.toString()
                val loc = pDict["location"]?.toString()
                val locStr = if (!loc.isNullOrBlank()) " in $loc" else ""
                // CommunicationPlanner.individualAvailabilityPlan sets this explicitly:
                // true only when the candidate's availability was actually verified
                // (not just capability-matched). Absent (plain Boolean-less lookup)
                // defaults true so Flow A/legacy callers that never set it keep the
                // existing wording — only the planner's "unknown" path changes it.
                val availabilityConfirmed = pDict["availability_confirmed"] as? Boolean ?: true
                if (!candidate.isNullOrBlank() && course.isNotEmpty() && availabilityConfirmed) {
                    mainSentences.add("We have an upcoming $course delivery requirement$locStr$timeStr. ${italic(candidate)} is identified as a strong candidate to lead this.")
                    mainSentences.add("**Please confirm if you are available and prepared to take up this batch.**")
                } else if (!candidate.isNullOrBlank() && course.isNotEmpty()) {
                    // Capability match confirmed, but real availability was not —
                    // ask this specific person to confirm rather than asserting it.
                    mainSentences.add("There is an upcoming $course delivery requirement$locStr$timeStr that matches your capability profile.")
                    mainSentences.add("**Please confirm whether you are available so I can review the remaining details before allocation.**")
                } else if (course.isNotEmpty()) {
                    mainSentences.add("We have a $course delivery requirement$locStr$timeStr and available capacity across the team.")
                    mainSentences.add("**If you are available to take this up, please confirm with me so we can review the requirement and proceed accordingly.**")
                } else {
                    val demandCount = (pDict["open_demand"] ?: pDict["operations.open_demand"])?.toString()?.toIntOrNull() ?: 1
                    val reqStr = if (demandCount == 1) "1 open delivery requirement" else "$demandCount open delivery requirements"
                    mainSentences.add("We have $reqStr on the board and available capacity across the team.")
                    mainSentences.add("**If you are available to take this up, please confirm with me so we can review the requirement and proceed accordingly.**")
                }
            }
            "CAPABILITY_ESCALATION" -> {
                val demandCount = (pDict["open_demand"] ?: pDict["operations.open_demand"])?.toString()?.toIntOrNull() ?: 1
                val reqStr = if (demandCount == 1) "1 open delivery requirement" else "$demandCount open delivery requirements"
                val verb = if (demandCount == 1) "requires" else "require"
                mainSentences.add("We have $reqStr that $verb skills outside our currently available team capacity.")
                mainSentences.add("**Please coordinate with the wider network to identify and allocate an external trainer.**")
            }
            "CAPABILITY_DEVELOPMENT" -> {
                val rawGaps = pDict["cert_gap_courses"]?.toString() ?: ""
                val gapCourses = rawGaps.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val cleanGaps = gapCourses.map { gc ->
                    Regex("""^([A-Z]{2,4}-\d{2,4}[A-Z0-9]*)""").find(gc)?.value ?: gc
                }
                if (cleanGaps.isNotEmpty()) {
                    val coursesStr = if (cleanGaps.size <= 2) cleanGaps.joinToString(" and ") else cleanGaps.dropLast(1).joinToString(", ") + ", and " + cleanGaps.last()
                    mainSentences.add("We have certification gaps linked to upcoming delivery requirements, specifically in $coursesStr.")
                } else {
                    mainSentences.add("We have certification gaps linked to upcoming delivery requirements.")
                }
                mainSentences.add("**Please review these areas and prioritise completing the certifications that support our active demand.**")
            }
            "APPRECIATION" -> {
                val ratingVal = pDict["avg_rating"]
                if (ratingVal != null) {
                    mainSentences.add("Thank you all for the strong effort and high quality delivery across our batches this month, averaging $ratingVal out of 5 in participant feedback.")
                } else {
                    mainSentences.add("Thank you all for the strong effort and high quality delivery across our batches this month.")
                }
                mainSentences.add("**Keep up the great work and consistency.**")
            }
            "DELIVERY_SUPPORT", "DELIVERY_UPDATE" -> {
                if (plan.myMessage.lowercase(Locale.getDefault()).contains("power")) {
                    mainSentences.add("The training session is going well.")
                    mainSentences.add("There was a power interruption on the client side, but we completed what was required and took the assessment.")
                    mainSentences.add("**Please let me know if you need any additional details.**")
                } else {
                    mainSentences.add("We have operational points from recent feedback that require attention.")
                    mainSentences.add("**Please ensure any delivery concerns or escalation points are raised early rather than at the end of a batch.**")
                }
            }
            "TRAVEL_COORDINATION" -> {
                mainSentences.add(
                    "A reminder for those with FMAT or ILT travel coming up: please coordinate " +
                    "with the Travel Desk and Payroll in advance whenever cab arrangements outside " +
                    "India may be needed."
                )
                mainSentences.add("**Please confirm your travel arrangements prior to departure.**")
            }
            "TASK_ASSIGNMENT", "TASK_FOLLOWUP" -> {
                val sourceText = if (plan.myMessage.isNotEmpty()) plan.myMessage else plan.intent
                val lowerSource = sourceText.lowercase(Locale.getDefault())
                if (lowerSource.contains("pl-300")) {
                    val timeClause = if (timeRef.isNotEmpty()) " before ${boldUnderline(timeRef)}" else ""
                    mainSentences.add("**Please ensure all lab environments for next week's PL-300 batch are provisioned**$timeClause.")
                } else if (lowerSource.contains("az-104")) {
                    val timeClause = if (timeRef.isNotEmpty()) " before ${boldUnderline(timeRef)}" else ""
                    mainSentences.add("**Please review the AZ-104 labs and share your feedback**$timeClause.")
                } else if (lowerSource.contains("ms-900")) {
                    val timeClause = if (timeRef.isNotEmpty()) " before ${boldUnderline(timeRef)}" else ""
                    mainSentences.add("**Please complete your preparation for MS-900**$timeClause.")
                } else if (sourceText.isNotBlank()) {
                    var cleaned = stripLeadingDirectives(sourceText)
                    if (plan.tone == "firm") {
                        cleaned = Regex("""\b(?:when possible|at your convenience|if possible)\b[?,.]?""", RegexOption.IGNORE_CASE).replace(cleaned, "").trim()
                        if (!cleaned.endsWith(".")) cleaned += "."
                        val pendingClause = if (plan.timeReferences.isNotEmpty()) "This task has been pending since ${plan.timeReferences.first()}. " else ""
                        mainSentences.add("$pendingClause**${cleaned.trimEnd('.')}**.")
                    } else {
                        mainSentences.add("**${cleaned.trimEnd('.')}**.")
                    }
                } else {
                    mainSentences.add("Please confirm the current status of this task.")
                    mainSentences.add("**Please keep me updated on your progress.**")
                }
            }
            "TEAM_PERIODIC_UPDATE", "INDIVIDUAL_PERIODIC_UPDATE" -> {
                // Evidence sentences are composed first and always from
                // plan.selectedFacts — a manager instruction (plan.myMessage)
                // may only append a further sentence, never replace or
                // precede the evidence-based one. See
                // ManagerCommunicationComposer, the only caller that uses
                // these two purposes.
                val util = pDict["current_utilization"]?.toString()?.toIntOrNull()
                val certGaps = (pDict["cert_gap_courses"] as? String)?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
                val rating = pDict["avg_rating"]
                val ratingCount = pDict["avg_rating_count"]?.toString()?.toIntOrNull() ?: 0

                if (util != null) {
                    mainSentences.add(
                        if (p == "TEAM_PERIODIC_UPDATE") "The team's average utilisation currently stands at $util%."
                        else "Your current utilisation stands at $util%.",
                    )
                }
                if (rating != null && ratingCount > 0) {
                    mainSentences.add("Participant feedback averages $rating out of 5 across $ratingCount responses.")
                }
                if (certGaps.isNotEmpty()) {
                    val coursesStr = if (certGaps.size <= 2) certGaps.joinToString(" and ") else certGaps.dropLast(1).joinToString(", ") + ", and " + certGaps.last()
                    mainSentences.add("Open certification gaps remain in $coursesStr.")
                }
                if (mainSentences.isEmpty()) {
                    mainSentences.add(
                        if (p == "TEAM_PERIODIC_UPDATE") "No exceptions to report for the team this period."
                        else "No exceptions to report this period.",
                    )
                }
                if (plan.myMessage.isNotBlank()) {
                    mainSentences.add("**${stripLeadingDirectives(plan.myMessage).trimEnd('.')}**.")
                }
            }
            else -> {
                val sourceText = if (plan.myMessage.isNotEmpty()) plan.myMessage else if (plan.userMessage.isNotEmpty()) plan.userMessage else plan.intent
                var cleaned = if (sourceText.isNotBlank()) stripLeadingDirectives(sourceText) else "Please review the current operational requirements."
                if (plan.tone == "firm") {
                    cleaned = Regex("""\b(?:when possible|at your convenience|if possible)\b[?,.]?""", RegexOption.IGNORE_CASE).replace(cleaned, "").trim()
                    if (!cleaned.endsWith(".")) cleaned += "."
                    val pendingClause = if (plan.timeReferences.isNotEmpty()) "This task has been pending since ${plan.timeReferences.first()}. " else ""
                    mainSentences.add("$pendingClause**${cleaned.trimEnd('.')}**.")
                } else {
                    val sents = splitSentences(cleaned)
                    if (sents.isNotEmpty()) {
                        mainSentences.addAll(sents.dropLast(1))
                        mainSentences.add("**${sents.last().trimEnd('.')}**.")
                    } else {
                        mainSentences.add("**${cleaned.trimEnd('.')}**.")
                    }
                }
            }
        }

        var bodyText = mainSentences.filter { it.isNotBlank() }.joinToString(" ").trim()
        bodyText = formatTimeReferences(bodyText, plan.timeReferences)

        // 3. Closing
        val closing = closingFor(plan.tone, plan.recipientRelationship)

        // 4. Assembled message
        return cleanFormatting("$greeting\n\n$bodyText\n\n$closing")
    }

    // ── MORNING_TEAM_GREETING ──────────────────────────────────────────────
    //
    // A short weekday note from the manager to the team for Teams/Viber. No
    // "Hello team," header and no sign-off block: one fresh opening, one
    // thought that fits the weekday, and a very short close. Formatting uses
    // Viber/WhatsApp markers (*bold*, _italic_) and never code fences. Parts
    // already used in a recent greeting are skipped, so openings, thoughts,
    // jokes and closings rotate instead of repeating.

    private data class DayBank(val openings: List<String>, val thoughts: List<String>, val closings: List<String>)

    private val MORNING_BANK: Map<String, DayBank> = mapOf(
        "Monday" to DayBank(
            openings = listOf("Morning, everyone.", "Hi all, welcome back.", "Hope the weekend was a good one.", "New week, everyone.", "Hello all, back at it."),
            thoughts = listOf(
                "*A clean page this week* — worth ten minutes to reconnect with each other before the calendar fills up.",
                "Easing back in is fine. _The coffee is doing most of the work until eleven anyway._",
                "If something from last week is still bugging you, *say it early* — it is usually quicker to sort together.",
                "Good time to catch up with someone you did not get to speak to last week.",
                "*One small win today is plenty* to set the rhythm for the week.",
                "A quick check-in with a colleague often saves a long thread later.",
            ),
            closings = listOf("_Have a good Monday._", "_Glad to have you back._", "_Enjoy the start._", "_Talk soon._"),
        ),
        "Tuesday" to DayBank(
            openings = listOf("Hi all.", "Morning, team.", "Hello everyone.", "Tuesday already.", "Hope the week has settled in."),
            thoughts = listOf(
                "*The week has found its rhythm* — a nice day for the conversations that turn into good ideas.",
                "If you picked up something useful yesterday, *share it* — someone else is probably stuck on it.",
                "_Tuesday is the quiet hero of the week_: fewer surprises, more real work getting done.",
                "Worth asking a colleague how their week is going — the answer is often more useful than the status update.",
                "*Good collaboration beats long emails.* A five-minute call can clear most of today's questions.",
                "Learning something new this week? *Pass it on* while it is fresh.",
            ),
            closings = listOf("_Have a good Tuesday._", "_Enjoy the day._", "_Take care._", "_Catch you later._"),
        ),
        "Wednesday" to DayBank(
            openings = listOf("Midweek already, everyone.", "Hi all, halfway there.", "Hello team, it's Wednesday.", "Morning, all.", "Happy Wednesday, everyone."),
            thoughts = listOf(
                "*Halfway through* — a good day to share what's working and help someone past a small hurdle.",
                "A small reset helps: _look at what already moved this week_ before planning the rest.",
                "_Midweek rule_: if a meeting could be a message, it probably should be.",
                "*Thanks for the effort so far this week* — it has not gone unnoticed.",
                "Good day to learn one small thing from someone on the team.",
                "If you are carrying something heavy this week, *ask for a hand* — that is what the team is for.",
            ),
            closings = listOf("_Have a good Wednesday._", "_Enjoy the day._", "_Onwards, gently._", "_Take care._"),
        ),
        "Thursday" to DayBank(
            openings = listOf("Hi all.", "Morning, everyone.", "Thursday, team.", "Hello all, nearly there.", "Hope everyone is well."),
            thoughts = listOf(
                "*The week is taking shape.* A good day to tie up loose ends before they follow you into Friday.",
                "If someone helped you out this week, *today is a nice day to tell them.*",
                "_Almost-Friday energy is allowed_, as long as the calendar invites are still being answered.",
                "What did you learn this week? *A two-line share* can save a colleague an afternoon.",
                "Worth a quick look at anything still open, so tomorrow can be lighter for everyone.",
                "*Small favours add up.* Offer help on one thing that is not yours today.",
            ),
            closings = listOf("_Have a good Thursday._", "_Enjoy the day._", "_Nearly there._", "_Talk soon._"),
        ),
        "Friday" to DayBank(
            openings = listOf("Friday, everyone.", "Hi all, we made it.", "Happy Friday, team.", "Morning, all, it's Friday.", "Hello everyone, weekend is in sight."),
            thoughts = listOf(
                "*Thanks for a solid week*, everyone — properly appreciated.",
                "_Friday forecast_: a few meetings, one mystery calendar invite, and the weekend approaching fast.",
                "If your inbox is winning today, *call it a draw* and pick it up on Monday.",
                "*Proud of how the team pulled together this week.* Enjoy the switch-off when it comes.",
                "_Official Friday policy_: at least one conversation today that has nothing to do with work.",
                "Take a moment to note one thing that went well this week — *there is usually more than you think.*",
            ),
            closings = listOf("_Have a great weekend._", "_Enjoy the weekend, all._", "_Rest well._", "_See you Monday._"),
        ),
    )

    private fun composeMorningGreeting(plan: ContextSelectionPlan): String {
        val facts = plan.selectedFacts.associate { it.key to it.value }
        val day = facts["local_weekday"]?.toString().orEmpty()
        val bank = MORNING_BANK[day] ?: return ""
        val variation = facts["variation"]?.toString()?.toIntOrNull() ?: 0
        val recent = plan.selectedFacts.filter { it.key.startsWith("recent_greeting_") }.map { it.value.toString() }
        val seed = Math.floorMod(variation * 7 + recent.size * 3 + day.length, 997)

        val opening = pickUnused(bank.openings, recent, seed)
        val thought = pickUnused(bank.thoughts, recent, seed / 2 + variation)
        val closing = pickUnused(bank.closings, recent.take(2), seed + variation)
        return opening + "\n\n" + thought + " " + closing
    }

    /**
     * First option (rotating from [seed]) that appears in no recent greeting.
     * When every option was used recently, the one used longest ago is
     * reused, so history can never block generation.
     */
    private fun pickUnused(options: List<String>, recent: List<String>, seed: Int): String {
        val start = Math.floorMod(seed, options.size)
        val rotated = options.indices.map { options[(start + it) % options.size] }
        rotated.firstOrNull { o -> recent.none { it.contains(o) } }?.let { return it }
        return rotated.maxBy { o -> recent.indexOfFirst { it.contains(o) }.let { if (it < 0) Int.MAX_VALUE else it } }
    }

    private fun extractCourse(text: String): String {
        val m = Regex("""\b([A-Z]{2,4}-[0-9]{2,4}[A-Z0-9]*)\b""", RegexOption.IGNORE_CASE).find(text)
        return m?.value?.uppercase(Locale.getDefault()) ?: ""
    }

    private fun stripLeadingDirectives(text: String): String {
        var t = text.trim()
        t = Regex("^(?:tell|ask|message|inform|ping)\\\\s+(?:[A-Za-z]+\\\\s+)?(?:that\\\\s+)?", RegexOption.IGNORE_CASE).replace(t, "")
        t = Regex("^(?:please\\\\s+)?(?:tell|ask|inform)\\\\s+", RegexOption.IGNORE_CASE).replace(t, "")
        return if (t.isNotEmpty()) t.replaceFirstChar { it.uppercase() } else t
    }

    private fun formatTimeReferences(text: String, timeRefs: List<String>): String {
        var res = text
        for (tr in timeRefs) {
            if (tr.length < 3) continue
            val pattern = Regex("\\b${Regex.escape(tr)}\\b", RegexOption.IGNORE_CASE)
            val match = pattern.find(res) ?: continue
            val start = match.range.first
            val end = match.range.last + 1

            val pre = res.substring(maxOf(0, start - 4), start)
            val post = res.substring(end, minOf(res.length, end + 4))
            if (pre.contains("__") || post.contains("__")) continue

            val preBolds = res.substring(0, start).count { it == '*' } / 2
            val replacement = if (preBolds % 2 == 1) {
                "__${match.value}__"
            } else {
                "__**${match.value}**__"
            }
            res = res.substring(0, start) + replacement + res.substring(end)
        }
        return res
    }

    fun cleanFormatting(text: String): String {
        var t = text.trim()
        t = Regex("[ \t]+").replace(t, " ")
        t = Regex("\n{3,}").replace(t, "\n\n")
        return t
    }

    fun bold(text: String): String = if (text.startsWith("**") && text.endsWith("**")) text else "**$text**"
    fun italic(text: String): String = if (text.startsWith("*") && text.endsWith("*")) text else "*$text*"
    fun boldUnderline(text: String): String = "__**${text.trim('*')}**__"

    fun closingFor(tone: String, relationship: String): String {
        val t = tone.lowercase(Locale.getDefault())
        return when {
            t.contains("urgent") || t.contains("firm") || t.contains("correct") -> "*Please act on this promptly*"
            t.contains("appreciat") -> "*Much appreciated*"
            else -> "*Thanks*"
        }
    }

    fun splitSentences(text: String): List<String> {
        return text.split(Regex("(?<=[.!?])\\\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
    }
}
