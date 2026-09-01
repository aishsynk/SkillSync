package com.example.skillsync.ui.batch

/**
 * Builds a single team-facing message that lists **all** unallocated batches,
 * so a manager can share the whole pipeline from "This Week" in one tap.
 *
 * House style is the same as [BatchShare]:
 *  - greeting on its own line, body next, closing last
 *  - complete sentences and full word forms; no contractions
 *  - no emojis, bullets, decorative symbols or dashes as separators
 *  - italics only for the course name, bold only for the one action,
 *    underline only for dates/times/deadlines
 *  - emphasis never stacked
 *  - each batch is a numbered prose line, not a table
 *
 * Individual-batch sharing already lives in [BatchShare]; this object is the
 * bulk counterpart. Clip/share helpers are intentionally reused from
 * [BatchShare] so the two surfaces never diverge on delivery.
 */
object BulkBatchShare {

    private const val MAX_CHARS = 3500
    private const val MAX_LINES_SHOWN = 25

    private fun greetingName(recipient: String): String =
        recipient.split(" ").firstOrNull { it.isNotBlank() } ?: "Team"

    private fun window(b: BatchShare.Batch): String = when {
        b.startDate.isBlank() -> "on dates still to be confirmed"
        b.endDate.isBlank() || b.endDate == b.startDate -> "on ${b.startDate}"
        else -> "from ${b.startDate} to ${b.endDate}"
    }

    /** One numbered prose line for a single batch inside the bulk list. */
    private fun entryLine(
        index: Int,
        b: BatchShare.Batch,
        italic: (String) -> String,
        underline: (String) -> String,
    ): String = buildString {
        append("$index. ")
        append(italic(b.courseName.ifBlank { "Unnamed course" }))
        append(" ")
        append(underline(window(b)))
        if (b.sessionTime.isNotBlank()) append(", ${underline(b.sessionTime)}")
        // Compact inline facts — keep each line to one sentence of qualifiers.
        val extras = mutableListOf<String>()
        if (b.deliveryMode.isNotBlank()) extras.add(b.deliveryMode)
        if (b.language.isNotBlank()) extras.add("Language ${b.language}")
        if (b.participants.isNotBlank() && b.participants != "0") extras.add("${b.participants} pax")
        if (b.location.isNotBlank()) extras.add(b.location)
        if (b.vendor.isNotBlank()) extras.add(b.vendor)
        if (extras.isNotEmpty()) append(", ${extras.joinToString(", ")}")
        if (b.reference.isNotBlank()) append(" (Ref ${b.reference})")
        append(".")
    }

    /**
     * Viber and WhatsApp read `*bold*` and `_italic_`; neither has underline, so
     * time references are left plain rather than wrapped in markers that would
     * show as literal punctuation.
     */
    fun composeBulkMessage(
        batches: List<BatchShare.Batch>,
        recipient: String = "Team",
        maxSkillLevel: Int = 4,
        respondBy: String = "end of day",
    ): String = buildBulk(
        batches, recipient, maxSkillLevel, respondBy,
        bold = { "*$it*" }, italic = { "_${it}_" }, underline = { it }
    )

    /** Plain text with no markers — for targets where markup would be noise. */
    fun plainBulkMessage(
        batches: List<BatchShare.Batch>,
        recipient: String = "Team",
        maxSkillLevel: Int = 4,
        respondBy: String = "end of day",
    ): String = buildBulk(
        batches, recipient, maxSkillLevel, respondBy,
        bold = { it }, italic = { it }, underline = { it }
    )

    /** HTML for the clipboard, so Teams and Outlook keep all three styles. */
    fun htmlBulkMessage(
        batches: List<BatchShare.Batch>,
        recipient: String = "Team",
        maxSkillLevel: Int = 4,
        respondBy: String = "end of day",
    ): String = buildBulk(
        batches, recipient, maxSkillLevel, respondBy,
        bold = { "<b>$it</b>" }, italic = { "<i>$it</i>" }, underline = { "<u>$it</u>" }
    ).replace("\n", "<br>")

    private fun buildBulk(
        batches: List<BatchShare.Batch>,
        recipient: String,
        maxSkillLevel: Int,
        respondBy: String,
        bold: (String) -> String,
        italic: (String) -> String,
        underline: (String) -> String,
    ): String {
        val raw = buildString {
            appendLine("Hello ${greetingName(recipient)},")
            appendLine()
            if (batches.isEmpty()) {
                append("There are no batches open for allocation at this time. ")
                append("I will share new demand as soon as it arrives.")
                appendLine()
                appendLine()
                append(italic("Thank you."))
                return@buildString
            }
            val n = batches.size
            append("There ${if (n == 1) "is" else "are"} $n batch${if (n == 1) "" else "es"} open for allocation. ")
            append("The details are below. Please review them and let me know which ones you can cover.")
            appendLine()
            appendLine()
            val shown = batches.take(MAX_LINES_SHOWN)
            shown.forEachIndexed { idx, b ->
                appendLine(entryLine(idx + 1, b, italic, underline))
            }
            if (batches.size > MAX_LINES_SHOWN) {
                appendLine("and ${batches.size - MAX_LINES_SHOWN} more. Please check the Demand Desk for the full list.")
            }
            appendLine()
            append("If you can take any of these, please ")
            append(bold("mark your skill in RMS at level $maxSkillLevel or below"))
            append(" and confirm here by ${underline(respondBy)}. ")
            append("If you are not available on these dates, please let me know so it can be offered to someone else.")
            appendLine()
            appendLine()
            append(italic("Thank you."))
        }.trim()

        // Bulk lists can easily exceed the limit. Preserve greeting + closing
        // and trim the middle rather than cutting the closing.
        if (raw.length <= MAX_CHARS) return raw
        val parts = raw.split("\n\n")
        if (parts.size < 4) {
            val trimmed = raw.take(MAX_CHARS)
            return trimmed.substringBeforeLast(" ").ifBlank { trimmed } + "."
        }
        val greeting = parts.first()
        val closing = parts.last()
        val action = parts[parts.size - 2]
        val batchPart = parts.subList(1, parts.size - 2).joinToString("\n\n")

        val overhead = greeting.length + closing.length + action.length + 6
        val room = MAX_CHARS - overhead
        if (room <= 0) return raw.take(MAX_CHARS)

        val batchLines = batchPart.split("\n")
        val intro = batchLines.firstOrNull() ?: ""
        val remainingLines = if (batchLines.size > 1) batchLines.drop(1) else emptyList()

        val kept = mutableListOf<String>()
        if (intro.isNotBlank()) kept.add(intro)
        var cur = if (intro.isNotBlank()) intro.length + 1 else 0

        val reservationForNote = 80 // room for "and XX more. Please check..."
        for (line in remainingLines) {
            if (line.startsWith("and ") && line.contains("more")) continue
            if (cur + line.length + 1 + reservationForNote > room) break
            kept.add(line)
            cur += line.length + 1
        }
        val batchesShown = kept.count { it.matches(Regex("^\\d+\\. .*")) }
        val batchesTrimmed = batches.size - batchesShown
        if (batchesTrimmed > 0) {
            kept.add("and $batchesTrimmed more. Please check the Demand Desk for the full list.")
        }
        val trimmedBatchPart = kept.joinToString("\n")
        return "$greeting\n\n$trimmedBatchPart\n\n$action\n\n$closing".trim()
    }
}
