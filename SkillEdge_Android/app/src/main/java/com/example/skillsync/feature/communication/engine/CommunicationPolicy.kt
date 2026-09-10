package com.example.skillsync.feature.communication.engine

const val MAX_LENGTH = 1000

val TONES = listOf(
    "professional", "firm", "corrective", "advisory", "appreciative",
    "collaborative", "urgent", "informational",
)

object Tone {
    const val PROFESSIONAL = "professional"
    const val FIRM = "firm"
    const val CORRECTIVE = "corrective"
    const val ADVISORY = "advisory"
    const val APPRECIATIVE = "appreciative"
    const val COLLABORATIVE = "collaborative"
    const val URGENT = "urgent"
    const val INFORMATIONAL = "informational"
}

val RECIPIENT_TYPES = listOf(
    "INDIVIDUAL", "MANAGER", "REPORTEE", "COLLEAGUE", "CLIENT", "TEAM", "OTHER", "UNKNOWN",
)

val PURPOSES = listOf(
    "DELIVERY_UPDATE", "TASK_ASSIGNMENT", "TASK_FOLLOWUP", "STATUS_UPDATE",
    "APPROVAL_REQUEST", "BLOCKER_ESCALATION", "OPPORTUNITY_RESPONSE",
    "TRAVEL_COORDINATION", "SCHEDULE_UPDATE", "APPRECIATION",
    "CORRECTIVE_MESSAGE", "GENERAL_PROFESSIONAL",
)

private val GREETINGS = mapOf(
    "team_professional" to "Hello team,",
    "team_collaborative" to "Hi everyone,",
    "team_urgent" to "Team,",
    "team_informational" to "Hello team,",
    "individual_professional" to "Hello {name},",
    "individual_collaborative" to "Hi {name},",
    "individual_urgent" to "{name},",
    "individual_informational" to "Hello {name},",
    "client_professional" to "Hello {name},",
    "client_collaborative" to "Dear {name},",
    "client_urgent" to "{name},",
    "client_informational" to "Hello {name},",
)

fun greetingFor(recipientType: String, tone: String, name: String = ""): String {
    val rt = (recipientType.ifBlank { "OTHER" }).uppercase()
    val toneKey = nearestTone(tone)
    if (rt == "TEAM") return GREETINGS["team_$toneKey"] ?: GREETINGS.getValue("team_professional")
    val kind = if (rt == "CLIENT") "client" else "individual"
    val bare = GREETINGS["${kind}_$toneKey"] ?: GREETINGS.getValue("${kind}_professional")
    val display = name.trim()
    if (display.isEmpty()) {
        if (rt == "CLIENT") return if ("{name}" in bare) bare.replace("{name}", italic("team")) else bare
        val prefix = bare.substringBefore("{name}").trimEnd(',', ' ')
        return if (prefix.isNotBlank()) "$prefix," else "Hello,"
    }
    return bare.replace("{name}", italic(display))
}

fun nearestTone(tone: String): String {
    val t = (tone.ifBlank { "" }).lowercase()
    for (candidate in listOf(Tone.FIRM, Tone.URGENT, Tone.CORRECTIVE)) {
        if (t.contains(candidate)) return if (candidate != Tone.CORRECTIVE) "urgent" else "professional"
    }
    if (t.contains("appreciat")) return "collaborative"
    if (t.contains("inform")) return "informational"
    return "professional"
}

val CLOSINGS = listOf(
    "*Thanks*", "*Thank you*", "*Regards*", "*Kind regards*",
    "*Much appreciated*", "*Thanks for your support*", "*Looking forward*",
)

fun closingFor(tone: String, relationship: String = ""): String {
    val t = (tone.ifBlank { "" }).lowercase()
    if (t.contains("urgent") || t.contains("firm") || t.contains("correct")) return "*Thanks*"
    if (t.contains("appreciat")) return "*Much appreciated*"
    val pool = listOf("*Thanks*", "*Regards*")
    return pool[(t.length + relationship.length) % pool.size]
}

fun italic(text: String): String {
    val t = text.trim()
    if (t.isEmpty()) return t
    if (t.startsWith("*") && t.endsWith("*")) return t
    return "*$t*"
}

fun bold(text: String): String {
    val t = text.trim()
    if (t.isEmpty()) return t
    if (t.startsWith("**") && t.endsWith("**")) return t
    return "**$t**"
}

fun boldUnderline(text: String): String {
    val t = text.trim()
    if (t.isEmpty()) return t
    return "__**" + t.trim('*') + "**__"
}

fun hasEmoji(text: String): Boolean {
    val s = text
    var i = 0
    while (i < s.length) {
        val cp = s.codePointAt(i)
        if (cp in 0x1F300..0x1FAFF ||
            cp in 0x2600..0x27BF ||
            cp in 0x1F1E6..0x1F1FF ||
            cp in 0x2B00..0x2BFF ||
            cp == 0xFE0F ||
            cp in intArrayOf(0x2705, 0x2611, 0x2728, 0x2714, 0x2716)
        ) return true
        i += Character.charCount(cp)
    }
    return false
}

fun hasBulletList(text: String): Boolean {
    return text.split("\n").any { line ->
        val t = line.trim()
        listOf("- ", "* ", "• ", "·").any { t.startsWith(it) }
    }
}

fun hasNumberedList(text: String): Boolean =
    Regex("(?m)^\\s*\\d{1,2}[.)]\\s+\\S").containsMatchIn(text)

fun splitSentences(text: String): List<String> =
    Regex("(?<=[.!?])\\s+").split(text).map { it.trim() }.filter { it.isNotEmpty() }