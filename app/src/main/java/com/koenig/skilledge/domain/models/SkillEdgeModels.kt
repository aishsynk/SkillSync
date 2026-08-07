package com.koenig.skilledge.domain.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// ============= Session & Auth =============

@Parcelize
data class SessionData(
    val email: String,
    val name: String? = null,
    val issuedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (24 * 60 * 60 * 1000) // 24h default
) : Parcelable

// ============= Unified Intelligence Payload =============

@Parcelize
data class UnifiedManagerIntelligence(
    val generatedAt: Long,
    val servedAt: Long,
    val cacheStatus: String, // "live", "cached", "stale", "refresh_failed"
    val cacheAgeMinutes: Int? = null,
    val refreshPending: Boolean = false,
    val lastRefreshError: String? = null,
    val trainerCount: Int,
    val trainerOperationsDf: List<TrainerOperation>,
    val trainerCurrentStateDf: List<TrainerCurrentState>,
    val batchEngagementDf: List<BatchEngagement>,
    val managerActionObjects: List<ManagerAction>,
    val trainerFeedbackSummaryDf: List<TrainerFeedback>,
    val unallocatedDemandDf: List<UnallocatedDemand>,
    val trainerDecisionObjects: List<TrainerDecision>,
    val dataHealthDf: List<DataHealth>,
    val futureSkillRoadmapDf: List<FutureSkill>,
    val certificationSummary: CertificationSummary? = null,
    val deliveryIntelligence: DeliveryIntelligence? = null,
    val allocationIntelligence: AllocationIntelligence? = null,
    val organizationIntelligence: OrganizationIntelligence? = null,
    val growthIntelligence: List<GrowthIntelligence> = emptyList(),
    val executiveIntelligence: ExecutiveIntelligence? = null,
    val feedbackCoachingDf: List<FeedbackCoaching> = emptyList(),
    val futureCertificationRoadmapDf: List<FutureCertification> = emptyList(),
    val vendorStrengthDf: List<VendorStrength> = emptyList()
) : Parcelable

// ============= Core Datasets =============

@Parcelize
data class TrainerOperation(
    val trainerId: String,
    val trainerName: String,
    val officialEmail: String,
    val designation: String? = null,
    val directOrIndirect: String? = null,
    val currentUtilization: Double? = null,
    val utilizationCurrent: Double? = null, // Fallback
    val readinessBucket: String? = null, // "Ready", "Prep", "Blocked", "Unknown"
    val overallReadinessScore: Double? = null,
    val feedbackRisk: String? = null, // "Low", "Medium", "High"
    val recommendedAction: String? = null
) : Parcelable

@Parcelize
data class TrainerCurrentState(
    val trainerId: String,
    val trainerName: String,
    val trainerEmail: String,
    val trainerKey: String? = null,
    val currentStatus: String, // "teaching_now", "preparing", "scheduled_today", "free", "blocked", "unknown"
    val statusLabel: String? = null,
    val confidence: Int = 0, // 0-100
    val confidenceReason: String? = null,
    val currentBatch: BatchInfo? = null,
    val nextBatch: BatchInfo? = null,
    val upcomingBatchCount: Int = 0,
    val contradictions: List<String> = emptyList(),
    val reason: String? = null
) : Parcelable

@Parcelize
data class BatchInfo(
    val batchId: String? = null,
    val courseName: String? = null,
    val deliveryMode: String? = null,
    val location: String? = null,
    val startAt: String? = null,
    val endAt: String? = null
) : Parcelable

@Parcelize
data class BatchEngagement(
    val trainerId: String,
    val trainerName: String,
    val trainerEmail: String,
    val courseName: String,
    val startAt: String,
    val endAt: String? = null,
    val deliveryMode: String,
    val engagementState: String, // "current", "upcoming", "completed"
    val location: String? = null,
    val customerName: String? = null
) : Parcelable

@Parcelize
data class ManagerAction(
    val actionId: String,
    val trainerId: String,
    val trainerName: String,
    val title: String,
    val category: String, // "blocked_allocation", "feedback_followup", "sales_demand"
    val priority: String, // "High", "Medium", "Low"
    val lifecycleState: String, // "open", "closed", "escalated", "reassigned"
    val recommendedAction: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val dueAt: Long? = null
) : Parcelable

@Parcelize
data class TrainerFeedback(
    val trainerId: String,
    val trainerName: String,
    val trainerEmail: String,
    val negativeCount: Int = 0,
    val positiveCount: Int = 0,
    val averageRating: Double? = null,
    val riskLevel: String? = null,
    val recentFeedback: List<FeedbackItem> = emptyList()
) : Parcelable

@Parcelize
data class FeedbackItem(
    val date: String,
    val rating: Int,
    val comment: String? = null
) : Parcelable

@Parcelize
data class UnallocatedDemand(
    val demandId: String,
    val courseName: String,
    val startDate: String,
    val endDate: String? = null,
    val deliveryMode: String,
    val location: String? = null,
    val customer: String? = null,
    val requiredSkills: List<String> = emptyList(),
    val priority: String = "Medium",
    val isInternational: Boolean = false,
    val countryCode: String = "IN",
    val flagEmoji: String = "🇮🇳",
    val mismatchConstraints: List<String> = emptyList(),
    val isException: Boolean = false,
    val suitabilityScore: Int = 90,
    val priorityScore: Int = 70
) : Parcelable

@Parcelize
data class TrainerDecision(
    val trainerId: String,
    val trainerName: String,
    val trainerEmail: String,
    val assignmentStatus: String, // "ready", "prep", "blocked", "unknown"
    val recommendedAction: String,
    val skillGaps: List<String> = emptyList(),
    val riskFactors: List<String> = emptyList(),
    val nextManagerAction: String? = null
) : Parcelable

@Parcelize
data class DataHealth(
    val dataset: String,
    val issueType: String,
    val affectedRecords: Int,
    val severity: String, // "Info", "Warning", "Error"
    val recommendation: String? = null
) : Parcelable

@Parcelize
data class FutureSkill(
    val trainerId: String,
    val trainerName: String,
    val relatedFutureSkill: String,
    val futureOem: String? = null,
    val confidence: Double? = null,
    val recommendation: String? = null
) : Parcelable

// ============= Intelligence Blocks =============

@Parcelize
data class CertificationSummary(
    val totalCertifications: Int,
    val certificationsNeeded: Int,
    val certByVendor: Map<String, Int> = emptyMap(),
    val certificationGaps: List<String> = emptyList(),
    val vendorCoveragePercentage: Double? = null
) : Parcelable

@Parcelize
data class DeliveryIntelligence(
    val currentDeliveries: Int,
    val upcomingBatches: Int,
    val unallocatedDemand: Int,
    val deliveryPipeline: List<PipelineEntry> = emptyList()
) : Parcelable

@Parcelize
data class PipelineEntry(
    val phase: String, // "live", "upcoming", "unallocated", "unknown"
    val count: Int
) : Parcelable

@Parcelize
data class AllocationIntelligence(
    val totalDemand: Int,
    val allocatedTrainers: Int,
    val allocationBlockers: List<String> = emptyList(),
    val matchedPairs: List<TrainerDemandPair> = emptyList()
) : Parcelable

@Parcelize
data class TrainerDemandPair(
    val trainerId: String,
    val demandId: String,
    val fitScore: Double, // 0-100
    val confidence: Double? = null
) : Parcelable

@Parcelize
data class OrganizationIntelligence(
    val totalReportees: Int,
    val benchRisk: String, // "Single Point of Failure", "Thin Bench", "Well-Distributed"
    val benchRiskLevel: String, // "High", "Medium", "Low"
    val averageUtilization: Double? = null,
    val capacityHeadroom: Double? = null
) : Parcelable

@Parcelize
data class GrowthIntelligence(
    val trainerId: String,
    val trainerName: String,
    val suggestedSkill: String,
    val growthPath: String? = null,
    val priority: String = "Medium"
) : Parcelable

@Parcelize
data class ExecutiveIntelligence(
    val summary: String,
    val needsAttention: Boolean,
    val highSignals: List<String> = emptyList(),
    val riskFactors: List<String> = emptyList(),
    val opportunityFactors: List<String> = emptyList()
) : Parcelable

@Parcelize
data class FeedbackCoaching(
    val trainerId: String,
    val trainerName: String,
    val coachingRecommendation: String,
    val focusAreas: List<String> = emptyList()
) : Parcelable

@Parcelize
data class FutureCertification(
    val trainerId: String,
    val trainerName: String,
    val certificationName: String,
    val vendor: String,
    val completionEstimate: String? = null,
    val priority: String = "Medium"
) : Parcelable

@Parcelize
data class VendorStrength(
    val vendor: String,
    val trainerCount: Int,
    val certificationCount: Int,
    val averageScore: Double? = null,
    val benchRisk: String? = null
) : Parcelable

// ============= API Response Wrappers =============

data class ApiResponse<T>(
    val content: T?,
    val error: String? = null,
    val statuscode: Int = 200,
    val message: String? = null
)

// ============= UI State =============

sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val error: String) : UiState<Nothing>()
    object Empty : UiState<Nothing>()
}

// ============= Batch Details Accordion Models =============

@Parcelize
data class PaxItem(
    val studentName: String,
    val studentEmail: String
) : Parcelable

@Parcelize
data class BatchDetailsData(
    val assignmentId: String,
    val courseName: String = "AZ-104: Microsoft Azure Administrator",
    val deliveryMode: String = "ILT",
    val region: String = "London, UK",
    val startDate: String = "10 Aug 2026",
    val endDate: String = "14 Aug 2026",
    val paxCount: Int = 12,
    val paxRoster: List<PaxItem> = emptyList(),
    val recordingLink: String? = null,
    val totalFee: String = "£ 4,500",
    val currency: String = "GBP",
    val csmName: String = "Operations UK",
    val scid: String = "SC-99823",
    val location: String = "London, UK (Virtual / Onsite)",
    val startTime: String = "09:00 AM",
    val endTime: String = "05:00 PM",
    val tocUrl: String = "https://www.koenig-solutions.com/course-toc"
) : Parcelable
