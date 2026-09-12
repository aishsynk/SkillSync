package com.example.skillsync.feature.communication.engine

import java.util.Locale

object CommunicationContextSelector {

    const val NO_MEANINGFUL_MESSAGE = "NO_MEANINGFUL_MESSAGE"

    private val SENSITIVE_KEYS = setOf(
        "salary", "ctc", "compensation", "billing_rate", "client_rate", "margin", "cost",
        "profit", "billing_code", "client_billing_key", "retention_flag", "attrition_risk",
        "internal_notes", "performance_warning", "pip", "disciplinary", "confidential",
        "ssn", "passport", "bank_account", "pan", "aadhar", "national_id"
    )

    fun evaluateAndSelect(
        userMessage: String,
        myMessage: String,
        recipientName: String,
        recipientType: String,
        recipientRelationship: String,
        purposeHint: String,
        verifiedContext: Map<String, Any>,
        inferredIntent: Intent? = null,
    ): ContextSelectionPlan {
        val um = userMessage.trim()
        val mm = myMessage.trim()
        val hasManualInput = um.isNotEmpty() || mm.isNotEmpty()

        // 1. Filter sensitive keys first
        val cleanContext = mutableMapOf<String, Any>()
        val sensitiveRemoved = mutableListOf<String>()

        for ((k, v) in verifiedContext) {
            if (SENSITIVE_KEYS.contains(k.lowercase(Locale.getDefault()))) {
                sensitiveRemoved.add(k)
            } else if (v is Map<*, *>) {
                val subClean = mutableMapOf<String, Any>()
                for ((sk, sv) in v) {
                    val skStr = sk?.toString() ?: ""
                    if (SENSITIVE_KEYS.contains(skStr.lowercase(Locale.getDefault()))) {
                        sensitiveRemoved.add("$k.$skStr")
                    } else if (sv != null) {
                        subClean[skStr] = sv
                    }
                }
                cleanContext[k] = subClean
            } else {
                cleanContext[k] = v
            }
        }

        // Recipient
        var rType = (if (recipientType.isNotBlank()) recipientType else inferredIntent?.recipientClass ?: "UNKNOWN").uppercase(Locale.getDefault())
        if (rType !in listOf("TEAM", "INDIVIDUAL", "MANAGER", "REPORTEE", "COLLEAGUE", "CLIENT")) {
            rType = if ("$um $mm".lowercase(Locale.getDefault()).contains("team") || "$um $mm".lowercase(Locale.getDefault()).contains("everyone")) "TEAM" else "INDIVIDUAL"
        }
        val rName = if (recipientName.isNotBlank()) recipientName else inferredIntent?.recipientName ?: ""

        // Time references
        val timeRefs = mutableListOf<String>()
        inferredIntent?.timeRefs?.let { timeRefs.addAll(it) }
        val combinedText = "$um $mm".lowercase(Locale.getDefault())
        for (tr in listOf("next week", "this week", "today", "tomorrow", "friday", "monday")) {
            if (combinedText.contains(tr) && !timeRefs.contains(tr) && !timeRefs.contains(tr.replaceFirstChar { it.uppercase() })) {
                timeRefs.add(tr)
            }
        }

        val selectedFacts = mutableListOf<FactItem>()
        val rejectedFacts = mutableListOf<String>()
        var tone = inferredIntent?.tone ?: "professional"
        val urgency = inferredIntent?.urgency ?: "NORMAL"
        var purpose = purposeHint.ifBlank { inferredIntent?.purpose ?: "GENERAL_PROFESSIONAL" }
        var situationSummary = ""
        var expectedOutcome = ""
        var requestedAction = ""
        var requiresComm = true
        var noMsgReason: String? = null

        // ── FLOW A: CONVERSATION REWRITE (MANUAL INPUT PROVIDED) ──────────────
        if (hasManualInput) {
            requiresComm = true
            val oppMap = cleanContext["opportunity"] as? Map<*, *>
            val oppCourse = oppMap?.get("course_code")?.toString() ?: oppMap?.get("course")?.toString()
            val course = inferredIntent?.course?.ifEmpty { null }
                ?: cleanContext["course_code"]?.toString()
                ?: cleanContext["course"]?.toString()
                ?: oppCourse
                ?: ""
            if (!oppCourse.isNullOrEmpty()) {
                selectedFacts.add(FactItem("opportunity.course", oppCourse, "VERIFIED_SKILLSYNC_CONTEXT"))
            } else if (course.isNotEmpty()) {
                selectedFacts.add(FactItem("course", course, if ("$um $mm".contains(course)) "EXPLICIT_USER_INPUT" else "VERIFIED_SKILLSYNC_CONTEXT"))
            }

            if (um.isNotEmpty()) {
                when (purpose) {
                    "OPPORTUNITY_RESPONSE" -> {
                        situationSummary = "Responding to delivery inquiry for ${course.ifEmpty { "the batch" }}."
                        if (inferredIntent?.responsePosition == "DECLINE") {
                            requestedAction = "decline_delivery_with_reason"
                            expectedOutcome = "Sender is informed of unavailability and reallocates batch."
                        } else if (inferredIntent?.qualifiers?.contains("preparation") == true) {
                            requestedAction = "confirm_acceptance_and_request_schedule"
                            expectedOutcome = "Sender is informed of confirmation and provides schedule/requirements for prep."
                        } else {
                            requestedAction = "confirm_acceptance"
                            expectedOutcome = "Sender confirms allocation."
                        }
                    }
                    "AVAILABILITY_RESPONSE" -> {
                        situationSummary = "Responding to availability inquiry."
                        if (inferredIntent?.responsePosition == "DECLINE") {
                            requestedAction = "clarify_unavailability_and_propose_alternate"
                            expectedOutcome = "Sender is informed of current delivery and alternate connection time."
                        } else {
                            requestedAction = "confirm_availability"
                            expectedOutcome = "Sender confirms connection schedule."
                        }
                    }
                    "STATUS_UPDATE" -> {
                        situationSummary = "Responding with status confirmation on requested task/report."
                        requestedAction = "confirm_completion_and_delivery"
                        expectedOutcome = "Sender receives completion confirmation."
                    }
                    "TASK_FOLLOWUP" -> {
                        situationSummary = "Directing action or following up on requested task."
                        requestedAction = "request_review_and_feedback"
                        expectedOutcome = "Recipient reviews and provides required output."
                    }
                    else -> {
                        situationSummary = "Responding to: $um"
                        requestedAction = "convey_response"
                        expectedOutcome = "Recipient receives clear response."
                    }
                }
            } else {
                when (purpose) {
                    "COURSE_PREPARATION_CHECK" -> {
                        situationSummary = "Inquiring about trainer readiness and confidence for ${course.ifEmpty { "upcoming curriculum" }}."
                        requestedAction = "check_readiness_and_preparation"
                        expectedOutcome = "Trainer confirms confidence and readiness timeline."
                        if (mm.lowercase(Locale.getDefault()).contains("fabric") || cleanContext.containsKey("fabric")) {
                            selectedFacts.add(FactItem("capability_background", "Fabric", "VERIFIED_SKILLSYNC_CONTEXT"))
                        }
                    }
                    "AVAILABILITY_REQUEST" -> {
                        situationSummary = "Checking availability across team for ${course.ifEmpty { "open delivery requirement" }}."
                        requestedAction = "request_availability_confirmation"
                        expectedOutcome = "Available trainer confirms willingness to take up delivery."
                        cleanContext["open_demand"]?.let { selectedFacts.add(FactItem("open_demand", it, "VERIFIED_SKILLSYNC_CONTEXT")) }
                        cleanContext["bench"]?.let { selectedFacts.add(FactItem("bench", it, "VERIFIED_SKILLSYNC_CONTEXT")) }
                    }
                    "TASK_ASSIGNMENT" -> {
                        situationSummary = "Manager directing task provisioning or completion."
                        requestedAction = "ensure_task_provisioning"
                        expectedOutcome = "Recipient ensures required environments and tasks are completed."
                    }
                    "APPRECIATION" -> {
                        situationSummary = "Manager expressing recognition for team delivery performance."
                        requestedAction = "share_appreciation"
                        expectedOutcome = "Team feels recognized and motivated."
                        val avgR = cleanContext["avg_rating"] ?: cleanContext["average_rating"]
                        if (avgR != null) {
                            val rFloat = (avgR as? Number)?.toDouble() ?: avgR.toString().toDoubleOrNull()
                            if (rFloat != null && rFloat >= 4.0) {
                                selectedFacts.add(FactItem("avg_rating", rFloat, "VERIFIED_SKILLSYNC_CONTEXT"))
                            }
                        }
                    }
                    "DELIVERY_UPDATE" -> {
                        situationSummary = "Providing update on active training session and status."
                        requestedAction = "share_delivery_update"
                        expectedOutcome = "Recipient is briefed on training progress."
                    }
                    "TRAVEL_COORDINATION" -> {
                        situationSummary = "Coordinating travel logistics and desk arrangements."
                        requestedAction = "coordinate_travel_arrangements"
                        expectedOutcome = "Travelers confirm arrangements in advance."
                    }
                    else -> {
                        situationSummary = "Manager communication: $mm"
                        requestedAction = "communicate_intent"
                        expectedOutcome = "Recipient acts on communication."
                    }
                }
            }

            for (k in listOf("total_pax", "total_participants", "total_batches", "avg_rating", "total_gaps", "delivering", "headcount")) {
                if (cleanContext.containsKey(k) && selectedFacts.none { it.key == k }) {
                    rejectedFacts.add(k)
                }
            }
        }
        // ── FLOW B: AUTO-GENERATION MODE (SITUATION EVALUATION) ───────────────
        else {
            val openDemand = (cleanContext["open_demand"] as? Number)?.toInt() ?: (cleanContext["open_batches_total"] as? Number)?.toInt() ?: 0
            val coverable = (cleanContext["coverable_open"] as? Number)?.toInt() ?: (cleanContext["open_batches_coverable"] as? Number)?.toInt() ?: 0
            val bench = (cleanContext["bench"] as? Number)?.toInt() ?: (cleanContext["bench_count"] as? Number)?.toInt() ?: 0
            val atRisk = (cleanContext["at_risk"] as? Number)?.toInt() ?: (cleanContext["negative_feedback_count"] as? Number)?.toInt() ?: 0
            val gapDemandCount = (cleanContext["gap_demand_count"] as? Number)?.toInt() ?: 0
            val deliv = (cleanContext["delivering"] as? Number)?.toInt() ?: 0

            when {
                openDemand > 0 && (bench > 0 || coverable > 0) -> {
                    purpose = "AVAILABILITY_REQUEST"
                    val course = (cleanContext["course"] ?: cleanContext["open_course"] ?: cleanContext["opportunity_course"])?.toString()
                    val candidate = (cleanContext["candidate_trainer"] ?: cleanContext["candidate"])?.toString()
                    val loc = (cleanContext["location"] ?: cleanContext["batch_location"])?.toString()

                    situationSummary = if (openDemand == 1) "There is 1 open delivery requirement and available trainer capacity." else "There are $openDemand open delivery requirements and available trainer capacity."
                    requestedAction = "request_availability_confirmation"
                    expectedOutcome = "Available trainer confirms willingness to take open delivery."
                    selectedFacts.add(FactItem("open_demand", openDemand, "VERIFIED_SKILLSYNC_CONTEXT"))
                    if (bench > 0) selectedFacts.add(FactItem("bench", bench, "VERIFIED_SKILLSYNC_CONTEXT"))
                    if (coverable > 0) selectedFacts.add(FactItem("coverable_open", coverable, "VERIFIED_SKILLSYNC_CONTEXT"))
                    if (!course.isNullOrBlank()) selectedFacts.add(FactItem("course", course, "VERIFIED_SKILLSYNC_CONTEXT"))
                    if (!candidate.isNullOrBlank()) selectedFacts.add(FactItem("candidate_trainer", candidate, "VERIFIED_SKILLSYNC_CONTEXT"))
                    if (!loc.isNullOrBlank()) selectedFacts.add(FactItem("location", loc, "VERIFIED_SKILLSYNC_CONTEXT"))
                    for (k in listOf("total_pax", "total_participants", "total_batches", "total_gaps", "avg_rating", "headcount")) {
                        if (cleanContext.containsKey(k) && selectedFacts.none { it.key == k }) rejectedFacts.add(k)
                    }
                }
                openDemand > 0 && coverable == 0 && bench == 0 -> {
                    purpose = "CAPABILITY_ESCALATION"
                    situationSummary = "There is $openDemand open requirement requiring skills not currently verified in available team capacity."
                    requestedAction = "escalate_external_coverage"
                    expectedOutcome = "External staffing or cross-team support initiated."
                    selectedFacts.add(FactItem("open_demand", openDemand, "VERIFIED_SKILLSYNC_CONTEXT"))
                    selectedFacts.add(FactItem("coverable_open", 0, "VERIFIED_SKILLSYNC_CONTEXT"))
                    for (k in listOf("total_pax", "total_batches", "avg_rating")) {
                        if (cleanContext.containsKey(k)) rejectedFacts.add(k)
                    }
                }
                atRisk > 0 -> {
                    purpose = "DELIVERY_SUPPORT"
                    situationSummary = "Delivery feedback indicators show operational points requiring attention."
                    requestedAction = "review_delivery_feedback"
                    expectedOutcome = "Trainers raise delivery risks early for prompt resolution."
                    selectedFacts.add(FactItem("at_risk", atRisk, "VERIFIED_SKILLSYNC_CONTEXT"))
                    for (k in listOf("total_pax", "open_demand", "total_gaps", "bench", "total_batches")) {
                        if (cleanContext.containsKey(k)) rejectedFacts.add(k)
                    }
                }
                gapDemandCount > 0 -> {
                    purpose = "CAPABILITY_DEVELOPMENT"
                    situationSummary = "$gapDemandCount open demand requirements require certification coverage."
                    requestedAction = "schedule_priority_certifications"
                    expectedOutcome = "Trainers prioritize exams tied to verified upcoming demand."
                    selectedFacts.add(FactItem("gap_demand_count", gapDemandCount, "VERIFIED_SKILLSYNC_CONTEXT"))
                    cleanContext["cert_gap_courses"]?.let { selectedFacts.add(FactItem("cert_gap_courses", it, "VERIFIED_SKILLSYNC_CONTEXT")) }
                    for (k in listOf("total_pax", "open_demand", "bench", "avg_rating")) {
                        if (cleanContext.containsKey(k)) rejectedFacts.add(k)
                    }
                }
                deliv > 3 && openDemand == 0 && atRisk == 0 -> {
                    requiresComm = false
                    noMsgReason = "Delivery load is high and operations are steady; no operational intervention or communication required."
                    situationSummary = "High steady delivery; suppressing redundant broadcast."
                    expectedOutcome = "No message broadcast."
                }
                else -> {
                    requiresComm = false
                    noMsgReason = "All operations and deliveries are steady with no unstaffed batches, critical blockers, or immediate actions required."
                    situationSummary = "Operational baseline is steady; suppressing unnecessary broadcast noise."
                    expectedOutcome = "No message broadcast."
                }
            }
        }

        return ContextSelectionPlan(
            intent = if (mm.isNotEmpty()) mm else if (um.isNotEmpty()) um else purpose,
            userMessage = um,
            myMessage = mm,
            recipientName = rName,
            recipientType = rType,
            recipientRelationship = recipientRelationship,
            purpose = purpose,
            situationSummary = situationSummary,
            expectedOutcome = expectedOutcome,
            urgency = urgency,
            tone = tone,
            selectedFacts = selectedFacts,
            rejectedFacts = rejectedFacts,
            sensitiveFactsRemoved = sensitiveRemoved,
            timeReferences = timeRefs,
            actionRequired = requestedAction,
            requiresCommunication = requiresComm,
            noMessageReason = noMsgReason,
            provenance = selectedFacts.associate { it.key to it.provenance }
        )
    }
}
