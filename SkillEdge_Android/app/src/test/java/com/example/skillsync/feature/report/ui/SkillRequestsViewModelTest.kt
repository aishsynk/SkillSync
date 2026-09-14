package com.example.skillsync.feature.report.ui

import com.example.skillsync.core.data.SkillRequestsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Confirms SkillRequestsViewModel goes through SkillRequestsRepository (not a
 * direct Retrofit call, which is the P0/P1 bypass this test guards against)
 * and handles success/error/retry correctly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SkillRequestsViewModelTest {

    private class FakeRepository(
        var pendingResult: Result<Map<String, Any>> = Result.success(emptyMap()),
    ) : SkillRequestsRepository() {
        var pendingCalls = 0
        override suspend fun pending(status: String): Map<String, Any> {
            pendingCalls++
            return pendingResult.getOrThrow()
        }
        override suspend fun resolve(id: String, approve: Boolean): Map<String, Any> =
            mapOf("success" to true)
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
    fun load_success_populatesRequestsThroughTheRepository() = runVmTest {
        val repo = FakeRepository(
            pendingResult = Result.success(
                mapOf("requests" to listOf(mapOf("id" to "r1", "trainer_name" to "Priya Sharma"))),
            ),
        )
        val vm = SkillRequestsViewModel(repo)
        vm.load()
        advanceUntilIdle()

        assertEquals(1, repo.pendingCalls)
        assertEquals(1, vm.requests.value.size)
        assertNull(vm.error.value)
        assertTrue(!vm.loading.value)
    }

    @Test
    fun load_failure_surfacesAnErrorInsteadOfSilentlyEmptyingTheList() = runVmTest {
        val repo = FakeRepository(pendingResult = Result.failure(RuntimeException("network down")))
        val vm = SkillRequestsViewModel(repo)
        vm.load()
        advanceUntilIdle()

        assertTrue(vm.error.value != null)
        assertTrue(!vm.loading.value)
    }

    @Test
    fun load_afterFailure_canRetryAndRecover() = runVmTest {
        val repo = FakeRepository(pendingResult = Result.failure(RuntimeException("network down")))
        val vm = SkillRequestsViewModel(repo)
        vm.load()
        advanceUntilIdle()
        assertTrue(vm.error.value != null)

        repo.pendingResult = Result.success(mapOf("requests" to listOf(mapOf("id" to "r1"))))
        vm.load()
        advanceUntilIdle()

        assertNull(vm.error.value)
        assertEquals(1, vm.requests.value.size)
        assertEquals(2, repo.pendingCalls)
    }
}
