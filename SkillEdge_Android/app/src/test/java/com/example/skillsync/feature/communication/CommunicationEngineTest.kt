package com.example.skillsync.feature.communication

import com.example.skillsync.feature.communication.engine.CommunicationGenerator
import com.example.skillsync.feature.communication.engine.GeneratedMessage
import com.example.skillsync.feature.communication.engine.MAX_LENGTH
import com.example.skillsync.feature.communication.engine.hasBulletList
import com.example.skillsync.feature.communication.engine.hasEmoji
import com.example.skillsync.feature.communication.engine.hasNumberedList
import com.example.skillsync.feature.communication.engine.validate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunicationEngineTest {

    private fun generate(
        request: Map<String, Any>,
        verified: Map<String, Any>? = null,
    ): GeneratedMessage = CommunicationGenerator.generate(request, verified ?: emptyMap())

    @Test
    fun myMessageOnlyDeliveryUpdate() {
        val out = generate(
            mapOf(
                "recipient" to mapOf("name" to "Gaurav Joshi", "type" to "MANAGER"),
                "purpose" to "DELIVERY_UPDATE",
                "myMessage" to (
                    "Tell Gaurav Joshi the session is going fine. There was around one hour of " +
                        "power cut from client side but we still completed what was required " +
                        "and took the assessment."
                    ),
            ),
        )
        val text = out.text
        assertTrue(out.validation.passed)
        assertEquals(emptyList<String>(), out.validation.issues)
        assertTrue(text.length <= MAX_LENGTH)
        assertTrue(text.startsWith("Hello"))
        assertTrue(text.contains("*Gaurav Joshi*"))
        assertTrue(text.contains("*Thanks*"))
        assertTrue(text.contains("going well"))
        assertTrue(text.contains("power interruption"))
        assertTrue(text.contains("assessment"))
        assertFalse(text.lowercase().contains("complaint"))
    }

    @Test
    fun userMessageOverridesFirmnessAndMonday() {
        val out = generate(
            mapOf(
                "recipient" to mapOf("name" to "", "type" to "REPORTEE"),
                "userMessage" to "Please make it firmer because the task has been pending since Monday.",
                "myMessage" to "Can you please complete this when possible?",
            ),
        )
        val text = out.text
        assertTrue(out.validation.passed)
        assertEquals("firm", out.tone)
        assertTrue(text.contains("Monday"))
        assertTrue(text.contains("**"))
        assertFalse(text.contains("when possible"))
    }

    @Test
    fun teamMessageTravelAdvisory() {
        val out = generate(
            mapOf(
                "recipient" to mapOf("name" to "", "type" to "TEAM"),
                "purpose" to "TRAVEL_COORDINATION",
                "myMessage" to (
                    "Tell the team that if they have FMAT or ILT travel coming up, they " +
                        "should coordinate with Travel Desk and Payroll in advance where cab " +
                        "arrangements outside India may be needed."
                    ),
            ),
        )
        val text = out.text
        assertTrue(out.validation.passed)
        assertTrue(text.startsWith("Hello team,"))
        assertTrue(text.contains("Travel Desk"))
        assertTrue(text.contains("Payroll"))
        assertTrue(text.lowercase().contains("cab"))
        assertFalse(text.contains("•"))
        text.split("\n").forEach { line ->
            assertFalse(line.trim().startsWith("- "))
        }
    }

    @Test
    fun opportunityResponseUsesVerifiedContextOnly() {
        val out = generate(
            mapOf(
                "recipient" to mapOf("name" to "Gaurav Joshi", "type" to "MANAGER"),
                "purpose" to "OPPORTUNITY_RESPONSE",
                "relatedEntityId" to "SE-TEST1",
                "myMessage" to "say yes I can do it",
            ),
            verified = mapOf(
                "opportunity" to mapOf(
                    "id" to "SE-TEST1",
                    "course_code" to "DP-700",
                    "location" to "Egypt",
                    "course" to "DP-700T00",
                    "decision" to "accept",
                    "preparation_hours" to "4-6 hours",
                ),
            ),
        )
        val text = out.text
        assertTrue(out.validation.passed)
        assertTrue(text.contains("Yes, I can take this up"))
        assertTrue(text.contains("DP-700"))
        assertTrue(text.contains("*Gaurav Joshi*"))
        assertTrue(out.factsUsed.any { it.startsWith("opportunity.") })
        assertFalse(text.contains("Egypt"))
    }

    @Test
    fun opportunityResponseWithoutVerifiedRecordStaysGeneric() {
        val out = generate(
            mapOf(
                "recipient" to mapOf("name" to "Gaurav Joshi", "type" to "MANAGER"),
                "purpose" to "OPPORTUNITY_RESPONSE",
                "relatedEntityId" to "DOES-NOT-EXIST",
                "myMessage" to "say yes I can do it",
            ),
        )
        assertEquals("OPPORTUNITY_RESPONSE", out.purpose)
        assertFalse(out.text.contains("DP-700"))
    }

    @Test
    fun missingContextPreservesFridayWithoutInventingDate() {
        val out = generate(
            mapOf(
                "recipient" to mapOf("name" to "Niharika", "type" to "COLLEAGUE"),
                "myMessage" to "Tell Niharika I will finish it by Friday.",
            ),
        )
        val text = out.text
        assertTrue(text.contains("Friday"))
        assertFalse(Regex("\\d{1,2}[/-]\\d{1,2}(?:[/-]\\d{2,4})?").containsMatchIn(text))
    }

    @Test
    fun purposeClassification() {
        assertEquals("DELIVERY_UPDATE", generate(mapOf("purpose" to "", "myMessage" to "session going well")).purpose)
        assertEquals("OPPORTUNITY_RESPONSE", generate(mapOf("purpose" to "", "myMessage" to "can i do it yes")).purpose)
        assertEquals("TRAVEL_COORDINATION", generate(mapOf("purpose" to "", "myMessage" to "cab to airport please")).purpose)
    }

    @Test
    fun validatorBlocksEmojiBulletsAndOversize() {
        assertTrue(hasEmoji("hello \uD83D\uDE42"))
        assertFalse(hasEmoji("plain text"))
        assertTrue(hasBulletList("a\n- item\nb"))
        assertTrue(hasNumberedList("1. first\n2. second"))
        assertEquals(1000, MAX_LENGTH)
    }

    @Test
    fun validateFlagsMissingGreeting() {
        val result = validate("No greeting here.\n*Thanks*")
        assertFalse(result.passed)
        assertTrue(result.issues.any { it.contains("greeting") })
    }

    @Test
    fun validateAcceptsWellFormedMessage() {
        val text = "Hello *Niharika*,\nI will finish it by Friday.\n*Thanks*"
        val result = validate(text)
        assertTrue(result.passed)
        assertEquals(emptyList<String>(), result.issues)
    }
}