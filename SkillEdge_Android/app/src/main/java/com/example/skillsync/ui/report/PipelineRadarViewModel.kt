package com.example.skillsync.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skillsync.data.ManagerRepository
import com.example.skillsync.data.DataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class PipelineRadarState {
    object Loading : PipelineRadarState()
    data class Success(val data: Map<String, Any>, val source: DataSource = DataSource.LIVE) : PipelineRadarState()
    data class Error(val message: String) : PipelineRadarState()
}

class PipelineRadarViewModel(
    private val repo: ManagerRepository = ManagerRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow<PipelineRadarState>(PipelineRadarState.Loading)
    val state: StateFlow<PipelineRadarState> = _state

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    private var currentManager: String = ""

    fun init(manager: String) {
        if (currentManager == manager && _state.value is PipelineRadarState.Success) return
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
            if (fresh) _refreshing.value = true else _state.value = PipelineRadarState.Loading
            try {
                val res = repo.preDemandPipeline(manager, fresh)
                val d = res.data
                if (d != null) {
                    _state.value = PipelineRadarState.Success(d, res.source)
                } else {
                    _state.value = PipelineRadarState.Error(res.error ?: "Could not load sales SC pipeline")
                }
            } catch (e: Exception) {
                _state.value = PipelineRadarState.Error(e.localizedMessage ?: "Network error")
            } finally {
                _refreshing.value = false
            }
        }
    }
}
