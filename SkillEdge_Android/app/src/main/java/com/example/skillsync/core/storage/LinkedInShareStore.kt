package com.example.skillsync.core.storage

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow

data class LinkedInSharePayload(val text: String, val subject: String?)

/**
 * In-memory, transient bridge between an `ACTION_SEND` share intent and the
 * LinkedIn capture screen.
 *
 * Deliberately NOT persisted: a stale share must never resurface on a later
 * cold start. Navigation consumes it immediately after routing, so it only
 * ever carries one intent. It survives only while the user is logged in —
 * the navigation layer holds it until a logged-in shell exists.
 */
object LinkedInShareStore {
    const val EXTRA_TEXT = Intent.EXTRA_TEXT
    const val EXTRA_SUBJECT = Intent.EXTRA_SUBJECT

    val pending = MutableStateFlow<LinkedInSharePayload?>(null)

    fun accept(intent: Intent?) {
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        if (text.isNotBlank()) {
            pending.value = LinkedInSharePayload(
                text = text,
                subject = intent?.getStringExtra(Intent.EXTRA_SUBJECT),
            )
        }
    }

    fun consume() { pending.value = null }
}