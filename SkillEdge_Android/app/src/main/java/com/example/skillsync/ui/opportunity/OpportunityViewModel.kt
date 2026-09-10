package com.example.skillsync.ui.opportunity

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skillsync.data.DataRepository
import com.example.skillsync.data.cache.LocalCache
import com.example.skillsync.data.models.Opportunity
import com.example.skillsync.data.models.OpportunityGuardianConfig
import com.example.skillsync.data.models.OpportunityMatchResult
import com.example.skillsync.data.models.OpportunitySummary
import com.example.skillsync.data.models.SkillProfile
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
    private val repository = DataRepository()
    private val _uiState = MutableStateFlow(OpportunityUiState())
    val uiState: StateFlow<OpportunityUiState> = _uiState.asStateFlow()

    fun loadOpportunities(manager: String, status: String = "") {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                val result = repository.opportunities(manager, status)
                val items = (result["items"] as? List<*>?)?.mapNotNull {
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
                        location = map["location"].toString(),
                        country = map["country"].toString(),
                        datesStart = map["dates_start"].toString(),
                        datesEnd = map["dates_end"].toString(),
                        detectedAt = map["detected_at"].toString(),
                        status = map["status"].toString(),
                        skillMatchScore = (map["skill_match_score"] as? Number)?.toInt() ?: 0,
                        verdict = map["verdict"].toString(),
                        confidence = map["confidence"].toString(),
                        preparationHours = map["preparation_hours"].toString(),
                        majorGap = map["major_gap"].toString(),
                        strongAreas = (map["strong_areas"] as? List<*>?)?.map { it.toString() } ?: emptyList(),
                        weakAreas = (map["weak_areas"] as? List<*>?)?.map { it.toString() } ?: emptyList(),
                        evidence = emptyList(),
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
                val result = repository.guardianConfig(manager)
                val config = OpportunityGuardianConfig(
                    trustedSources = (result["trusted_sources"] as? List<*>?)?.mapNotNull {
                        (it as? Map<*, *>)?.let { m ->
                            OpportunityGuardianConfig.TrustedSource(
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
                            OpportunityGuardianConfig.EscalationRule(
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
                val result = repository.skillProfile(manager)
                val profile = SkillProfile(
                    email = manager,
                    certifications = (result["certifications"] as? List<*>?)?.map { it.toString() } ?: emptyList(),
                    technologies = (result["technologies"] as? List<*>?)?.map { it.toString() } ?: emptyList(),
                    coursesDelivered = (result["courses_delivered"] as? List<*>?)?.map { it.toString() } ?: emptyList(),
                    experienceYears = (result["experience_years"] as? Number)?.toInt() ?: 0,
                    labsProjects = (result["labs_projects"] as? List<*>?)?.map { it.toString() } ?: emptyList(),
                    confidenceByTopic = (result["confidence_by_topic"] as? Map<*, *>)?.mapValues { (_, v) ->
                        (v as? Number)?.toDouble() ?: 0.0
                    } ?: emptyMap(),
                    capabilityGraph = SkillProfile.CapabilityGraph(
                        certified = (result["certified"] as? List<*>?)?.mapNotNull {
                            (it as? Map<*, *>)?.let { m ->
                                SkillProfile.GraphNode(
                                    code = m["code"].toString(),
                                    name = m["name"].toString(),
                                    level = m["level"].toString(),
                                    count = (m["count"] as? Number)?.toInt() ?: 0,
                                )
                            }
                        } ?: emptyList(),
                        delivered = (result["delivered"] as? List<*>?)?.mapNotNull {
                            (it as? Map<*, *>)?.let { m ->
                                SkillProfile.GraphNode(
                                    code = m["code"].toString(),
                                    name = m["name"].toString(),
                                    level = m["level"].toString(),
                                    count = (m["count"] as? Number)?.toInt() ?: 0,
                                )
                            }
                        } ?: emptyList(),
                        built = (result["built"] as? List<*>?)?.mapNotNull {
                            (it as? Map<*, *>)?.let { m ->
                                SkillProfile.GraphNode(
                                    code = m["code"].toString(),
                                    name = m["name"].toString(),
                                    level = m["level"].toString(),
                                    count = (m["count"] as? Number)?.toInt() ?: 0,
                                )
                            }
                        } ?: emptyList(),
                        skills = (result["skills"] as? List<*>?)?.mapNotNull {
                            (it as? Map<*, *>)?.let { m ->
                                SkillProfile.SkillNode(
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

    fun matchOpportunity(body: Map<String, Any>) {
        viewModelScope.launch {
            try {
                val result = repository.matchOpportunity(body)
                _uiState.value = _uiState.value.copy(
                    matchResult = OpportunityMatchResult(
                        matchScore = (result["match_score"] as? Number)?.toInt() ?: 0,
                        verdict = result["verdict"].toString(),
                        strongAreas = (result["strong_areas"] as? List<*>?)?.map { it.toString() } ?: emptyList(),
                        weakAreas = (result["weak_areas"] as? List<*>?)?.map { it.toString() } ?: emptyList(),
                        majorGap = result["major_gap"].toString(),
                        preparationHours = result["preparation_hours"].toString(),
                        confidence = result["confidence"].toString(),
                        evidence = emptyList(),
                    )
                )
            } catch (_: Exception) {}
        }
    }

    fun acceptOpportunity(id: String) {
        viewModelScope.launch {
            try { repository.acceptOpportunity(id) } catch (_: Exception) {}
        }
    }

    fun declineOpportunity(id: String) {
        viewModelScope.launch {
            try { repository.declineOpportunity(id) } catch (_: Exception) {}
        }
    }
}
