package com.example.skillsync.feature.opportunity.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skillsync.core.data.ManagerRepository
import com.example.skillsync.core.storage.LocalCache
import com.example.skillsync.feature.opportunity.data.CapabilityGraph
import com.example.skillsync.feature.opportunity.data.EscalationRule
import com.example.skillsync.feature.opportunity.data.EvidenceItem
import com.example.skillsync.feature.opportunity.data.GraphNode
import com.example.skillsync.feature.opportunity.data.Opportunity
import com.example.skillsync.feature.opportunity.data.OpportunityGuardianConfig
import com.example.skillsync.feature.opportunity.data.OpportunityMatchResult
import com.example.skillsync.feature.opportunity.data.OpportunityRequirements
import com.example.skillsync.feature.opportunity.data.OpportunitySummary
import com.example.skillsync.feature.opportunity.data.SkillNode
import com.example.skillsync.feature.opportunity.data.SkillProfile
import com.example.skillsync.feature.opportunity.data.TrustedSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OpportunityUiState(
    val opportunities: List<Opportunity> = emptyList(),
    val summary: OpportunitySummary = OpportunitySummary(),
    val guardianConfig: OpportunityGuardianConfig = OpportunityGuardianConfig(),
    val skillProfile: SkillProfile = SkillProfile(),
    val matchResult: OpportunityMatchResult = OpportunityMatchResult(),
    val loading: Boolean = false,
    val error: String? = null,
)

class OpportunityViewModel : ViewModel() {
    private val repository = ManagerRepository()
    private val _uiState = MutableStateFlow(OpportunityUiState())
    val uiState: StateFlow<OpportunityUiState> = _uiState.asStateFlow()

    fun loadOpportunities(manager: String, status: String = "") {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                val result = repository.opportunities(manager, status).data
                val items = (result?.get("items") as? List<*>?)?.mapNotNull {
                    it as? Map<*, *>
                }?.map { map ->
                    Opportunity(
                        id = map["id"].toString(),
                        source = map["source"].toString(),
                        sourceApp = map["source_app"].toString(),
                        sourceGroup = map["source_group"].toString(),
                        sender = map["sender"].toString(),
                        senderPhone = map["sender_phone"].toString(),
                        title = map["title"].toString(),
                        course = map["course"].toString(),
                        courseCode = map["course_code"].toString(),
                        location = map["location"].toString(),
                        country = map["country"].toString(),
                        datesStart = map["dates_start"].toString(),
                        datesEnd = map["dates_end"].toString(),
                        detectedAt = map["detected_at"].toString(),
                        status = map["status"].toString(),
                        skillMatchScore = (map["skill_match_score"] as? Number)?.toInt() ?: 0,
                        verdict = map["verdict"].toString(),
                        decision = map["decision"].toString(),
                        confidence = map["confidence"].toString(),
                        preparationHours = map["preparation_hours"].toString(),
                        majorGap = map["major_gap"].toString(),
                        strongAreas = (map["strong_areas"] as? List<*>?)?.map { it.toString() } ?: emptyList(),
                        weakAreas = (map["weak_areas"] as? List<*>?)?.map { it.toString() } ?: emptyList(),
                        evidence = (map["evidence"] as? List<*>?)?.mapNotNull {
                            (it as? Map<*, *>)?.let { e ->
                                EvidenceItem(
                                    topic = e["topic"].toString(),
                                    evidence = e["evidence"].toString(),
                                    source = e["source"].toString(),
                                    status = e["status"].toString(),
                                    strength = (e["strength"] as? Number)?.toDouble() ?: 0.0,
                                )
                            }
                        } ?: emptyList(),
                        requirements = (map["requirements"] as? Map<*, *>)?.let { r ->
                            OpportunityRequirements(
                                courseCode = r["course_code"].toString(),
                                course = r["course"].toString(),
                                datesStart = r["dates_start"].toString(),
                                datesEnd = r["dates_end"].toString(),
                                location = r["location"].toString(),
                                country = r["country"].toString(),
                                mode = r["mode"].toString(),
                                participants = r["participants"].toString(),
                                documentationMentioned = (r["documentation_mentioned"] as? List<*>?)?.map { it.toString() } ?: emptyList(),
                                action = r["action"].toString(),
                            )
                        } ?: OpportunityRequirements(),
                        rawText = map["raw_text"].toString(),
                        documentStatus = map["document_status"].toString(),
                        isHighOpportunity = map["is_high_opportunity"] == true,
                        isCritical = map["is_critical"] == true,
                        isInternational = map["is_international"] == true,
                        createdAt = map["created_at"].toString(),
                    )
                } ?: emptyList()
                _uiState.value = _uiState.value.copy(
                    opportunities = items,
                    summary = OpportunitySummary(
                        detected = items.count { it.status == "detected" },
                        accepted = items.count { it.status == "accepted" },
                        declined = items.count { it.status == "declined" },
                        missed = items.count { it.status == "missed" },
                        manual = items.count { it.status == "manual" },
                    ),
                    loading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(loading = false, error = e.localizedMessage)
            }
        }
    }

    fun loadGuardianConfig(manager: String) {
        viewModelScope.launch {
            try {
                val result = repository.guardianConfig(manager).data ?: emptyMap<String, Any>()
                val config = OpportunityGuardianConfig(
                    trustedSources = (result["trusted_sources"] as? List<*>?)?.mapNotNull {
                        (it as? Map<*, *>)?.let { m ->
                            TrustedSource(
                                app = m["app"].toString(),
                                group = m["group"].toString(),
                                sender = m["sender"].toString(),
                                enabled = m["enabled"] == true,
                            )
                        }
                    } ?: emptyList(),
                    triggerKeywords = (result["trigger_keywords"] as? List<*>?)?.map { it.toString() } ?: emptyList(),
                    quietHoursStart = result["quiet_hours_start"].toString(),
                    quietHoursEnd = result["quiet_hours_end"].toString(),
                    quietHoursNormalMessages = result["quiet_hours_normal_messages"] == true,
                    quietHoursHighOpportunities = result["quiet_hours_high_opportunities"] == true,
                    quietHoursCriticalOpportunities = result["quiet_hours_critical_opportunities"] == true,
                    escalationRules = (result["escalation_rules"] as? List<*>?)?.mapNotNull {
                        (it as? Map<*, *>)?.let { m ->
                            EscalationRule(
                                trigger = m["trigger"].toString(),
                                action = m["action"].toString(),
                                level = m["level"].toString(),
                            )
                        }
                    } ?: emptyList(),
                    enabled = result["enabled"] == true,
                )
                _uiState.value = _uiState.value.copy(guardianConfig = config)
            } catch (_: Exception) {}
        }
    }

    fun updateGuardianConfig(manager: String, config: OpportunityGuardianConfig) {
        viewModelScope.launch {
            try {
                repository.updateGuardianConfig(manager, mapOf(
                    "trusted_sources" to config.trustedSources.map {
                        mapOf("app" to it.app, "group" to it.group, "sender" to it.sender, "enabled" to it.enabled)
                    },
                    "trigger_keywords" to config.triggerKeywords,
                    "quiet_hours_start" to config.quietHoursStart,
                    "quiet_hours_end" to config.quietHoursEnd,
                    "quiet_hours_normal_messages" to config.quietHoursNormalMessages,
                    "quiet_hours_high_opportunities" to config.quietHoursHighOpportunities,
                    "quiet_hours_critical_opportunities" to config.quietHoursCriticalOpportunities,
                    "escalation_rules" to config.escalationRules.map {
                        mapOf("trigger" to it.trigger, "action" to it.action, "level" to it.level)
                    },
                    "enabled" to config.enabled,
                ))
            } catch (_: Exception) {}
        }
    }

    fun loadSkillProfile(manager: String) {
        viewModelScope.launch {
            try {
                val result = repository.skillProfile(manager).data ?: emptyMap<String, Any>()
                val profile = SkillProfile(
                    email = manager,
                    certifications = (result["certifications"] as? List<*>?)?.map { it.toString() } ?: emptyList(),
                    technologies = (result["technologies"] as? List<*>?)?.map { it.toString() } ?: emptyList(),
                    coursesDelivered = (result["courses_delivered"] as? List<*>?)?.map { it.toString() } ?: emptyList(),
                    experienceYears = (result["experience_years"] as? Number)?.toInt() ?: 0,
                    labsProjects = (result["labs_projects"] as? List<*>?)?.map { it.toString() } ?: emptyList(),
                    confidenceByTopic = (result["confidence_by_topic"] as? Map<*, *>)?.entries?.associate {
                        it.key.toString() to ((it.value as? Number)?.toDouble() ?: 0.0)
                    } ?: emptyMap(),
                    capabilityGraph = CapabilityGraph(
                        certified = (result["certified"] as? List<*>?)?.mapNotNull {
                            (it as? Map<*, *>)?.let { m ->
                                GraphNode(
                                    code = m["code"].toString(),
                                    name = m["name"].toString(),
                                    level = m["level"].toString(),
                                    count = (m["count"] as? Number)?.toInt() ?: 0,
                                )
                            }
                        } ?: emptyList(),
                        delivered = (result["delivered"] as? List<*>?)?.mapNotNull {
                            (it as? Map<*, *>)?.let { m ->
                                GraphNode(
                                    code = m["code"].toString(),
                                    name = m["name"].toString(),
                                    level = m["level"].toString(),
                                    count = (m["count"] as? Number)?.toInt() ?: 0,
                                )
                            }
                        } ?: emptyList(),
                        built = (result["built"] as? List<*>?)?.mapNotNull {
                            (it as? Map<*, *>)?.let { m ->
                                GraphNode(
                                    code = m["code"].toString(),
                                    name = m["name"].toString(),
                                    level = m["level"].toString(),
                                    count = (m["count"] as? Number)?.toInt() ?: 0,
                                )
                            }
                        } ?: emptyList(),
                        skills = (result["skills"] as? List<*>?)?.mapNotNull {
                            (it as? Map<*, *>)?.let { m ->
                                SkillNode(
                                    name = m["name"].toString(),
                                    strength = m["strength"].toString(),
                                    moderate = m["moderate"] == true,
                                    gap = m["gap"] == true,
                                    confidence = (m["confidence"] as? Number)?.toDouble() ?: 0.0,
                                )
                            }
                        } ?: emptyList(),
                    ),
                )
                _uiState.value = _uiState.value.copy(skillProfile = profile)
            } catch (_: Exception) {}
        }
    }

    fun matchOpportunity(manager: String, opportunity: Opportunity) {
        viewModelScope.launch {
            try {
                val result = repository.matchOpportunity(mapOf(
                    "manager" to manager,
                    "opportunity" to mapOf(
                        "course_topics" to (opportunity.strongAreas.ifEmpty {
                            listOf(opportunity.courseCode).filter { it.isNotBlank() }
                        }.ifEmpty { listOf(opportunity.course) }.filter { it.isNotBlank() }),
                        "course_code" to opportunity.courseCode,
                        "course" to opportunity.course,
                        "raw_text" to opportunity.rawText,
                        "is_international" to opportunity.isInternational,
                        "is_critical" to opportunity.isCritical,
                    ),
                ))
                _uiState.value = _uiState.value.copy(
                    matchResult = OpportunityMatchResult(
                        matchScore = (result["match_score"] as? Number)?.toInt() ?: 0,
                        verdict = result["verdict"].toString(),
                        decision = result["decision"].toString(),
                        strongAreas = (result["strong_areas"] as? List<*>?)?.map { it.toString() } ?: emptyList(),
                        weakAreas = (result["weak_areas"] as? List<*>?)?.map { it.toString() } ?: emptyList(),
                        majorGap = result["major_gap"].toString(),
                        preparationHours = result["preparation_hours"].toString(),
                        confidence = result["confidence"].toString(),
                        recommendation = result["recommendation"].toString(),
                        evidence = (result["evidence"] as? List<*>?)?.mapNotNull {
                            (it as? Map<*, *>)?.let { e ->
                                EvidenceItem(
                                    topic = e["topic"].toString(),
                                    evidence = e["evidence"].toString(),
                                    source = e["source"].toString(),
                                    status = e["status"].toString(),
                                    strength = (e["strength"] as? Number)?.toDouble() ?: 0.0,
                                )
                            }
                        } ?: emptyList(),
                        requirements = (result["requirements"] as? Map<*, *>)?.let { r ->
                            OpportunityRequirements(
                                courseCode = r["course_code"].toString(),
                                course = r["course"].toString(),
                                datesStart = r["dates_start"].toString(),
                                datesEnd = r["dates_end"].toString(),
                                location = r["location"].toString(),
                                country = r["country"].toString(),
                                mode = r["mode"].toString(),
                                participants = r["participants"].toString(),
                                documentationMentioned = (r["documentation_mentioned"] as? List<*>?)?.map { it.toString() } ?: emptyList(),
                                action = r["action"].toString(),
                            )
                        } ?: OpportunityRequirements(),
                    )
                )
            } catch (_: Exception) {}
        }
    }

    fun updateDocumentStatus(id: String, status: String) {
        val updated = _uiState.value.opportunities.map {
            if (it.id == id) it.copy(documentStatus = status, status = if (status == "snoozed" || status == "seen") status else it.status)
            else it
        }
        _uiState.value = _uiState.value.copy(opportunities = updated)
        viewModelScope.launch {
            try {
                repository.updateOpportunityDocument(id, mapOf("status" to status))
            } catch (_: Exception) {}
        }
    }

    fun acceptOpportunity(id: String) {
        val updated = _uiState.value.opportunities.map {
            if (it.id == id) it.copy(status = "accepted", decision = "ACCEPTED")
            else it
        }
        _uiState.value = _uiState.value.copy(
            opportunities = updated,
            summary = _uiState.value.summary.copy(
                accepted = _uiState.value.summary.accepted + 1,
                detected = (_uiState.value.summary.detected - 1).coerceAtLeast(0)
            )
        )
        viewModelScope.launch {
            try { repository.acceptOpportunity(id) } catch (_: Exception) {}
        }
    }

    fun declineOpportunity(id: String) {
        val updated = _uiState.value.opportunities.map {
            if (it.id == id) it.copy(status = "declined", decision = "DECLINED")
            else it
        }
        _uiState.value = _uiState.value.copy(
            opportunities = updated,
            summary = _uiState.value.summary.copy(
                declined = _uiState.value.summary.declined + 1,
                detected = (_uiState.value.summary.detected - 1).coerceAtLeast(0)
            )
        )
        viewModelScope.launch {
            try { repository.declineOpportunity(id) } catch (_: Exception) {}
        }
    }

    fun toggleTrustedSource(manager: String, index: Int, enabled: Boolean) {
        val currentSources = _uiState.value.guardianConfig.trustedSources
        if (index !in currentSources.indices) return
        val updated = currentSources.toMutableList()
        updated[index] = updated[index].copy(enabled = enabled)
        val newConfig = _uiState.value.guardianConfig.copy(trustedSources = updated)
        _uiState.value = _uiState.value.copy(guardianConfig = newConfig)
        updateGuardianConfig(manager, newConfig)
    }
}
