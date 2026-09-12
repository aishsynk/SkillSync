package com.example.skillsync.ui

import com.example.skillsync.feature.training.ui.BatchShare
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
        participants = "30",
        location = "Gurgaon, India",
        vendor = "Microsoft",
        reference = "264587",
        assignmentLevel = "6",
        tocUrl = "https://www.koenig-solutions.com/CourseContent/custom/PL300.pdf",
    )

    private fun plain(recipient: String = "Team") =
        BatchShare.plainMessage(batch, recipient = recipient)

    /** "Hi Team," on line one, the summary on line two. */
    @Test
    fun message_opensWithGreetingThenSummary() {
        val lines = plain("Abhinav Samant").lines()
        assertEquals("Hi Abhinav,", lines[0])
        assertEquals("New assignment open for allocation.", lines[1])
    }

    @Test
    fun message_carriesEveryFieldRmsReturned_asItsOwnLabelledLine() {
        val m = plain()
        listOf(
            "Course : PL-300T00: Design and Manage Analytics Solutions Using Power BI",
            "Assignment ID : 264587",
            "Schedule : 01 Oct 2026 to 05 Oct 2026",
            "Daily Time : 12:30 to 20:30 IST",
            "Delivery Mode : ILO",
            "Location : Gurgaon, India",
            "Customer : Microsoft",
            "Language : French",
            "Pax Count : 30",
            "Assignment Level : 6",
            "TOC : https://www.koenig-solutions.com/CourseContent/custom/PL300.pdf",
        ).forEach { assertTrue("missing line '$it' in:\n$m", m.contains(it)) }
    }

    @Test
    fun message_asksForTheSkillMarkAndTheCertificationPreference() {
        val m = plain()
        assertTrue(m.contains("mark your skill in RMS at level 6 or above"))
        assertTrue(m.contains("with a live date before the assignment start date"))
        assertTrue(m.contains("Preference is given to certified trainers"))
    }

    /** With no level on the assignment the instruction still reads correctly. */
    @Test
    fun message_fallsBackWhenAssignmentLevelIsUnknown() {
        val m = BatchShare.plainMessage(batch.copy(assignmentLevel = ""))
        assertFalse(m.contains("Assignment Level :"))
        assertTrue(m.contains("mark your skill in RMS at the assignment level or above"))
    }

    /** No signature: the sender is already visible in the chat. */
    @Test
    fun message_doesNotSignOff() {
        val m = plain()
        listOf("Koenig Solutions", "Delivery Manager", "Regards", "Aishwar", "Thank you").forEach {
            assertFalse("'$it' should not appear in:\n$m", m.contains(it))
        }
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
        assertTrue("single day must not read as a range", m.contains("Schedule : 18 Aug 2026"))
        assertFalse(m.contains("18 Aug 2026 to"))
        assertFalse("zero pax should be omitted", m.contains("Pax Count :"))
        assertFalse("blank language should be omitted", m.contains("Language :"))
        assertFalse("blank location should be omitted", m.contains("Location :"))
        assertFalse("blank daily time should be omitted", m.contains("Daily Time :"))
    }

    @Test
    fun message_staysWithinTheCharacterLimit() {
        assertTrue(plain().length <= 1200)
    }

    /** Underline every date line; bold the action; italicise the preference note. */
    @Test
    fun htmlMessage_appliesEmphasisToTheRightThings() {
        val h = BatchShare.htmlMessage(batch)
        assertTrue(h.contains("Schedule : <u>01 Oct 2026 to 05 Oct 2026</u>"))
        assertTrue(h.contains("Daily Time : <u>12:30 to 20:30 IST</u>"))
        assertTrue(h.contains("<b>mark your skill in RMS at level 6 or above, with a live date before the assignment start date</b>"))
        assertTrue(h.contains("<i>Preference is given to certified trainers"))
    }

    /** Emphasis is never stacked. */
    @Test
    fun htmlMessage_doesNotNestEmphasis() {
        val h = BatchShare.htmlMessage(batch)
        listOf("<b><i", "<i><b", "<b><u", "<u><b", "<i><u", "<u><i", "</u></b>", "</b></u>").forEach {
            assertFalse("stacked emphasis '$it' in:\n$h", h.contains(it))
        }
    }

    /** Viber renders `*bold*` and `_italic_` but has no underline. */
    @Test
    fun viberMessage_usesOnlyMarkersViberRenders() {
        val m = BatchShare.composeMessage(batch)
        assertTrue(m.contains("*mark your skill in RMS at level 6 or above"))
        assertTrue(m.contains("_Preference is given to certified trainers"))
        assertTrue("dates must not carry underline markers", m.contains("Schedule : 01 Oct 2026 to 05 Oct 2026"))
        assertFalse(m.contains("__"))
        assertFalse(m.contains("<u>"))
    }

    @Test
    fun message_greetsByFirstNameOnly() {
        assertTrue(plain("Niharika  Niharika").startsWith("Hi Niharika,"))
        assertTrue(plain().startsWith("Hi Team,"))
    }

    @Test
    fun composeWithIntent_embedsManagerIntentWhenProvided() {
        val intentText = "Urgent requirement for client delivery next week."
        val msg = BatchShare.composeWithIntent(batch, recipient = "Abhinav Samant", myMessage = intentText)
        assertTrue(msg.startsWith("Hi Abhinav,\n\nUrgent requirement for client delivery next week."))
        assertTrue(msg.contains("Course : PL-300T00: Design and Manage Analytics Solutions Using Power BI"))
        assertTrue(msg.contains("Schedule : 01 Oct 2026 to 05 Oct 2026"))
    }

    @Test
    fun composeWithIntent_stripsCustomerFinancialsAndPrivateData() {
        val msg = BatchShare.composeWithIntent(batch, recipient = "Team")
        // CommunicationPurpose.BATCH_INVITATION allowlist permits course_title, batch_id, schedule, mode, location
        // Customer name is stripped from broadcast invitation to protect commercial privacy
        assertFalse(msg.contains("Customer : Microsoft"))
    }

    @Test
    fun composeExternalStaffingRequest_formatsCleanInquiryWithoutMarginLeaks() {
        val req = BatchShare.composeExternalStaffingRequest(
            courseName = "AZ-104: Microsoft Azure Administrator",
            dates = "10 Nov 2026 to 14 Nov 2026",
            deliveryMode = "Virtual Instructor-Led",
            location = "London, UK",
            myMessage = "We are seeking a certified Azure trainer.",
        )
        assertTrue(req.contains("Dear Trainer / Partner,"))
        assertTrue(req.contains("We are seeking a certified Azure trainer."))
        assertTrue(req.contains("Course / Technology : AZ-104: Microsoft Azure Administrator"))
        assertTrue(req.contains("Target Schedule : 10 Nov 2026 to 14 Nov 2026"))
        assertTrue(req.contains("Delivery Mode : Virtual Instructor-Led"))
        assertTrue(req.contains("Location : London, UK"))
        assertFalse(req.contains("margin"))
        assertFalse(req.contains("rate"))
    }
}
