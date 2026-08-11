package com.example.skillsync.ui

import com.example.skillsync.ui.report.MESSAGE_LIMIT
import com.example.skillsync.ui.report.MessageStyle
import com.example.skillsync.ui.report.ReporteeSignals
import com.example.skillsync.ui.report.TeamSignals
import com.example.skillsync.ui.report.composeReporteeMessage
import com.example.skillsync.ui.report.composeTeamMessage
import com.example.skillsync.ui.report.weekReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The house style is a contract, so it is tested as one. Every rule the brief
 * sets out is asserted here rather than trusted to whoever writes the next
 * message variant.
 */
class WeeklyMessageTest {

    /** 10 August 2026 is a Monday; 11 August, used below, is the Tuesday. */
    private val monday = LocalDate.of(2026, 8, 10)

    private fun allMessages(style: MessageStyle): List<String> {
        val people = listOf(
            ReporteeSignals("Abhinav Samant", 95, "Stretched"),
            ReporteeSignals("Beena Rao", 30, "On Bench"),
            ReporteeSignals("Chetan Iyer", 61, "Balanced", certGaps = 2),
            ReporteeSignals("Divya Nair", 55, "Balanced", feedbackRisk = "High"),
            ReporteeSignals("Esha Kapoor", 70, "Balanced", openActions = 3),
            ReporteeSignals("Farhan Ali", 72, "Balanced"),
        )
        val teams = listOf(
            TeamSignals(strength = 10, deployed = 7, free = 3, utilisation = 76, atRisk = 1),
            TeamSignals(strength = 10, deployed = 7, free = 3, utilisation = 76, unallocated = 8, international = 6),
            TeamSignals(strength = 10, deployed = 7, free = 3, utilisation = 76, certGaps = 4),
            TeamSignals(strength = 10, deployed = 10, free = 0, utilisation = 80),
        )
        return people.map { composeReporteeMessage(it, style, monday) } +
            teams.map { composeTeamMessage(it, style, monday) }
    }

    // ── Structure ───────────────────────────────────────────────────────────

    @Test
    fun everyMessage_isGreetingThenBodyThenClosing() {
        allMessages(MessageStyle.TEAMS).forEach { msg ->
            val blocks = msg.split("\n\n")
            assertEquals("Expected greeting, body and closing in:\n$msg", 3, blocks.size)
            assertTrue("Greeting must be one line: $msg", !blocks[0].contains("\n"))
            assertTrue("Closing must be one line: $msg", !blocks[2].contains("\n"))
            blocks.forEach { assertTrue("No empty block allowed:\n$msg", it.isNotBlank()) }
        }
    }

    @Test
    fun everyMessage_staysUnderTheCharacterLimit() {
        allMessages(MessageStyle.TEAMS).forEach {
            assertTrue("Too long (${it.length}):\n$it", it.length <= MESSAGE_LIMIT)
        }
    }

    // ── Forbidden characters ────────────────────────────────────────────────

    @Test
    fun everyMessage_hasNoHyphensBulletsOrEmoji() {
        allMessages(MessageStyle.TEAMS).forEach { msg ->
            assertFalse("Hyphen found:\n$msg", msg.contains("-"))
            assertFalse("En or em dash found:\n$msg", msg.contains("—") || msg.contains("–"))
            assertFalse("Bullet found:\n$msg", msg.contains("•") || msg.contains("·"))
            // Emoji and other symbol categories.
            assertFalse("Pictograph found:\n$msg", Regex("""[\p{So}]""").containsMatchIn(msg))
        }
    }

    @Test
    fun everyMessage_usesFullWordFormsOnly() {
        val contractions = listOf("don't", "won't", "can't", "isn't", "it's", "we're", "you're", "let's")
        allMessages(MessageStyle.TEAMS).forEach { msg ->
            contractions.forEach { c ->
                assertFalse("Contraction '$c' found:\n$msg", msg.contains(c, ignoreCase = true))
            }
        }
    }

    // ── Formatting ──────────────────────────────────────────────────────────

    @Test
    fun plainStyle_emitsNoMarkers() {
        allMessages(MessageStyle.PLAIN).forEach { msg ->
            assertFalse("Plain style must not emit markers:\n$msg", msg.contains("**"))
            assertFalse("Plain style must not emit markers:\n$msg", msg.contains("_"))
        }
    }

    @Test
    fun teamsStyle_boldsExactlyOneAction() {
        // Bold is reserved for the action being set, and overuse is the failure
        // mode the brief calls out, so at most one bold run per message.
        allMessages(MessageStyle.TEAMS).forEach { msg ->
            val bolds = Regex("""\*\*(.+?)\*\*""").findAll(msg).count()
            assertTrue("Expected at most one bold run, found $bolds:\n$msg", bolds <= 1)
        }
    }

    // ── Intent ──────────────────────────────────────────────────────────────

    @Test
    fun theMessageIsNotGeneric_itChangesWithTheSituation() {
        val stretched = composeReporteeMessage(ReporteeSignals("A", 95, "Stretched"), MessageStyle.PLAIN, monday)
        val benched = composeReporteeMessage(ReporteeSignals("A", 30, "On Bench"), MessageStyle.PLAIN, monday)
        val flagged = composeReporteeMessage(ReporteeSignals("A", 55, feedbackRisk = "High"), MessageStyle.PLAIN, monday)
        val clean = composeReporteeMessage(ReporteeSignals("A", 72, "Balanced"), MessageStyle.PLAIN, monday)

        assertEquals("Four situations must give four messages", 4, setOf(stretched, benched, flagged, clean).size)
        assertTrue(stretched.contains("rebalance"))
        assertTrue(benched.contains("pick up next"))
        assertTrue(flagged.contains("feedback"))
        assertTrue(clean.contains("nothing outstanding"))
    }

    @Test
    fun aFlaggedTrainerOutranksACertificationGap() {
        // Only the most important point is made; a note raising four issues at
        // once gets actioned as none of them.
        val both = composeReporteeMessage(
            ReporteeSignals("A", 55, "Balanced", certGaps = 3, feedbackRisk = "High"),
            MessageStyle.PLAIN, monday,
        )
        assertTrue(both.contains("feedback"))
        assertFalse("Must not also lead on certification", both.contains("book the certification"))
    }

    @Test
    fun greetingUsesTheFirstNameOnly() {
        val msg = composeReporteeMessage(ReporteeSignals("Abhinav Samant"), MessageStyle.PLAIN, monday)
        assertTrue(msg.startsWith("Hello Abhinav\n"))
    }

    @Test
    fun weekReference_isWrittenOutWithNoHyphen() {
        assertEquals("10 August to 16 August", weekReference(monday))
        // Any day in the week resolves to the same Monday to Sunday range.
        assertEquals("10 August to 16 August", weekReference(LocalDate.of(2026, 8, 11)))
        assertEquals("10 August to 16 August", weekReference(LocalDate.of(2026, 8, 16)))
    }

    @Test
    fun courseCodesSurviveTheHyphenRule() {
        // The no-hyphen rule is about prose punctuation. Stripping the hyphen
        // out of AZ-305 renames the course, so identifiers are protected.
        val msg = composeReporteeMessage(
            ReporteeSignals("Divya Nair", 55, "Balanced", currentCourse = "AZ-305 Azure Infrastructure"),
            MessageStyle.PLAIN, monday,
        )
        assertTrue("Course code was mangled: $msg", msg.contains("AZ-305"))
        // And nothing else gained a hyphen.
        assertEquals(1, Regex("-").findAll(msg).count())
    }
}
