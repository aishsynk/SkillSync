package com.example.skillsync.feature.report.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skillsync.core.data.SkillRequestsRepository
import com.example.skillsync.core.ui.rows
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Pending reportee skill-level elevation requests. A manager approves (which
 * performs the real, verified RMS write on the backend) or denies each one.
 */
class SkillRequestsViewModel(
    private val repository: SkillRequestsRepository = SkillRequestsRepository(),
) : ViewModel() {

    private val _requests = MutableStateFlow<List<Map<*, *>>>(emptyList())
    val requests: StateFlow<List<Map<*, *>>> = _requests

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                _requests.value = repository.pending().rows("requests")
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Could not load skill requests"
            } finally {
                _loading.value = false
            }
        }
    }

    fun resolve(id: String, approve: Boolean, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val res = repository.resolve(id, approve)
                val ok = res["success"] == true
                onResult(
                    ok,
                    res["error"]?.toString()
                        ?: if (ok) (if (approve) "Approved and written to RMS" else "Declined")
                        else "Could not resolve",
                )
                load()
            } catch (e: Exception) {
                onResult(false, e.localizedMessage ?: "Network error")
            }
        }
    }
}
