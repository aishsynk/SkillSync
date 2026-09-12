package com.example.skillsync.feature.communication.engine

fun validateFactualIntegrity(text: String, plan: ContextSelectionPlan?): List<String> {
    val issues = mutableListOf<String>()
    if (plan == null || text.trim() == NO_MEANINGFUL_MESSAGE) return issues

    val gtCourses = mutableSetOf<String>()
    val gtNumbers = mutableSetOf<String>()
    val gtDays = mutableSetOf<String>()
    val gtNames = mutableSetOf<String>()

    val courseRegex = Regex("""\b([A-Z]{2,4}-\d{2,4}[A-Z0-9]*)\b""", RegexOption.IGNORE_CASE)
    val dayRegex = Regex("""\b(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)\b""", RegexOption.IGNORE_CASE)
    val numRegex = Regex("""\b(\d+(?:\.\d+)?)\b""")
    val nameRegex = Regex("""\b([A-Z][a-z]+)\b""")

    if (plan.recipientName.isNotBlank()) {
        for (n in plan.recipientName.split(" ")) gtNames.add(n.lowercase())
    }

    for (src in listOf(plan.userMessage, plan.myMessage)) {
        if (src.isNotBlank()) {
            courseRegex.findAll(src).forEach { gtCourses.add(it.groupValues[1].uppercase()) }
            dayRegex.findAll(src).forEach { gtDays.add(it.groupValues[1].replaceFirstChar { c -> c.uppercase() }) }
            numRegex.findAll(src).forEach { gtNumbers.add(it.groupValues[1]) }
            nameRegex.findAll(src).forEach { gtNames.add(it.groupValues[1].lowercase()) }
        }
    }

    for (tr in plan.timeReferences) {
        dayRegex.findAll(tr).forEach { gtDays.add(it.groupValues[1].replaceFirstChar { c -> c.uppercase() }) }
    }

    for (f in plan.selectedFacts) {
        val fVal = f.value.toString()
        courseRegex.findAll(fVal).forEach { gtCourses.add(it.groupValues[1].uppercase()) }
        numRegex.findAll(fVal).forEach { gtNumbers.add(it.groupValues[1]) }
        nameRegex.findAll(fVal).forEach { gtNames.add(it.groupValues[1].lowercase()) }
    }

    // 1. Course codes check
    courseRegex.findAll(text).forEach { m ->
        val c = m.groupValues[1]
        if (!gtCourses.contains(c.uppercase())) {
            issues.add("Factual violation: course code '$c' not found in verified facts or input")
        }
    }

    // 2. Day names check
    dayRegex.findAll(text).forEach { m ->
        val d = m.groupValues[1].replaceFirstChar { it.uppercase() }
        if (!gtDays.contains(d)) {
            issues.add("Factual violation: day '$d' not found in time references or input")
        }
    }

    // 3. Open demand quantity check
    val openMatch = Regex("""\b(\d+)\s+open delivery requirement""", RegexOption.IGNORE_CASE).find(text)
    if (openMatch != null) {
        val countVal = openMatch.groupValues[1]
        if (!gtNumbers.contains(countVal)) {
            issues.add("Factual violation: open demand count '$countVal' does not match verified facts")
        }
    }

    // Rating check
    val ratingMatch = Regex("""\baveraging\s+(\d+(?:\.\d+)?)\b""", RegexOption.IGNORE_CASE).find(text)
    if (ratingMatch != null) {
        val rVal = ratingMatch.groupValues[1]
        if (!gtNumbers.contains(rVal)) {
            issues.add("Factual violation: rating '$rVal' does not match verified facts")
        }
    }

    // 4. Person names check
    val closingWords = setOf("thanks", "thank", "regards", "best")
    Regex("""\*([A-Z][a-z]+)\*""").findAll(text).forEach { m ->
        val name = m.groupValues[1]
        if (!closingWords.contains(name.lowercase()) && !gtNames.contains(name.lowercase())) {
            issues.add("Factual violation: person name '$name' not found in verified facts or input")
        }
    }

    return issues
}

fun validate(message: String, plan: ContextSelectionPlan? = null): ValidationResult {
    val text = message.trim()
    if (text == NO_MEANINGFUL_MESSAGE) return ValidationResult(true, emptyList())
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

    if (plan != null) {
        issues.addAll(validateFactualIntegrity(text, plan))
    }

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