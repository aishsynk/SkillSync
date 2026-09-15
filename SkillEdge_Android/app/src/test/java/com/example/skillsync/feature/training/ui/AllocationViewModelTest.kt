package com.example.skillsync.feature.training.ui

import com.example.skillsync.core.data.BatchRepository
import com.example.skillsync.core.data.ManagerRepository
import com.example.skillsync.core.data.TrainerRepository
import com.example.skillsync.core.network.AllocationCandidatesResponse
import com.example.skillsync.core.network.BulkAssignRequest
import com.example.skillsync.core.network.BulkAssignResponse
import com.example.skillsync.core.network.BulkAssignResult
import com.example.skillsync.core.network.DemandContextResponse
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
 * Confirms AllocationViewModel's Trainer/candidate and Batch/demand calls
 * go through TrainerRepository/BatchRepository, not a direct Retrofit call.
 * Deliberately does not exercise load()/refresh()/fetch()/markSkill(), which
 * take an android.content.Context and touch LocalCache/ActionQueueManager --
 * same out-of-scope rationale as Trainer360ViewModelTest.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AllocationViewModelTest {

    private class FakeTrainerRepository : TrainerRepository() {
        var allocationCandidatesCalls = 0
        var alternativeTrainersCalls = 0
        var bulkAssignSkillCalls = 0

        override suspend fun allocationCandidates(
            manager: String, course: String, start: String, end: String,
            country: String, customer: String, deliveryMode: String, international: String,
        ): AllocationCandidatesResponse {
            allocationCandidatesCalls++
            return AllocationCandidatesResponse(ready = true, candidates = listOf(mapOf("trainer_name" to "Test Trainer")))
        }

        override suspend fun alternativeTrainers(course: String): Map<String, Any> {
            alternativeTrainersCalls++
            return mapOf("available" to true, "trainers" to emptyList<Any>())
        }

        override suspend fun bulkAssignSkill(request: BulkAssignRequest): BulkAssignResponse {
            bulkAssignSkillCalls++
            return BulkAssignResponse(
                requested = request.trainers.size, succeeded = request.trainers.size,
                results = request.trainers.map {
                    BulkAssignResult(trainer_email = it.trainer_email, ok = true, verified = true, message = "Skill recorded")
                },
            )
        }
    }

    private class FakeBatchRepository : BatchRepository() {
        var demandContextCalls = 0

        override suspend fun demandContext(manager: String, demandId: String, courseName: String): DemandContextResponse {
            demandContextCalls++
            return DemandContextResponse(demandId = demandId, confidence = "verified")
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
    fun loadGatedCandidates_goesThroughTheTrainerRepository() = runVmTest {
        val trainerRepo = FakeTrainerRepository()
        val vm = AllocationViewModel(ManagerRepository(), trainerRepo, FakeBatchRepository())

        vm.loadGatedCandidates("manager@koenig-solutions.com", "AZ-104", "2026-09-01", "2026-09-05")
        advanceUntilIdle()

        assertEquals(1, trainerRepo.allocationCandidatesCalls)
        assertTrue(vm.gatedCandidates.value?.ready == true)
    }

    @Test
    fun loadDemandContext_goesThroughTheBatchRepository() = runVmTest {
        val batchRepo = FakeBatchRepository()
        val vm = AllocationViewModel(ManagerRepository(), TrainerRepository(), batchRepo)

        vm.loadDemandContext("manager@koenig-solutions.com", "demand-1", "AZ-104")
        advanceUntilIdle()

        assertEquals(1, batchRepo.demandContextCalls)
        assertEquals("demand-1", vm.demandContext.value?.demandId)
    }

    @Test
    fun globalSearch_goesThroughTheTrainerRepository() = runVmTest {
        val trainerRepo = FakeTrainerRepository()
        val vm = AllocationViewModel(ManagerRepository(), trainerRepo, FakeBatchRepository())

        vm.globalSearch("AZ-104")
        advanceUntilIdle()

        assertEquals(1, trainerRepo.alternativeTrainersCalls)
        assertEquals(true, vm.globalSearchData.value?.get("available"))
    }

    @Test
    fun bulkAssignSkill_goesThroughTheTrainerRepositoryAndReportsPerRowResults() = runVmTest {
        val trainerRepo = FakeTrainerRepository()
        val vm = AllocationViewModel(ManagerRepository(), trainerRepo, FakeBatchRepository())

        vm.bulkAssignSkill("AZ-104", listOf("trainer1@koenig-solutions.com" to 8, "trainer2@koenig-solutions.com" to 6))
        advanceUntilIdle()

        assertEquals(1, trainerRepo.bulkAssignSkillCalls)
        assertEquals(2, vm.bulkResults.value?.size)
        assertTrue(vm.bulkResults.value?.all { it.ok } == true)
    }
}
