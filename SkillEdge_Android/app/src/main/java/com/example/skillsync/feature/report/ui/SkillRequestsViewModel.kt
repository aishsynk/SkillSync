package com.example.skillsync.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skillsync.data.api.RetrofitClient
import com.example.skillsync.data.api.SkillRequestResolve
import com.example.skillsync.ui.components.rows
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Pending reportee skill-level elevation requests. A manager approves (which
 * performs the real, verified RMS write on the backend) or denies each one.
 */
class SkillRequestsViewModel : ViewModel() {

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
                _requests.value = RetrofitClient.instance.skillRequests().rows("requests")
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
                val res = RetrofitClient.instance.resolveSkillRequest(
                    id, SkillRequestResolve(if (approve) "approve" else "deny"),
                )
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
