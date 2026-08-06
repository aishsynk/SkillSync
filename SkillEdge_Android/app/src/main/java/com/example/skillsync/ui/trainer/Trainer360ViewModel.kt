package com.example.skillsync.ui.trainer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skillsync.data.api.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class Trainer360State {
    object Loading : Trainer360State()
    data class Success(val data: Map<String, Any>) : Trainer360State()
    data class Error(val message: String) : Trainer360State()
}

class Trainer360ViewModel : ViewModel() {
    private val _state = MutableStateFlow<Trainer360State>(Trainer360State.Loading)
    val state: StateFlow<Trainer360State> = _state

    private var loadedFor: String? = null

    fun load(trainerEmail: String) {
        if (loadedFor == trainerEmail && _state.value is Trainer360State.Success) return
        loadedFor = trainerEmail
        viewModelScope.launch {
            _state.value = Trainer360State.Loading
            try {
                _state.value = Trainer360State.Success(
                    RetrofitClient.instance.getTrainer360(trainerEmail)
                )
            } catch (e: Exception) {
                _state.value = Trainer360State.Error(
                    e.localizedMessage ?: "Could not load this trainer's profile"
                )
            }
        }
    }
}
