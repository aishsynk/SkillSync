package com.example.skillsync.feature.communication.engine

object CommunicationGenerator {
    fun generate(request: Map<String, Any>, verifiedContext: Map<String, Any> = emptyMap()): GeneratedMessage {
        val recipientMap = (request["recipient"] as? Map<*, *>)
            ?.mapKeys { it.key.toString() }

        val overrides: Map<String, Any> = (request["overrides"] as? Map<*, *>)
            ?.mapKeys { it.key.toString() }
            ?.mapValues { it.value ?: "" } ?: emptyMap()

        val recipient = CommunicationRecipient(
            name = (recipientMap?.get("name") as? String ?: "").trim(),
            type = (recipientMap?.get("type") as? String ?: "").trim(),
            relationship = (recipientMap?.get("relationship") as? String ?: "").trim(),
        )
        val context = CommunicationContext(
            recipient = recipient,
            channel = request["channel"] as? String ?: "MS_TEAMS_OR_VIBER",
            purpose = request["purpose"] as? String ?: "",
            userMessage = ((request["userMessage"] ?: request["user_message"]) as? String ?: "").trim(),
            myMessage = ((request["myMessage"] ?: request["my_message"]) as? String ?: "").trim(),
            relatedEntityType = (request["relatedEntityType"] ?: request["related_entity_type"]) as? String ?: "",
            relatedEntityId = (request["relatedEntityId"] ?: request["related_entity_id"]) as? String ?: "",
            verifiedContext = verifiedContext,
            userOverrides = overrides,
        )

        // Step 1: Intent Analysis
        val intent = analyze(
            context.userMessage,
            context.myMessage,
            recipientName = recipient.name,
            recipientType = recipient.type,
            purposeHint = context.purpose.ifBlank { (context.userOverrides["purpose"] as? String).orEmpty() },
        )

        // Step 2 & 3: Context Selection & Situation Evaluation
        val plan = CommunicationContextSelector.evaluateAndSelect(
            userMessage = context.userMessage,
            myMessage = context.myMessage,
            recipientName = recipient.name,
            recipientType = recipient.type,
            recipientRelationship = recipient.relationship,
            purposeHint = context.purpose,
            verifiedContext = context.verifiedContext,
            inferredIntent = intent,
        )

        // Step 4: Check for NO_MEANINGFUL_MESSAGE
        if (!plan.requiresCommunication) {
            return GeneratedMessage(
                text = NO_MEANINGFUL_MESSAGE,
                validation = ValidationResult(passed = true, issues = emptyList()),
                factsUsed = emptyList(),
                purpose = plan.purpose,
                tone = plan.tone,
                selectedFacts = emptyList(),
                rejectedFacts = plan.rejectedFacts,
                generationMode = "DETERMINISTIC_GENERATOR",
                requiresCommunication = false,
                noMessageReason = plan.noMessageReason,
                sensitiveFactsRemoved = plan.sensitiveFactsRemoved,
            )
        }

        // Step 5: Natural Message Generation
        val text = CommunicationComposer.composeFromPlan(plan)
        val genMode = "DETERMINISTIC_GENERATOR"

        // Step 6: Validation
        var validation = validate(text, plan)
        var finalText = text
        if (!validation.passed && finalText.length > MAX_LENGTH) {
            finalText = truncate(finalText)
            validation = validate(finalText, plan)
        }

        val factsUsed = plan.selectedFacts.map { "${it.key}=${it.value}" }

        return GeneratedMessage(
            text = finalText,
            validation = validation,
            factsUsed = factsUsed,
            purpose = plan.purpose,
            tone = plan.tone,
            selectedFacts = factsUsed,
            rejectedFacts = plan.rejectedFacts,
            generationMode = genMode,
            requiresCommunication = true,
            noMessageReason = null,
            sensitiveFactsRemoved = plan.sensitiveFactsRemoved,
        )
    }
}
