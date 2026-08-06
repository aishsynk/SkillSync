package com.example.skillsync.ui.components

/**
 * Gson decodes our untyped payloads into Map<String, Any> with every number as a
 * Double, so every read site needs coercion. These live in one place rather than
 * being re-declared privately per screen.
 */

fun Map<String, Any>.rows(key: String): List<Map<*, *>> =
    (this[key] as? List<*>)?.filterIsInstance<Map<*, *>>() ?: emptyList()

fun Map<*, *>.obj(key: String): Map<*, *>? = this[key] as? Map<*, *>

fun Map<*, *>.list(key: String): List<Map<*, *>> =
    (this[key] as? List<*>)?.filterIsInstance<Map<*, *>>() ?: emptyList()

fun Map<*, *>.strings(key: String): List<String> =
    (this[key] as? List<*>)?.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }
        ?: emptyList()

fun Map<*, *>.str(key: String): String {
    val v = this[key] ?: return ""
    return if (v is String) v.trim() else v.toString().trim()
}

fun Map<*, *>.int(key: String): Int = when (val v = this[key]) {
    null -> 0
    is Int -> v
    is Double -> v.toInt()
    is Float -> v.toInt()
    is Long -> v.toInt()
    is String -> v.toDoubleOrNull()?.toInt() ?: 0
    else -> 0
}

fun Map<*, *>.bool(key: String): Boolean = when (val v = this[key]) {
    is Boolean -> v
    is String -> v.equals("true", ignoreCase = true) || v == "Yes"
    is Number -> v.toInt() != 0
    else -> false
}

/** Null when the key is absent or unparseable, so callers can show "—" rather than 0. */
fun Map<*, *>.intOrNull(key: String): Int? = when (val v = this[key]) {
    null -> null
    is Number -> v.toInt()
    is String -> v.toDoubleOrNull()?.toInt()
    else -> null
}

/** "2026-08-09" -> "09 Aug". Returns the input unchanged if it isn't ISO. */
fun String.shortDate(): String {
    val p = split("-")
    if (p.size != 3) return this
    val m = p[1].toIntOrNull() ?: return this
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun",
                        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    return "${p[2]} ${months.getOrNull(m - 1) ?: p[1]}"
}
