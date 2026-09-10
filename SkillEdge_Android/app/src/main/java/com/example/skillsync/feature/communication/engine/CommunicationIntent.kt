package com.example.skillsync.feature.communication.engine

private val DAY_NAMES = listOf(
    "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
)

private val HINGLISH = mapOf(
    "kal" to "tomorrow",
    "parso" to "day after tomorrow",
    "jaldi" to "soon",
    "plz" to "please",
    "bhejo" to "send",
    "thoda" to "a little",
    "aaj" to "today",
    "karo" to "please do",
    "nahi" to "not",
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
    var owner: String = ""
    var deadlineText: String = ""
    var pendingSince: String = ""
    var exclusions: MutableList<String> = mutableListOf()
    var positivePoints: MutableList<String> = mutableListOf()
    var issues: MutableList<String> = mutableListOf()
    var completed: MutableList<String> = mutableListOf()
    var nextActions: MutableList<String> = mutableListOf()
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
    val um = normalize(userMessage).trim()
    val mm = normalize(myMessage).trim()
    val combined = listOf(um, mm).filter { it.isNotEmpty() }.joinToString(" ").trim()
    val intent = Intent()
    intent.recipientName = recipientName.trim()
    if (intent.recipientName.isEmpty()) {
        val m = Regex(
            """\b(?:tell|ask|message|inform|text|ping|email|update)\s+((?:[A-Z][A-Za-z]{1,20})(?:[ ][A-Z][A-Za-z]{1,20})?)""",
        ).find(combined)
        if (m != null) intent.recipientName = m.groupValues[1].trim()
    }

    val rt = recipientType.uppercase().trim()
    intent.recipientClass = when {
        rt in listOf("TEAM", "MANAGER", "REPORTEE", "COLLEAGUE", "CLIENT") -> rt
        Regex("""\b(team|everyone|all|colleagues)\b""", RegexOption.IGNORE_CASE).containsMatchIn(combined) -> "TEAM"
        rt.isNotEmpty() -> rt
        else -> "UNKNOWN"
    }

    if (Regex("""\b(?:softer|kindly|kind|gentle|polite|nicer|less firm|soften)\b""", RegexOption.IGNORE_CASE).containsMatchIn(combined)) {
        intent.soft = true
        intent.tone = "professional"
    }
    if (Regex("""\b(?:firm|firmer|strict|strictly|strongly worded|not be soft|be firm)\b""", RegexOption.IGNORE_CASE).containsMatchIn(combined)) {
        intent.firm = true
        intent.tone = "firm"
    }
    if (Regex("""\b(?:urgent|asap|immediately|right away|do not delay|at the earliest)\b""", RegexOption.IGNORE_CASE).containsMatchIn(combined)) {
        intent.urgency = "HIGH"
    }
    if (Regex("""\b(?:short|shorter|brief|concise|one line|one sentence)\b""", RegexOption.IGNORE_CASE).containsMatchIn(combined)) {
        intent.short = true
    }
    if (Regex("remove closing|no closing|without closing|leave off closing", RegexOption.IGNORE_CASE).containsMatchIn(combined)) {
        intent.removeClosing = true
    }

    val excl = Regex(
        """\b(?:do not mention|dont mention|don'?t mention|without mentioning|leave out|skip)\s+([^.,;\n]{2,40})""",
        RegexOption.IGNORE_CASE,
    ).findAll(combined)
    intent.exclusions = excl.map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toMutableList()

    val addline = Regex("""add this line\s*[:\-]?\s*(.+?)(?:\.|$)""", RegexOption.IGNORE_CASE).findAll(combined)
    intent.overrideLines = addline.map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toMutableList()

    val pending = Regex(
        """\bpending\s+(?:since|from|for)\s+((?:an? )?(?:${DAY_NAMES.joinToString("|")}))""",
        RegexOption.IGNORE_CASE,
    ).find(combined)
    if (pending != null) {
        intent.pendingSince = pending.groupValues[1]
        if (!intent.firm) {
            intent.firm = true
            intent.tone = "firm"
        }
    }

    val deadline = Regex(
        """\b(?:by|before|until|till|for)\s+((?:today|tomorrow|next week|this week)?(?:\s*(?:an? )?(?:${DAY_NAMES.joinToString("|")}))?(?:\s*(?:at\s*)?\d{1,2}(?:[:.]\d{2})?\s*(?:am|pm|IST|PST)?)?)\b""",
        RegexOption.IGNORE_CASE,
    ).find(combined)
    if (deadline != null) intent.deadlineText = deadline.groupValues[1].split(Regex("\\s+")).joinToString(" ")

    val lower = combined.lowercase()
    val has = { pattern: String -> Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(lower) }
    intent.purpose = when {
        purposeHint.isNotBlank() && purposeHint.uppercase() in PURPOSES -> purposeHint.uppercase()
        has("""\b(can i|yes i can|take it up|i (?:can|could) do|available to|aligns with)\b""") -> "OPPORTUNITY_RESPONSE"
        has("""\b(session|training|class|delivery|assessment|took the assessment|power cut|going (?:on|fine|good))\b""") -> "DELIVERY_UPDATE"
        has("""\b(cab|travel desk|travel|hotel|flight|expense|outstation|ilove|fmat|ilt)\b""") -> "TRAVEL_COORDINATION"
        has("""\b(appreciat|good job|great work|well done|thank(s| you))\b""") &&
            !Regex("""\b(please|kindly|can you|need)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lower) -> "APPRECIATION"
        has("""\b(approve|approval|signed off|sign-off|authorise|authorize)\b""") -> "APPROVAL_REQUEST"
        has("""\b(blocked|blocker|cannot (?:proceed|start|complete)|stuck|escalat)\b""") -> "BLOCKER_ESCALATION"
        has("""\b(pl?ea?se (?:complete|do|share|send|cross-check|fill|submit)|when possible|pending|overdue|outstanding|need to (?:complete|finish)|must (?:be|complete|share))\b""") -> "TASK_FOLLOWUP"
        has("""\b(reschedul|postpon|move (?:to|the)|schedule|cancel|timing|date of)\b""") -> "SCHEDULE_UPDATE"
        has("""\b(assign|task (?:is|to)|add this|please (?:handle|own|take care)|from monday|do this)\b""") -> "TASK_ASSIGNMENT"
        has("""\b(status|update on|where is|how is)\b""") -> "STATUS_UPDATE"
        else -> "GENERAL_PROFESSIONAL"
    }
    if (intent.purpose == "OPPORTUNITY_RESPONSE" && intent.recipientName.isEmpty()) {
        intent.recipientName = "team"
    }

    val source = if (mm.isNotEmpty()) mm else if (um.isNotEmpty()) um else ""
    val body = stripDirectives(source)
    intent.content = body

    val hits = Regex(
        """\b(session|training|class|delivery|assessment|power|transport|hiccup|good|fine|great|went well|completed|managed|collected|finished)\w*""",
        RegexOption.IGNORE_CASE,
    ).findAll(body)
    for (hit in hits) {
        val word = hit.value.lowercase()
        when {
            word in listOf("good", "fine", "great", "well") -> intent.positivePoints.add(word)
            word in listOf("power", "hiccup", "transport", "issue", "problem") -> intent.issues.add(word)
            word in listOf("completed", "finished", "managed", "took", "collected") -> intent.completed.add(word)
            else -> Unit
        }
    }
    if (body.lowercase().contains("assessment")) intent.completed.add("assessment")
    return intent
}