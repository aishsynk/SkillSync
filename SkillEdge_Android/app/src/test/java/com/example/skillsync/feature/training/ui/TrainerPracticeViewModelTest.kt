package com.example.skillsync.feature.training.ui

import com.example.skillsync.core.data.TrainerRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Confirms TrainerPracticeViewModel goes through TrainerRepository (not a
 * direct Retrofit call) for both feedback log and recordings.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrainerPracticeViewModelTest {

    private class FakeRepository : TrainerRepository() {
        var feedbackLogCalls = 0
        var recordingsCalls = 0

        override suspend fun feedbackLog(email: String): Map<String, Any> {
            feedbackLogCalls++
            return mapOf("entries" to listOf(mapOf("comment" to "Great session")))
        }

        override suspend fun recordings(email: String): Map<String, Any> {
            recordingsCalls++
            return mapOf("recordings" to listOf(mapOf("url" to "https://example.com/rec.mp4")))
        }
    }

    private fun runVmTest(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            block()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun load_goesThroughTheRepositoryForBothFeedbackAndRecordings() = runVmTest {
        val repo = FakeRepository()
        val vm = TrainerPracticeViewModel(repo)

        vm.load("trainer@koenig-solutions.com")
        advanceUntilIdle()

        assertEquals(1, repo.feedbackLogCalls)
        assertEquals(1, repo.recordingsCalls)
        assertEquals(1, vm.feedback.value.size)
        assertEquals(1, vm.recordings.value.size)
        assertFalse(vm.loading.value)
    }
}
