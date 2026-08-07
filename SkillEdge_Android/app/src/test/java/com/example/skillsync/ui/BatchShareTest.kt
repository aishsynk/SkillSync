package com.example.skillsync.ui

import com.example.skillsync.ui.batch.BatchShare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchShareTest {

    private val batch = BatchShare.Batch(
        courseName = "PL-300T00: Design and Manage Analytics Solutions Using Power BI",
        startDate = "01 Oct 2026",
        endDate = "05 Oct 2026",
        sessionTime = "12:30 to 20:30 IST",
        days = 5,
        deliveryMode = "ILO",
        language = "French",
        participants = "1",
        location = "Gurgaon, India",
        vendor = "Microsoft",
        reference = "264587",
        tocUrl = "https://www.koenig-solutions.com/CourseContent/custom/PL300.pdf",
    )

    private fun plain(recipient: String = "Team") =
        BatchShare.plainMessage(batch, recipient = recipient)

    /** Greeting on its own line, body next, closing last. */
    @Test
    fun message_isStructuredAsGreetingBodyClosing() {
        val m = plain("Abhinav Samant")
        val lines = m.lines()
        assertEquals("Hello Abhinav,", lines.first())
        assertEquals("", lines[1])
        assertEquals("Thank you.", lines.last())
    }

    @Test
    fun message_carriesTheFactsATrainerNeedsToDecide() {
        val m = plain()
        listOf(
            "PL-300T00: Design and Manage Analytics Solutions Using Power BI",
            "from 01 Oct 2026 to 05 Oct 2026",
            "12:30 to 20:30 IST",
            "Delivery is ILO",
            "the language is French",
            "1 participant",
            "Gurgaon, India",
            "The reference is 264587",
            "mark your skill in RMS at level 4 or below",
            "confirm here by end of day",
        ).forEach { assertTrue("missing '$it' in:\n$m", m.contains(it)) }
    }

    /**
     * No signature. These go to the manager's own team in a chat where the
     * sender is already visible, so naming themselves is boilerplate.
     */
    @Test
    fun message_doesNotSignOff() {
        val m = plain()
        listOf("Koenig Solutions", "Delivery Manager", "Regards", "Aishwar").forEach {
            assertFalse("'$it' should not appear in:\n$m", m.contains(it))
        }
    }

    /** House style: no decorative symbols, and full word forms only. */
    @Test
    fun message_avoidsDecorationAndContractions() {
        val m = plain()
        listOf("—", "–", "•", "*", "_", ">", "|").forEach {
            assertFalse("'$it' should not appear in:\n$m", m.contains(it))
        }
        listOf("can't", "don't", "you're", "we'll", "isn't").forEach {
            assertFalse("contraction '$it' in:\n$m", m.contains(it))
        }
    }

    @Test
    fun message_staysShort() {
        val m = plain()
        assertTrue("over the 1000 character limit: ${m.length}", m.length <= 1000)
        // The old version ran past 700 characters of label lines; prose is tighter.
        assertTrue("message drifted long again: ${m.length}", m.length <= 700)
    }

    /** Bold the action, italicise the course name, underline every time reference. */
    @Test
    fun htmlMessage_appliesEmphasisToTheRightThings() {
        val h = BatchShare.htmlMessage(batch)
        assertTrue(h.contains("<b>mark your skill in RMS at level 4 or below</b>"))
        assertTrue(h.contains("<i>PL-300T00: Design and Manage Analytics Solutions Using Power BI</i>"))
        assertTrue(h.contains("<u>from 01 Oct 2026 to 05 Oct 2026</u>"))
        assertTrue(h.contains("<u>12:30 to 20:30 IST</u>"))
        assertTrue(h.contains("<u>end of day</u>"))
        assertTrue(h.contains("<i>Thank you.</i>"))
    }

    /** Emphasis is never stacked, so no marker immediately opens inside another. */
    @Test
    fun htmlMessage_doesNotNestEmphasis() {
        val h = BatchShare.htmlMessage(batch)
        listOf("<b><i", "<i><b", "<b><u", "<u><b", "<i><u", "<u><i").forEach {
            assertFalse("stacked emphasis '$it' in:\n$h", h.contains(it))
        }
    }

    /**
     * Viber understands `*bold*` and `_italic_` but has no underline, so time
     * references stay plain rather than carrying markers that would render as
     * literal punctuation.
     */
    @Test
    fun viberMessage_usesOnlyMarkersViberRenders() {
        val m = BatchShare.composeMessage(batch)
        assertTrue(m.contains("*mark your skill in RMS at level 4 or below*"))
        assertTrue(m.contains("_PL-300T00: Design and Manage Analytics Solutions Using Power BI_"))
        assertTrue("dates must not carry underline markers", m.contains("from 01 Oct 2026 to 05 Oct 2026, 12:30 to 20:30 IST."))
        assertFalse(m.contains("__"))
    }

    @Test
    fun message_omitsFieldsRmsDidNotReturn() {
        val m = BatchShare.plainMessage(
            BatchShare.Batch(
                courseName = "Power BI Dashboard in a Day",
                startDate = "18 Aug 2026",
                endDate = "18 Aug 2026",
                sessionTime = "",
                deliveryMode = "ILO",
                participants = "0",
                reference = "264455",
            )
        )
        assertTrue("single day must not read as a range", m.contains("on 18 Aug 2026."))
        assertFalse(m.contains("18 Aug 2026 to"))
        assertFalse("zero pax should be omitted", m.contains("0 participant"))
        assertFalse("blank language should be omitted", m.contains("the language is"))
        assertFalse("blank location should be omitted", m.contains("the location is"))
        // A missing session time drops the clause rather than inventing one.
        assertFalse(m.contains(", ."))
    }

    @Test
    fun message_greetsTheTeamWhenBroadcasting() {
        assertTrue(plain().startsWith("Hello Team,"))
    }

    /** RMS names carry doubled spaces and repeated surnames; greet by first name. */
    @Test
    fun message_greetsByFirstNameOnly() {
        assertTrue(plain("Niharika  Niharika").startsWith("Hello Niharika,"))
    }
}
