package com.example.skillsync.feature.communication.engine

/**
 * Communication Context Allowlist & Sensitivity Policy.
 *
 * Core Product Principle:
 * AVAILABLE DATA != MESSAGE CONTENT
 *
 * Available SkillSync data is evidence from which relevant context may be selected.
 * It must NEVER automatically be inserted into messages without purposeful,
 * recipient-aware filtering.
 */

enum class CommunicationPurpose(val id: String, val displayName: String) {
    TRAINER_SUMMARY_EXTERNAL("TRAINER_SUMMARY_EXTERNAL", "Trainer Summary (External)"),
    TRAINER_PERFORMANCE_INTERNAL("TRAINER_PERFORMANCE_INTERNAL", "Trainer Performance Review (Internal)"),
    BATCH_INVITATION("BATCH_INVITATION", "Batch Allocation Invitation"),
    EXTERNAL_STAFFING_REQUEST("EXTERNAL_STAFFING_REQUEST", "Vendor Staffing Escalation"),
    EXECUTIVE_REPORT("EXECUTIVE_REPORT", "Executive Stakeholder Summary"),
    CLIENT_STATUS_UPDATE("CLIENT_STATUS_UPDATE", "Client Delivery Status Update"),
    OPPORTUNITY_RESPONSE("OPPORTUNITY_RESPONSE", "Commercial Opportunity Response"),
    COURSE_PREPARATION_REQUEST("COURSE_PREPARATION_REQUEST", "Course Delivery Preparation Request"),
    CAPABILITY_DEVELOPMENT_REQUEST("CAPABILITY_DEVELOPMENT_REQUEST", "Capability Up-skilling Request"),
    CURRICULUM_SHARE("CURRICULUM_SHARE", "Course Curriculum & Reference Share"),
    GENERAL_PROFESSIONAL("GENERAL_PROFESSIONAL", "General Operational Communication"),
    TEAM_PERIODIC_UPDATE("TEAM_PERIODIC_UPDATE", "Team Weekly/Monthly Update"),
    INDIVIDUAL_PERIODIC_UPDATE("INDIVIDUAL_PERIODIC_UPDATE", "Individual Weekly/Monthly Update"),
    MORNING_TEAM_GREETING("MORNING_TEAM_GREETING", "Weekday Morning Team Greeting");

    companion object {
        fun fromId(id: String): CommunicationPurpose =
            entries.find { it.id.equals(id, ignoreCase = true) } ?: GENERAL_PROFESSIONAL
    }
}

object CommunicationContextFilter {

    /**
     * Strict denylists per purpose. Keys found in these sets are permanently
     * stripped from any message generation payload before prompt synthesis.
     */
    private val DENY_RULES: Map<CommunicationPurpose, Set<String>> = mapOf(
        CommunicationPurpose.TRAINER_SUMMARY_EXTERNAL to setOf(
            "private_notes", "manager_notes", "negative_feedback", "raw_feedback",
            "internal_economics", "cost_rate", "margin", "utilization_delta",
            "internal_rating", "retention_risk", "disciplinary_flag"
        ),
        CommunicationPurpose.BATCH_INVITATION to setOf(
            "billing_code", "client_billing_id", "financial_id", "commercial_terms",
            "client_rate", "gross_margin", "internal_coordinator_phone", "contract_type"
        ),
        CommunicationPurpose.EXTERNAL_STAFFING_REQUEST to setOf(
            "target_margin", "internal_rate_calc", "internal_budget",
            "client_revenue", "client_confidential_notes", "koenig_internal_margin"
        ),
        CommunicationPurpose.CLIENT_STATUS_UPDATE to setOf(
            "trainer_compensation", "internal_dispute", "vendor_margin",
            "bench_cost", "private_trainer_notes"
        ),
        CommunicationPurpose.COURSE_PREPARATION_REQUEST to setOf(
            "internal_notes", "manager_notes", "client_commercials", "billing_rate",
            "cost_margin", "salary_info", "retention_risk"
        ),
        CommunicationPurpose.CAPABILITY_DEVELOPMENT_REQUEST to setOf(
            "private_ratings", "disciplinary_notes", "client_margins", "compensation"
        ),
        CommunicationPurpose.CURRICULUM_SHARE to setOf(
            "internal_pricing", "trainer_costs", "margin", "commercial_terms", "internal_notes"
        ),
    )

    /**
     * Strict allowlists per purpose. Only keys in these sets may be admitted.
     */
    private val ALLOW_RULES: Map<CommunicationPurpose, Set<String>> = mapOf(
        CommunicationPurpose.TRAINER_SUMMARY_EXTERNAL to setOf(
            "trainer_name", "primary_skills", "certifications", "approved_readiness",
            "public_bio", "years_experience", "delivery_count", "csat_band"
        ),
        CommunicationPurpose.BATCH_INVITATION to setOf(
            "course_code", "course_title", "start_date", "end_date", "delivery_mode",
            "daily_timing", "timezone", "location", "prerequisites", "batch_id"
        ),
        CommunicationPurpose.EXTERNAL_STAFFING_REQUEST to setOf(
            "required_technology", "course_title", "start_date", "end_date",
            "delivery_mode", "location", "timezone", "candidate_criteria"
        ),
        CommunicationPurpose.COURSE_PREPARATION_REQUEST to setOf(
            "course_code", "course_title", "vendor", "target_date", "exam_code",
            "topics_to_review", "lab_access_url", "prerequisites"
        ),
        CommunicationPurpose.CAPABILITY_DEVELOPMENT_REQUEST to setOf(
            "technology", "course_code", "course_title", "target_level",
            "target_certification", "development_rationale"
        ),
        CommunicationPurpose.CURRICULUM_SHARE to setOf(
            "course_code", "course_title", "vendor", "duration_days", "modules",
            "official_resources", "syllabus_url", "public_schedules"
        ),
    )

    /**
     * Sanitizes raw operational context according to the communication purpose and recipient.
     * Returns a pruned, safe map containing only allowed, verified public facts.
     */
    fun sanitize(purpose: CommunicationPurpose, rawData: Map<String, Any?>): Map<String, Any?> {
        val deniedKeys = DENY_RULES[purpose].orEmpty()
        val allowedKeys = ALLOW_RULES[purpose]

        return rawData.filter { (key, value) ->
            val normalizedKey = key.lowercase().trim()
            val isDenied = deniedKeys.any { normalizedKey.contains(it) }
            val isAllowed = allowedKeys == null || allowedKeys.any { normalizedKey.contains(it) }
            !isDenied && isAllowed && value != null
        }
    }

    /**
     * Returns true if a key represents sensitive commercial or internal HR data.
     */
    fun isSensitiveFact(key: String): Boolean {
        val sensitivePatterns = listOf(
            "margin", "rate", "cost", "billing", "private", "confidential",
            "salary", "compensation", "negative", "risk", "profit"
        )
        val k = key.lowercase()
        return sensitivePatterns.any { k.contains(it) }
    }
}
