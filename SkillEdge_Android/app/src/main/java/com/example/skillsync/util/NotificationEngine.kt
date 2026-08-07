package com.example.skillsync.util

import com.example.skillsync.ui.components.rows
import com.example.skillsync.ui.components.str

/** [id] is the dedupe key persisted to [NotificationStateStore] once fired. */
data class NotifyEvent(
    val id: String,
    val bucket: String,
    val title: String,
    val message: String,
)

/**
 * Pure delta detection over the unified intelligence payload
 * (`batch_engagement_df` / `unallocated_demand_df`, already fetched by the
 * dashboard — no extra API call). Both the foreground poll and the background
 * WorkManager check call [detect] with the same persisted seen-sets, so an
 * event is reported exactly once regardless of which path notices it first.
 *
 * Three triggers, each answering a distinct manager question:
 *  - BUCKET_ALLOCATION: "did one of my reportees just get put on a batch?"
 *  - BUCKET_FEEDBACK:   "did a batch just end? feedback submission is mandatory."
 *  - BUCKET_DEMAND:     "is there a new unallocated batch I need to staff?"
 */
object NotificationEngine {
    const val BUCKET_ALLOCATION = "allocation"
    const val BUCKET_FEEDBACK = "feedback_due"
    const val BUCKET_DEMAND = "demand"

    fun detect(
        data: Map<String, Any>,
        seenAllocation: Set<String>,
        seenFeedback: Set<String>,
        seenDemand: Set<String>,
    ): List<NotifyEvent> {
        val events = mutableListOf<NotifyEvent>()
        val batches = data.rows("batch_engagement_df")
        val demand = data.rows("unallocated_demand_df")

        batches.forEach { b ->
            val id = b.str("assignment_id")
            if (id.isBlank()) return@forEach
            val state = b.str("engagement_state")
            val trainer = b.str("trainer_name").ifBlank { "A trainer" }
            val course = b.str("course_name").ifBlank { "a course" }

            if (state == "current" || state == "upcoming") {
                if (id !in seenAllocation) {
                    events += NotifyEvent(
                        id = id, bucket = BUCKET_ALLOCATION,
                        title = "New Batch Assigned",
                        message = "$trainer has been allocated to $course.",
                    )
                }
            } else if (state == "completed") {
                if (id !in seenFeedback) {
                    events += NotifyEvent(
                        id = id, bucket = BUCKET_FEEDBACK,
                        title = "Feedback Required",
                        message = "$trainer's $course session has ended — feedback submission is mandatory.",
                    )
                }
            }
        }

        demand.forEach { d ->
            val id = d.str("demand_id")
            if (id.isBlank() || id in seenDemand) return@forEach
            val course = d.str("course_name").ifBlank { "A batch" }
            events += NotifyEvent(
                id = id, bucket = BUCKET_DEMAND,
                title = "New Unallocated Batch",
                message = "$course needs a trainer assigned.",
            )
        }

        return events
    }

    /**
     * Individual notifications when there are few; one summary per bucket when
     * there are many, so a big overnight sync doesn't fire a dozen separate
     * system notifications at once.
     */
    fun toNotifications(events: List<NotifyEvent>): List<Pair<String, String>> {
        return events.groupBy { it.bucket }.flatMap { (bucket, group) ->
            if (group.size <= 3) {
                group.map { it.title to it.message }
            } else {
                val title = when (bucket) {
                    BUCKET_ALLOCATION -> "New Batches Assigned"
                    BUCKET_FEEDBACK -> "Feedback Required"
                    BUCKET_DEMAND -> "New Unallocated Batches"
                    else -> "SkillSync Update"
                }
                listOf(title to "${group.size} updates — open the app for details.")
            }
        }
    }
}
