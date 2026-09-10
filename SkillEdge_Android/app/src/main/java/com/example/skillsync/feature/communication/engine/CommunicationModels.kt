package com.example.skillsync.feature.communication.engine

data class CommunicationRecipient(
    val name: String = "",
    val type: String = "UNKNOWN",
    val relationship: String = "",
)

data class CommunicationContext(
    val recipient: CommunicationRecipient = CommunicationRecipient(),
    val channel: String = "MS_TEAMS_OR_VIBER",
    val purpose: String = "GENERAL_PROFESSIONAL",
    val userMessage: String = "",
    val myMessage: String = "",
    val relatedEntityType: String = "",
    val relatedEntityId: String = "",
    val verifiedContext: Map<String, Any> = emptyMap(),
    val userOverrides: Map<String, Any> = emptyMap(),
)

data class ValidationResult(
    val passed: Boolean = true,
    val issues: List<String> = listOf(),
)

data class GeneratedMessage(
    val text: String = "",
    val validation: ValidationResult = ValidationResult(),
    val factsUsed: List<String> = listOf(),
    val purpose: String = "",
    val tone: String = "",
)