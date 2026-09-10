package com.example.skillsync.ui.common

import retrofit2.HttpException

/**
 * One human-readable message for a failed request, phrased around what the
 * manager was trying to do ("Could not load the dashboard"). Replaces the
 * per-ViewModel ad-hoc mappers so every screen says the same thing about the
 * same failure.
 */
fun Throwable.userMessage(verb: String): String {
    if (this is HttpException) {
        return "Could not $verb (${code()}) — please try again"
    }
    val detail = localizedMessage?.takeIf { it.isNotBlank() && !it.startsWith("HTTP ") }
        ?: message
    return detail?.let { "Could not $verb: $it" } ?: "Could not $verb — please try again"
}