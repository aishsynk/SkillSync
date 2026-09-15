package com.example.skillsync.feature.communication
import com.example.skillsync.feature.communication.engine.MessageRewriter

import com.example.skillsync.feature.communication.engine.MESSAGE_LIMIT
import com.example.skillsync.feature.communication.engine.MessageStyle
import com.example.skillsync.feature.communication.engine.ReporteeSignals
import com.example.skillsync.feature.communication.engine.TeamSignals
import com.example.skillsync.feature.communication.engine.composeManagerStandpointNote
import com.example.skillsync.feature.communication.engine.composeReporteeMessage
import com.example.skillsync.feature.communication.engine.composeTeamMessage
import com.example.skillsync.feature.communication.engine.weekReference
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

    // ── Manager's own words ─────────────────────────────────────────────────

    @Test
    fun aManagerNoteLeadsTheMessage() {
        // The house style treats the manager's words as the primary intent;
        // the generated summary follows as supporting context.
        val msg = composeReporteeMessage(
            ReporteeSignals("Abhinav Samant", 72, "Balanced"),
            MessageStyle.PLAIN, monday,
            managerNote = "Thanks for covering the Dubai batch at short notice",
        )
        val body = msg.split("\n\n")[1]
        assertTrue("The note must lead the body: $msg",
                   body.startsWith("Thanks for covering the Dubai batch at short notice."))
        assertTrue(body.contains("Here is a quick summary"))
    }

    @Test
    fun aManagerNoteIsHeldToTheSameHouseStyle() {
        val msg = composeReporteeMessage(
            ReporteeSignals("A", 72, "Balanced"), MessageStyle.PLAIN, monday,
            managerNote = "Don't worry about the mid-week slot - it's covered",
        )
        assertFalse("Contractions must be expanded: $msg", msg.contains("Don't"))
        assertFalse("Hyphens must be stripped: $msg", msg.contains("-"))
        assertTrue(msg.contains("Do not worry"))
    }

    @Test
    fun aNoteDoesNotBreakTheCharacterLimit() {
        val msg = composeTeamMessage(
            TeamSignals(strength = 10, deployed = 7, free = 3, utilisation = 76,
                        unallocated = 8, international = 6, certGaps = 4),
            MessageStyle.TEAMS, monday,
            managerNote = "A ".repeat(400),
        )
        assertTrue("Too long (${msg.length})", msg.length <= MESSAGE_LIMIT)
    }

    @Test
    fun anEmptyNoteChangesNothing() {
        val a = composeReporteeMessage(ReporteeSignals("A", 72, "Balanced"), MessageStyle.PLAIN, monday)
        val b = composeReporteeMessage(ReporteeSignals("A", 72, "Balanced"), MessageStyle.PLAIN, monday, "")
        assertEquals(a, b)
    }

    // ── Semantic correctness (Communication Intelligence rebuild, Phase C3) ──

    /**
     * The exact regression this test guards against: TeamSignals.free is an
     * aggregate headcount with no per-person verification behind it. A team
     * broadcast must ask people to confirm availability, never assert a count
     * of "N of you are available" — that was the literal bad example the
     * rebuild started from ("There are 5 open batches... and 2 of us are free").
     */
    @Test
    fun unallocatedDemandMessage_neverClaimsAnAggregateFreeHeadcount() {
        val msg = composeTeamMessage(
            TeamSignals(strength = 10, deployed = 7, free = 3, utilisation = 76, unallocated = 5),
            MessageStyle.PLAIN, monday,
        )
        assertFalse(msg.contains(Regex("""\d+\s+of (us|you)\s+(is|are)""")))
        assertFalse(msg.contains("free"))
        // It must still ask the team to act — honesty is not the same as silence.
        assertTrue(msg.contains("confirm your availability"))
    }

    // ── Closing emphasis ────────────────────────────────────────────────────

    @Test
    fun theClosingCarriesLightEmphasisInTeamsStyle() {
        val msg = composeReporteeMessage(ReporteeSignals("A", 72, "Balanced"), MessageStyle.TEAMS, monday)
        val closing = msg.split("\n\n").last()
        assertTrue("Closing should be italicised: $closing", closing.startsWith("_") && closing.endsWith("_"))
    }

    @Test
    fun plainStyleStillEmitsNoMarkers() {
        val msg = composeReporteeMessage(ReporteeSignals("A", 72, "Balanced"), MessageStyle.PLAIN, monday)
        assertFalse(msg.contains("_"))
    }

    // ── Manager Standpoint ───────────────────────────────────────────────────

    @Test
    fun managerStandpoint_containsStandpointMockReadinessAndImmediateFocus() {
        val signals = ReporteeSignals(
            name = "Abhinav Samant",
            utilisation = 88,
            capacityBucket = "Stretched",
            readiness = 82,
            certGaps = 1,
            certGapCourses = listOf("AZ-104"),
            learnerRating = 4.2,
            learnerRatingCount = 5,
            learnerRecentDate = "10 Aug 2026",
        )
        val note = composeManagerStandpointNote(signals, MessageStyle.TEAMS)
        assertTrue(note.contains("Weekly Manager Standpoint for Abhinav"))
        assertTrue(note.contains("**Standpoint:**"))
        assertTrue(note.contains("High Workload (Stretched at 88% util)"))
        // Evidence-only: learner rating replaces the old mock & readiness boilerplate
        assertTrue(note.contains("**Learner rating 90 day:**"))
        assertTrue(note.contains("4.2/5 from 5 responses"))
        assertTrue(note.contains("**Immediate Focus:**"))
        assertTrue(note.contains("AZ-104") && note.contains("certification exam"))
        assertFalse(note.contains("Mock & Readiness"))
        assertFalse(note.contains("Goal → Steps → Verify"))
        assertFalse(note.contains("•"))
    }

    @Test
    fun managerStandpoint_handlesBenchAndFeedbackRisk() {
        val signals = ReporteeSignals(
            name = "Divya Nair",
            utilisation = 20,
            capacityBucket = "On Bench",
            feedbackRisk = "High",
            targetGrowthCourses = listOf("DP-203"),
            negativeFeedbackCount = 2,
            hrNegativeCount = 1,
        )
        val note = composeManagerStandpointNote(signals, MessageStyle.PLAIN)
        assertTrue(note.contains("Weekly Manager Standpoint for Divya"))
        assertTrue(note.contains("Standpoint: Available / On Bench (20% util)"))
        assertTrue(note.contains("Learner rating 90 day:"))
        assertTrue(note.contains("Immediate Focus: review the 2 negative feedback"))
        assertFalse(note.contains("•"))
    }

    // MessageRewriter (the free-text "[User Message]"/"[My Message]" intent-
    // precedence engine) was retired in Phase 2 of the architecture
    // restructuring — it had zero production callers left after Phase 1
    // moved report screens off it, and its intent-precedence model is
    // exactly the pattern the manager-communication contract now forbids.
    // See feature/communication/domain/ManagerCommunicationComposer for its
    // structured-contract replacement, covered by
    // ManagerCommunicationComposerTest.
}
