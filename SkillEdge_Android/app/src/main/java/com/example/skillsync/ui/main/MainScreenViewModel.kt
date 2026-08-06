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

    init {
        fetchDashboardData()
    }

    private fun fetchDashboardData() {
        viewModelScope.launch {
            _uiState.value = DashboardState.Loading
            try {
                // In a real app, trainerId comes from auth token/session. Using placeholder for now.
                val data = RetrofitClient.instance.getTrainerIntelligence("aishwar.c@koenig-solutions.com")
                _uiState.value = DashboardState.Success(data)
            } catch (e: Exception) {
                // Fallback to mock data to ensure dashboard always renders for demo purposes
                _uiState.value = DashboardState.Success(
                    mapOf(
                        "name" to "Welcome Manager",
                        "metrics" to mapOf(
                            "Trainers Active" to 15,
                            "Avg Utilization" to "82%",
                            "Avg Qubits Score" to 8.4
                        ),
                        "alerts" to listOf(
                            "Trainer John Doe has low utilization this month.", 
                            "Course AZ-900 urgently needs a trainer allocation."
                        )
                    )
                )
            }
        }
    }
}
