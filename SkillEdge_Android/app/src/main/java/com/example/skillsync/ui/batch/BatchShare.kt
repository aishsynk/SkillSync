package com.example.skillsync.ui.batch

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri

/**
 * Builds the trainer-facing broadcast for an unallocated assignment.
 *
 * Format (the RMS allocation broadcast, applied literally):
 *  - "Hi Team," on its own line, then a one-line summary
 *  - one labelled fact per line ("Course : ...", "Schedule : ..."), and a
 *    label is printed only when RMS actually returned that field
 *  - dates and the daily time window are underlined on every line that carries them
 *  - the skill-marking instruction is bold; the certification-preference note is italic
 *  - no emojis, no bullet glyphs, no dashes as separators
 *
 * No sender signature: these go to the manager's own team inside a chat where
 * the sender is already on screen, so naming themselves reads as boilerplate.
 *
 * Emphasis travels as HTML on the clipboard ([htmlMessage]) because no chat app
 * renders underline from markdown. Pasting into Teams or Outlook keeps bold,
 * italic and underline; pasting into Viber falls back to [composeMessage], which
 * carries the Viber markers it does understand and drops the rest rather than
 * leaving punctuation lying in the text.
 */
object BatchShare {

    private const val MAX_CHARS = 1200

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
        val assignmentLevel: String = "",
        val tocUrl: String = "",
    )

    private fun schedule(b: Batch): String = when {
        b.startDate.isBlank() -> "dates still to be confirmed"
        b.endDate.isBlank() || b.endDate == b.startDate -> b.startDate
        else -> "${b.startDate} to ${b.endDate}"
    }

    private fun greetingName(recipient: String): String =
        recipient.split(" ").firstOrNull { it.isNotBlank() } ?: "Team"

    /** Viber and WhatsApp read `*bold*` and `_italic_`; neither has underline. */
    fun composeMessage(
        batch: Batch,
        recipient: String = "Team",
    ): String = build(batch, recipient,
        bold = { "*$it*" }, italic = { "_${it}_" }, underline = { it })

    /** Plain text with no markers at all, for anywhere emphasis would be noise. */
    fun plainMessage(
        batch: Batch,
        recipient: String = "Team",
    ): String = build(batch, recipient,
        bold = { it }, italic = { it }, underline = { it })

    /** HTML for the clipboard, so Teams and Outlook keep all three styles. */
    fun htmlMessage(
        batch: Batch,
        recipient: String = "Team",
    ): String = build(batch, recipient,
        bold = { "<b>$it</b>" }, italic = { "<i>$it</i>" }, underline = { "<u>$it</u>" })
        .replace("\n", "<br>")

    private fun build(
        b: Batch,
        recipient: String,
        bold: (String) -> String,
        italic: (String) -> String,
        underline: (String) -> String,
    ): String = buildString {
        appendLine("Hi ${greetingName(recipient)},")
        appendLine("New assignment open for allocation.")
        appendLine()

        appendLine("Course : ${b.courseName.ifBlank { "To be confirmed" }}")
        if (b.reference.isNotBlank()) appendLine("Assignment ID : ${b.reference}")
        appendLine("Schedule : ${underline(schedule(b))}")
        if (b.sessionTime.isNotBlank()) appendLine("Daily Time : ${underline(b.sessionTime)}")
        if (b.deliveryMode.isNotBlank()) appendLine("Delivery Mode : ${b.deliveryMode}")
        if (b.location.isNotBlank()) appendLine("Location : ${b.location}")
        if (b.vendor.isNotBlank()) appendLine("Customer : ${b.vendor}")
        if (b.language.isNotBlank()) appendLine("Language : ${b.language}")
        if (b.participants.isNotBlank() && b.participants != "0") appendLine("Pax Count : ${b.participants}")
        if (b.assignmentLevel.isNotBlank()) appendLine("Assignment Level : ${b.assignmentLevel}")
        // The URL is held verbatim (not sanitised) so the trainer can tap it.
        if (b.tocUrl.isNotBlank()) appendLine("TOC : ${b.tocUrl}")
        appendLine()

        val levelPhrase =
            if (b.assignmentLevel.isNotBlank()) "at level ${b.assignmentLevel} or above"
            else "at the assignment level or above"
        appendLine(
            "If you do not hold this skill but can prepare and deliver with quality, " +
                bold("mark your skill in RMS $levelPhrase, with a live date before the assignment start date") + "."
        )
        appendLine()
        append(
            italic(
                "Preference is given to certified trainers where certification exists, " +
                    "and otherwise to a mock delivery completed with quality."
            )
        )
    }.trim().take(MAX_CHARS)

    // ── Delivery ──────────────────────────────────────────────────────────────

    /**
     * Copies the message to the clipboard.
     *
     * Deliberately not a `viber://forward?text=` deep link. That scheme puts the
     * whole body in a URI, and Viber truncates it around a hundred characters,
     * so a complete message arrived cut off mid sentence and lost its meaning.
     * Copy and paste has no length limit and the sender sees exactly what goes.
     */
    fun copyMessage(context: Context, plain: String, html: String? = null) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null) {
            Toast.makeText(context, "Clipboard unavailable on this device", Toast.LENGTH_SHORT).show()
            return
        }
        val clip = if (html.isNullOrBlank()) {
            ClipData.newPlainText("Batch message", plain)
        } else {
            // Plain text is the fallback every target can read; the HTML is only
            // used by apps that accept rich text.
            ClipData.newHtmlText("Batch message", plain, html)
        }
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Message copied. Paste it into Viber or Teams.", Toast.LENGTH_LONG).show()
    }

    /**
     * Hands the message to a share target. `ACTION_SEND` passes the body as an
     * intent extra rather than a URI, so unlike the deep link it is not truncated.
     */
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
