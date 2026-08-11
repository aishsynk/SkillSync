package com.example.skillsync.ai

import com.example.skillsync.ui.components.int
import com.example.skillsync.ui.components.intOrNull
import com.example.skillsync.ui.components.list
import com.example.skillsync.ui.components.obj
import com.example.skillsync.ui.components.rows
import com.example.skillsync.ui.components.str

/**
 * The fact base the agent reasons over.
 *
 * Every RMS surface the app can reach is fused into one row per trainer, so a
 * question can be answered from a single object instead of five payloads that
 * each know a third of the story. Nothing here is inferred or invented: each
 * field names the payload it came from, and anything RMS did not return stays
 * null so the agent can say "not measured" rather than guess a zero.
 *
 * The distinction matters. A trainer with `utilisation = null` has not been
 * measured; a trainer with `utilisation = 0` is genuinely idle. Collapsing the
 * two is how a dashboard starts lying.
 */
data class TrainerFact(
    val email: String,
    val name: String,
    val designation: String = "",
    val location: String = "",

    // unified-manager-intelligence · trainer_operations_df
    val utilisation: Int? = null,
    val capacityBucket: String = "",
    val feedbackRisk: String = "",
    val recommendedAction: String = "",

    // unified-manager-intelligence · trainer_current_state_df
    val currentStatus: String = "",
    val currentCourse: String = "",
    val nextCourse: String = "",
    val nextStartsAt: String = "",

    // team-capability · trainers[]
    val readiness: Int? = null,
    val readinessBucket: String = "",
    val certsHeld: List<String> = emptyList(),
    val certGaps: List<String> = emptyList(),
    val teachableCourses: List<String> = emptyList(),
    val coveragePct: Int? = null,

    // delivery_intelligence_df
    val deliveryRisk: String = "",
    val deliveryReadinessLabel: String = "",

    // trainer-utilization-history · series
    val utilisationHistory: List<Int> = emptyList(),

    // actions inbox
    val openActions: Int = 0,

    // allocation-desk · derived: demand this trainer is a ranked candidate for
    val candidateFor: List<DemandMatch> = emptyList(),
) {
    /** Month over month movement, only when a real series exists. */
    val utilisationTrend: Int?
        get() = if (utilisationHistory.size >= 2)
            utilisationHistory.last() - utilisationHistory[utilisationHistory.lastIndex - 1]
        else null

    val isBenched: Boolean get() = capacityBucket.equals("On Bench", true)
    val isStretched: Boolean get() = capacityBucket.equals("Stretched", true)
    val isFlagged: Boolean get() = feedbackRisk.equals("High", true)
    val isFree: Boolean get() = currentStatus.equals("free", true)
}

/** One unallocated batch this trainer could take, with the fit RMS scored. */
data class DemandMatch(
    val demandId: String,
    val courseName: String,
    val relevance: Int,
    val startDate: String = "",
    val deliveryMode: String = "",
    val location: String = "",
    val participants: String = "",
) {
    val isInternational: Boolean get() = deliveryMode.uppercase() in setOf("FMAT", "ILT")
}

/** Team-wide facts, for questions that are about the org rather than a person. */
data class TeamFact(
    val trainers: List<TrainerFact> = emptyList(),
    val utilisationHistory: List<Int> = emptyList(),
    val avgUtilisation: Int? = null,
    val readinessScore: Int? = null,
    val unallocated: List<DemandMatch> = emptyList(),
    val activeDeliveries: Int = 0,
    val upcomingDeliveries: Int = 0,
) {
    val benched get() = trainers.filter { it.isBenched }
    val stretched get() = trainers.filter { it.isStretched }
    val flagged get() = trainers.filter { it.isFlagged }
    val free get() = trainers.filter { it.isFree }
    val totalCertGaps get() = trainers.sumOf { it.certGaps.size }
    val internationalDemand get() = unallocated.filter { it.isInternational }

    fun find(query: String): TrainerFact? {
        val q = query.trim().lowercase()
        if (q.isBlank()) return null
        return trainers.firstOrNull { it.name.lowercase() == q }
            ?: trainers.firstOrNull { it.email.lowercase() == q }
            ?: trainers.firstOrNull { it.name.lowercase().startsWith(q) }
            ?: trainers.firstOrNull { it.name.lowercase().contains(q) }
            ?: trainers.firstOrNull { q.contains(it.name.substringBefore(" ").lowercase()) }
    }
}

/**
 * Fuses every payload the app holds into the fact base.
 *
 * Deliberately tolerant: each source is optional, because the capability call is
 * slower than the dashboard and the allocation desk is only loaded once the
 * manager visits Demand. The agent degrades to what it actually knows rather
 * than refusing to answer.
 */
object FactBuilder {

    fun build(
        dashboard: Map<String, Any>,
        capability: Map<String, Any>? = null,
        allocation: Map<String, Any>? = null,
        actions: List<Map<String, Any>> = emptyList(),
        utilisationHistories: Map<String, List<Int>> = emptyMap(),
    ): TeamFact {
        val ops = dashboard.rows("trainer_operations_df")
        val states = dashboard.rows("trainer_current_state_df")
            .associateBy { it.str("trainer_email").lowercase() }
        val delivery = dashboard.rows("delivery_intelligence_df")
            .associateBy { it.str("trainer_email").lowercase() }
        val capRows = capability?.rows("trainers").orEmpty()
            .associateBy { it.str("trainer_email").lowercase() }
        val kpis = dashboard.obj("manager_kpis")
        val batches = dashboard.rows("batch_engagement_df")

        val openActions = actions
            .filter { it.str("lifecycle_state").ifBlank { "open" } !in setOf("closed", "resolved") }
            .groupBy { it.str("trainer_email").lowercase() }

        // Allocation desk ranks candidates per batch; invert it so each trainer
        // carries the demand they are actually in the running for.
        val demandRows = allocation?.rows("batches").orEmpty()
        val matchesByTrainer = mutableMapOf<String, MutableList<DemandMatch>>()
        demandRows.forEach { batch ->
            val match = DemandMatch(
                demandId = batch.str("demand_id"),
                courseName = batch.str("course_name"),
                relevance = 0,
                startDate = batch.str("start_date"),
                deliveryMode = batch.str("delivery_mode"),
                location = batch.str("location"),
                participants = batch.str("participants"),
            )
            batch.list("candidates").forEach { candidate ->
                val key = candidate.str("trainer_email").lowercase()
                if (key.isBlank()) return@forEach
                matchesByTrainer.getOrPut(key) { mutableListOf() }
                    .add(match.copy(relevance = candidate.intOrNull("relevance") ?: 0))
            }
        }

        val trainers = ops.map { row ->
            val email = row.str("official_email").lowercase()
            val state = states[email]
            val cap = capRows[email]
            val cert = cap?.obj("certification")
            TrainerFact(
                email = email,
                name = row.str("trainer_name").ifBlank { email },
                designation = row.str("designation"),
                location = row.str("location"),

                utilisation = row.intOrNull("current_utilization"),
                capacityBucket = row.str("capacity_bucket"),
                feedbackRisk = row.str("feedback_risk"),
                recommendedAction = row.str("recommended_action"),

                currentStatus = state?.str("current_status").orEmpty(),
                currentCourse = state?.obj("current_batch")?.str("course_name").orEmpty(),
                nextCourse = state?.obj("next_batch")?.str("course_name").orEmpty(),
                nextStartsAt = state?.obj("next_batch")?.str("start_at").orEmpty(),

                readiness = cap?.intOrNull("readiness_score"),
                readinessBucket = cap?.str("readiness_bucket").orEmpty(),
                certsHeld = cert?.list("held")?.map { it.str("name") }?.filter { it.isNotBlank() }.orEmpty(),
                certGaps = cert?.list("missing")?.map { it.str("name") }?.filter { it.isNotBlank() }.orEmpty(),
                teachableCourses = cap?.list("courses")?.map { it.str("course") }?.filter { it.isNotBlank() }.orEmpty(),
                coveragePct = cert?.intOrNull("coverage_pct"),

                deliveryRisk = delivery[email]?.str("delivery_risk_level").orEmpty(),
                deliveryReadinessLabel = delivery[email]?.str("delivery_readiness_label").orEmpty(),

                utilisationHistory = utilisationHistories[email].orEmpty(),
                openActions = openActions[email]?.size ?: 0,
                candidateFor = matchesByTrainer[email]?.sortedByDescending { it.relevance }.orEmpty(),
            )
        }

        return TeamFact(
            trainers = trainers,
            utilisationHistory = (kpis?.get("utilization_history") as? List<*>)
                ?.mapNotNull { (it as? Number)?.toInt() }.orEmpty(),
            avgUtilisation = kpis?.intOrNull("avg_team_utilization"),
            readinessScore = capability?.obj("kpis")?.intOrNull("team_readiness_score")
                ?: kpis?.intOrNull("team_readiness_score"),
            unallocated = dashboard.rows("unallocated_demand_df").map {
                DemandMatch(
                    demandId = it.str("demand_id"),
                    courseName = it.str("course_name"),
                    relevance = 0,
                    startDate = it.str("start_date"),
                    deliveryMode = it.str("delivery_mode"),
                    location = it.str("location"),
                    participants = it.str("participants"),
                )
            },
            activeDeliveries = batches.count { it.str("engagement_state") == "current" },
            upcomingDeliveries = batches.count { it.str("engagement_state") == "upcoming" },
        )
    }
}
