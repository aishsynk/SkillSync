package com.example.skillsync.feature.communication

import com.example.skillsync.feature.communication.domain.CommunicationAudience
import com.example.skillsync.feature.communication.domain.CommunicationAudienceType
import com.example.skillsync.feature.communication.domain.CommunicationEvidence
import com.example.skillsync.feature.communication.domain.CommunicationRepository
import com.example.skillsync.feature.communication.domain.CommunicationRequest
import com.example.skillsync.feature.communication.domain.ComposeManagerMessageUseCase
import com.example.skillsync.feature.communication.domain.ComposeResult
import com.example.skillsync.feature.communication.domain.MorningNoteText
import com.example.skillsync.feature.communication.engine.CommunicationPlanner
import com.example.skillsync.feature.communication.engine.CommunicationPurpose
import com.example.skillsync.feature.communication.ui.MorningNoteAction
import com.example.skillsync.feature.communication.ui.MorningNoteStore
import com.example.skillsync.feature.communication.ui.MorningNoteViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class MorningGreetingTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val weekdays = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    private val wednesday = LocalDate.of(2026, 9, 16)

    private fun request(day: DayOfWeek, recent: List<String> = emptyList(), variation: Int = 0) = CommunicationRequest(
        audience = CommunicationAudience(CommunicationAudienceType.TEAM),
        purpose = CommunicationPurpose.MORNING_TEAM_GREETING,
        cadence = "morning",
        evidence = CommunicationEvidence(localWeekday = day, recentGreetings = recent, variation = variation),
    )

    private class FakeStore : MorningNoteStore {
        var saved: Pair<String, String>? = null
        val history = mutableListOf<String>()
        override fun draft() = saved
        override fun saveDraft(date: String, text: String) { saved = date to text }
        override fun recent() = history.toList()
        override fun remember(text: String) { history.add(0, text) }
    }

    /** A composer that counts calls and returns a distinct greeting each time. */
    private class CountingCompose {
        var calls = 0
        val compose: suspend (String, CommunicationRequest) -> ComposeResult = { _, req ->
            calls++
            ComposeResult("Hi all.\n\n*Note $calls* for ${req.evidence.localWeekday}. _Take care._", fromServer = true)
        }
    }

    private fun TestScope.vm(clock: () -> LocalDate, composer: CountingCompose, store: FakeStore, logged: MutableList<Map<String, Any>> = mutableListOf()) =
        MorningNoteViewModel(compose = composer.compose, today = clock).also {
            it.bind("m@x.com", store, delivery = { m -> logged += m })
        }

    // ── daily stability ─────────────────────────────────────────────────────

    @Test fun sameDayReopenShowsTheSameDraftWithoutRecomposing() = runTest(dispatcher) {
        val store = FakeStore(); val composer = CountingCompose()
        val first = vm({ wednesday }, composer, store)
        first.refreshForToday(); advanceUntilIdle()
        val text = first.state.value.text
        assertEquals(1, composer.calls)
        assertEquals(wednesday.toString() to text, store.saved)

        first.refreshForToday(); advanceUntilIdle()                 // recomposition / resume
        val reopened = vm({ wednesday }, composer, store)            // Today reopened later the same day
        reopened.refreshForToday(); advanceUntilIdle()
        assertEquals(1, composer.calls)
        assertEquals(text, reopened.state.value.text)
    }

    @Test fun nextLocalWeekdayCreatesANewDraftAutomatically() = runTest(dispatcher) {
        val store = FakeStore(); val composer = CountingCompose()
        var date = wednesday
        val vm = vm({ date }, composer, store)
        vm.refreshForToday(); advanceUntilIdle()
        val wednesdayText = vm.state.value.text
        date = wednesday.plusDays(1)
        vm.refreshForToday(); advanceUntilIdle()
        assertEquals(2, composer.calls)
        assertNotEquals(wednesdayText, vm.state.value.text)
        assertEquals(DayOfWeek.THURSDAY, vm.state.value.weekday)
        assertEquals(date.toString(), store.saved!!.first)
        assertEquals(2, store.history.size)
    }

    @Test fun regenerateReplacesTheDaysDraft() = runTest(dispatcher) {
        val store = FakeStore(); val composer = CountingCompose()
        val vm = vm({ wednesday }, composer, store)
        vm.refreshForToday(); advanceUntilIdle()
        val before = vm.state.value.text
        vm.regenerate(); advanceUntilIdle()
        assertNotEquals(before, vm.state.value.text)
        assertEquals(wednesday.toString() to vm.state.value.text, store.saved)
    }

    @Test fun copyAndShareDoNotMutateTheDraft() = runTest(dispatcher) {
        val store = FakeStore(); val composer = CountingCompose(); val logged = mutableListOf<Map<String, Any>>()
        val vm = vm({ wednesday }, composer, store, logged)
        vm.refreshForToday(); advanceUntilIdle()
        val draft = store.saved
        val text = vm.state.value.text
        assertEquals("COPIED", vm.record(MorningNoteAction.COPY))
        assertEquals("SHARED_EXTERNALLY", vm.record(MorningNoteAction.SHARE))
        advanceUntilIdle()
        assertEquals(1, composer.calls)
        assertEquals(draft, store.saved)
        assertEquals(text, vm.state.value.text)
        assertEquals(listOf("COPIED", "SHARED_EXTERNALLY"), logged.map { it["status"] })
        assertTrue(MorningNoteAction.entries.none { it.status == "SENT" })
    }

    @Test fun weekendComposesNothingAndIsHidden() = runTest(dispatcher) {
        listOf(LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 20)).forEach { day ->
            val store = FakeStore(); val composer = CountingCompose()
            val vm = vm({ day }, composer, store)
            vm.refreshForToday(); vm.regenerate(); advanceUntilIdle()
            assertTrue(vm.state.value.isWeekend)
            assertEquals(0, composer.calls)
            assertNull(store.saved)
        }
        listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY).forEach { assertNull(CommunicationPlanner.planMorningGreeting(it)) }
    }

    // ── online / offline ────────────────────────────────────────────────────

    @Test fun onlineUsesTheServerGreetingAndSendsTheWeekdayAndHistory() = runTest(dispatcher) {
        var sent: Map<String, Any>? = null
        val result = CommunicationRepository.composeMorningGreeting("m@x.com", request(DayOfWeek.WEDNESDAY, recent = listOf("old"))) { body ->
            sent = body
            mapOf("message" to "```\nMidweek already, everyone.\n\n**Halfway through.** _Enjoy the day._\n```", "requires_communication" to true)
        }
        assertTrue(result.fromServer)
        assertEquals("Midweek already, everyone.\n\n*Halfway through.* _Enjoy the day._", result.text)
        assertEquals("MORNING_TEAM_GREETING", sent!!["purpose"])
        assertEquals("WEDNESDAY", sent!!["localWeekday"])
        assertEquals(listOf("old"), sent!!["recentGreetings"])
    }

    @Test fun offlineFallsBackToTheLocalComposer() = runTest(dispatcher) {
        val req = request(DayOfWeek.TUESDAY)
        val failing = CommunicationRepository.composeMorningGreeting("m@x.com", req) { throw IOException("offline") }
        val blank = CommunicationRepository.composeMorningGreeting("m@x.com", req) { mapOf("message" to "  ") }
        listOf(failing, blank).forEach {
            assertFalse(it.fromServer)
            assertEquals(ComposeManagerMessageUseCase().offline(req), it.text)
            assertTrue(it.text.isNotBlank())
        }
    }

    @Test fun serverWeekendResponseIsRespected() = runTest(dispatcher) {
        val r = CommunicationRepository.composeMorningGreeting("m@x.com", request(DayOfWeek.FRIDAY)) {
            mapOf("message" to "", "requires_communication" to false)
        }
        assertEquals("", r.text)
    }

    // ── copy contract ───────────────────────────────────────────────────────

    @Test fun copyPayloadIsTheGreetingOnly() {
        val raw = "```\nMORNING NOTE\nWednesday\nGenerated message: Midweek already, everyone.\n\n**Halfway through** — _a good day_ to help. ~meetings~\n```"
        val clean = MorningNoteText.clean(raw)
        assertEquals("Midweek already, everyone.\n\n*Halfway through* — _a good day_ to help. ~meetings~", clean)
        listOf("```", "MORNING NOTE", "Generated message", "**").forEach { assertFalse(clean.contains(it)) }
    }

    @Test fun viewModelPayloadMatchesTheCleanDraft() = runTest(dispatcher) {
        val store = FakeStore()
        val dirty: suspend (String, CommunicationRequest) -> ComposeResult = { _, _ -> ComposeResult("Greeting:\nHi all.\n\n**Bold** _Take care._", true) }
        val vm = MorningNoteViewModel(compose = dirty, today = { wednesday }).also { it.bind("m@x.com", store) }
        vm.refreshForToday(); advanceUntilIdle()
        assertEquals("Hi all.\n\n*Bold* _Take care._", vm.payload())
        assertEquals(vm.payload(), store.saved!!.second)
    }

    // ── local fallback composer quality ─────────────────────────────────────

    @Test fun localFallbackCoversEveryWeekdayWithoutBannedPhrasesOrFences() {
        val banned = listOf("stay focused", "keep the momentum", "finish strong", "make today count", "steady progress",
            "give 100%", "crush your goals", "have a productive day", "wishing everyone", "have a smooth day")
        weekdays.forEach { day ->
            repeat(6) { v ->
                val text = ComposeManagerMessageUseCase().offline(request(day, variation = v))
                assertTrue("$day empty", text.isNotBlank())
                assertFalse(text.contains("```"))
                assertTrue(text.length <= 200)
                banned.forEach { assertFalse("'$it' in $text", text.lowercase().contains(it)) }
            }
        }
    }

    @Test fun localFallbackDoesNotRepeatRecentOpeningThoughtOrClosing() {
        val first = ComposeManagerMessageUseCase().offline(request(DayOfWeek.WEDNESDAY))
        val second = ComposeManagerMessageUseCase().offline(request(DayOfWeek.WEDNESDAY, recent = listOf(first)))
        val (o1, rest1) = first.split("\n\n", limit = 2)
        val (o2, rest2) = second.split("\n\n", limit = 2)
        assertNotEquals(o1, o2)
        assertNotEquals(Regex("""_[^_]+_$""").find(rest1)!!.value, Regex("""_[^_]+_$""").find(rest2)!!.value)
        assertNotEquals(rest1.substringBeforeLast(" _"), rest2.substringBeforeLast(" _"))
    }
}
