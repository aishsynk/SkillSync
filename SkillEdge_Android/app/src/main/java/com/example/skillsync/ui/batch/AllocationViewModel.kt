package com.example.skillsync.ui.batch

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skillsync.data.api.MarkSkillRequest
import com.example.skillsync.data.api.MarkSkillResponse
import com.example.skillsync.data.api.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class AllocationState {
    object Loading : AllocationState()
    data class Success(val data: Map<String, Any>) : AllocationState()
    data class Error(val message: String) : AllocationState()
}

/**
 * Outcome of a write to RMS.
 *
 * [Done] means the backend re-read the RMS skill register and found the course
 * there. [Unconfirmed] means RMS accepted the request but the read-back could not
 * be performed — genuinely different from success, and the manager is told so
 * rather than being shown a tick that might be a lie.
 */
sealed class MarkState {
    object Idle : MarkState()
    object Working : MarkState()
    data class Done(val who: String, val level: Int, val message: String, val alreadyHeld: Boolean) : MarkState()
    data class Unconfirmed(val who: String, val message: String) : MarkState()
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

    /**
     * { available, trainers, note } for the wider-network lookup; null while
     * the request is in flight. `available: false` means RMS would not answer
     * the question at all — which is not the same as an empty trainer list,
     * and the sheet must not render it as "nobody found".
     */
    val globalSearchData = MutableStateFlow<Map<String, Any>?>(null)

    fun load(email: String, context: Context) {
        if (loadedFor == email && _state.value is AllocationState.Success) return
        loadedFor = email
        viewModelScope.launch {
            _state.value = AllocationState.Loading
            fetch(email, context, fresh = false)
        }
    }

    fun globalSearch(course: String) {
        viewModelScope.launch {
            globalSearchData.value = null // reset while loading
            try {
                globalSearchData.value = RetrofitClient.instance.getAlternativeTrainers(course)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun refresh(email: String, context: Context) {
        viewModelScope.launch {
            _refreshing.value = true
            fetch(email, context, fresh = true)
            _refreshing.value = false
        }
    }

    private fun cacheKey(email: String) = "allocation_$email"

    private suspend fun fetch(email: String, context: Context, fresh: Boolean) {
        // 1. Instantly read from LocalCache if not already loaded and no fresh push requested
        if (!fresh && _state.value !is AllocationState.Success) {
            val cached = com.example.skillsync.data.cache.LocalCache.loadMap(cacheKey(email))
            if (cached != null) {
                _newIds.value = SeenBatches.diffAndRemember(context, email, cached)
                _state.value = AllocationState.Success(cached)
            }
        }

        // 2. Network Check
        if (!RetrofitClient.isNetworkAvailable(context)) {
            if (_state.value !is AllocationState.Success) {
                _state.value = AllocationState.Error("No internet connection")
            }
            return
        }

        // 3. Fetch from API in background
        try {
            val data = RetrofitClient.instance.getAllocationDesk(email, if (fresh) 1 else null)
            com.example.skillsync.data.cache.LocalCache.saveMap(cacheKey(email), data)
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

    /**
     * Writes a skill to production RMS and reports what actually happened.
     *
     * A rejected write comes back as HTTP 409 with a populated body. The previous
     * plain-suspend call turned that into an HttpException whose message was
     * "HTTP 409", so a skill that had silently failed to save surfaced as a
     * generic network error — or worse, as success. The body is read explicitly
     * here, and [onSaved] fires only on a verified write so dependent screens
     * refresh against data that really changed.
     */
    fun markSkill(
        context: android.content.Context,
        courseId: String,
        trainerEmail: String,
        level: Int,
        fromDate: String,
        who: String,
        onSaved: () -> Unit = {},
    ) {
        viewModelScope.launch {
            _mark.value = MarkState.Working

            // OFFLINE QUEUE CHECK
            if (!RetrofitClient.isNetworkAvailable(context)) {
                com.example.skillsync.data.cache.ActionQueueManager.enqueueAction(
                    com.example.skillsync.data.cache.QueuedAction(
                        payload = com.example.skillsync.data.api.MarkSkillRequest(
                            course_id = courseId,
                            trainer_email = trainerEmail,
                            skill_level = level,
                            from_date = fromDate,
                        )
                    )
                )
                // Optimistic success when offline
                _mark.value = MarkState.Done(
                    who = who,
                    level = level,
                    message = "Action queued for sync when online.",
                    alreadyHeld = false
                )
                onSaved()
                return@launch
            }

            _mark.value = try {
                val resp = RetrofitClient.instance.markSkill(
                    com.example.skillsync.data.api.MarkSkillRequest(
                        course_id = courseId,
                        trainer_email = trainerEmail,
                        skill_level = level,
                        from_date = fromDate,
                    )
                )
                val body = resp.body() ?: runCatching {
                    // Error bodies still carry the reason the write was refused.
                    com.google.gson.Gson().fromJson(
                        resp.errorBody()?.string().orEmpty(), com.example.skillsync.data.api.MarkSkillResponse::class.java
                    )
                }.getOrNull()

                when {
                    body == null -> MarkState.Failed("Server returned an unreadable response (HTTP ${resp.code()})")
                    body.success == true && body.verified == true -> {
                        // Only re-read dependent screens when something actually
                        // changed; a no-op re-assert leaves the data identical.
                        if (body.changed == true) onSaved()
                        MarkState.Done(
                            who = who,
                            level = level,
                            message = body.message ?: "Skill recorded and confirmed in RMS.",
                            alreadyHeld = body.already_held == true,
                        )
                    }
                    body.success == true -> MarkState.Unconfirmed(
                        who,
                        body.message ?: "RMS accepted the request but it could not be confirmed.",
                    )
                    else -> MarkState.Failed(body.error ?: "RMS rejected the request")
                }
            } catch (e: Exception) {
                MarkState.Failed(e.localizedMessage ?: "Could not reach the server")
            }
        }
    }

    fun clearMark() {
        _mark.value = MarkState.Idle
    }
}
