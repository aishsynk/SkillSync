package com.example.skillsync.ui

import com.example.skillsync.ui.batch.BatchShare
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchShareTest {

    private val batch = BatchShare.Batch(
        courseName = "55071-A Microsoft Software Asset Manager",
        startDate = "24 Aug 2026",
        endDate = "25 Aug 2026",
        sessionTime = "09:00 - 17:00 IST",
        days = 2,
        deliveryMode = "ILO",
        language = "English",
        participants = "12",
        location = "Gurgaon, India",
        vendor = "Microsoft",
        reference = "266386",
        tocUrl = "https://www.koenig-solutions.com/CourseContent/custom/55071A.pdf",
    )

    private val sender = BatchShare.Sender("Aishwar Nigam", "Delivery Manager")

    private fun msg(recipient: String = "Team") =
        BatchShare.composeMessage(batch, recipient = recipient, sender = sender)

    /**
     * The spec for these messages: name the trainer, the course, the date, the
     * time, the action and a closing. Partial messages are the defect being
     * guarded against, so every element is asserted individually.
     */
    @Test
    fun message_containsEveryRequiredElement() {
        val m = msg(recipient = "Abhinav Samant")
        mapOf(
            "trainer name" to "Hello Abhinav,",
            "course name" to "55071-A Microsoft Software Asset Manager",
            "start date" to "24 Aug 2026",
            "end date" to "25 Aug 2026",
            "session time" to "09:00 - 17:00 IST",
            "required action" to "Action required:",
            "the actual ask" to "mark your skill in RMS",
            "response deadline" to "Please respond by end of day",
            "closing" to "Thank you,",
            "signature" to "Aishwar Nigam",
            "sender title" to "Delivery Manager, Koenig Solutions",
        ).forEach { (what, text) ->
            assertTrue("missing $what ('$text') in:\n$m", m.contains(text))
        }
    }

    @Test
    fun message_carriesTheDeliveryFacts() {
        val m = msg()
        listOf("ILO", "English", "12", "Gurgaon, India", "Microsoft", "266386", "(2 days)")
            .forEach { assertTrue("missing '$it' in:\n$m", m.contains(it)) }
    }

    /** A broadcast names the people who can actually deliver it. */
    @Test
    fun broadcast_namesMatchedCandidates() {
        val m = BatchShare.composeMessage(
            batch, recipient = "Team",
            candidates = listOf("Abhinav Samant", "Niharika Niharika"),
            sender = sender,
        )
        assertTrue(m.startsWith("Hello Team,"))
        assertTrue(m.contains("Matched on skill: Abhinav Samant, Niharika Niharika"))
    }

    /** Viber renders plain text, so markdown would show up as literal punctuation. */
    @Test
    fun viberMessage_containsNoMarkdownNoise() {
        val m = msg()
        listOf("*", "_", "•").forEach {
            assertFalse("'$it' would render literally in Viber:\n$m", m.contains(it))
        }
    }

    /**
     * A missing time is stated, not dropped. Silently omitting the line is how the
     * message ends up looking incomplete to the trainer reading it.
     */
    @Test
    fun message_statesMissingTimeRatherThanOmittingIt() {
        val m = BatchShare.composeMessage(batch.copy(sessionTime = ""), sender = sender)
        assertTrue(m.contains("Time: To be confirmed"))
    }

    @Test
    fun message_handlesSingleDayAndMissingOptionalFields() {
        val m = BatchShare.composeMessage(
            BatchShare.Batch(
                courseName = "Power BI Dashboard in a Day",
                startDate = "18 Aug 2026",
                endDate = "18 Aug 2026",
                sessionTime = "09:00 - 17:00 IST",
                days = 1,
                deliveryMode = "ILO",
                reference = "264455",
                participants = "0",
            ),
            sender = sender,
        )
        assertTrue(m.contains("Dates: 18 Aug 2026 (1 day)"))
        assertFalse("single day must not read as a range", m.contains("18 Aug 2026 to"))
        assertFalse("zero pax should be omitted", m.contains("Participants: 0"))
        assertFalse("blank vendor should not leave an empty line", m.contains("Vendor:"))
    }

    @Test
    fun message_fallsBackWhenSenderUnknown() {
        val m = BatchShare.composeMessage(batch)
        assertTrue(m.contains("Delivery Management, Koenig Solutions"))
    }

    @Test
    fun message_staysWithinLimit() {
        assertTrue("message too long: ${msg().length}", msg().length <= 2000)
    }

    @Test
    fun richText_keepsEmphasisForMarkdownTargets() {
        val m = BatchShare.asRichText(batch, sender = sender)
        assertTrue(m.contains("**Unallocated batch — action required**"))
        assertTrue(m.contains("__24 Aug 2026 to 25 Aug 2026 (2 days)__"))
        assertTrue(m.contains("*Time:* 09:00 - 17:00 IST"))
        assertTrue(m.contains("**4 or below**"))
    }
}
