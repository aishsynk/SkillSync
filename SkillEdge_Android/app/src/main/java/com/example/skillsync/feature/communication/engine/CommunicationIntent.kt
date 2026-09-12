package com.example.skillsync.feature.communication.engine

import java.util.Locale

private val DAY_NAMES = listOf(
    "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
)

private val HINGLISH = mapOf(
    "kal" to "tomorrow",
    "parso" to "day after tomorrow",
    "jaldi" to "soon",
    "plz" to "please",
    "pls" to "please",
    "bhejo" to "send",
    "thoda" to "a little",
    "aaj" to "today",
    "karo" to "please do",
    "nahi" to "not",
    "haan" to "yes",
    "ha" to "yes",
    "bol do" to "tell them",
    "le lunga" to "I will take it",
    "le lenge" to "we will take it",
    "chahiye" to "is needed",
    "ke liye" to "for",
    "possible hai" to "is possible",
    "ho jayega" to "will be done",
    "kya" to "what",
    "kab" to "when",
    "bhej diya" to "sent it",
    "de diya" to "gave it",
)

fun normalize(text: String): String {
    var out = text
    HINGLISH.forEach { (k, v) ->
        out = Regex("\\b${Regex.escape(k)}\\b", RegexOption.IGNORE_CASE).replace(out, v)
    }
    return out
}

class Intent {
    var tone: String = "professional"
    var purpose: String = "GENERAL_PROFESSIONAL"
    var urgency: String = "NORMAL"
    var recipientName: String = ""
    var recipientClass: String = "UNKNOWN"
    var userMessage: String = ""
    var myMessage: String = ""
    var inboundContext: String = ""
    var responsePosition: String = "" // ACCEPT | DECLINE | COMPLETED | DIRECTIVE | INQUIRY
    var course: String = ""
    var qualifiers: MutableList<String> = mutableListOf()
    var deadlineText: String = ""
    var pendingSince: String = ""
    var timeRefs: MutableList<String> = mutableListOf()
    var exclusions: MutableList<String> = mutableListOf()
    var content: String = ""
    var short: Boolean = false
    var removeClosing: Boolean = false
    var firm: Boolean = false
    var soft: Boolean = false
    var overrideLines: MutableList<String> = mutableListOf()
}

fun stripDirectives(text: String): String {
    return Regex(
        """\b(?:please |could you |kindly )?(?:make it |keep it )?(?:more |less )?(?:firm|softer|soft|short|shorter|brief|to the point|polite|formal)\b[^.\n]*""",
        RegexOption.IGNORE_CASE,
    ).replace(text, "").trim()
}

fun analyze(
    userMessage: String,
    myMessage: String,
    recipientName: String = "",
    recipientType: String = "UNKNOWN",
    purposeHint: String = "",
): Intent {
    val umRaw = userMessage.trim()
    val mmRaw = myMessage.trim()
    val um = normalize(umRaw)
    val mm = normalize(mmRaw)
    val combined = "$um $mm".trim()
    val lowerComb = combined.lowercase(Locale.getDefault())

    val intent = Intent()
    intent.userMessage = umRaw
    intent.myMessage = mmRaw
    intent.recipientName = recipientName.trim()

    if (intent.recipientName.isEmpty()) {
        val m = Regex("""\b(?:tell|ask|message|inform|text|ping|email|update)\s+(([A-Z][A-Za-z]{1,20})(?:[ ][A-Z][A-Za-z]{1,20})?)""").find(combined)
        if (m != null) intent.recipientName = m.groupValues[1].trim()
    }

    val rt = recipientType.uppercase(Locale.getDefault())
    intent.recipientClass = when (rt) {
        "TEAM", "MANAGER", "REPORTEE", "COLLEAGUE", "CLIENT" -> rt
        else -> if (Regex("""\b(team|everyone|all|colleagues)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lowerComb)) "TEAM" else rt.ifEmpty { "UNKNOWN" }
    }

    if (Regex("""\b(?:softer|kindly|kind|gentle|polite|nicer|less firm|soften)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lowerComb)) {
        intent.soft = true
        intent.tone = "professional"
    }
    if (Regex("""\b(?:firm|firmer|strict|strictly|strongly worded|not be soft|be firm)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lowerComb)) {
        intent.firm = true
        intent.tone = "firm"
    }
    if (Regex("""\b(?:urgent|asap|immediately|right away|do not delay|at the earliest)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lowerComb)) {
        intent.urgency = "HIGH"
    }
    if (Regex("""\b(?:short|shorter|brief|concise|one line|one sentence)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lowerComb)) {
        intent.short = true
    }

    // Time references
    for (tr in listOf("next week", "this week", "today", "tomorrow", "friday", "monday")) {
        if (lowerComb.contains(tr) && !intent.timeRefs.contains(tr) && !intent.timeRefs.contains(tr.replaceFirstChar { it.uppercase() })) {
            intent.timeRefs.add(if (DAY_NAMES.contains(tr)) tr.replaceFirstChar { it.uppercase() } else tr)
        }
    }

    // Course extraction
    val cMatch = Regex("""\b([A-Z]{2,4}-[0-9]{2,4}[A-Z0-9]*)\b""", RegexOption.IGNORE_CASE).find("$umRaw $mmRaw")
    if (cMatch != null) {
        intent.course = cMatch.value.uppercase(Locale.getDefault())
    }

    // Authoritative Intent Resolution
    if (purposeHint.isNotBlank()) {
        intent.purpose = purposeHint.uppercase(Locale.getDefault())
    } else if (umRaw.isNotEmpty()) {
        // CASE 1: USER MESSAGE IS PRIMARY INCOMING CONTEXT
        val lowerUm = um.lowercase(Locale.getDefault())
        val lowerMm = mm.lowercase(Locale.getDefault())

        if (Regex("""\b(can you take|take up|possible to take|could you deliver|are you available to (?:deliver|teach|take)|teach|take (?:the )?([A-Z]{2,4}-\d+|batch))\b""", RegexOption.IGNORE_CASE).containsMatchIn(lowerUm) || umRaw.lowercase(Locale.getDefault()).contains("possible hai")) {
            intent.purpose = "OPPORTUNITY_RESPONSE"
            intent.inboundContext = "Delivery request for ${intent.course.ifEmpty { "the batch" }}"
            if (Regex("""\b(yes|can take|will take|take it|sure|available|haan|ha|le lunga)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lowerMm)) {
                intent.responsePosition = "ACCEPT"
                if (Regex("""\b(prep|preparation|toc|table of contents|material|study)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lowerMm)) {
                    intent.qualifiers.add("preparation")
                    if (lowerMm.contains("toc")) intent.qualifiers.add("toc")
                }
            } else if (Regex("""\b(no|cannot|can't|unable|not possible|another delivery|busy|nahi)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lowerMm)) {
                intent.responsePosition = "DECLINE"
            } else {
                intent.responsePosition = "ACCEPT"
            }
        } else if (Regex("""\b(are you free|free tomorrow|free next week|available tomorrow|available next week|can we meet|can we connect)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lowerUm)) {
            intent.purpose = "AVAILABILITY_RESPONSE"
            intent.inboundContext = "Inquiry regarding availability"
            if (Regex("""\b(no|delivery|in delivery|cannot|busy|not free)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lowerMm)) {
                intent.responsePosition = "DECLINE"
                if (lowerMm.contains("friday")) intent.qualifiers.add("connect_friday")
            } else {
                intent.responsePosition = "ACCEPT"
            }
        } else if (Regex("""\b(please send|share (?:the )?(?:feedback|report)|send the report|submit|status of)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lowerUm)) {
            if (Regex("""\b(completed|shared|already sent|done|sent it|bhej diya)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lowerMm)) {
                intent.purpose = "STATUS_UPDATE"
                intent.responsePosition = "COMPLETED"
            } else {
                intent.purpose = "TASK_FOLLOWUP"
                intent.responsePosition = "DIRECTIVE"
            }
        } else {
            intent.purpose = if (lowerMm.contains("thank") || lowerMm.contains("appreciat")) "APPRECIATION" else "GENERAL_PROFESSIONAL"
        }
    } else if (mmRaw.isNotEmpty()) {
        // CASE 2: MY MESSAGE ONLY
        val lowerMm = mm.lowercase(Locale.getDefault())
        when {
            Regex("""\b(confident|preparation|prep|readiness|take\s+[A-Z]{2,4}-\d+.*deliver\s+it\s+with\s+quality)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lowerMm) -> {
                intent.purpose = "COURSE_PREPARATION_CHECK"
            }
            Regex("""\b(ask the team|who can take|check availability|open requirement|available to take)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lowerMm) -> {
                intent.purpose = "AVAILABILITY_REQUEST"
            }
            Regex("""\b(appreciat\w*|good job|great work|well done|thank\w*|kudos|congrat\w*|proud)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lowerMm) -> {
                intent.purpose = "APPRECIATION"
                intent.tone = "appreciative"
            }
            Regex("""\b(session|training|class|delivery|assessment|power cut)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lowerMm) -> {
                intent.purpose = "DELIVERY_UPDATE"
            }
            Regex("""\b(cab|travel desk|travel|flight)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lowerMm) -> {
                intent.purpose = "TRAVEL_COORDINATION"
            }
            Regex("""\b(cert|exam|book exam)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lowerMm) -> {
                intent.purpose = "CAPABILITY_DEVELOPMENT"
            }
            Regex("""\b(can i do it|say yes|take (?:it|this) up|accept (?:the )?(?:batch|delivery))\b""", RegexOption.IGNORE_CASE).containsMatchIn(lowerMm) -> {
                intent.purpose = "OPPORTUNITY_RESPONSE"
            }
            Regex("""\b(please ensure|provision|complete|review)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lowerMm) -> {
                intent.purpose = "TASK_ASSIGNMENT"
            }
            else -> intent.purpose = "GENERAL_PROFESSIONAL"
        }
    } else {
        // CASE 3: AUTO-GENERATION MODE
        intent.purpose = "SITUATION_EVALUATION"
    }

    val source = if (mmRaw.isNotEmpty()) mmRaw else umRaw
    intent.content = stripDirectives(source)
    return intent
}
