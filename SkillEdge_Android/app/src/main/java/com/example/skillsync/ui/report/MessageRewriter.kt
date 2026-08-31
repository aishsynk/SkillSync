package com.example.skillsync.ui.report

import java.util.Locale

/**
 * Deterministic rewrite engine for the house-style Teams/Viber contract.
 * Mirrors the backend `backend.py::_compose_rewritten` so offline and online
 * agree exactly.
 *
 * Inputs  [User Message] and/or [My Message]  (at least one)
 * Output  Greeting on one line, body on new line, closing on new line,
 *         ≤1000 chars, no emojis/bullets/hyphens, italics only for names,
 *         bold only for key action, underline only for time refs.
 */
object MessageRewriter {

    private const val LIMIT = 1000
    private val COURSE_CODE = Regex("\\b[A-Z]{2,4}-[0-9]{2,4}\\b")

    private val HINGLISH = mapOf(
        "parso" to "day after tomorrow",
        "parsoon" to "day after tomorrow",
        "jaldi se" to "at the earliest",
        "jaldi" to "at the earliest",
        "thoda" to "a little",
        "zyada" to "more",
        "kar dijiye" to "please do",
        "kar do" to "please do",
        "kr dijiye" to "please do",
        "krdo" to "please do",
        "bhej do" to "please share",
        "bhejdo" to "please share",
        "bhejo" to "please share",
        "chahiye" to "is required",
        "ho jayega" to "will be done",
        "ho gaya" to "is done",
        "karna hai" to "needs to be done",
        "aap" to "you",
        "tum" to "you",
        "haan" to "yes",
        "nahi" to "no",
        "nahin" to "no",
        "kya" to "what",
        "kab" to "when",
        "kahan" to "where",
        "plz" to "please",
        "pls" to "please",
        "sir" to "Sir",
        "mam" to "Ma'am",
        "maam" to "Ma'am",
    )

    private val CONTRACTIONS = mapOf(
        "don't" to "do not", "won't" to "will not", "can't" to "cannot",
        "isn't" to "is not", "aren't" to "are not", "doesn't" to "does not",
        "didn't" to "did not", "haven't" to "have not", "hasn't" to "has not",
        "wouldn't" to "would not", "shouldn't" to "should not", "couldn't" to "could not",
        "it's" to "it is", "we're" to "we are", "you're" to "you are",
        "I'm" to "I am", "we'll" to "we will", "you'll" to "you will",
        "let's" to "let us", "that's" to "that is", "there's" to "there is",
    )

    private val URGENCY = listOf("urgent", "asap", "immediate", "priority", "critical", "at the earliest", "as soon as possible", "eod", "deadline")
    private val FIRM = listOf("must", "should", "need to", "ensure", "make sure", "do not", "strictly", "mandatory", "required", "final", "warning")
    private val APPRECIATIVE = listOf("thank", "thanks", "shukriya", "appreciate", "well done", "great work", "good job")
    private val CORRECTIVE = listOf("feedback", "improvement", "concern", "flag", "issue", "risk", "coaching", "review", "gap")

    data class EvidenceContext(
        val certGapCourses: List<String> = emptyList(),
        val learnerRating: Double? = null,
        val learnerRatingCount: Int = 0,
        val utilisation: Int? = null,
    )

    data class Detected(
        val urgency: String,
        val firmness: String,
        val tone: String,
        val hinglish: Boolean,
        val timeRefs: List<String>,
    )

    fun detectIntent(userMessage: String, myMessage: String): Detected {
        val raw = "$userMessage $myMessage"
        val norm = normalizeHinglish(raw).lowercase(Locale.getDefault())
        val urgency = when {
            URGENCY.any { norm.contains(it) } || raw.contains("!") || raw.lowercase().contains("kal") || raw.lowercase().contains("parso") -> "high"
            listOf("soon", "friday", "monday", "wednesday", "week").any { norm.contains(it) } -> "medium"
            else -> "low"
        }
        val firmness = when {
            FIRM.any { norm.contains(it) } -> "firm"
            listOf("please", "kindly", "request", "could you", "would you").any { norm.contains(it) } -> "soft"
            else -> "neutral"
        }
        val tone = when {
            APPRECIATIVE.any { norm.contains(it) } -> "appreciative"
            CORRECTIVE.any { norm.contains(it) } || norm.contains("gap") -> "corrective"
            urgency == "high" -> "urgent"
            norm.contains("available") || norm.contains("bench") -> "advisory"
            else -> "professional"
        }
        return Detected(
            urgency = urgency,
            firmness = firmness,
            tone = tone,
            hinglish = isHinglish(raw),
            timeRefs = extractTimeRefs(norm),
        )
    }

    fun isHinglish(text: String): Boolean {
        return HINGLISH.keys.any { k ->
            Regex("\\b${Regex.escape(k)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
        }
    }

    fun normalizeHinglish(text: String): String {
        var s = text
        HINGLISH.entries.sortedByDescending { it.key.length }.forEach { (k, v) ->
            s = Regex("\\b${Regex.escape(k)}\\b", RegexOption.IGNORE_CASE).replace(s, v)
        }
        return s
    }

    fun extractTimeRefs(text: String): List<String> {
        val pats = listOf(
            "\\b\\d{1,2}\\s+(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\\b",
            "\\b(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b",
            "\\bnext week\\b", "\\bthis week\\b", "\\btomorrow\\b", "\\btoday\\b",
            "\\bday after tomorrow\\b", "\\bby\\s+(?:monday|tuesday|wednesday|thursday|friday|eod|tomorrow|today)\\b",
            "\\b\\d{1,2}:\\d{2}\\s*(?:am|pm)?\\b",
        )
        val found = mutableListOf<String>()
        pats.forEach { pat ->
            Regex(pat, RegexOption.IGNORE_CASE).findAll(text).forEach { m ->
                val v = m.value.trim()
                if (v.isNotBlank() && found.none { it.equals(v, ignoreCase = true) }) found += v
            }
        }
        return found
    }

    internal fun sanitise(raw: String): String {
        var s = raw
        val codes = mutableListOf<String>()
        s = COURSE_CODE.replace(s) { mr ->
            codes += mr.value
            "\u0001${codes.size - 1}\u0001"
        }
        CONTRACTIONS.forEach { (short, long) ->
            s = Regex(Regex.escape(short), RegexOption.IGNORE_CASE).replace(s) { m ->
                if (m.value.first().isUpperCase()) long.replaceFirstChar { c -> c.uppercase() } else long
            }
        }
        s = s.replace(Regex("[\\u2010-\\u2015]"), " ")
            .replace("-", " ")
            .replace(Regex("[•·▪●◦*]"), "")
            .replace(Regex("[\\p{So}\\p{Cn}]"), "")
        s = s.split("\n").joinToString("\n") { line -> line.replace(Regex("[ \\t]+"), " ").trim() }
        codes.forEachIndexed { i, code -> s = s.replace("\u0001$i\u0001", code) }
        return s.trim()
    }

    internal fun professionalRephrase(text: String): String {
        var s = normalizeHinglish(text)
        s = sanitise(s)
        s = s.replace(Regex("\\s+"), " ").trim()
        if (s.isEmpty()) return ""
        s = Regex("\\b(yaar|bhai|actually|kindly please|please kindly)\\b", RegexOption.IGNORE_CASE).replace(s, "please")
        s = Regex("\\bpls\\b", RegexOption.IGNORE_CASE).replace(s, "please")
        s = s.replace(Regex("\\s+"), " ").trim()
        val parts = s.split(Regex("([.!?])\\s*"))
        // parts includes terminators as separate? Kotlin split with group keeps? Use find
        val sentences = mutableListOf<String>()
        val terminators = Regex("[.!?]").findAll(s).map { it.value }.toList()
        val segs = s.split(Regex("[.!?]\\s*")).filter { it.isNotBlank() }
        segs.forEachIndexed { idx, seg ->
            var t = seg.trim()
            if (t.isEmpty()) return@forEachIndexed
            t = t.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            val term = if (idx < terminators.size) terminators[idx] else "."
            sentences += "$t$term"
        }
        var res = sentences.joinToString(" ")
        res = res.replace(Regex("\\s+([.,!?])"), "$1")
        res = res.replace(Regex("[ \\t]+"), " ").trim()
        if (res.isNotEmpty() && res.last() !in ".!?") res += "."
        return res
    }

    private fun bold(text: String, style: MessageStyle) = if (style == MessageStyle.TEAMS) "**$text**" else text
    private fun italic(text: String, style: MessageStyle) = if (style == MessageStyle.TEAMS) "_${text}_" else text
    private fun underline(text: String, style: MessageStyle) = if (style == MessageStyle.TEAMS) "__${text}__" else text

    private fun trimToLimit(text: String, limit: Int = LIMIT): String {
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
            val sents = body.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
            val kept = StringBuilder()
            for (sent in sents) {
                val cand = if (kept.isEmpty()) sent else "${kept}. $sent"
                if (cand.length + 1 > room) break
                kept.setLength(0)
                kept.append(cand)
            }
            body = kept.toString().trimEnd('.', ' ') + "."
        }
        return "$greeting\n\n$body\n\n$closing"
    }

    /**
     * Core rewrite — mirrors backend `_compose_rewritten`.
     */
    fun compose(
        userMessage: String,
        myMessage: String,
        style: MessageStyle = MessageStyle.TEAMS,
        targetName: String = "",
        isTeam: Boolean = false,
        evidence: EvidenceContext? = null,
    ): String {
        val um = userMessage.trim()
        val mm = myMessage.trim()
        require(um.isNotEmpty() || mm.isNotEmpty()) { "At least one message is required" }
        val intent = detectIntent(um, mm)

        val greeting = if (isTeam) {
            "Hello team,"
        } else {
            val first = targetName.trim().substringBefore(" ").ifBlank { "" }
            if (first.isNotBlank()) {
                if (style == MessageStyle.TEAMS) "Hello ${italic(first, style)}," else "Hello $first,"
            } else "Hello there,"
        }

        val bodySentences = mutableListOf<String>()
        if (um.isNotEmpty() && mm.isNotEmpty()) {
            var topic = ""
            COURSE_CODE.find(um + " " + mm)?.let { topic = it.value }
            if (topic.isEmpty() && Regex("\\b(batch|assignment|delivery|material|course)\\b", RegexOption.IGNORE_CASE).containsMatchIn(um)) topic = "your update"
            val ack = if (topic.isNotEmpty() && topic != "your update") "Thank you for your update on $topic." else if (um.isNotEmpty()) "Thank you for your message." else ""
            if (ack.isNotBlank()) bodySentences += professionalRephrase(ack)
            var core = professionalRephrase(mm)
            if (core.split(" ").size < 4) core = professionalRephrase("$mm Please let me know if you need support")
            bodySentences += core
        } else if (mm.isNotEmpty()) {
            bodySentences += professionalRephrase(mm)
        } else {
            bodySentences += professionalRephrase(um)
        }

        evidence?.let { ev ->
            val evSentence = when {
                ev.certGapCourses.isNotEmpty() -> {
                    val gaps = ev.certGapCourses.take(2).joinToString(", ")
                    "On record you are delivering $gaps without the matching certification."
                }
                ev.learnerRating != null -> "Learners rate you ${ev.learnerRating}/5 from ${ev.learnerRatingCount} responses in the last 90 days."
                ev.utilisation != null && ev.utilisation < 55 -> "Your utilisation is at ${ev.utilisation} percent this week."
                else -> ""
            }
            if (evSentence.isNotBlank()) bodySentences += professionalRephrase(evSentence)
        }

        var body = bodySentences.filter { it.isNotBlank() }.joinToString(" ")

        // bold single key action sentence
        val actionCues = listOf("please", "book", "share", "confirm", "schedule", "ensure", "complete", "send", "prepare", "hold", "review", "let me know")
        val sents = body.split(Regex("(?<=[.!?])\\s+"))
        val bolded = mutableListOf<String>()
        var hasBold = false
        sents.forEach { s ->
            if (!hasBold && actionCues.any { s.lowercase().contains(it) }) {
                bolded += bold(s.trim(), style)
                hasBold = true
            } else bolded += s
        }
        body = bolded.joinToString(" ")

        // underline time refs — avoid nesting inside bold
        val boldSpans = Regex("\\*\\*.*?\\*\\*").findAll(body).map { it.range }.toList()
        intent.timeRefs.take(2).forEach { tr ->
            val m = Regex(Regex.escape(tr), RegexOption.IGNORE_CASE).find(body) ?: return@forEach
            val insideBold = boldSpans.any { m.range.first in it }
            if (!insideBold) {
                body = body.replaceRange(m.range, underline(m.value, style))
            }
        }

        val closingRaw = when (intent.tone) {
            "urgent" -> "Please confirm once done."
            "corrective" -> "Please let me know if you need support."
            "appreciative" -> "Thank you for your continued effort."
            "advisory" -> "Please let me know your plan."
            else -> "Thank you for your attention to this."
        }
        val closing = if (style == MessageStyle.TEAMS) italic(closingRaw, style) else closingRaw

        var assembled = "$greeting\n\n$body\n\n$closing"
        assembled = assembled.replace(Regex("[ \\t]+"), " ").let { txt ->
            txt.split("\n").joinToString("\n") { it.trimEnd() }
        }
        assembled = assembled.replace(Regex("\\n{3,}"), "\n\n").trim()
        return trimToLimit(assembled)
    }
}
