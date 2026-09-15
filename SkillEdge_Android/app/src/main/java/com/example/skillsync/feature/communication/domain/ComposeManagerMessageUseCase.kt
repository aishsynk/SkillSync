package com.example.skillsync.feature.communication.domain

/** Whether the returned text came from the authoritative backend composer or the local mirror. */
data class ComposeResult(val text: String, val fromServer: Boolean)

/**
 * The single business operation a report ViewModel expresses: "compose a
 * manager message for this request." ViewModels call this, never
 * [CommunicationRepository] or any engine class, directly — see
 * `feature/report/ui/WeeklyReportViewModel`/`HrMonthlyReportViewModel`.
 *
 * Narrowly scoped to this one operation on purpose (not a generic
 * "CommunicationUseCase" covering every communication capability).
 */
class ComposeManagerMessageUseCase(
    private val repository: CommunicationRepository = CommunicationRepository,
) {
    suspend operator fun invoke(managerEmail: String, request: CommunicationRequest): ComposeResult =
        repository.compose(managerEmail, request)

    /** Synchronous, no-network variant for a "copy now" action. */
    fun offline(request: CommunicationRequest): String = repository.composeOffline(request)
}
