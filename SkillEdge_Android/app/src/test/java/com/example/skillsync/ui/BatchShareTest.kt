package com.example.skillsync.ui

import com.example.skillsync.ui.batch.BatchShare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchShareTest {

    private fun msg() = BatchShare.composeMessage(
        courseName = "55071-A Microsoft Software Asset Manager",
        startDate = "24 Aug",
        endDate = "25 Aug",
        deliveryMode = "ILO",
        reference = "266386",
        participants = "12",
        location = "Gurgaon, India",
    )

    @Test
    fun message_carriesEverySignalATrainerNeeds() {
        val m = msg()
        listOf("55071-A Microsoft Software Asset Manager", "24 Aug", "25 Aug",
               "ILO", "266386", "12 participants", "Gurgaon").forEach {
            assertTrue("missing '$it' in:\n$m", m.contains(it))
        }
        assertTrue(m.startsWith("Hello Team,"))
        assertTrue(m.trimEnd().endsWith("Thank you."))
    }

    /** Viber renders plain text, so markdown would show up as literal punctuation. */
    @Test
    fun viberMessage_containsNoMarkdownNoise() {
        val m = msg()
        listOf("*", "_", "#", "•").forEach {
            assertFalse("'$it' would render literally in Viber:\n$m", m.contains(it))
        }
    }

    @Test
    fun message_staysWithinLimit() {
        assertTrue(msg().length <= 1000)
        // The realistic case should stay compact enough to read at a glance.
        assertTrue("message too long: ${msg().length}", msg().length <= 400)
    }

    @Test
    fun message_handlesSingleDayAndMissingOptionalFields() {
        val m = BatchShare.composeMessage(
            courseName = "Power BI Dashboard in a Day",
            startDate = "18 Aug", endDate = "18 Aug",
            deliveryMode = "ILO", reference = "264455",
        )
        assertTrue(m.contains("on 18 Aug"))
        assertFalse(m.contains("to 18 Aug"))
        assertFalse("zero pax should be omitted", m.contains("0 participants"))
    }

    @Test
    fun richText_keepsEmphasisForMarkdownTargets() {
        val m = BatchShare.asRichText(
            courseName = "55071-A Microsoft Software Asset Manager",
            startDate = "24 Aug 2026", endDate = "25 Aug 2026",
            deliveryMode = "ILO", reference = "266386", participants = "12",
        )
        assertTrue(m.contains("*Please note*"))
        assertTrue(m.contains("**mark your skill**"))
        assertTrue(m.contains("__24 Aug 2026 to 25 Aug 2026__"))
        assertTrue(m.contains("*ILO*"))
    }
}
