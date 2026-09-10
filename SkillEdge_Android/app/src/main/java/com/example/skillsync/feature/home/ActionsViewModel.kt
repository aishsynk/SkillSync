package com.example.skillsync.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skillsync.data.api.RetrofitClient
import com.example.skillsync.data.ManagerRepository
import com.example.skillsync.data.models.ActionRow
import com.example.skillsync.data.models.parseActions
import com.example.skillsync.ui.common.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The manager's action inbox.
 *
 * Every mutation is applied to the in-memory list first and only then sent to
 * the server, so tapping "Close" never blanks the row while a request is in
 * flight. If the call fails the optimistic change is rolled back and the
 * failure is surfaced, rather than leaving the UI asserting something the
 * server never accepted.
 *
 * A refresh never clears [actions] — the previous list stays on screen while
 * the new one is fetched, and is replaced only once real data arrives.
 */
class ActionsViewModel(
    private val repository: ManagerRepository = ManagerRepository(),
) : ViewModel() {

    private val _actions = MutableStateFlow<List<ActionRow>>(emptyList())
    val actions: StateFlow<List<ActionRow>> = _actions

    /** True only on the very first load, when there is nothing to show yet. */
    private val _initialLoading = MutableStateFlow(false)
    val initialLoading: StateFlow<Boolean> = _initialLoading

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var loadedFor: String? = null

    /** Comment: see [MainScreenViewModel] — same version guard around adopting
     *  a persisted revision so a background write never overrides newer data
     *  that a foreground refresh raced in first. */
    private var lastAdoptedAt = 0L

    fun load(managerEmail: String) {
        if (loadedFor == managerEmail && _actions.value.isNotEmpty()) return
        loadedFor = managerEmail
        viewModelScope.launch {
            val cached = com.example.skillsync.data.cache.LocalCache.loadMap("actions_$managerEmail")
            if (cached != null) {
                lastAdoptedAt = com.example.skillsync.data.cache.LocalCache.savedAt("actions_$managerEmail")
                _actions.value = parseActions(cached)
            }
            if (_actions.value.isEmpty()) _initialLoading.value = true
            fetch(managerEmail)
            _initialLoading.value = false
        }
    }

    fun refresh(managerEmail: String) {
        viewModelScope.launch {
            _refreshing.value = true
            fetch(managerEmail)
            _refreshing.value = false
        }
    }

    fun adoptBackgroundSync(managerEmail: String) {
        viewModelScope.launch {
            val key = "actions_$managerEmail"
            val savedAt = com.example.skillsync.data.cache.LocalCache.savedAt(key)
            if (savedAt <= lastAdoptedAt) return@launch
            val body = com.example.skillsync.data.cache.LocalCache.loadMap(key) ?: return@launch
            lastAdoptedAt = savedAt
            val rows = parseActions(body)
            if (_actions.value != rows) _actions.value = rows
        }
    }

    private suspend fun fetch(managerEmail: String) {
        try {
            val result = repository.actions(managerEmail)
            val rows = result.data ?: throw IllegalStateException(result.error ?: "Could not load actions")
            // Only replace on success. A failed refresh leaves the existing
            // inbox on screen rather than emptying it.
            lastAdoptedAt = System.currentTimeMillis()
            _actions.value = rows
            _error.value = null
        } catch (e: Exception) {
            if (_actions.value.isEmpty()) {
                _error.value = e.userMessage("load actions")
            }
        }
    }

    /** Optimistically move an action, rolling back if the server refuses. */
    fun setState(
        managerEmail: String,
        actionId: String,
        state: String,
        note: String = "",
        dueDate: String = "",
    ) {
        val before = _actions.value
        _actions.value = before.map {
            if (it.id == actionId) it.copy(lifecycleState = state) else it
        }
        viewModelScope.launch {
            try {
                RetrofitClient.instance.setActionState(
                    actionId,
                    buildMap {
                        put("state", state)
                        put("manager_email", managerEmail)
                        if (note.isNotBlank()) put("note", note)
                        if (dueDate.isNotBlank()) put("due_date", dueDate)
                    },
                )
                fetch(managerEmail)          // reconcile with the server's truth
            } catch (e: Exception) {
                _actions.value = before      // the change never landed
                _error.value = e.userMessage("update this action")
            }
        }
    }

    fun addNote(managerEmail: String, actionId: String, note: String) {
        if (note.isBlank()) return
        viewModelScope.launch {
            try {
                RetrofitClient.instance.addActionNote(
                    actionId, mapOf("note" to note, "manager_email" to managerEmail),
                )
                fetch(managerEmail)
            } catch (e: Exception) {
                _error.value = e.userMessage("save this note")
            }
        }
    }

    fun raise(
        managerEmail: String,
        title: String,
        detail: String = "",
        category: String = "Other",
        priority: String = "medium",
        trainerName: String = "",
        trainerEmail: String = "",
        dueDate: String = "",
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            try {
                RetrofitClient.instance.raiseAction(
                    buildMap {
                        put("title", title)
                        put("manager_email", managerEmail)
                        put("category", category)
                        put("priority", priority)
                        if (detail.isNotBlank()) put("detail", detail)
                        if (trainerName.isNotBlank()) put("trainer_name", trainerName)
                        if (trainerEmail.isNotBlank()) put("trainer_email", trainerEmail)
                        if (dueDate.isNotBlank()) put("due_date", dueDate)
                    },
                )
                fetch(managerEmail)
            } catch (e: Exception) {
                _error.value = e.userMessage("raise this action")
            }
        }
    }

    fun clearError() { _error.value = null }
}
