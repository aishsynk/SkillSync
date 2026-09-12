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

data class FactItem(
    val key: String,
    val value: Any,
    val provenance: String = "VERIFIED_SKILLSYNC_CONTEXT",
)

data class ContextSelectionPlan(
    val intent: String = "",
    val userMessage: String = "",
    val myMessage: String = "",
    val recipientName: String = "",
    val recipientType: String = "UNKNOWN",
    val recipientRelationship: String = "",
    val purpose: String = "GENERAL_PROFESSIONAL",
    val situationSummary: String = "",
    val expectedOutcome: String = "",
    val urgency: String = "NORMAL",
    val tone: String = "professional",
    val selectedFacts: List<FactItem> = emptyList(),
    val rejectedFacts: List<String> = emptyList(),
    val sensitiveFactsRemoved: List<String> = emptyList(),
    val timeReferences: List<String> = emptyList(),
    val actionRequired: String = "",
    val requiresCommunication: Boolean = true,
    val noMessageReason: String? = null,
    val provenance: Map<String, String> = emptyMap(),
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
    val selectedFacts: List<String> = listOf(),
    val rejectedFacts: List<String> = listOf(),
    val generationMode: String = "DETERMINISTIC_GENERATOR",
    val requiresCommunication: Boolean = true,
    val noMessageReason: String? = null,
    val sensitiveFactsRemoved: List<String> = emptyList(),
)