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
 *
 * Every message is complete: it names who it is for, what the course is, when it
 * runs (dates *and* the daily time window), the one action being asked for, a
 * response deadline, and who is asking. The earlier one-sentence version left the
 * trainer to go and look up the timing before they could answer, which is what
 * made the messages feel unfinished.
 */
object BatchShare {

    private const val MAX_CHARS = 2000

    /** Everything the message needs, so no caller can accidentally omit a field. */
    data class Batch(
        val courseName: String,
        val startDate: String,
        val endDate: String,
        val sessionTime: String = "",
        val days: Int? = null,
        val deliveryMode: String = "",
        val language: String = "",
        val participants: String = "",
        val location: String = "",
        val vendor: String = "",
        val reference: String = "",
        val tocUrl: String = "",
    )

    data class Sender(val name: String = "", val title: String = "")

    /**
     * Plain, Viber-safe wording addressed to [recipient] — a trainer's first name,
     * or a team when broadcasting. [candidates] are named in the broadcast form so
     * the people who can actually deliver it know the message is meant for them.
     */
    fun composeMessage(
        batch: Batch,
        recipient: String = "Team",
        candidates: List<String> = emptyList(),
        sender: Sender = Sender(),
        maxSkillLevel: Int = 4,
        respondBy: String = "end of day",
    ): String = buildString {
        appendLine("Hello ${greetingName(recipient)},")
        appendLine()
        appendLine("Please note the following unallocated batch.")
        appendLine()

        appendLine("Course: ${batch.courseName.ifBlank { "Not specified" }}")
        appendLine("Dates: ${dateWindow(batch)}")
        // Time is a required field; say so explicitly rather than dropping the
        // line, so nobody assumes it was simply forgotten.
        appendLine("Time: ${batch.sessionTime.ifBlank { "To be confirmed" }}")
        batch.deliveryMode.ifBlank { null }?.let { appendLine("Delivery mode: $it") }
        batch.language.ifBlank { null }?.let { appendLine("Language: $it") }
        batch.participants.takeIf { it.isNotBlank() && it != "0" }?.let { appendLine("Participants: $it") }
        batch.location.ifBlank { null }?.let { appendLine("Location: $it") }
        batch.vendor.ifBlank { null }?.let { appendLine("Vendor: $it") }
        batch.reference.ifBlank { null }?.let { appendLine("Reference: $it") }
        batch.tocUrl.ifBlank { null }?.let { appendLine("Course outline: $it") }

        if (candidates.isNotEmpty()) {
            appendLine()
            appendLine(
                "Matched on skill: ${candidates.joinToString(", ")}" +
                    " — you are already on record for a matching course."
            )
        }

        appendLine()
        appendLine("Action required:")
        appendLine(
            "If you can deliver this course, please mark your skill in RMS and keep the " +
                "skill level at $maxSkillLevel or below, then confirm here. If you are not " +
                "available on these dates, please reply so the batch can be offered elsewhere."
        )
        appendLine()
        appendLine("Please respond by $respondBy so allocation can be closed.")
        appendLine()
        appendLine("Thank you,")
        append(signature(sender))
    }.trim().take(MAX_CHARS)

    /** Same content with emphasis markers, for targets that render markdown. */
    fun asRichText(
        batch: Batch,
        recipient: String = "Team",
        candidates: List<String> = emptyList(),
        sender: Sender = Sender(),
        maxSkillLevel: Int = 4,
        respondBy: String = "end of day",
    ): String = buildString {
        appendLine("Hello ${greetingName(recipient)},")
        appendLine()
        appendLine("**Unallocated batch — action required**")
        appendLine()
        appendLine("*Course:* ${batch.courseName.ifBlank { "Not specified" }}")
        appendLine("*Dates:* __${dateWindow(batch)}__")
        appendLine("*Time:* ${batch.sessionTime.ifBlank { "To be confirmed" }}")
        batch.deliveryMode.ifBlank { null }?.let { appendLine("*Delivery mode:* $it") }
        batch.language.ifBlank { null }?.let { appendLine("*Language:* $it") }
        batch.participants.takeIf { it.isNotBlank() && it != "0" }?.let { appendLine("*Participants:* $it") }
        batch.location.ifBlank { null }?.let { appendLine("*Location:* $it") }
        batch.vendor.ifBlank { null }?.let { appendLine("*Vendor:* $it") }
        batch.reference.ifBlank { null }?.let { appendLine("*Reference:* $it") }
        batch.tocUrl.ifBlank { null }?.let { appendLine("*Course outline:* $it") }
        if (candidates.isNotEmpty()) {
            appendLine()
            appendLine("*Matched on skill:* ${candidates.joinToString(", ")}")
        }
        appendLine()
        appendLine(
            "**Action required:** mark your skill in RMS at level **$maxSkillLevel or below** " +
                "and confirm here, or reply if you are unavailable on these dates."
        )
        appendLine()
        appendLine("_Please respond by $respondBy._")
        appendLine()
        appendLine("Thank you,")
        append(signature(sender))
    }.trim().take(MAX_CHARS)

    /**
     * Greet by first name. RMS stores names with doubled spaces and repeated
     * surnames ("Abhinav   Samant", "Niharika  Niharika"), and addressing someone
     * by their full RMS record reads like a mail merge, so the salutation is
     * normalised here rather than at each call site.
     */
    private fun greetingName(recipient: String): String =
        recipient.split(" ").firstOrNull { it.isNotBlank() } ?: "Team"

    private fun dateWindow(b: Batch): String {
        val span = when {
            b.startDate.isBlank() -> "To be confirmed"
            b.endDate.isBlank() || b.endDate == b.startDate -> b.startDate
            else -> "${b.startDate} to ${b.endDate}"
        }
        val days = b.days?.takeIf { it > 0 }?.let { " ($it day${if (it == 1) "" else "s"})" }.orEmpty()
        return span + days
    }

    private fun signature(s: Sender): String = when {
        s.name.isBlank() -> "Delivery Management, Koenig Solutions"
        s.title.isBlank() -> "${s.name}\nKoenig Solutions"
        else -> "${s.name}\n${s.title}, Koenig Solutions"
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
