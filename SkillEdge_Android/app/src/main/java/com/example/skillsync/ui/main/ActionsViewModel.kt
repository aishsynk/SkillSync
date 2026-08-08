package com.example.skillsync.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skillsync.data.api.RetrofitClient
import com.example.skillsync.data.ManagerRepository
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

    private val _actions = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val actions: StateFlow<List<Map<String, Any>>> = _actions

    /** True only on the very first load, when there is nothing to show yet. */
    private val _initialLoading = MutableStateFlow(false)
    val initialLoading: StateFlow<Boolean> = _initialLoading

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var loadedFor: String? = null

    fun load(managerEmail: String) {
        if (loadedFor == managerEmail && _actions.value.isNotEmpty()) return
        loadedFor = managerEmail
        viewModelScope.launch {
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

    private suspend fun fetch(managerEmail: String) {
        try {
            val result = repository.actions(managerEmail)
            val rows = result.data ?: throw IllegalStateException(result.error ?: "Could not load actions")
            // Only replace on success. A failed refresh leaves the existing
            // inbox on screen rather than emptying it.
            _actions.value = rows
            _error.value = null
        } catch (e: Exception) {
            if (_actions.value.isEmpty()) {
                _error.value = e.localizedMessage ?: "Could not load actions"
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
            if (it["id"] == actionId) it + mapOf("lifecycle_state" to state) else it
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
                _error.value = "Could not update: ${e.localizedMessage ?: "request failed"}"
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
                _error.value = "Could not save note: ${e.localizedMessage ?: "request failed"}"
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
                _error.value = "Could not raise action: ${e.localizedMessage ?: "request failed"}"
            }
        }
    }

    fun clearError() { _error.value = null }
}
