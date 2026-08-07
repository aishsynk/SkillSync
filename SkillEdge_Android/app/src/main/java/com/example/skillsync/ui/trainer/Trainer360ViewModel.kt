package com.example.skillsync.ui.trainer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skillsync.data.api.RetrofitClient
import com.example.skillsync.data.cache.LocalCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class Trainer360State {
    object Loading : Trainer360State()
    /** See [com.example.skillsync.ui.main.DashboardState.Success] — same offline contract. */
    data class Success(
        val data: Map<String, Any>,
        val fromCache: Boolean = false,
        val cachedAt: Long = 0L,
    ) : Trainer360State()
    data class Error(val message: String) : Trainer360State()
}

class Trainer360ViewModel : ViewModel() {
    private val _state = MutableStateFlow<Trainer360State>(Trainer360State.Loading)
    val state: StateFlow<Trainer360State> = _state

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    private var loadedFor: String? = null

    fun load(trainerEmail: String, managerEmail: String = "") {
        if (loadedFor == trainerEmail && _state.value is Trainer360State.Success) return
        loadedFor = trainerEmail
        viewModelScope.launch {
            _state.value = Trainer360State.Loading
            fetch(trainerEmail, managerEmail, fresh = false)
        }
    }

    /** Pull-to-refresh and screen-resume; keeps the profile on screen while re-reading. */
    fun refresh(trainerEmail: String, managerEmail: String = "") {
        viewModelScope.launch {
            _refreshing.value = true
            fetch(trainerEmail, managerEmail, fresh = true)
            _refreshing.value = false
        }
    }

    private fun cacheKey(trainerEmail: String) = "trainer360_$trainerEmail"

    private suspend fun fetch(trainerEmail: String, managerEmail: String, fresh: Boolean) {
        try {
            val data = RetrofitClient.instance.getTrainer360(
                email = trainerEmail,
                manager = managerEmail.takeIf { it.isNotBlank() },
                refresh = if (fresh) 1 else null,
            )
            _state.value = Trainer360State.Success(data)
            LocalCache.saveMap(cacheKey(trainerEmail), data)
        } catch (e: Exception) {
            // A failed refresh must not blank a profile the manager is reading.
            if (_state.value !is Trainer360State.Success) {
                val cached = LocalCache.loadMap(cacheKey(trainerEmail))
                _state.value = if (cached != null) {
                    Trainer360State.Success(
                        cached,
                        fromCache = true,
                        cachedAt = LocalCache.savedAt(cacheKey(trainerEmail)),
                    )
                } else {
                    Trainer360State.Error(e.localizedMessage ?: "Could not load this trainer's profile")
                }
            }
        }
    }
}
