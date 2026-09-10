package com.example.skillsync.feature.communication.engine

fun validate(message: String): ValidationResult {
    val text = message.trim()
    if (text.isEmpty()) return ValidationResult(false, listOf("empty message"))

    val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    val issues = mutableListOf<String>()

    if (text.length > MAX_LENGTH) issues.add("over $MAX_LENGTH characters (${text.length})")
    if (lines.isEmpty()) {
        issues.add("no content")
    } else if (Regex("^(hello|hi|dear|good morning|good afternoon|team[,:]|.+,$)", RegexOption.IGNORE_CASE).find(lines[0]) == null) {
        issues.add("missing greeting on the first line")
    }
    if (hasEmoji(text)) issues.add("contains emojis")
    if (hasBulletList(text)) issues.add("contains a bullet list")
    if (hasNumberedList(text)) issues.add("contains a numbered list")
    if (lines.size >= 2) {
        val last = lines.last()
        val okClosing = CLOSINGS.any { last.equals(it, ignoreCase = true) } ||
            Regex("""^\*[A-Za-z][A-Za-z ,]*\*$""").matches(last)
        if (!okClosing) issues.add("missing closing")
    }
    val greets = lines.filter { Regex("^(hello|hi|dear)", RegexOption.IGNORE_CASE).find(it) != null }
    if (greets.size > 1) issues.add("duplicate greeting")
    val closings = lines.filter { Regex("""^\*[A-Za-z][A-Za-z ,]*\*$""").matches(it) }
    if (closings.size > 1) issues.add("duplicate closing")

    return ValidationResult(issues.isEmpty(), issues)
}

fun truncate(message: String, limit: Int = MAX_LENGTH): String {
    val text = message.trim()
    if (text.length <= limit) return text
    val cut = text.take(limit)
    var best = -1
    for (splitter in listOf(". ", "! ", "? ")) best = maxOf(best, cut.lastIndexOf(splitter))
    if (best > limit / 2) {
        var head = cut.substring(0, best + 2)
        for (marker in listOf("**", "*")) {
            if ((head.split(marker).size - 1) % 2 != 0) {
                val idx = head.lastIndexOf(marker)
                if (idx > limit / 2) head = head.substring(0, idx)
            }
        }
        return head.trim()
    }
    return cut
}