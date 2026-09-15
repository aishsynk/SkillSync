package com.example.skillsync.feature.communication.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagerCommunicationComposerTest {

    private fun individualRequest(
        utilisation: Int? = 62,
        certGaps: List<String> = listOf("AZ-104"),
        managerInstruction: String = "",
    ) = CommunicationRequest(
        audience = CommunicationAudience(CommunicationAudienceType.INDIVIDUAL, name = "Priya Rao", email = "priya@koenig-solutions.com"),
        purpose = CommunicationPurpose.INDIVIDUAL_PERIODIC_UPDATE,
        cadence = "weekly",
        evidence = CommunicationEvidence(currentUtilisation = utilisation, certGapCourses = certGaps),
        managerInstruction = managerInstruction,
    )

    @Test
    fun evidenceAloneProducesAFactualMessageWithNoManagerInstruction() {
        val text = ManagerCommunicationComposer.compose(individualRequest())
        assertTrue(text.contains("62%"))
        assertTrue(text.contains("AZ-104"))
        assertTrue(text.startsWith("Hello"))
    }

    @Test
    fun managerInstructionAppendsRatherThanReplacesEvidence() {
        val withInstruction = ManagerCommunicationComposer.compose(
            individualRequest(managerInstruction = "please prioritise the Bangalore batch"),
        )
        // The verified facts are still present even though a manager
        // instruction was supplied — the instruction never replaces them.
        assertTrue(withInstruction.contains("62%"))
        assertTrue(withInstruction.contains("AZ-104"))
        // And the instruction itself is reflected as an additional point.
        assertTrue(withInstruction.contains("Bangalore"))
    }

    @Test
    fun managerInstructionCannotInventAFactNotInEvidence() {
        // No utilisation, no cert gaps in evidence — an instruction that
        // references a number/course must not cause the composer to invent
        // it as a stated fact outside of the instruction sentence itself.
        val request = CommunicationRequest(
            audience = CommunicationAudience(CommunicationAudienceType.INDIVIDUAL, name = "Priya Rao", email = "priya@koenig-solutions.com"),
            purpose = CommunicationPurpose.INDIVIDUAL_PERIODIC_UPDATE,
            cadence = "weekly",
            evidence = CommunicationEvidence(),
            managerInstruction = "great work this month",
        )
        val text = ManagerCommunicationComposer.compose(request)
        assertFalse(text.contains("62%"))
        assertFalse(text.contains("AZ-104"))
    }

    @Test
    fun teamAudienceUsesTeamGreeting() {
        val request = CommunicationRequest(
            audience = CommunicationAudience(CommunicationAudienceType.TEAM),
            purpose = CommunicationPurpose.TEAM_PERIODIC_UPDATE,
            cadence = "weekly",
            evidence = CommunicationEvidence(currentUtilisation = 74),
        )
        val text = ManagerCommunicationComposer.compose(request)
        assertTrue(text.startsWith("Hello team,"))
        assertTrue(text.contains("74%"))
    }

    @Test
    fun noEvidenceAndNoInstructionStillProducesAnHonestMessage() {
        val request = CommunicationRequest(
            audience = CommunicationAudience(CommunicationAudienceType.INDIVIDUAL, name = "Priya Rao"),
            purpose = CommunicationPurpose.INDIVIDUAL_PERIODIC_UPDATE,
            cadence = "weekly",
        )
        val text = ManagerCommunicationComposer.compose(request)
        assertTrue(text.contains("No exceptions to report"))
    }
}
