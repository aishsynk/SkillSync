package com.example.skillsync.ui.batch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BulkBatchShareTest {

    private fun batch(
        courseName: String = "Kotlin for Beginners",
        startDate: String = "01 Oct 2026",
        endDate: String = "05 Oct 2026",
        sessionTime: String = "",
        deliveryMode: String = "",
        language: String = "",
        participants: String = "",
        location: String = "",
        vendor: String = "",
        reference: String = "",
    ) = BatchShare.Batch(
        courseName = courseName,
        startDate = startDate,
        endDate = endDate,
        sessionTime = sessionTime,
        deliveryMode = deliveryMode,
        language = language,
        participants = participants,
        location = location,
        vendor = vendor,
        reference = reference,
    )

    @Test
    fun emptyList_showsNoBatchesMessage() {
        val msg = BulkBatchShare.composeBulkMessage(emptyList(), recipient = "Team")
        assertTrue(msg.startsWith("Hello Team,"))
        assertTrue(msg.contains("There are no batches open for allocation at this time."))
        assertTrue(msg.contains("I will share new demand as soon as it arrives."))
        assertTrue(msg.endsWith("_Thank you._"))
        // Plain variant has no markers
        val plain = BulkBatchShare.plainBulkMessage(emptyList())
        assertTrue(plain.endsWith("Thank you."))
        assertFalse(plain.contains("_Thank you._"))
        assertFalse(plain.contains("<i>"))
    }

    @Test
    fun singleBatch_isNumberedAndFormatted() {
        val b = batch(
            courseName = "Power BI Dashboard in a Day",
            startDate = "01 Oct 2026",
            endDate = "05 Oct 2026",
            sessionTime = "12:30 to 20:30 IST",
            deliveryMode = "ILO",
            language = "English",
            participants = "5",
            location = "Gurgaon, India",
            vendor = "Microsoft",
            reference = "REF123",
        )
        val msg = BulkBatchShare.composeBulkMessage(listOf(b), recipient = "Team")
        assertTrue("singular heading", msg.contains("There is 1 batch open for allocation."))
        assertTrue("numbered entry", msg.contains("1. _Power BI Dashboard in a Day_"))
        assertTrue("window range", msg.contains("from 01 Oct 2026 to 05 Oct 2026"))
        assertTrue("session time", msg.contains("12:30 to 20:30 IST"))
        assertTrue(msg.contains("ILO"))
        assertTrue(msg.contains("Language English"))
        assertTrue(msg.contains("5 pax"))
        assertTrue(msg.contains("Gurgaon, India"))
        assertTrue(msg.contains("Microsoft"))
        assertTrue(msg.contains("(Ref REF123)"))
        assertTrue(msg.contains("*mark your skill in RMS at level 4 or below*"))
    }

    @Test
    fun twentySixBatches_truncatesToTwentyFivePlusAndMore() {
        val list = (1..26).map { i ->
            batch(courseName = "Course $i", startDate = "01 Oct 2026", endDate = "05 Oct 2026")
        }
        val msg = BulkBatchShare.composeBulkMessage(list)
        assertTrue(msg.contains("There are 26 batches open for allocation."))
        assertTrue(msg.contains("1. _Course 1_"))
        assertTrue(msg.contains("25. _Course 25_"))
        assertFalse("26th should not be shown", msg.contains("26. _Course 26_"))
        assertTrue(msg.contains("and 1 more. Please check the Demand Desk for the full list."))
        // Exactly 25 should not have truncation note
        val list25 = (1..25).map { i -> batch(courseName = "Course $i") }
        val msg25 = BulkBatchShare.composeBulkMessage(list25)
        assertFalse(msg25.contains("more. Please check the Demand Desk"))
        assertTrue(msg25.contains("25. _Course 25_"))
    }

    @Test
    fun charLimit_trimmingPreservesGreetingAndClosing() {
        // Each entry ~170 chars, 30 such entries will force char trimming beyond line cap
        val longCourse = "A".repeat(120) + " Very Long Course Title That Exceeds Normal Length"
        val list = (1..30).map { i ->
            batch(
                courseName = "$longCourse $i",
                startDate = "01 Oct 2026",
                endDate = "05 Oct 2026",
                deliveryMode = "ILO Classroom Very Long Delivery Descriptor",
                location = "Gurgaon, India, Sector 18, Very Long Location Name To Inflate Characters",
                vendor = "Microsoft Long Vendor Name Incorporated",
                participants = "15",
                reference = "REF${1000 + i}",
            )
        }
        val msg = BulkBatchShare.composeBulkMessage(list, recipient = "Team")
        assertTrue("must respect 3500 limit, was ${msg.length}", msg.length <= 3500)
        assertTrue(msg.startsWith("Hello Team,"))
        assertTrue(msg.endsWith("_Thank you._"))
        // Greeting + closing preserved, action paragraph ideally preserved
        assertTrue(msg.contains("If you can take any of these"))
        // Should indicate truncation
        assertTrue(msg.contains("Please check the Demand Desk for the full list."))
        // Plain variant also respects limit
        val plain = BulkBatchShare.plainBulkMessage(list)
        assertTrue(plain.length <= 3500)
        assertTrue(plain.startsWith("Hello Team,"))
        assertTrue(plain.endsWith("Thank you."))
    }

    @Test
    fun composeUsesViberMarkers() {
        val b = batch(courseName = "Azure Fundamentals")
        val msg = BulkBatchShare.composeBulkMessage(listOf(b))
        assertTrue(msg.contains("_Azure Fundamentals_"))
        assertTrue(msg.contains("*mark your skill in RMS at level 4 or below*"))
        assertFalse(msg.contains("<b>"))
        assertFalse(msg.contains("<i>"))
        assertFalse(msg.contains("<u>"))
    }

    @Test
    fun plainHasNoMarkers() {
        val b = batch(courseName = "Azure Fundamentals")
        val msg = BulkBatchShare.plainBulkMessage(listOf(b))
        assertTrue(msg.contains("Azure Fundamentals"))
        assertFalse(msg.contains("*mark your skill"))
        assertFalse(msg.contains("_Azure Fundamentals_"))
        assertFalse(msg.contains("<b>"))
        assertFalse(msg.contains("<i>"))
        assertFalse(msg.contains("<u>"))
        assertTrue(msg.contains("mark your skill in RMS at level 4 or below"))
    }

    @Test
    fun htmlUsesTagsAndBr() {
        val b = batch(courseName = "Azure Fundamentals", startDate = "01 Oct 2026", endDate = "05 Oct 2026")
        val html = BulkBatchShare.htmlBulkMessage(listOf(b))
        assertTrue(html.contains("<b>mark your skill in RMS at level 4 or below</b>"))
        assertTrue(html.contains("<i>Azure Fundamentals</i>"))
        assertTrue(html.contains("<u>from 01 Oct 2026 to 05 Oct 2026</u>"))
        assertTrue(html.contains("<i>Thank you.</i>"))
        assertTrue(html.contains("<br>"))
        assertFalse(html.contains("*mark your skill"))
        assertFalse(html.contains("_Azure Fundamentals_"))
    }

    @Test
    fun viberNoUnderlineForDates() {
        val b = batch(
            courseName = "Data 101",
            startDate = "01 Oct 2026",
            endDate = "05 Oct 2026",
            sessionTime = "09:00 to 17:00 IST",
        )
        val compose = BulkBatchShare.composeBulkMessage(listOf(b))
        // Viber drops underline, so dates appear plain without markers
        assertTrue(compose.contains("from 01 Oct 2026 to 05 Oct 2026"))
        assertTrue(compose.contains("09:00 to 17:00 IST"))
        assertFalse(compose.contains("<u>"))
        // Must not have underline markers wrapped as literal punctuation
        // Compose underline is identity, so no extra wrapping
        assertFalse(compose.contains("__"))
    }

    @Test
    fun extras_vendorLocationParticipantsAreIncluded() {
        val b = batch(
            courseName = "Leadership 101",
            deliveryMode = "ILO",
            language = "Spanish",
            participants = "12",
            location = "Dubai",
            vendor = "Acme Corp",
        )
        val msg = BulkBatchShare.plainBulkMessage(listOf(b))
        assertTrue(msg.contains("ILO"))
        assertTrue(msg.contains("Language Spanish"))
        assertTrue(msg.contains("12 pax"))
        assertTrue(msg.contains("Dubai"))
        assertTrue(msg.contains("Acme Corp"))
        // Zero participants omitted
        val zero = batch(participants = "0")
        val msgZero = BulkBatchShare.plainBulkMessage(listOf(zero))
        assertFalse(msgZero.contains("0 pax"))
        // Blank participants omitted
        val blank = batch(participants = "")
        val msgBlank = BulkBatchShare.plainBulkMessage(listOf(blank))
        assertFalse(msgBlank.contains("pax"))
    }

    @Test
    fun reference_isIncluded() {
        val withRef = batch(reference = "REF999")
        val msg = BulkBatchShare.plainBulkMessage(listOf(withRef))
        assertTrue(msg.contains("(Ref REF999)"))
        val withoutRef = batch(reference = "")
        val msg2 = BulkBatchShare.plainBulkMessage(listOf(withoutRef))
        assertFalse(msg2.contains("(Ref"))
        val vendorRef = batch(vendor = "Microsoft", reference = "264587")
        val msg3 = BulkBatchShare.plainBulkMessage(listOf(vendorRef))
        assertTrue(msg3.contains("Microsoft"))
        assertTrue(msg3.contains("(Ref 264587)"))
    }

    @Test
    fun greeting_usesFirstNameOnly() {
        val b = batch()
        val msg1 = BulkBatchShare.composeBulkMessage(listOf(b), recipient = "Abhinav Samant")
        assertTrue(msg1.startsWith("Hello Abhinav,"))
        val msg2 = BulkBatchShare.composeBulkMessage(listOf(b), recipient = "Niharika  Niharika")
        assertTrue(msg2.startsWith("Hello Niharika,"))
        val msg3 = BulkBatchShare.composeBulkMessage(listOf(b), recipient = "Team")
        assertTrue(msg3.startsWith("Hello Team,"))
        val msg4 = BulkBatchShare.composeBulkMessage(listOf(b), recipient = "")
        assertTrue(msg4.startsWith("Hello Team,"))
        val msg5 = BulkBatchShare.plainBulkMessage(emptyList(), recipient = "  John  ")
        assertTrue(msg5.startsWith("Hello John,"))
    }

    @Test
    fun windowDates_logic() {
        // Blank start -> dates still to be confirmed
        val blank = batch(startDate = "", endDate = "")
        val msgBlank = BulkBatchShare.plainBulkMessage(listOf(blank))
        assertTrue(msgBlank.contains("on dates still to be confirmed"))
        // Single day (end blank or same as start)
        val singleEndBlank = batch(startDate = "01 Oct 2026", endDate = "")
        val msgSingle1 = BulkBatchShare.plainBulkMessage(listOf(singleEndBlank))
        assertTrue(msgSingle1.contains("on 01 Oct 2026"))
        assertFalse(msgSingle1.contains("from 01 Oct"))
        val singleSame = batch(startDate = "01 Oct 2026", endDate = "01 Oct 2026")
        val msgSingle2 = BulkBatchShare.plainBulkMessage(listOf(singleSame))
        assertTrue(msgSingle2.contains("on 01 Oct 2026"))
        assertFalse(msgSingle2.contains("to 01 Oct"))
        // Range
        val range = batch(startDate = "01 Oct 2026", endDate = "05 Oct 2026")
        val msgRange = BulkBatchShare.plainBulkMessage(listOf(range))
        assertTrue(msgRange.contains("from 01 Oct 2026 to 05 Oct 2026"))
        // HTML underlines the window
        val htmlRange = BulkBatchShare.htmlBulkMessage(listOf(range))
        assertTrue(htmlRange.contains("<u>from 01 Oct 2026 to 05 Oct 2026</u>"))
        val htmlSingle = BulkBatchShare.htmlBulkMessage(listOf(singleEndBlank))
        assertTrue(htmlSingle.contains("<u>on 01 Oct 2026</u>"))
    }
}
