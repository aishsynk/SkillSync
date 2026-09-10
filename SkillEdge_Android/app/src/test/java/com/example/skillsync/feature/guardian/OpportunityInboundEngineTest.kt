package com.example.skillsync.feature.guardian
import com.example.skillsync.feature.guardian.engine.OpportunityInboundEngine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpportunityInboundEngineTest {

    private fun extract(text: String): Map<String, Any> =
        OpportunityInboundEngine.extractRequirements(text)

    @Test
    fun extractsCourseCodeDatesLocationAndDocuments() {
        val r = extract(
            "Can anyone deliver DP-600 for a client in Dubai from 22 Sep 2026 to 26 Sep 2026? TOC attached.",
        )
        assertEquals("DP-600", r["course_code"])
        assertEquals("2026-09-22", r["dates_start"])
        assertEquals("2026-09-26", r["dates_end"])
        assertEquals("Dubai", r["location"])
        assertEquals("UAE", r["country"])
        assertTrue((r["documentation_mentioned"] as List<*>).isNotEmpty())
        assertEquals("training requirement", r["action"])
    }

    @Test
    fun parsesHyphenatedRangeAndParticipants() {
        val r = extract("Trainer needed for AZ-104 in Singapore, 15-19 August 2026, 12 participants.")
        assertEquals("AZ-104", r["course_code"])
        assertEquals("2026-08-15", r["dates_start"])
        assertEquals("2026-08-19", r["dates_end"])
        assertEquals("Singapore", r["location"])
        assertEquals("Singapore", r["country"])
        assertEquals("12", r["participants"])
    }

    @Test
    fun availabilityCheckHasNoCourse() {
        val r = extract("Availability check - who is free next week for Microsoft Fabric training?")
        assertEquals("", r["course_code"])
        assertEquals("availability check", r["action"])
    }

    @Test
    fun virtualModeDetected() {
        val r = extract("Can you deliver PL-300 online for 8 delegates in September?")
        assertEquals("virtual", r["mode"])
        assertEquals("PL-300", r["course_code"])
    }

    @Test
    fun missingEvidenceFieldsStayEmpty() {
        val r = extract("Hello team")
        assertEquals("", r["course_code"])
        assertEquals("", r["dates_start"])
        assertEquals("", r["location"])
        assertEquals("", r["participants"])
        assertEquals(emptyList<Any>(), r["documentation_mentioned"])
    }
}
