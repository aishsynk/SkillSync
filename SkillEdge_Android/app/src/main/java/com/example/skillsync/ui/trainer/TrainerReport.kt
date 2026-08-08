package com.example.skillsync.ui.trainer

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.skillsync.ui.components.*

/**
 * Publishes a trainer profile as a PDF.
 *
 * Rendered through WebView + the system PrintManager rather than drawing a
 * PdfDocument by hand: the framework then owns pagination, page size and the
 * save/share sheet, and the manager gets the standard Android print dialog
 * they already know — including "Save as PDF" and sending straight to Drive or
 * email.
 *
 * The report is built from the same trainer-360 payload the screen renders, so
 * a published PDF cannot disagree with what was on screen. Fields RMS did not
 * return are omitted rather than printed as blanks or dashes.
 */
object TrainerReport {

    fun export(context: Context, data: Map<String, Any>) {
        val identity = data.obj("identity")
        val name = identity?.str("name").orEmpty().ifBlank { "Trainer" }

        val web = WebView(context)
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                val jobName = "SkillEdge — $name"
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                printManager.print(
                    jobName,
                    view.createPrintDocumentAdapter(jobName),
                    PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .build(),
                )
            }
        }
        web.loadDataWithBaseURL(null, buildHtml(data), "text/html", "UTF-8", null)
    }

    // ── Document ──────────────────────────────────────────────────────────

    private fun buildHtml(data: Map<String, Any>): String {
        val identity = data.obj("identity")
        val metrics = data.obj("metrics")
        val util = data.obj("utilization")
        val cap = data.obj("capability")
        val certs = data.obj("certifications")
        val delivery = data.obj("delivery")
        val feedback = data.obj("feedback")

        val name = identity?.str("name").orEmpty().ifBlank { "Trainer" }
        val sb = StringBuilder()

        sb.append(
            """
            <!doctype html><html><head><meta charset="utf-8"/>
            <style>
              @page { margin: 18mm 14mm; }
              body { font-family: -apple-system, "Segoe UI", Roboto, sans-serif;
                     color: #16202c; font-size: 11pt; line-height: 1.45; }
              h1 { font-size: 19pt; margin: 0 0 2mm; letter-spacing: -0.4pt; }
              .sub { color: #5b6875; font-size: 10pt; margin: 0 0 6mm; }
              h2 { font-size: 8.5pt; text-transform: uppercase; letter-spacing: 1.1pt;
                   color: #2e5c8a; margin: 7mm 0 2mm; border-bottom: 0.6pt solid #d5dde6;
                   padding-bottom: 1.4mm; }
              table { width: 100%; border-collapse: collapse; }
              td { padding: 1.5mm 0; vertical-align: top; font-size: 10pt; }
              td.k { color: #5b6875; width: 38%; }
              .kpis { display: flex; gap: 5mm; margin: 0 0 3mm; }
              .kpi { flex: 1; border: 0.6pt solid #d5dde6; border-radius: 2mm;
                     padding: 3mm; }
              .kpi .n { font-size: 16pt; font-weight: 600; }
              .kpi .l { font-size: 7.5pt; text-transform: uppercase;
                        letter-spacing: 0.8pt; color: #5b6875; }
              ul { margin: 1mm 0 0; padding-left: 5mm; }
              li { font-size: 10pt; margin-bottom: 1mm; }
              .foot { margin-top: 9mm; padding-top: 2.5mm; border-top: 0.6pt solid #d5dde6;
                      color: #8a95a2; font-size: 8pt; }
            </style></head><body>
            """.trimIndent()
        )

        sb.append("<h1>${esc(name)}</h1>")
        val subline = listOfNotNull(
            identity?.str("designation")?.takeIf { it.isNotBlank() },
            identity?.str("email")?.takeIf { it.isNotBlank() },
            identity?.intOrNull("tenure_years")?.let { "$it yrs at Koenig" },
        ).joinToString(" · ")
        if (subline.isNotBlank()) sb.append("<p class='sub'>${esc(subline)}</p>")

        // Headline figures
        sb.append("<div class='kpis'>")
        sb.append(kpi(metrics?.intOrNull("readiness_score")?.toString() ?: "—", "Readiness"))
        sb.append(kpi(util?.intOrNull("current")?.let { "$it%" } ?: "—", "Utilisation"))
        sb.append(kpi(certs?.intOrNull("count")?.toString() ?: "—", "Certificates"))
        sb.append(kpi(certs?.intOrNull("gap_count")?.toString() ?: "—", "Gaps"))
        sb.append("</div>")

        section(sb, "Profile", listOfNotNull(
            row("Employee code", identity?.str("emp_code")),
            row("Joined", identity?.str("date_of_joining")),
            row("Reporting line", if (identity?.bool("direct_report") == true) "Direct" else "Indirect"),
            row("Trainer Plus", if (identity?.bool("trainer_plus") == true) "Yes" else null),
            row("Languages", identity?.list("languages")
                ?.joinToString(", ") { it.str("language") }?.takeIf { it.isNotBlank() }),
        ))

        section(sb, "Capacity", listOfNotNull(
            row("Current utilisation", util?.intOrNull("current")?.let { "$it%" }),
            row("Three-month average", util?.intOrNull("avg_3m")?.let { "$it%" }),
            row("Status", util?.str("status")),
            row("Availability", util?.str("availability")),
            row("Peak", util?.intOrNull("peak")?.let { "$it%" }),
            row("Upcoming assignments", util?.intOrNull("upcoming_load")?.toString()),
        ))

        section(sb, "Readiness and risk", listOfNotNull(
            row("Readiness score", metrics?.intOrNull("readiness_score")?.toString()),
            row("Readiness band", metrics?.str("readiness_bucket")),
            row("Risk level", metrics?.str("risk_level")),
            row("Skill match", metrics?.intOrNull("skill_match_pct")?.let { "$it%" }),
            row("Rank in team", metrics?.intOrNull("team_rank")?.let {
                "$it of ${metrics.intOrNull("team_size") ?: "?"}"
            }),
            row("Average Qubits", metrics?.intOrNull("avg_qubits")?.toString()),
        ))

        section(sb, "Capability", listOfNotNull(
            row("Courses on record", cap?.intOrNull("total_courses")?.toString()),
            row("Officially approved", cap?.intOrNull("approved_courses")?.toString()),
            row("Future skills", cap?.intOrNull("future_skills")?.toString()),
        ))

        // Certifications held and missing
        val held = certs?.list("held").orEmpty()
        if (held.isNotEmpty()) {
            sb.append("<h2>Certifications held (${held.size})</h2><ul>")
            held.take(30).forEach {
                val code = it.str("code").takeIf { c -> c.isNotBlank() }?.let { c -> "$c — " }.orEmpty()
                sb.append("<li>${esc(code + it.str("name"))}</li>")
            }
            sb.append("</ul>")
        }
        val missing = certs?.list("missing").orEmpty()
        if (missing.isNotEmpty()) {
            sb.append("<h2>Certification gaps (${missing.size})</h2><ul>")
            missing.take(30).forEach {
                val code = it.str("code").takeIf { c -> c.isNotBlank() }?.let { c -> "$c — " }.orEmpty()
                val because = it.str("because").takeIf { b -> b.isNotBlank() }
                    ?.let { b -> " (teaching $b)" }.orEmpty()
                sb.append("<li>${esc(code + it.str("name") + because)}</li>")
            }
            sb.append("</ul>")
        }

        section(sb, "Delivery", listOfNotNull(
            row("Assignments on record", delivery?.intOrNull("total")?.toString()),
            row("Currently delivering", delivery?.intOrNull("current")?.toString()),
            row("Upcoming", delivery?.intOrNull("upcoming")?.toString()),
        ))

        section(sb, "Feedback", listOfNotNull(
            row("Negative feedback records", feedback?.intOrNull("negative_total")?.toString()),
            row("HR positive", feedback?.intOrNull("hr_positive")?.toString()),
            row("HR negative", feedback?.intOrNull("hr_negative")?.toString()),
        ))

        sb.append(
            "<p class='foot'>Generated by SkillEdge from RMS data" +
                (data.str("timestamp").takeIf { it.isNotBlank() }
                    ?.let { " · snapshot ${esc(it.take(19).replace('T', ' '))} UTC" } ?: "") +
                ". Figures reflect what RMS returned at that moment; blank fields are " +
                "data RMS did not supply.</p>"
        )
        sb.append("</body></html>")
        return sb.toString()
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun kpi(value: String, label: String) =
        "<div class='kpi'><div class='n'>${esc(value)}</div><div class='l'>${esc(label)}</div></div>"

    private fun row(key: String, value: String?): String? =
        value?.takeIf { it.isNotBlank() && it != "—" }
            ?.let { "<tr><td class='k'>${esc(key)}</td><td>${esc(it)}</td></tr>" }

    /** Omits the whole section when RMS supplied none of its fields. */
    private fun section(sb: StringBuilder, title: String, rows: List<String>) {
        if (rows.isEmpty()) return
        sb.append("<h2>${esc(title)}</h2><table>")
        rows.forEach { sb.append(it) }
        sb.append("</table>")
    }

    private fun esc(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;")
}
