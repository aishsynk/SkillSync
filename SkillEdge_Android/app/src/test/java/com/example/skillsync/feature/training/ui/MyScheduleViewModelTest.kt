package com.example.skillsync.feature.training.ui

import com.example.skillsync.core.data.ScheduleRepository
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
 * Confirms MyScheduleViewModel goes through ScheduleRepository (not a direct
 * Retrofit call) and — the P0 regression this guards against — that a real
 * fetch failure surfaces as an error rather than silently rendering as an
 * empty "Clear diary" schedule.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MyScheduleViewModelTest {

    private class FakeRepository(
        var result: Result<Map<String, Any>> = Result.success(emptyMap()),
    ) : ScheduleRepository() {
        var calls = 0
        override suspend fun myCalendar(email: String): Map<String, Any> {
            calls++
            return result.getOrThrow()
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
    fun load_success_populatesDataThroughTheRepository() = runVmTest {
        val repo = FakeRepository(result = Result.success(mapOf("current" to listOf(mapOf("course" to "AI-102")))))
        val vm = MyScheduleViewModel(repo)
        vm.load("trainer@koenig-solutions.com")
        advanceUntilIdle()

        assertEquals(1, repo.calls)
        val current = vm.data.value?.get("current") as List<*>
        assertEquals("AI-102", (current.first() as Map<*, *>)["course"])
        assertNull(vm.error.value)
        assertTrue(!vm.loading.value)
    }

    @Test
    fun load_genuinelyEmptySchedule_isNotAnError() = runVmTest {
        val repo = FakeRepository(result = Result.success(emptyMap()))
        val vm = MyScheduleViewModel(repo)
        vm.load("trainer@koenig-solutions.com")
        advanceUntilIdle()

        assertNull(vm.error.value)
        assertEquals(emptyMap<String, Any>(), vm.data.value)
    }

    @Test
    fun load_networkFailure_surfacesAnErrorInsteadOfAFalseEmptySchedule() = runVmTest {
        // This is the exact P0 regression: the old implementation caught the
        // exception and did nothing, leaving `data` null — indistinguishable
        // from a genuinely empty schedule. It must now be a real error state.
        val repo = FakeRepository(result = Result.failure(RuntimeException("timeout")))
        val vm = MyScheduleViewModel(repo)
        vm.load("trainer@koenig-solutions.com")
        advanceUntilIdle()

        assertTrue(vm.error.value != null)
        assertNull(vm.data.value)
        assertTrue(!vm.loading.value)
    }

    @Test
    fun load_afterFailure_retryCanRecover() = runVmTest {
        val repo = FakeRepository(result = Result.failure(RuntimeException("timeout")))
        val vm = MyScheduleViewModel(repo)
        vm.load("trainer@koenig-solutions.com")
        advanceUntilIdle()
        assertTrue(vm.error.value != null)

        repo.result = Result.success(mapOf("current" to emptyList<Map<String, Any>>()))
        vm.load("trainer@koenig-solutions.com")
        advanceUntilIdle()

        assertNull(vm.error.value)
        assertEquals(2, repo.calls)
    }
}
