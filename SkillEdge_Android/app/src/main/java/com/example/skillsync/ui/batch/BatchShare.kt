package com.example.skillsync.ui.batch

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.net.toUri

/**
 * Builds the trainer-facing broadcast for an unallocated batch and hands it to
 * Viber (or any share target).
 *
 * Formatting note: Viber renders message bodies as PLAIN TEXT — it has no
 * markdown. Asterisks and underscores would show up literally as punctuation, so
 * the message is written to read well without them. [asRichText] keeps the
 * emphasis markers for targets that do understand them, such as MS Teams.
 */
object BatchShare {

    private const val MAX_CHARS = 1000

    /**
     * Plain, Viber-safe wording. Deliberately leads with the course and dates,
     * because that is what a trainer needs to decide, and closes with the one
     * action being asked for.
     */
    fun composeMessage(
        courseName: String,
        startDate: String,
        endDate: String,
        deliveryMode: String,
        reference: String,
        participants: String = "",
        location: String = "",
        maxSkillLevel: Int = 4,
    ): String {
        val window = when {
            startDate.isBlank() -> ""
            endDate.isBlank() || endDate == startDate -> "on $startDate"
            else -> "from $startDate to $endDate"
        }
        val detail = listOfNotNull(
            deliveryMode.takeIf { it.isNotBlank() }?.let { "Delivery mode is $it" },
            participants.takeIf { it.isNotBlank() && it != "0" }?.let { "$it participants" },
            location.takeIf { it.isNotBlank() },
        ).joinToString(", ")

        return buildString {
            appendLine("Hello Team,")
            append("Please note $courseName is scheduled $window.")
            if (detail.isNotBlank()) append(" $detail.")
            if (reference.isNotBlank()) append(" Reference $reference.")
            append(" If you can deliver this course, please mark your skill")
            append(" and keep the skill level at $maxSkillLevel or below.")
            appendLine()
            appendLine()
            append("Thank you.")
        }.trim().take(MAX_CHARS)
    }

    /** Same content with emphasis markers, for targets that render markdown. */
    fun asRichText(
        courseName: String,
        startDate: String,
        endDate: String,
        deliveryMode: String,
        reference: String,
        participants: String = "",
        maxSkillLevel: Int = 4,
    ): String {
        val window = if (endDate.isBlank() || endDate == startDate) {
            "__${startDate}__"
        } else {
            "__${startDate} to ${endDate}__"
        }
        return buildString {
            appendLine("Hello Team,")
            append("*Please note* _${courseName}_ is scheduled $window")
            if (deliveryMode.isNotBlank()) append(", delivery mode *$deliveryMode*")
            if (participants.isNotBlank() && participants != "0") append(", $participants pax")
            append(". Ref *$reference*. If you can deliver this course, **mark your skill**")
            append(" and keep skill level at **$maxSkillLevel or below**.")
            appendLine()
            appendLine()
            append("*Thank you.*")
        }.trim().take(MAX_CHARS)
    }

    /**
     * Opens Viber with [message] pre-filled. Falls back to the system share sheet
     * when Viber is not installed, so the action never dead-ends.
     */
    fun shareToViber(context: Context, message: String) {
        val viber = Intent(Intent.ACTION_VIEW).apply {
            data = "viber://forward?text=${Uri.encode(message)}".toUri()
        }
        try {
            context.startActivity(viber)
        } catch (_: ActivityNotFoundException) {
            shareAnywhere(context, message, "Viber is not installed — choose another app")
        }
    }

    fun shareAnywhere(context: Context, message: String, toast: String? = null) {
        toast?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        context.startActivity(Intent.createChooser(send, "Share batch"))
    }

    /** Opens the course TOC pdf in the browser / pdf viewer. */
    fun openUrl(context: Context, url: String) {
        if (url.isBlank()) {
            Toast.makeText(context, "No document link on this batch", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No app available to open this link", Toast.LENGTH_SHORT).show()
        }
    }
}
