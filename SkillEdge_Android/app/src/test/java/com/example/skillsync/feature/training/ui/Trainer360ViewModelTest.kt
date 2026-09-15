package com.example.skillsync.feature.training.ui

import com.example.skillsync.core.data.ManagerRepository
import com.example.skillsync.core.data.TrainerRepository
import com.example.skillsync.core.network.TrainerIndexDto
import com.example.skillsync.core.network.TrainerIndexResponseDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Confirms Trainer360ViewModel's trainer-domain calls (sentiment, trainer
 * index, endorsement, readiness) go through TrainerRepository, not a direct
 * Retrofit call. Deliberately does not exercise load()/refresh()/fetch(),
 * which take an android.content.Context and touch LocalCache -- out of
 * scope for a plain JVM unit test without a mocking framework in this repo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Trainer360ViewModelTest {

    private class FakeTrainerRepository : TrainerRepository() {
        var sentimentCalls = 0
        var trainerIndexCalls = 0
        var endorseSkillCalls = 0
        var readinessCalls = 0

        override suspend fun sentiment(email: String): Map<String, Any> {
            sentimentCalls++
            return mapOf("praise_ratio" to 0.8)
        }

        override suspend fun trainerIndex(email: String): TrainerIndexResponseDto {
            trainerIndexCalls++
            return TrainerIndexResponseDto(trainer_index = TrainerIndexDto(total_score = 92.0))
        }

        override suspend fun readiness(manager: String, email: String): Map<String, Any> {
            readinessCalls++
            return mapOf("available" to true)
        }

        override suspend fun endorseSkill(body: Map<String, Any>): Map<String, Any> {
            endorseSkillCalls++
            return mapOf("ok" to true, "rms_message" to "Skill endorsed to RMS")
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
    fun fetchSentiment_goesThroughTheRepository() = runVmTest {
        val trainerRepo = FakeTrainerRepository()
        val vm = Trainer360ViewModel(ManagerRepository(), trainerRepo)

        vm.fetchSentiment("trainer@koenig-solutions.com")
        advanceUntilIdle()

        assertEquals(1, trainerRepo.sentimentCalls)
        assertEquals(0.8, vm.sentiment.value?.get("praise_ratio"))
    }

    @Test
    fun loadReadiness_goesThroughTheRepository() = runVmTest {
        val trainerRepo = FakeTrainerRepository()
        val vm = Trainer360ViewModel(ManagerRepository(), trainerRepo)

        vm.loadReadiness("manager@koenig-solutions.com", "trainer@koenig-solutions.com")
        advanceUntilIdle()

        assertEquals(1, trainerRepo.readinessCalls)
        assertEquals(true, vm.readiness.value?.get("available"))
    }

    @Test
    fun endorseSkill_goesThroughTheRepositoryAndReportsSuccess() = runVmTest {
        val trainerRepo = FakeTrainerRepository()
        val vm = Trainer360ViewModel(ManagerRepository(), trainerRepo)
        var resultOk = false
        var resultMsg = ""

        vm.endorseSkill(
            managerEmail = "manager@koenig-solutions.com",
            trainerEmail = "trainer@koenig-solutions.com",
            courseId = "course-1",
            courseName = "Kubernetes",
            onResult = { ok, msg -> resultOk = ok; resultMsg = msg },
        )
        advanceUntilIdle()

        assertEquals(1, trainerRepo.endorseSkillCalls)
        assertTrue(resultOk)
        assertEquals("Skill endorsed to RMS", resultMsg)
    }
}
