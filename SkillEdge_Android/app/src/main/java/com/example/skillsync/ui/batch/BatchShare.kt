package com.example.skillsync.ui.batch

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri

/**
 * Builds the trainer-facing message for an unallocated batch.
 *
 * House style, applied literally:
 *  - greeting on its own line, message on a new line, closing on a new line
 *  - complete sentences and full word forms; no contractions
 *  - no emojis, bullets, decorative symbols or dashes as separators
 *  - italics only for a name where clarity needs it (the course)
 *  - bold only for the action being asked for
 *  - underline only for dates, times and deadlines
 *  - emphasis used sparingly and never stacked
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

    private const val MAX_CHARS = 1000

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

    /** One sentence of delivery facts, only for the fields RMS actually returned. */
    private fun facts(b: Batch): String {
        val parts = listOfNotNull(
            b.deliveryMode.ifBlank { null }?.let { "delivery is $it" },
            b.language.ifBlank { null }?.let { "the language is $it" },
            b.participants.takeIf { it.isNotBlank() && it != "0" }
                ?.let { "there ${if (it == "1") "is" else "are"} $it participant${if (it == "1") "" else "s"}" },
            b.location.ifBlank { null }?.let { "the location is $it" },
        )
        if (parts.isEmpty()) return ""
        val joined = when (parts.size) {
            1 -> parts[0]
            else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
        }
        return joined.replaceFirstChar { it.uppercase() } + "."
    }

    private fun window(b: Batch): String = when {
        b.startDate.isBlank() -> "on dates still to be confirmed"
        b.endDate.isBlank() || b.endDate == b.startDate -> "on ${b.startDate}"
        else -> "from ${b.startDate} to ${b.endDate}"
    }

    private fun greetingName(recipient: String): String =
        recipient.split(" ").firstOrNull { it.isNotBlank() } ?: "Team"

    /**
     * Viber and WhatsApp read `*bold*` and `_italic_`; neither has underline, so
     * time references are left plain rather than wrapped in markers that would
     * show up as literal punctuation.
     */
    fun composeMessage(
        batch: Batch,
        recipient: String = "Team",
        maxSkillLevel: Int = 4,
        respondBy: String = "end of day",
    ): String = build(batch, recipient, maxSkillLevel, respondBy,
        bold = { "*$it*" }, italic = { "_${it}_" }, underline = { it })

    /** Plain text with no markers at all, for anywhere emphasis would be noise. */
    fun plainMessage(
        batch: Batch,
        recipient: String = "Team",
        maxSkillLevel: Int = 4,
        respondBy: String = "end of day",
    ): String = build(batch, recipient, maxSkillLevel, respondBy,
        bold = { it }, italic = { it }, underline = { it })

    /** HTML for the clipboard, so Teams and Outlook keep all three styles. */
    fun htmlMessage(
        batch: Batch,
        recipient: String = "Team",
        maxSkillLevel: Int = 4,
        respondBy: String = "end of day",
    ): String = build(batch, recipient, maxSkillLevel, respondBy,
        bold = { "<b>$it</b>" }, italic = { "<i>$it</i>" }, underline = { "<u>$it</u>" })
        .replace("\n", "<br>")

    private fun build(
        b: Batch,
        recipient: String,
        maxSkillLevel: Int,
        respondBy: String,
        bold: (String) -> String,
        italic: (String) -> String,
        underline: (String) -> String,
    ): String = buildString {
        appendLine("Hello ${greetingName(recipient)},")
        appendLine()

        append("A batch of ${italic(b.courseName.ifBlank { "an unnamed course" })} is open for allocation ")
        append(underline(window(b)))
        if (b.sessionTime.isNotBlank()) append(", ${underline(b.sessionTime)}")
        append(".")
        facts(b).ifBlank { null }?.let { append(" $it") }
        b.reference.ifBlank { null }?.let { append(" The reference is $it.") }
        appendLine()
        appendLine()

        append("If you can take this, please ")
        append(bold("mark your skill in RMS at level $maxSkillLevel or below"))
        append(" and confirm here by ${underline(respondBy)}. ")
        append("If you are not available on these dates, please let me know so it can be offered to someone else.")
        appendLine()
        appendLine()
        append(italic("Thank you."))
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
