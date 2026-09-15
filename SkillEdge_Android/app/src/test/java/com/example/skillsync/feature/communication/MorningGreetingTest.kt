package com.example.skillsync.feature.communication

import com.example.skillsync.feature.communication.domain.CommunicationAudience
import com.example.skillsync.feature.communication.domain.CommunicationAudienceType
import com.example.skillsync.feature.communication.domain.CommunicationEvidence
import com.example.skillsync.feature.communication.domain.CommunicationRepository
import com.example.skillsync.feature.communication.domain.CommunicationRequest
import com.example.skillsync.feature.communication.domain.ComposeManagerMessageUseCase
import com.example.skillsync.feature.communication.engine.CommunicationComposer
import com.example.skillsync.feature.communication.engine.CommunicationPlanner
import com.example.skillsync.feature.communication.engine.CommunicationPurpose
import com.example.skillsync.feature.communication.ui.MorningNoteAction
import com.example.skillsync.feature.communication.ui.MorningNoteViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class MorningGreetingTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun request(day: DayOfWeek, recent: List<String> = emptyList(), variation: Int = 0) = CommunicationRequest(
        audience = CommunicationAudience(CommunicationAudienceType.TEAM),
        purpose = CommunicationPurpose.MORNING_TEAM_GREETING,
        cadence = "morning",
        evidence = CommunicationEvidence(localWeekday = day, recentGreetings = recent, variation = variation),
    )

    private val weekdays = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

    // ── weekday selection ───────────────────────────────────────────────────

    @Test fun everyWeekdayProducesAGreetingForThatDay() {
        weekdays.forEach { day ->
            val plan = CommunicationPlanner.planMorningGreeting(day)!!
            assertEquals(CommunicationPurpose.MORNING_TEAM_GREETING.id, plan.purpose)
            val text = ComposeManagerMessageUseCase().offline(request(day))
            assertTrue("$day produced nothing", text.isNotBlank())
        }
    }

    @Test fun fridayReadsAsFridayNotAsAnotherWeekday() {
        val text = ComposeManagerMessageUseCase().offline(request(DayOfWeek.FRIDAY))
        listOf("Tuesday", "Wednesday", "Thursday").forEach { assertFalse("Friday note mentions $it", text.contains(it)) }
    }

    // ── weekend suppression ─────────────────────────────────────────────────

    @Test fun saturdayAndSundayAreSuppressed() {
        listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY).forEach { day ->
            assertNull(CommunicationPlanner.planMorningGreeting(day))
            assertEquals("", ComposeManagerMessageUseCase().offline(request(day)))
        }
    }

    @Test fun viewModelComposesNothingAtTheWeekend() = runTest(dispatcher) {
        val vm = MorningNoteViewModel(today = { LocalDate.of(2026, 9, 19) }) // Saturday
        vm.bind("m@x.com", history = { emptyList() }, record = {}, delivery = {})
        vm.generate(); advanceUntilIdle()
        assertTrue(vm.state.value.isWeekend)
        assertEquals("", vm.state.value.text)
    }

    // ── anti-repeat ─────────────────────────────────────────────────────────

    @Test fun aGreetingDoesNotRepeatTheRecentOnesOpeningThoughtOrClosing() {
        val first = ComposeManagerMessageUseCase().offline(request(DayOfWeek.WEDNESDAY))
        val second = ComposeManagerMessageUseCase().offline(request(DayOfWeek.WEDNESDAY, recent = listOf(first)))
        assertNotEquals(first, second)
        val (o1, rest1) = first.split("\n\n", limit = 2)
        val (o2, rest2) = second.split("\n\n", limit = 2)
        assertNotEquals("same opening", o1, o2)
        val closing1 = Regex("""_[^_]+_$""").find(rest1)!!.value
        val closing2 = Regex("""_[^_]+_$""").find(rest2)!!.value
        assertNotEquals("same closing", closing1, closing2)
        assertNotEquals("same thought", rest1.removeSuffix(closing1), rest2.removeSuffix(closing2))
    }

    @Test fun historyNeverBlocksGenerationWhenEveryPartWasUsed() {
        val history = mutableListOf<String>()
        repeat(CommunicationPlanner.MORNING_HISTORY_SIZE + 5) {
            val next = ComposeManagerMessageUseCase().offline(request(DayOfWeek.MONDAY, recent = history.take(CommunicationPlanner.MORNING_HISTORY_SIZE)))
            assertTrue(next.isNotBlank())
            history.add(0, next)
        }
    }

    @Test fun viewModelRegenerateFeedsHistoryBackSoTheNoteChanges() = runTest(dispatcher) {
        val stored = mutableListOf<String>()
        val vm = MorningNoteViewModel(today = { LocalDate.of(2026, 9, 16) }) // Wednesday
        vm.bind("m@x.com", history = { stored.toList() }, record = { stored.add(0, it) }, delivery = {})
        vm.generate(); advanceUntilIdle()
        val first = vm.state.value.text
        vm.regenerate(); advanceUntilIdle()
        assertTrue(first.isNotBlank())
        assertNotEquals(first, vm.state.value.text)
        assertEquals(2, stored.size)
    }

    // ── output contract ─────────────────────────────────────────────────────

    @Test fun outputIsTheGreetingOnlyWithViberMarkersAndNoFencesOrLabels() {
        weekdays.forEach { day ->
            repeat(4) { v ->
                val text = ComposeManagerMessageUseCase().offline(request(day, variation = v))
                assertFalse(text.contains("```"))
                assertFalse(text.contains("**"))
                assertFalse(text.startsWith("Generated", ignoreCase = true))
                assertFalse(text.contains("Hello team,"))
                assertTrue("no Viber marker in: $text", Regex("""\*[^*\n]+\*|_[^_\n]+_""").containsMatchIn(text))
                assertTrue("too long (${text.length}): $text", text.length <= 200)
            }
        }
    }

    @Test fun bannedStockPhrasesNeverAppear() {
        val banned = listOf(
            "stay focused", "keep the momentum", "finish strong", "make today count", "steady progress",
            "give 100%", "crush your goals", "have a productive day", "wishing everyone", "have a smooth day",
        )
        weekdays.forEach { day ->
            repeat(8) { v ->
                val text = ComposeManagerMessageUseCase().offline(request(day, variation = v)).lowercase()
                banned.forEach { assertFalse("'$it' in: $text", text.contains(it)) }
            }
        }
    }

    // ── routing ─────────────────────────────────────────────────────────────

    @Test fun routesThroughTheSharedComposerAndIsNotReportedAsServerText() = runTest(dispatcher) {
        val req = request(DayOfWeek.TUESDAY)
        val plan = CommunicationPlanner.planMorningGreeting(DayOfWeek.TUESDAY)!!
        val result = CommunicationRepository.compose("m@x.com", req)
        assertFalse(result.fromServer)
        assertEquals(CommunicationComposer.composeFromPlan(plan), result.text)
    }

    // ── share truthfulness ──────────────────────────────────────────────────

    @Test fun shareIsRecordedAsSharedExternallyAndNeverSent() = runTest(dispatcher) {
        val logged = mutableListOf<Map<String, Any>>()
        val vm = MorningNoteViewModel(today = { LocalDate.of(2026, 9, 17) })
        vm.bind("m@x.com", history = { emptyList() }, record = {}, delivery = { logged += it })
        vm.generate(); advanceUntilIdle()
        assertEquals("SHARED_EXTERNALLY", vm.record(MorningNoteAction.SHARE))
        assertEquals("COPIED", vm.record(MorningNoteAction.COPY))
        advanceUntilIdle()
        assertEquals(listOf("SHARED_EXTERNALLY", "COPIED"), logged.map { it["status"] })
        assertTrue(MorningNoteAction.entries.none { it.status == "SENT" })
    }
}
