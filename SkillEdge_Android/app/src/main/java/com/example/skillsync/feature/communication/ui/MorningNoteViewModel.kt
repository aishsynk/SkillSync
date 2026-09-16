package com.example.skillsync.feature.communication.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skillsync.core.data.ManagerRepository
import com.example.skillsync.core.storage.DigestStateStore
import com.example.skillsync.feature.communication.domain.CommunicationAudience
import com.example.skillsync.feature.communication.domain.CommunicationAudienceType
import com.example.skillsync.feature.communication.domain.CommunicationEvidence
import com.example.skillsync.feature.communication.domain.CommunicationRequest
import com.example.skillsync.feature.communication.domain.ComposeManagerMessageUseCase
import com.example.skillsync.feature.communication.domain.ComposeResult
import com.example.skillsync.feature.communication.domain.MorningNoteText
import com.example.skillsync.feature.communication.engine.CommunicationPlanner
import com.example.skillsync.feature.communication.engine.CommunicationPurpose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * What the manager did with a greeting. Opening Android's share sheet only
 * hands text to another app, so it is recorded as SHARED_EXTERNALLY — there
 * is deliberately no SENT value here.
 */
enum class MorningNoteAction(val status: String) {
    COPY("COPIED"),
    SHARE("SHARED_EXTERNALLY"),
}

data class MorningNoteState(
    val weekday: DayOfWeek,
    val text: String = "",
    val loading: Boolean = false,
    val fromServer: Boolean = false,
) {
    val isWeekend: Boolean get() = weekday == DayOfWeek.SATURDAY || weekday == DayOfWeek.SUNDAY
}

/** Where the day's draft and the anti-repeat history live. */
interface MorningNoteStore {
    fun draft(): Pair<String, String>?
    fun saveDraft(date: String, text: String)
    fun recent(): List<String>
    fun remember(text: String)
}

private class PrefsMorningNoteStore(private val email: String) : MorningNoteStore {
    override fun draft() = DigestStateStore.morningDraft(email)
    override fun saveDraft(date: String, text: String) = DigestStateStore.setMorningDraft(email, date, text)
    override fun recent() = DigestStateStore.recentGreetings(email)
    override fun remember(text: String) = DigestStateStore.recordGreeting(email, text, CommunicationPlanner.MORNING_HISTORY_SIZE)
}

/**
 * Today's Morning Note: one greeting per local weekday.
 *
 * - First open on a weekday composes a greeting (server first, local fallback,
 *   via [ComposeManagerMessageUseCase]) and persists it as that date's draft.
 * - Reopening the same date shows the same draft; nothing is recomposed.
 * - A new local weekday composes a new draft automatically.
 * - Regenerate is the only action that replaces the day's draft.
 * - Copy/Share read the draft; they never change it.
 * - Saturday/Sunday compose nothing.
 */
class MorningNoteViewModel(
    private val compose: suspend (String, CommunicationRequest) -> ComposeResult =
        { email, request -> ComposeManagerMessageUseCase()(email, request) },
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val _state = MutableStateFlow(MorningNoteState(today().dayOfWeek))
    val state: StateFlow<MorningNoteState> = _state.asStateFlow()

    private var managerEmail = ""
    private var store: MorningNoteStore? = null
    private var variation = 0
    private var recordDelivery: suspend (Map<String, Any>) -> Unit = { ManagerRepository().saveCommunication(it) }

    fun start(context: Context, email: String) {
        DigestStateStore.init(context.applicationContext)
        bind(email, PrefsMorningNoteStore(email))
        refreshForToday()
    }

    /** Test seam: a store and delivery logger without Android storage or network. */
    internal fun bind(email: String, store: MorningNoteStore, delivery: (suspend (Map<String, Any>) -> Unit)? = null) {
        managerEmail = email
        this.store = store
        delivery?.let { recordDelivery = it }
    }

    /** Shows today's persisted draft, or composes one if today has none. Safe to call repeatedly. */
    fun refreshForToday() {
        val date = today()
        if (isWeekend(date.dayOfWeek)) {
            _state.value = MorningNoteState(date.dayOfWeek)
            return
        }
        val saved = store?.draft()
        if (saved != null && saved.first == date.toString()) {
            if (_state.value.text != saved.second || _state.value.weekday != date.dayOfWeek) {
                _state.value = MorningNoteState(date.dayOfWeek, text = saved.second)
            }
            return
        }
        if (_state.value.loading) return
        generate(date)
    }

    fun regenerate() {
        val date = today()
        if (isWeekend(date.dayOfWeek) || _state.value.loading) return
        variation++
        generate(date)
    }

    private fun generate(date: LocalDate) {
        val s = store ?: return
        _state.value = _state.value.copy(weekday = date.dayOfWeek, loading = true)
        viewModelScope.launch {
            val request = CommunicationRequest(
                audience = CommunicationAudience(CommunicationAudienceType.TEAM),
                purpose = CommunicationPurpose.MORNING_TEAM_GREETING,
                cadence = "morning",
                evidence = CommunicationEvidence(
                    localWeekday = date.dayOfWeek,
                    recentGreetings = s.recent(),
                    variation = variation,
                ),
            )
            val result = compose(managerEmail, request)
            val text = MorningNoteText.clean(result.text)
            if (text.isNotBlank()) {
                s.saveDraft(date.toString(), text)
                s.remember(text)
            }
            _state.value = MorningNoteState(date.dayOfWeek, text = text, fromServer = result.fromServer)
        }
    }

    /** Exactly what Copy/Share hand over: the greeting alone. */
    fun payload(): String = MorningNoteText.clean(_state.value.text)

    /** Logs a copy/share to communication history. Never claims the message was sent. */
    fun record(action: MorningNoteAction): String {
        val text = payload()
        if (text.isNotBlank()) {
            viewModelScope.launch {
                runCatching {
                    recordDelivery(
                        mapOf(
                            "manager" to managerEmail,
                            "recipient" to mapOf("name" to "", "type" to "TEAM", "relationship" to "team"),
                            "channel" to "MS_TEAMS_OR_VIBER",
                            "purpose" to CommunicationPurpose.MORNING_TEAM_GREETING.id,
                            "message" to text,
                            "status" to action.status,
                        ),
                    )
                }
            }
        }
        return action.status
    }

    private fun isWeekend(d: DayOfWeek) = d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY
}
