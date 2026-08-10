package com.example.skillsync.util

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationEngineTest {
    @Test
    fun newDemandTargetsItsDetail() {
        val data = mapOf<String, Any>(
            "unallocated_demand_df" to listOf(
                mapOf("demand_id" to "D-42", "course_name" to "DP-700")
            )
        )
        val event = NotificationEngine.detect(data, emptySet(), emptySet(), emptySet()).single()
        assertEquals("demand", event.targetType)
        assertEquals("D-42", event.targetId)
    }

    @Test
    fun allocationTargetsTrainerProfile() {
        val data = mapOf<String, Any>(
            "batch_engagement_df" to listOf(
                mapOf(
                    "assignment_id" to "A-7", "engagement_state" to "upcoming",
                    "trainer_name" to "Niharika", "trainer_email" to "n@example.com",
                    "course_name" to "AI-102",
                )
            )
        )
        val event = NotificationEngine.detect(data, emptySet(), emptySet(), emptySet()).single()
        assertEquals("trainer", event.targetType)
        assertEquals("n@example.com", event.targetId)
    }
}
