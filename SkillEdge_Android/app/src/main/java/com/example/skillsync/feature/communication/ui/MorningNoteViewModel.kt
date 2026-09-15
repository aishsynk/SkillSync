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
) {
    val isWeekend: Boolean get() = weekday == DayOfWeek.SATURDAY || weekday == DayOfWeek.SUNDAY
}

/**
 * Today's Morning Note. Builds a MORNING_TEAM_GREETING [CommunicationRequest]
 * and runs it through [ComposeManagerMessageUseCase] — the same boundary the
 * weekly and monthly composers use — so there is no second generator.
 */
class MorningNoteViewModel(
    private val composeMessage: ComposeManagerMessageUseCase = ComposeManagerMessageUseCase(),
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val _state = MutableStateFlow(MorningNoteState(today().dayOfWeek))
    val state: StateFlow<MorningNoteState> = _state.asStateFlow()

    private var managerEmail = ""
    private var variation = 0
    private var loadHistory: () -> List<String> = { emptyList() }
    private var saveHistory: (String) -> Unit = {}
    private var recordDelivery: suspend (Map<String, Any>) -> Unit = { ManagerRepository().saveCommunication(it) }

    fun start(context: Context, email: String) {
        if (managerEmail == email && _state.value.text.isNotBlank()) return
        DigestStateStore.init(context.applicationContext)
        bind(
            email = email,
            history = { DigestStateStore.recentGreetings(email) },
            record = { DigestStateStore.recordGreeting(email, it, CommunicationPlanner.MORNING_HISTORY_SIZE) },
        )
        generate()
    }

    /** Test seam: supplies history and delivery logging without Android storage or network. */
    internal fun bind(
        email: String,
        history: () -> List<String>,
        record: (String) -> Unit,
        delivery: (suspend (Map<String, Any>) -> Unit)? = null,
    ) {
        managerEmail = email
        loadHistory = history
        saveHistory = record
        delivery?.let { recordDelivery = it }
    }

    fun regenerate() {
        variation++
        generate()
    }

    internal fun generate() {
        val weekday = today().dayOfWeek
        _state.value = MorningNoteState(weekday, loading = true)
        viewModelScope.launch {
            val request = CommunicationRequest(
                audience = CommunicationAudience(CommunicationAudienceType.TEAM),
                purpose = CommunicationPurpose.MORNING_TEAM_GREETING,
                cadence = "morning",
                evidence = CommunicationEvidence(
                    localWeekday = weekday,
                    recentGreetings = loadHistory(),
                    variation = variation,
                ),
            )
            val text = composeMessage(managerEmail, request).text
            if (text.isNotBlank()) saveHistory(text)
            _state.value = MorningNoteState(weekday, text = text)
        }
    }

    /** Logs a copy/share to communication history. Never claims the message was sent. */
    fun record(action: MorningNoteAction): String {
        val text = _state.value.text
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
}
