package com.example.skillsync.feature.opportunity.data

data class Opportunity(
    val id: String = "",
    val source: String = "",
    val sourceApp: String = "",
    val sourceGroup: String = "",
    val sender: String = "",
    val senderPhone: String = "",
    val title: String = "",
    val course: String = "",
    val courseCode: String = "",
    val location: String = "",
    val country: String = "",
    val datesStart: String = "",
    val datesEnd: String = "",
    val detectedAt: String = "",
    val status: String = "detected",
    val skillMatchScore: Int = 0,
    val verdict: String = "",
    val decision: String = "",
    val confidence: String = "",
    val preparationHours: String = "",
    val majorGap: String = "",
    val strongAreas: List<String> = emptyList(),
    val weakAreas: List<String> = emptyList(),
    val evidence: List<EvidenceItem> = emptyList(),
    val requirements: OpportunityRequirements = OpportunityRequirements(),
    val rawText: String = "",
    val documentStatus: String = "none",
    val isHighOpportunity: Boolean = false,
    val isCritical: Boolean = false,
    val isInternational: Boolean = false,
    val createdAt: String = "",
)

data class EvidenceItem(
    val topic: String = "",
    val evidence: String = "",
    val source: String = "",
    val status: String = "",
    val strength: Double = 0.0,
)

data class OpportunityRequirements(
    val courseCode: String = "",
    val course: String = "",
    val datesStart: String = "",
    val datesEnd: String = "",
    val location: String = "",
    val country: String = "",
    val mode: String = "",
    val participants: String = "",
    val documentationMentioned: List<String> = emptyList(),
    val action: String = "information",
)

data class SkillProfile(
    val email: String = "",
    val certifications: List<String> = emptyList(),
    val technologies: List<String> = emptyList(),
    val coursesDelivered: List<String> = emptyList(),
    val experienceYears: Int = 0,
    val labsProjects: List<String> = emptyList(),
    val confidenceByTopic: Map<String, Double> = emptyMap(),
    val capabilityGraph: CapabilityGraph = CapabilityGraph(),
)

data class CapabilityGraph(
    val certified: List<GraphNode> = emptyList(),
    val delivered: List<GraphNode> = emptyList(),
    val built: List<GraphNode> = emptyList(),
    val skills: List<SkillNode> = emptyList(),
)

data class GraphNode(
    val code: String = "",
    val name: String = "",
    val level: String = "",
    val count: Int = 0,
)

data class SkillNode(
    val name: String = "",
    val strength: String = "",
    val moderate: Boolean = false,
    val gap: Boolean = false,
    val confidence: Double = 0.0,
)

data class OpportunityGuardianConfig(
    val trustedSources: List<TrustedSource> = emptyList(),
    val triggerKeywords: List<String> = emptyList(),
    val quietHoursStart: String = "23:00",
    val quietHoursEnd: String = "07:00",
    val quietHoursNormalMessages: Boolean = true,
    val quietHoursHighOpportunities: Boolean = false,
    val quietHoursCriticalOpportunities: Boolean = false,
    val escalationRules: List<EscalationRule> = emptyList(),
    val enabled: Boolean = true,
)

data class TrustedSource(
    val app: String = "",
    val group: String = "",
    val sender: String = "",
    val enabled: Boolean = true,
)

data class EscalationRule(
    val trigger: String = "",
    val action: String = "",
    val level: String = "",
)

data class GuardianSettings(
    val enabled: Boolean = true,
    val quietHoursStart: String = "23:00",
    val quietHoursEnd: String = "07:00",
    val notifyNormal: Boolean = false,
    val notifyHigh: Boolean = true,
    val notifyCritical: Boolean = true,
    val trustedSources: List<TrustedSource> = emptyList(),
    val triggerKeywords: List<String> = emptyList(),
)

data class OpportunityMatchResult(
    val matchScore: Int = 0,
    val verdict: String = "",
    val decision: String = "",
    val strongAreas: List<String> = emptyList(),
    val weakAreas: List<String> = emptyList(),
    val majorGap: String = "",
    val preparationHours: String = "",
    val confidence: String = "",
    val recommendation: String = "",
    val evidence: List<EvidenceItem> = emptyList(),
    val requirements: OpportunityRequirements = OpportunityRequirements(),
)

data class OpportunitySummary(
    val detected: Int = 0,
    val accepted: Int = 0,
    val declined: Int = 0,
    val missed: Int = 0,
    val manual: Int = 0,
)
