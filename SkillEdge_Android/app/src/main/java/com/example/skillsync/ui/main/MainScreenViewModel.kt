package com.example.skillsync.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skillsync.data.api.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class DashboardState {
    object Loading : DashboardState()
    data class Success(val intelligenceData: Map<String, Any>) : DashboardState()
    data class Error(val message: String) : DashboardState()
}

class MainScreenViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<DashboardState>(DashboardState.Loading)
    val uiState: StateFlow<DashboardState> = _uiState

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    private var loadedFor: String? = null

    /** First load for [email]; a no-op once that email's data is already on screen. */
    fun loadData(email: String) {
        if (loadedFor == email && _uiState.value is DashboardState.Success) return
        loadedFor = email
        viewModelScope.launch {
            _uiState.value = DashboardState.Loading
            fetch(email)
        }
    }

    /** Pull-to-refresh: keeps the current data visible while re-fetching. */
    fun refresh(email: String) {
        viewModelScope.launch {
            _refreshing.value = true
            fetch(email)
            _refreshing.value = false
        }
    }

    private suspend fun fetch(email: String) {
        try {
            _uiState.value = DashboardState.Success(
                RetrofitClient.instance.getTrainerIntelligence(email)
            )
        } catch (e: Exception) {
            // A failed refresh must not wipe out data the manager is already reading.
            if (_uiState.value !is DashboardState.Success) {
                _uiState.value = DashboardState.Error(
                    e.localizedMessage ?: "Failed to load dashboard data"
                )
            }
        }
    }
}
