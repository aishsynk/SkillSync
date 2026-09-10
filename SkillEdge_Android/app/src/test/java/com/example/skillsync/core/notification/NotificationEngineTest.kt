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

    @Test
    fun allocationMessageIncludesVerifiedTrainerSkillLevel() {
        val data = mapOf<String, Any>(
            "batch_engagement_df" to listOf(
                mapOf(
                    "assignment_id" to "A-8", "engagement_state" to "upcoming",
                    "trainer_name" to "Niharika", "trainer_email" to "n@example.com",
                    "course_name" to "AI-102", "skill_level" to "7",
                    "skill_level_source" to "trainer_details",
                )
            )
        )
        val event = NotificationEngine.detect(data, emptySet(), emptySet(), emptySet()).single()
        assertEquals("Niharika has been allocated to AI-102 · Trainer skill level: L7.", event.message)
    }

    @Test
    fun deliveryAlertBecomesEventAndDedupesByKind() {
        val data = mapOf<String, Any>(
            "delivery_alerts" to listOf(
                mapOf(
                    "assignment_id" to "A-9", "kind" to "recording_gap",
                    "course" to "AZ-104", "trainer_name" to "Alpha",
                    "detail" to "No session recording submitted yet.", "severity" to "high",
                )
            )
        )
        val fresh = NotificationEngine.detect(data, emptySet(), emptySet(), emptySet(), emptySet()).single()
        assertEquals(NotificationEngine.BUCKET_DELIVERY, fresh.bucket)
        assertEquals("A-9:recording_gap", fresh.id)
        assertEquals("delivery_list", fresh.targetType)
        assertEquals("A-9", fresh.targetId)

        val seen = NotificationEngine.detect(
            data, emptySet(), emptySet(), emptySet(), setOf("A-9:recording_gap")
        )
        assertEquals(0, seen.size)
    }
}
