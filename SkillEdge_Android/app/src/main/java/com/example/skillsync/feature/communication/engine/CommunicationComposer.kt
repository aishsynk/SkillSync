package com.example.skillsync.feature.communication.engine

fun compose(intent: Intent, context: CommunicationContext? = null): String {
    val recipientType = (if (context != null) context.recipient.type else "")
        .ifBlank { intent.recipientClass.ifBlank { "UNKNOWN" } }
    val tone = intent.tone.ifBlank { "professional" }
    val name = intent.recipientName.ifBlank { context?.recipient?.name ?: "" }

    val greeting = greetingFor(recipientType, tone, name)

    val body = if (intent.purpose == "OPPORTUNITY_RESPONSE" &&
        context != null &&
        context.verifiedContext.containsKey("opportunity")
    ) {
        val opp = context.verifiedContext["opportunity"] as? Map<*, *>
        opportunityResponse(intent, opp ?: emptyMap<String, Any>())
    } else {
        draftBody(intent, context)
    }

    var composed = body
    for (line in intent.overrideLines) {
        if (line.isNotEmpty() && !composed.lowercase().contains(line.lowercase())) {
            composed = composed.trimEnd().trimEnd('.') + ". " + line + "."
        }
    }

    val lines = mutableListOf(greeting, composed)
    if (!intent.removeClosing) lines.add(closingFor(tone, context?.recipient?.relationship ?: ""))
    var message = lines.joinToString("\n").trim()

    if (intent.short) {
        val sentences = splitSentences(composed)
        val firstSentence = sentences.firstOrNull() ?: composed
        message = if (intent.removeClosing) {
            listOf(greeting, firstSentence).joinToString("\n")
        } else {
            listOf(greeting, firstSentence, closingFor(tone)).joinToString("\n")
        }
    }

    message = stripNames(message, name)
    if (intent.deadlineText.isNotEmpty()) {
        for (marker in listOf(" by ", " before ", " until ", " till ", " for ")) {
            if (message.contains(marker + intent.deadlineText)) {
                message = message.replace(marker + intent.deadlineText, marker + boldUnderline(intent.deadlineText))
            }
        }
    }
    for (excl in intent.exclusions) {
        message = Regex(Regex.escape(excl), RegexOption.IGNORE_CASE).replace(message, "")
    }
    message = Regex("\\s{2,}").replace(message, " ")
    message = Regex("\\s+([.,;:])").replace(message, "$1")
    return message.trim()
}

private fun stripNames(text: String, name: String): String {
    if (name.isEmpty()) return text
    return Regex("(?<!\\*)\\b${Regex.escape(name)}\\b(?!\\*)", RegexOption.IGNORE_CASE)
        .replace(text) { italic(name) }
}

private fun opportunityResponse(intent: Intent, opp: Map<*, *>): String {
    val course = (opp["course_code"] as? String)?.takeIf { it.isNotBlank() }
        ?: (opp["course"] as? String) ?: ""
    val decision = (opp["decision"] as? String)?.lowercase() ?: ""
    if (decision == "decline") {
        return "Thank you for considering me, but I will not be able to take this up at this time."
    }
    val parts = mutableListOf("Yes, I can take this up.")
    if (course.isNotEmpty()) {
        parts.add("I have reviewed the requirement for ${bold(course)} and it aligns with my capability, with brief preparation.")
    } else {
        parts.add("I have reviewed the requirement and it aligns with my capability, with brief preparation.")
    }
    if (decision.contains("preparation")) {
        parts.add("A short preparation is needed and it is manageable.")
    }
    parts.add("Please share the next steps whenever ready.")
    return parts.joinToString(" ")
}

private fun draftBody(intent: Intent, context: CommunicationContext?): String {
    val content = intent.content.trim()
    when (intent.purpose) {
        "DELIVERY_UPDATE" -> return deliveryUpdateBody(intent)
        "TASK_FOLLOWUP" -> {
            if (intent.pendingSince.isNotEmpty()) {
                return bold("This task has been pending since ${intent.pendingSince}") +
                    ". Please complete it as soon as possible."
            }
            val action = if (intent.urgency == "HIGH") {
                bold("Please complete this soon")
            } else {
                "Can you please take care of this when possible?"
            }
            val deadline = intent.deadlineText
            val tail = if (deadline.isNotEmpty()) " It would help to have it $deadline." else ""
            return "Please confirm the current status of this task." + tail + " " + action
        }
        "TASK_ASSIGNMENT" -> {
            val item = content.ifBlank { "this item" }
            val deadline = if (intent.deadlineText.isNotEmpty()) {
                " The deadline is ${boldUnderline(intent.deadlineText)}."
            } else ""
            return bold("Please take ownership of $item") + deadline
        }
        "APPRECIATION" -> return content.ifBlank { "Great work on this." }.trimEnd('.') + "; please keep it up."
        "TRAVEL_COORDINATION" -> {
            return "A reminder for those with FMAT or ILT travel coming up: please coordinate " +
                "with the Travel Desk and Payroll in advance whenever cab arrangements outside " +
                "India may be needed."
        }
        "STATUS_UPDATE" -> return content.ifBlank { "Here is the current status." }.trimEnd('.') + "."
        "APPROVAL_REQUEST" -> return bold("Requesting your approval") + " on this so the next steps can proceed."
        "BLOCKER_ESCALATION" -> {
            val issue = if (intent.issues.isNotEmpty()) issuePhrase(intent.issues[0]) else "an operational issue"
            return bold("Escalating this: $issue") + " is blocking progress and needs your attention."
        }
        "SCHEDULE_UPDATE" -> {
            val tail = if (intent.deadlineText.isNotEmpty()) " The update is ${intent.deadlineText}." else ""
            return "A schedule change is coming up." + tail
        }
    }

    if (content.isNotEmpty()) {
        var body = stripSalutation(content, intent.recipientName)
        var sentences = splitSentences(body)
        if (sentences.isEmpty()) sentences = listOf(body)
        val points = sentences.toMutableList()
        if (intent.completed.isNotEmpty()) points.add("This has been completed as required.")
        if (intent.issues.isNotEmpty()) points.add("A brief operational issue came up, but it was managed.")
        return points.joinToString(" ")
    }
    return if (intent.owner.isEmpty()) "I would like to follow up on this." else {
        "Please can you handle ${intent.owner}'s request."
    }
}

private fun stripSalutation(content: String, name: String): String {
    val before = content
    var c = Regex("^tell\\s+(?:${Regex.escape(name)}\\s+)?(.+)$", RegexOption.IGNORE_CASE)
        .replace(content.trim(), "$1")
    c = Regex("^please tell\\s+(.+)$", RegexOption.IGNORE_CASE).replace(c, "$1")
    return c.trim().ifEmpty { before }
}

private fun issuePhrase(token: String): String {
    val t = token.lowercase()
    return when {
        t.contains("power") -> "a power interruption on the client side"
        t.contains("transport") -> "the daily transport coordination"
        t.contains("hiccup") -> "a brief interruption"
        else -> "an operational issue"
    }
}

private fun deliveryUpdateBody(intent: Intent): String {
    val points = mutableListOf<String>()
    if (intent.positivePoints.isNotEmpty()) points.add("The session is going well.")
    if (intent.issues.isNotEmpty()) {
        val issue = issuePhrase(intent.issues[0])
        points.add("There was $issue, but it was managed and the session continued on schedule.")
    }
    if (intent.completed.isNotEmpty()) {
        val done = intent.completed.toSortedSet().joinToString(" and ")
        if (done.contains("assessment")) {
            points.add("The required delivery, including the assessment, was completed as planned.")
        } else {
            points.add("The required work, including $done, was completed as planned.")
        }
    }
    if (points.isEmpty()) points.add("The delivery is proceeding as planned.")
    if (intent.issues.joinToString(" ").contains("transport")) {
        points.add("I would appreciate help with the daily transport coordination.")
    }
    return points.joinToString(" ")
}