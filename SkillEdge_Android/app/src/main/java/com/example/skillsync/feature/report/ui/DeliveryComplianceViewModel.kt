package com.example.skillsync.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skillsync.data.ManagerRepository
import com.example.skillsync.data.DataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class DeliveryComplianceState {
    object Loading : DeliveryComplianceState()
    data class Success(val data: Map<String, Any>, val source: DataSource = DataSource.LIVE) : DeliveryComplianceState()
    data class Error(val message: String) : DeliveryComplianceState()
}

class DeliveryComplianceViewModel(
    private val repo: ManagerRepository = ManagerRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow<DeliveryComplianceState>(DeliveryComplianceState.Loading)
    val state: StateFlow<DeliveryComplianceState> = _state

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    private var currentManager: String = ""

    fun init(manager: String) {
        if (currentManager == manager && _state.value is DeliveryComplianceState.Success) return
        currentManager = manager
        load(manager, fresh = false)
    }

    fun refresh() {
        if (currentManager.isNotBlank()) {
            load(currentManager, fresh = true)
        }
    }

    private fun load(manager: String, fresh: Boolean) {
        viewModelScope.launch {
            if (fresh) _refreshing.value = true else _state.value = DeliveryComplianceState.Loading
            try {
                val res = repo.deliveryCompliance(manager, fresh)
                val d = res.data
                if (d != null) {
                    _state.value = DeliveryComplianceState.Success(d, res.source)
                } else {
                    _state.value = DeliveryComplianceState.Error(res.error ?: "Could not load delivery compliance")
                }
            } catch (e: Exception) {
                _state.value = DeliveryComplianceState.Error(e.localizedMessage ?: "Network error")
            } finally {
                _refreshing.value = false
            }
        }
    }
}
