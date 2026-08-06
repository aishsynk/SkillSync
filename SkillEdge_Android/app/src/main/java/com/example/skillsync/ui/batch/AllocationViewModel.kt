package com.example.skillsync.ui.batch

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skillsync.data.api.MarkSkillRequest
import com.example.skillsync.data.api.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class AllocationState {
    object Loading : AllocationState()
    data class Success(val data: Map<String, Any>) : AllocationState()
    data class Error(val message: String) : AllocationState()
}

/** Outcome of a write to RMS, surfaced so the UI can confirm or explain. */
sealed class MarkState {
    object Idle : MarkState()
    object Working : MarkState()
    data class Done(val who: String, val level: Int) : MarkState()
    data class Failed(val message: String) : MarkState()
}

class AllocationViewModel : ViewModel() {

    private val _state = MutableStateFlow<AllocationState>(AllocationState.Loading)
    val state: StateFlow<AllocationState> = _state

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    private val _mark = MutableStateFlow<MarkState>(MarkState.Idle)
    val mark: StateFlow<MarkState> = _mark

    /** Assignment ids seen on a previous run, so genuinely new batches can be flagged. */
    private val _newIds = MutableStateFlow<Set<String>>(emptySet())
    val newIds: StateFlow<Set<String>> = _newIds

    private var loadedFor: String? = null

    fun load(email: String, context: Context) {
        if (loadedFor == email && _state.value is AllocationState.Success) return
        loadedFor = email
        viewModelScope.launch {
            _state.value = AllocationState.Loading
            fetch(email, context)
        }
    }

    fun refresh(email: String, context: Context) {
        viewModelScope.launch {
            _refreshing.value = true
            fetch(email, context)
            _refreshing.value = false
        }
    }

    private suspend fun fetch(email: String, context: Context) {
        try {
            val data = RetrofitClient.instance.getAllocationDesk(email)
            _newIds.value = SeenBatches.diffAndRemember(context, email, data)
            _state.value = AllocationState.Success(data)
        } catch (e: Exception) {
            if (_state.value !is AllocationState.Success) {
                _state.value = AllocationState.Error(
                    e.localizedMessage ?: "Could not load unallocated batches"
                )
            }
        }
    }

    /** Writes a skill to production RMS. */
    fun markSkill(courseId: String, trainerEmail: String, level: Int, fromDate: String, who: String) {
        viewModelScope.launch {
            _mark.value = MarkState.Working
            _mark.value = try {
                val r = RetrofitClient.instance.markSkill(
                    MarkSkillRequest(
                        course_id = courseId,
                        trainer_email = trainerEmail,
                        skill_level = level,
                        from_date = fromDate,
                    )
                )
                if (r.success == true) MarkState.Done(who, level)
                else MarkState.Failed(r.error ?: "RMS rejected the request")
            } catch (e: Exception) {
                MarkState.Failed(e.localizedMessage ?: "Could not reach the server")
            }
        }
    }

    fun clearMark() {
        _mark.value = MarkState.Idle
    }
}
