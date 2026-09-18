package com.example.skillsync.feature.training.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Content/portfolio-based fit — the case that started it: a custom Power BI
 * course must surface trainers whose portfolio holds PL-300 even though RMS's
 * course-name matcher would return nothing for it.
 */
class PortfolioContentFitTest {

    private fun fit(demandName: String, demandId: String, held: List<String>) =
        PortfolioContentFit.fit(demandCourseName = demandName, demandCourseId = demandId, heldTitles = held)

    @Test
    fun `custom power bi demand matches trainer holding PL-300`() {
        val result = fit(
            demandName = "Power BI Reporting for Finance Teams",
            demandId = "CUST-PBI-001",
            held = listOf("PL-300: Power BI Data Analyst", "DP-900: Azure Data Fundamentals"),
        )
        assertTrue(result.strong)
        assertTrue("power" in result.matchedTokens)
        assertTrue("bi" in result.matchedTokens)
        assertTrue(result.heldTitles.any { it.contains("PL-300") })
    }

    @Test
    fun `exact course match is always strong via code`() {
        val result = fit(
            demandName = "PL-300 Power BI Data Analyst",
            demandId = "PL-300",
            held = listOf("Pl 300 - Power BI Data Analyst"),
        )
        assertTrue(result.strong)
        assertTrue("pl300" in result.matchedTokens)
        assertEquals(1.0, result.coverage, 0.001)
    }

    @Test
    fun `unrelated technology does not match`() {
        val result = fit(
            demandName = "Advanced Networking on AWS",
            demandId = "NET-501",
            held = listOf("AI-102: Develop AI Solutions in Azure"),
        )
        assertFalse(result.strong)
    }

    @Test
    fun `certification name counts as portfolio content`() {
        val result = fit(
            demandName = "Power BI Dashboards",
            demandId = "PBI-200",
            held = listOf("Microsoft Certified: Power BI Data Analyst Associate"),
        )
        assertTrue(result.strong)
        assertTrue("power" in result.matchedTokens)
        assertTrue("bi" in result.matchedTokens)
    }

    @Test
    fun `empty portfolio never claims overlap`() {
        val result = fit("Power BI Reporting", "PBI-001", emptyList())
        assertFalse(result.strong)
        assertTrue(result.matchedTokens.isEmpty())
    }

    @Test
    fun `single weak token does not qualify on its own`() {
        val result = fit(
            demandName = "Advanced Networking on AWS",
            demandId = "NET-501",
            held = listOf("AZ-104: Manage Azure resources"),
        )
        // "net" / "networking" share no strong tokens; nothing should surface.
        assertFalse(result.strong)
    }

    @Test
    fun `code normalization treats hyphen and spaces identically`() {
        val hyphen = fit("Implementing Real-Time Analytics", "DP-603T00", listOf("DP 603T00 Fabric"))
        val spaced = fit("Implementing Real-Time Analytics", "DP-603T00", listOf("DP603T00: Fabric pipelines"))
        assertTrue(hyphen.strong)
        assertTrue(spaced.strong)
    }

    @Test
    fun `word overlap is case insensitive`() {
        val result = fit("POWER BI", "PB-1", listOf("power bi for makers"))
        assertTrue(result.strong)
        assertEquals(setOf("power", "bi"), result.matchedTokens.toSet())
    }
}