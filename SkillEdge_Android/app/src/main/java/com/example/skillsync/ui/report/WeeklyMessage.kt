package com.example.skillsync.ui.report

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Composes the weekly message a manager copies into MS Teams or Viber.
 *
 * The house style is fixed and enforced mechanically rather than by hand, so a
 * message cannot drift out of it:
 *
 *  - a greeting on its own line, the body on a new line, a closing on a new line
 *  - simple professional English, complete sentences, full word forms, so no
 *    contractions
 *  - no emojis, hyphens, bullet points or decorative symbols
 *  - formatting used sparingly: italics only for a name where clarity needs it,
 *    bold only for the action or expectation being set
 *  - never longer than 1000 characters
 *
 * Tone is chosen from the data, not asked for: an at-risk trainer gets a firm
 * message, a stretched one gets an appreciative and protective message, a
 * benched one gets an advisory message, and a clean week gets thanks. That is
 * the whole point of composing from the intelligence rather than opening an
 * empty box.
 *
 * These functions are deliberately pure so the rules above are unit-testable
 * without a device.
 */

const val MESSAGE_LIMIT = 1000

/**
 * How the copied text is marked up.
 *
 * Teams renders `**bold**` and `_italic_` on paste; Viber renders neither and
 * would show the markers literally, so [PLAIN] emits none.
 *
 * Neither target supports underline in pasted text. The house style reserves
 * underline for dates and deadlines, so rather than emit markers that would
 * paste as stray characters, time references are written out in words and left
 * unmarked. That is a limitation of the destination, not a style choice.
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

// ── Team message ────────────────────────────────────────────────────────────

fun composeTeamMessage(
    signals: TeamSignals,
    style: MessageStyle = MessageStyle.TEAMS,
    today: LocalDate = LocalDate.now(),
    /**
     * The manager's own words, the "My Message" input. When present it is the
     * primary statement of intent and leads the body; the generated summary
     * follows as supporting context rather than replacing what they wanted to
     * say. It is sanitised to the same house style as everything else.
     */
    managerNote: String = "",
): String {
    val body = StringBuilder()

    val formattedNote = formatManagerNote(managerNote)
    if (formattedNote.isNotEmpty()) body.append("$formattedNote ")

    // The single most important thing first, chosen by severity. The team
    // already knows how many they are, so no headcount line. Utilisation is
    // only surfaced when it is directionally useful (stretched team).
    when {
        signals.atRisk > 0 -> {
            body.append(
                "${count(signals.atRisk, "colleague is", "colleagues are")} carrying a delivery risk flag this week, " +
                    "and I will be speaking to each of them individually. "
            )
            body.append(bold("Please raise any delivery concern early rather than at the end of a batch.", style))
            body.append(" ")
        }
        signals.unallocated > 0 -> {
            body.append(
                "We have ${count(signals.unallocated, "unallocated batch", "unallocated batches")} on the desk right now"
            )
            if (signals.international > 0) {
                body.append(
                    ", including ${count(signals.international, "international batch", "international batches")}"
                )
            }
            body.append(". ")
            if (signals.free > 0) {
                body.append("${count(signals.free, "of you is", "of you are")} available. ")
            }
            body.append(
                bold("Please check the demand board and confirm your availability to me by ${nextAvailabilityDeadline(today)}.", style)
            )
            body.append(" ")
        }
        signals.certGaps > 0 -> {
            body.append(
                "We are carrying ${count(signals.certGaps, "open certification gap", "open certification gaps")} across the team. "
            )
            body.append(bold("Please book your pending certification before the end of this month.", style))
            body.append(" ")
        }
        else -> {
            body.append("Nothing is outstanding on allocation or certification, which is a good place to be. ")
            signals.utilisation?.let { u ->
                if (u > 70) body.append("The team is carrying a strong load at $u percent utilisation. ")
            }
        }
    }

    // Secondary: cert gaps when the primary item was unallocated demand.
    if (signals.certGaps > 0 && signals.atRisk == 0 && signals.unallocated > 0) {
        body.append(
            "There ${if (signals.certGaps == 1) "is also" else "are also"} " +
                "${count(signals.certGaps, "certification gap", "certification gaps")} to close across the team. "
        )
    }

    val closing = when {
        signals.atRisk > 0 -> "Thank you for staying on top of this."
        signals.unallocated > 0 -> "Thank you for the quick turnaround."
        else -> "Thank you all for a steady week."
    }

    return assemble("Hello team", body.toString(), closing, style)
}

// ── Reportee message ────────────────────────────────────────────────────────

fun composeReporteeMessage(
    signals: ReporteeSignals,
    style: MessageStyle = MessageStyle.TEAMS,
    today: LocalDate = LocalDate.now(),
    /** See [composeTeamMessage]; the manager's own words lead the body. */
    managerNote: String = "",
): String {
    val first = signals.name.trim().substringBefore(" ").ifBlank { "there" }
    val body = StringBuilder()

    val formattedNote = formatManagerNote(managerNote)
    if (formattedNote.isNotEmpty()) body.append("$formattedNote ")
    body.append("Here is a quick summary for you this week. ")

    // What the manager actually needs to say, in severity order. Only one
    // primary point per message: a note that raises four issues at once reads
    // as a list and gets actioned as none of them.
    when {
        signals.feedbackRisk.equals("High", true) -> {
            body.append("Your recent delivery feedback has been flagged for review. ")
            body.append(bold("Please set up time with me this week so we can go through it together and agree the support you need.", style))
            body.append(" I would rather we address this early than let it carry into your next batch. ")
        }
        signals.certGaps > 0 -> {
            val courses = signals.certGapCourses.takeIf { it.isNotEmpty() }
            if (courses != null) {
                val courseNames = when (courses.size) {
                    1 -> courses[0]
                    2 -> "${courses[0]} and ${courses[1]}"
                    else -> courses.dropLast(1).joinToString(", ") + ", and ${courses.last()}"
                }
                body.append("You are currently delivering $courseNames without the matching certification on record. ")
            } else {
                body.append("You are teaching ${if (signals.certGaps == 1) "a course" else "courses"} where the matching certification is not yet on record. ")
            }
            val certWord = if (signals.certGaps == 1) "certification" else "certifications"
            body.append(bold("Please book the $certWord and share the schedule with me by Friday.", style))
            body.append(" This is the main thing standing between you and the accredited work coming in. ")
        }
        signals.capacityBucket.equals("Stretched", true) -> {
            body.append("You have carried a heavy load this week")
            signals.utilisation?.let { body.append(" at $it percent utilisation") }
            body.append(", and that is appreciated. ")
            body.append(bold("Please tell me before you take on anything further so I can rebalance rather than overload you.", style))
            body.append(" ")
        }
        signals.capacityBucket.equals("On Bench", true) -> {
            body.append("You are available this week")
            signals.utilisation?.let { body.append(" at $it percent utilisation") }
            body.append(". ")
            if (signals.targetGrowthCourses.isNotEmpty()) {
                val courses = signals.targetGrowthCourses.joinToString(" and ")
                body.append("Our peer delivery data shows strong active client demand in $courses. ")
                body.append(bold("Please prepare for $courses so I can match you to the open batches.", style))
            } else if (signals.peerBenchmarkNote.isNotBlank()) {
                body.append("${signals.peerBenchmarkNote} ")
                body.append(bold("Please send me your upskilling plan by Friday so we can align you to incoming demand.", style))
            } else {
                body.append(bold("Please send me the two courses you would most like to pick up next, and I will match you to the incoming demand.", style))
            }
            body.append(" ")
        }
        signals.openActions > 0 -> {
            body.append("There ")
            body.append(if (signals.openActions == 1) "is one open item " else "are ${signals.openActions} open items ")
            body.append("against your name. ")
            body.append(bold("Please close them or tell me what is blocking you by the end of this week.", style))
            body.append(" ")
        }
        else -> {
            body.append("Everything looks in order")
            signals.utilisation?.let { body.append(" and your utilisation is at $it percent") }
            body.append(". ")
            body.append("There is nothing outstanding from my side. ")
        }
    }

    // Context, only where it genuinely helps the reader place the message.
    if (signals.currentCourse.isNotBlank()) {
        body.append("You are currently on ${signals.currentCourse}. ")
    }
    if (signals.nextCourse.isNotBlank()) {
        body.append("Your next confirmed batch is ${signals.nextCourse}. ")
    }

    val closing = when {
        signals.feedbackRisk.equals("High", true) -> "I am confident we can turn this around together."
        signals.capacityBucket.equals("Stretched", true) -> "Thank you for the effort this week."
        signals.certGaps > 0 -> "Thank you for prioritising this."
        else -> "Thank you for your work this week."
    }

    return assemble("Hello ${italic(first, style)}", body.toString(), closing, style)
}

/**
 * Composes an explicit "Where You Stand" weekly managerial evaluation note.
 * Evidence-only: every line derives from real RMS signals (utilisation,
 * learner rating, cert gaps, HR/negative feedback). No generic
 * "pacing & articulation" boilerplate. Bullet-free to meet the Teams/Viber
 * house style (no bullets, hyphens or decorative symbols).
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

// ── House style ─────────────────────────────────────────────────────────────

private fun assemble(
    greeting: String,
    body: String,
    closing: String,
    style: MessageStyle = MessageStyle.PLAIN,
): String {
    // The closing carries light emphasis, as the house style asks. Bold stays
    // reserved for the single action being set, so italics here cannot compete
    // with it for attention.
    val signed = sanitise(closing).trim()
    val text = buildString {
        append(greeting.trim())
        append("\n\n")
        append(sanitise(body).trim())
        append("\n\n")
        append(if (style == MessageStyle.TEAMS && signed.isNotEmpty()) "_${signed}_" else signed)
    }
    return trimToLimit(text)
}

/**
 * Enforces the parts of the style that are mechanical: no hyphens, bullets,
 * emojis or decorative symbols, no contractions, and single spacing.
 *
 * Doing this in one place means a new message variant cannot quietly break the
 * house style by writing "don't" or an em dash.
 */
internal fun sanitise(raw: String): String {
    var s = raw

    // Course and exam codes are identifiers, not punctuation. Stripping the
    // hyphen out of AZ-305 or PL-300 renames the course, so they are held aside
    // while the prose rules run and put back afterwards.
    val codes = mutableListOf<String>()
    s = COURSE_CODE.replace(s) { match ->
        codes += match.value
        "\u0001${codes.size - 1}\u0001"
    }

    // Full word forms only.
    val contractions = mapOf(
        "don't" to "do not", "won't" to "will not", "can't" to "cannot",
        "isn't" to "is not", "aren't" to "are not", "doesn't" to "does not",
        "didn't" to "did not", "haven't" to "have not", "hasn't" to "has not",
        "wouldn't" to "would not", "shouldn't" to "should not", "couldn't" to "could not",
        "it's" to "it is", "we're" to "we are", "you're" to "you are",
        "I'm" to "I am", "we'll" to "we will", "you'll" to "you will",
        "let's" to "let us", "that's" to "that is", "there's" to "there is",
    )
    contractions.forEach { (short, long) ->
        // Case-preserving: a plain ignoreCase replace turned the manager's
        // "Don't worry" into "do not worry", dropping the capital at the start
        // of their own sentence. Their words are quoted back to their team, so
        // the expansion must not make them look careless.
        s = Regex(Regex.escape(short), RegexOption.IGNORE_CASE).replace(s) { m ->
            if (m.value.first().isUpperCase()) long.replaceFirstChar { c -> c.uppercase() } else long
        }
    }

    // Hyphens, dashes and bullets are out entirely; a hyphen becomes a space so
    // "follow-up" degrades to "follow up" rather than "followup".
    s = s.replace(Regex("[\\u2010-\\u2015]"), " ")   // dash family
        .replace("-", " ")
        .replace(Regex("[•·▪●◦*]"), "")
        // Emoji and pictographs.
        .replace(Regex("[\\p{So}\\p{Cn}]"), "")

    // Collapse the whitespace the substitutions above leave behind, without
    // touching the paragraph breaks the layout depends on.
    s = s.split("\n").joinToString("\n") { line ->
        line.replace(Regex("[ \\t]+"), " ").trim()
    }

    codes.forEachIndexed { i, code -> s = s.replace("\u0001$i\u0001", code) }
    return s
}

/**
 * Applies house style string formatting to the manager's note.
 * Ensures basic professional English grammar (capitalization, trimming, punctuation fixes) 
 * to integrate nicely into the generated message.
 */
internal fun formatManagerNote(raw: String): String {
    if (raw.isBlank()) return ""
    // 1. Trim and apply basic sanitisation
    val s = sanitise(raw).trim()
    
    // 2. Fix capitalization for the first letter of each sentence.
    // We split by standard sentence terminators (. ! ?)
    val regex = Regex("([.!?])\\s*")
    val parts = s.split(regex)
    val terminators = Regex("([.!?])").findAll(s).map { it.value }.toList()
    
    val formatted = StringBuilder()
    for (i in parts.indices) {
        if (parts[i].isNotBlank()) {
            val capitalized = parts[i].trim().replaceFirstChar { 
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() 
            }
            formatted.append(capitalized)
            if (i < terminators.size) {
                formatted.append(terminators[i]).append(" ")
            }
        }
    }
    
    var result = formatted.toString().trim()
    
    // 3. Ensure it ends with a period if it doesn't end with punctuation
    if (result.isNotEmpty() && !result.endsWith(".") && !result.endsWith("!") && !result.endsWith("?")) {
        result += "."
    }
    
    return result
}

/** Course and exam identifiers such as AZ-305, PL-300, MS-700, SC-200. */
private val COURSE_CODE = Regex("\\b[A-Z]{2,4}-[0-9]{2,4}\\b")

/** Never exceed the limit, and never cut a sentence in half doing it. */
internal fun trimToLimit(text: String, limit: Int = MESSAGE_LIMIT): String {
    if (text.length <= limit) return text

    val parts = text.split("\n\n")
    if (parts.size < 3) return text.take(limit)
    val greeting = parts.first()
    val closing = parts.last()
    var body = parts.subList(1, parts.size - 1).joinToString("\n\n")

    val overhead = greeting.length + closing.length + 4
    val room = limit - overhead
    if (room <= 0) return text.take(limit)

    if (body.length > room) {
        // Drop whole trailing sentences until it fits.
        val sentences = body.split(". ").filter { it.isNotBlank() }
        val kept = StringBuilder()
        for (sentence in sentences) {
            val candidate = if (kept.isEmpty()) sentence else "${kept}. $sentence"
            if (candidate.length + 1 > room) break
            kept.setLength(0)
            kept.append(candidate)
        }
        body = kept.toString().trimEnd('.', ' ') + "."
    }
    return "$greeting\n\n$body\n\n$closing"
}

private fun bold(text: String, style: MessageStyle) =
    if (style == MessageStyle.TEAMS) "**$text**" else text

private fun italic(text: String, style: MessageStyle) =
    if (style == MessageStyle.TEAMS) "_${text}_" else text

private fun count(n: Int, singular: String, plural: String) =
    if (n == 1) "$n $singular" else "$n $plural"

/** Mon/Tue → "Wednesday", Wed/Thu → "Friday", Fri/Sat/Sun → "Monday". */
private fun nextAvailabilityDeadline(today: LocalDate): String = when (today.dayOfWeek) {
    java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.TUESDAY -> "Wednesday"
    java.time.DayOfWeek.WEDNESDAY, java.time.DayOfWeek.THURSDAY -> "Friday"
    else -> "Monday"
}

/**
 * "11 August to 17 August" — written out, because the house style forbids
 * hyphens and every numeric date range wants one.
 */
internal fun weekReference(today: LocalDate): String {
    val monday = today.minusDays(((today.dayOfWeek.value + 6) % 7).toLong())
    val sunday = monday.plusDays(6)
    val fmt = DateTimeFormatter.ofPattern("d MMMM", Locale.UK)
    return "${monday.format(fmt)} to ${sunday.format(fmt)}"
}
