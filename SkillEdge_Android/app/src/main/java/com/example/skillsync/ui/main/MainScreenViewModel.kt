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

    fun loadData(email: String) {
        viewModelScope.launch {
            _uiState.value = DashboardState.Loading
            try {
                val data = RetrofitClient.instance.getTrainerIntelligence(email)
                _uiState.value = DashboardState.Success(data)
            } catch (e: Exception) {
                _uiState.value = DashboardState.Error(
                    e.localizedMessage ?: "Failed to load dashboard data"
                )
            }
        }
    }
}
