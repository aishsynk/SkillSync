package com.example.skillsync.feature.training.ui

import com.example.skillsync.core.data.CopilotRepository
import com.example.skillsync.core.network.AgentAskRequest
import com.example.skillsync.core.network.AgentAskResponse
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
 * Confirms CopilotViewModel goes through CopilotRepository (not a direct
 * Retrofit call) for both per-trainer and team-level questions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CopilotViewModelTest {

    private class FakeRepository : CopilotRepository() {
        var askCalls = 0
        var askTeamCalls = 0

        override suspend fun ask(request: AgentAskRequest): AgentAskResponse {
            askCalls++
            return AgentAskResponse(
                answer = "Skill coverage looks solid",
                evidence = null, source = null, confidence = "high",
                decisionVersion = null, error = null,
            )
        }

        override suspend fun askTeam(body: Map<String, String>): Map<String, Any> {
            askTeamCalls++
            return mapOf("answer" to "3 trainers are ready", "confidence" to "high")
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
    fun askQuestion_goesThroughTheRepository() = runVmTest {
        val repo = FakeRepository()
        val vm = CopilotViewModel(repo)

        vm.askQuestion("manager@koenig-solutions.com", "trainer@koenig-solutions.com", "skill_gap", "What are the gaps?")
        advanceUntilIdle()

        assertEquals(1, repo.askCalls)
        assertTrue(vm.messages.value.any { it is ChatMessage.Agent })
        assertTrue(vm.messages.value.none { it is ChatMessage.Loading })
    }

    @Test
    fun askTeam_goesThroughTheRepository() = runVmTest {
        val repo = FakeRepository()
        val vm = CopilotViewModel(repo)

        vm.askTeam("manager@koenig-solutions.com", "Who is ready?", questionKey = "readiness")
        advanceUntilIdle()

        assertEquals(1, repo.askTeamCalls)
        assertTrue(vm.messages.value.any { it is ChatMessage.Team })
    }
}
