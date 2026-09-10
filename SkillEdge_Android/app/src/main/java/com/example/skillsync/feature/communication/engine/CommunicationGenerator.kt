package com.example.skillsync.feature.communication.engine

object CommunicationGenerator {
    fun generate(request: Map<String, Any>, verifiedContext: Map<String, Any> = emptyMap()): GeneratedMessage {
        val recipientMap = (request["recipient"] as? Map<*, *>)
            ?.mapKeys { it.key.toString() }

        val overrides: Map<String, Any> = (request["overrides"] as? Map<*, *>)
            ?.mapKeys { it.key.toString() }
            ?.mapValues { it.value ?: "" } ?: emptyMap()

        val recipient = CommunicationRecipient(
            name = recipientMap?.get("name") as? String ?: "",
            type = recipientMap?.get("type") as? String ?: "",
            relationship = recipientMap?.get("relationship") as? String ?: "",
        )
        val context = CommunicationContext(
            recipient = recipient,
            channel = request["channel"] as? String ?: "MS_TEAMS_OR_VIBER",
            purpose = request["purpose"] as? String ?: "",
            userMessage = request["userMessage"] as? String ?: "",
            myMessage = request["myMessage"] as? String ?: "",
            relatedEntityType = request["relatedEntityType"] as? String ?: "",
            relatedEntityId = request["relatedEntityId"] as? String ?: "",
            verifiedContext = verifiedContext,
            userOverrides = overrides,
        )

        val intent = analyze(
            context.userMessage,
            context.myMessage,
            recipientName = recipient.name,
            recipientType = recipient.type,
            purposeHint = context.purpose.ifBlank { (context.userOverrides["purpose"] as? String).orEmpty() },
        )

        var text = compose(intent, context)
        var validation = validate(text)
        if (!validation.passed && text.length > MAX_LENGTH) {
            text = truncate(text)
            validation = validate(text)
        }

        val facts = mutableListOf<String>()
        val opp = verifiedContext["opportunity"] as? Map<*, *>
        if (opp != null) {
            val om = opp.mapKeys { it.key.toString() }
            for (key in listOf("course_code", "course", "location", "country", "decision")) {
                val v = om[key]
                if (v != null && v.toString().isNotBlank()) facts.add("opportunity.$key=$v")
            }
        }
        if (intent.deadlineText.isNotEmpty()) facts.add("deadline=${intent.deadlineText}")

        return GeneratedMessage(
            text = text,
            validation = validation,
            factsUsed = facts,
            purpose = intent.purpose,
            tone = intent.tone,
        )
    }
}