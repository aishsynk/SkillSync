package com.example.skillsync.ui.reportee

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skillsync.data.api.RetrofitClient
import com.example.skillsync.ui.components.rows
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Backs the reportee self-service shell. Two independent reads — the matched
 * demand board and the in-app notification feed — plus a loading/error flag on
 * each so a slow RMS call on one tab never blanks the other.
 */
class ReporteeViewModel : ViewModel() {

    private val _demand = MutableStateFlow<List<Map<*, *>>>(emptyList())
    val demand: StateFlow<List<Map<*, *>>> = _demand

    private val _demandLoading = MutableStateFlow(true)
    val demandLoading: StateFlow<Boolean> = _demandLoading

    private val _demandError = MutableStateFlow<String?>(null)
    val demandError: StateFlow<String?> = _demandError

    private val _updates = MutableStateFlow<List<Map<*, *>>>(emptyList())
    val updates: StateFlow<List<Map<*, *>>> = _updates

    fun load() {
        loadDemand()
        loadUpdates()
    }

    fun loadDemand() {
        viewModelScope.launch {
            _demandLoading.value = true
            _demandError.value = null
            try {
                _demand.value = RetrofitClient.instance.reporteeDemand().rows("matched_demand")
            } catch (e: Exception) {
                _demandError.value = e.localizedMessage ?: "Could not load matched demand"
            } finally {
                _demandLoading.value = false
            }
        }
    }

    fun loadUpdates() {
        viewModelScope.launch {
            try {
                _updates.value = RetrofitClient.instance.notifications().rows("notifications")
            } catch (_: Exception) {
                // Secondary feed — leave the last good list in place.
            }
        }
    }
}
