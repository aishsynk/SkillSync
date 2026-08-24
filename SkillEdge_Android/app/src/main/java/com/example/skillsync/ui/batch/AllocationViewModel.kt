package com.example.skillsync.ui.batch

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skillsync.data.api.MarkSkillRequest
import com.example.skillsync.data.api.MarkSkillResponse
import com.example.skillsync.data.api.RetrofitClient
import com.example.skillsync.data.ManagerRepository
import com.example.skillsync.data.models.CourseIntelligence
import com.example.skillsync.ui.common.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

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

class AllocationViewModel(
    private val repository: ManagerRepository = ManagerRepository(),
) : ViewModel() {

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
     * Persisted-write time of the allocation board currently in memory. A
     * background sync may only swap it for a genuinely newer revision — never
     * for an older snapshot whose file happened to change during a network
     * flap (which flipped seen/unseen batches mid-tab before).
     */
    private var lastAdoptedAt = 0L

    /**
     * { available, trainers, note } for the wider-network lookup; null while
     * the request is in flight. `available: false` means RMS would not answer
     * the question at all — which is not the same as an empty trainer list,
     * and the sheet must not render it as "nobody found".
     */
    val globalSearchData = MutableStateFlow<Map<String, Any>?>(null)

    val courseSearchResults = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val courseSearchLoading = MutableStateFlow(false)
    val courseIntelligence = MutableStateFlow<CourseIntelligence?>(null)
    val courseIntelligenceLoading = MutableStateFlow(false)
    val demandContext = MutableStateFlow<com.example.skillsync.data.api.DemandContextResponse?>(null)
    val demandContextLoading = MutableStateFlow(false)
    val demandContextError = MutableStateFlow<String?>(null)
    val capacityPlan = MutableStateFlow<com.example.skillsync.data.api.CapacityPlanResponse?>(null)
    val capacityPlanLoading = MutableStateFlow(false)
    private var demandContextKey: String? = null

    /**
     * Fully gated candidates for the open batch.
     *
     * The board's overlay cannot check client exclusions or leave — those need
     * a per-trainer call that is multiplicative across a whole board — so the
     * complete evaluation runs here, when a manager opens one batch.
     * [candidatesUnverified] carries the reason when the course could not be
     * resolved, which must never be shown as "nobody available".
     */
    val gatedCandidates = MutableStateFlow<com.example.skillsync.data.api.AllocationCandidatesResponse?>(null)
    val gatedCandidatesLoading = MutableStateFlow(false)
    val gatedCandidatesUnverified = MutableStateFlow<String?>(null)
    private var gatedKey: String? = null

    fun loadGatedCandidates(
        manager: String, course: String, start: String, end: String,
        country: String = "", customer: String = "",
        deliveryMode: String = "", international: Boolean = false,
    ) {
        if (course.isBlank() || start.isBlank()) return
        val key = "$manager|$course|$start|$end"
        if (gatedKey == key && (gatedCandidates.value != null || gatedCandidatesLoading.value)) return
        gatedKey = key
        gatedCandidates.value = null
        gatedCandidatesUnverified.value = null
        viewModelScope.launch {
            gatedCandidatesLoading.value = true
            try {
                gatedCandidates.value = RetrofitClient.instance.getAllocationCandidates(
                    manager = manager, course = course, start = start, end = end,
                    country = country, customer = customer,
                    deliveryMode = deliveryMode,
                    international = if (international) "true" else "",
                )
            } catch (e: retrofit2.HttpException) {
                // 422 is the deliberate "cannot verify" answer, not a failure.
                gatedCandidatesUnverified.value = if (e.code() == 422)
                    "This course could not be matched in the RMS catalogue, so availability could not be verified."
                else e.userMessage("check candidate availability")
            } catch (e: Exception) {
                gatedCandidatesUnverified.value = e.userMessage("check candidate availability")
            } finally {
                gatedCandidatesLoading.value = false
            }
        }
    }

    fun loadDemandContext(manager: String, demandId: String, courseName: String) {
        val key = "$manager|$demandId|$courseName"
        if (demandContextKey == key && (demandContext.value != null || demandContextLoading.value)) return
        demandContextKey = key
        demandContext.value = null
        demandContextError.value = null
        viewModelScope.launch {
            demandContextLoading.value = true
            try {
                demandContext.value = RetrofitClient.instance.getDemandContext(manager, demandId, courseName)
            } catch (_: Exception) {
                demandContextError.value = "Live operational verification is unavailable. Cached demand details remain usable."
            } finally {
                demandContextLoading.value = false
            }
        }
    }

    fun searchCourses(query: String) {
        if (query.trim().length < 2) {
            courseSearchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            courseSearchLoading.value = true
            courseSearchResults.value = try {
                @Suppress("UNCHECKED_CAST")
                (repository.searchCourses(query.trim())["courses"] as? List<Map<String, Any>>).orEmpty()
            } catch (_: Exception) {
                emptyList()
            }
            courseSearchLoading.value = false
        }
    }

    fun loadCourseIntelligence(courseName: String) {
        if (courseName.isBlank()) return
        viewModelScope.launch {
            courseIntelligenceLoading.value = true
            // A failed lookup must surface as [CourseIntelligence.Unverified],
            // never as a hand-built "empty" payload the sheet would misread as
            // "RMS has no schedule for this course".
            courseIntelligence.value = try {
                repository.courseIntelligence(courseName).data
                    ?.let { CourseIntelligence.from(it) }
                    ?: CourseIntelligence.Unverified(courseName)
            } catch (_: Exception) {
                CourseIntelligence.Unverified(courseName)
            }
            courseIntelligenceLoading.value = false
        }
    }

    fun load(email: String, context: Context) {
        if (loadedFor == email && _state.value is AllocationState.Success) return
        loadedFor = email
        // Never replace a usable persisted Demand board with a loading screen.
        // The network pass below updates it silently when it completes.
        com.example.skillsync.data.cache.LocalCache.loadMap(cacheKey(email))?.let { cached ->
            _newIds.value = SeenBatches.diffAndRemember(context, email, cached)
            lastAdoptedAt = com.example.skillsync.data.cache.LocalCache.savedAt(cacheKey(email))
            _state.value = AllocationState.Success(cached)
            viewModelScope.launch { fetchCapacityPlan(email) }
        }
        viewModelScope.launch {
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

    private var livePollingJob: kotlinx.coroutines.Job? = null

    /**
     * Active real-time live demand scanner:
     * Listens to SyncCoordinator revisions for instant updates on the go,
     * and pulses a fresh demand fetch every 20s while the Demand tab is active.
     */
    fun startLiveDemandPolling(email: String, context: Context) {
        if (livePollingJob?.isActive == true) return
        livePollingJob = viewModelScope.launch {
            // Instant adoption whenever SyncCoordinator emits a new revision
            launch {
                com.example.skillsync.data.sync.SyncCoordinator.revisions.collect {
                    adoptBackgroundSync(email, context)
                }
            }
            while (true) {
                delay(20_000) // 20s active Demand radar
                if (RetrofitClient.isNetworkAvailable(context)) {
                    fetch(email, context, fresh = true)
                }
            }
        }
    }

    fun stopLiveDemandPolling() {
        livePollingJob?.cancel()
        livePollingJob = null
    }

    private fun cacheKey(email: String) = "allocation_$email"

    fun adoptBackgroundSync(email: String, context: Context) {
        viewModelScope.launch {
            val savedAt = com.example.skillsync.data.cache.LocalCache.savedAt(cacheKey(email))
            if (savedAt <= lastAdoptedAt) return@launch
            com.example.skillsync.data.cache.LocalCache.loadMap(cacheKey(email))?.let { cached ->
                val previous = (_state.value as? AllocationState.Success)?.data
                if (previous != cached) {
                    lastAdoptedAt = savedAt
                    _newIds.value = SeenBatches.diffAndRemember(context, email, cached)
                    _state.value = AllocationState.Success(cached)
                }
            }
        }
    }

    private suspend fun fetch(email: String, context: Context, fresh: Boolean) {
        // 1. Instantly read from LocalCache if not already loaded and no fresh push requested
        if (!fresh && _state.value !is AllocationState.Success) {
            val cached = com.example.skillsync.data.cache.LocalCache.loadMap(cacheKey(email))
            if (cached != null) {
                _newIds.value = SeenBatches.diffAndRemember(context, email, cached)
                lastAdoptedAt = com.example.skillsync.data.cache.LocalCache.savedAt(cacheKey(email))
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
            var result = repository.allocation(email, fresh)
            var data = result.data ?: throw IllegalStateException(result.error ?: "Could not load demand")
            // A cold backend prepares the expensive RMS board in the background
            // and answers 202 immediately, avoiding a proxy 502. Poll briefly
            // while keeping any existing board visible.
            repeat(12) {
                if (data["loading"] != true) return@repeat
                delay(3_000)
                result = repository.allocation(email, fresh = false)
                data = result.data ?: data
            }
            if (data["loading"] == true) {
                if (_state.value !is AllocationState.Success) {
                    _state.value = AllocationState.Error("Demand intelligence is still preparing. Pull to refresh shortly.")
                }
                return
            }
            _newIds.value = SeenBatches.diffAndRemember(context, email, data)
            lastAdoptedAt = System.currentTimeMillis()
            _state.value = AllocationState.Success(data)
            fetchCapacityPlan(email)
        } catch (e: Exception) {
            if (_state.value !is AllocationState.Success) {
                _state.value = AllocationState.Error(e.userMessage("load unallocated batches"))
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
                val resp = repository.markSkill(
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
                    body == null -> MarkState.Failed("Skill service returned an unreadable response. Please retry; no success was assumed.")
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
                MarkState.Failed("Skill marking could not be completed. Check connectivity and retry; no success was assumed.")
            }
        }
    }

    fun clearMark() {
        _mark.value = MarkState.Idle
    }

    private suspend fun fetchCapacityPlan(email: String) {
        capacityPlanLoading.value = true
        capacityPlan.value = repository.capacityPlan(email)
        capacityPlanLoading.value = false
    }

    /** Assign one selected course to one or more trainers, reporting partial failure honestly. */
    fun markSkillBatch(
        context: Context,
        courseId: String,
        trainers: List<Pair<String, String>>,
        level: Int,
        fromDate: String,
        onSaved: () -> Unit = {},
    ) {
        viewModelScope.launch {
            if (!RetrofitClient.isNetworkAvailable(context)) {
                _mark.value = MarkState.Failed("Multi-trainer assignment requires a live connection; nothing was queued.")
                return@launch
            }
            _mark.value = MarkState.Working
            var verified = 0
            val failures = mutableListOf<String>()
            for ((name, email) in trainers) {
                try {
                    val response = repository.markSkill(MarkSkillRequest(courseId, email, level, fromDate))
                    val body = response.body() ?: runCatching {
                        com.google.gson.Gson().fromJson(response.errorBody()?.string().orEmpty(), MarkSkillResponse::class.java)
                    }.getOrNull()
                    if (body?.success == true && body.verified == true) verified++
                    else failures += "$name: ${body?.error ?: body?.message ?: "not confirmed"}"
                } catch (_: Exception) {
                    failures += "$name: service unavailable"
                }
            }
            if (verified > 0) onSaved()
            _mark.value = when {
                failures.isEmpty() -> MarkState.Done("$verified trainers", level, "Skill confirmed for all $verified selected trainers.", false)
                verified > 0 -> MarkState.Unconfirmed("$verified of ${trainers.size} trainers", "Confirmed $verified; ${failures.joinToString(" · ")}")
                else -> MarkState.Failed(failures.joinToString(" · ").ifBlank { "No skill assignments were confirmed." })
            }
        }
    }

    // ── §7.6 bulk skill assignment ──────────────────────────────────────────
    val bulkWorking = MutableStateFlow(false)
    val bulkResults = MutableStateFlow<List<com.example.skillsync.ui.main.SkillWriteResult>?>(null)

    /**
     * One skill to many reportees.
     *
     * Results are held per row rather than collapsed to a success flag: this
     * writes to production RMS and a partial failure is the normal outcome,
     * so the manager has to see which rows still need them.
     */
    fun bulkAssignSkill(courseId: String, rows: List<Pair<String, Int>>) {
        if (courseId.isBlank() || rows.isEmpty()) return
        viewModelScope.launch {
            bulkWorking.value = true
            bulkResults.value = null
            val response = runCatching {
                com.example.skillsync.data.api.RetrofitClient.instance.bulkAssignSkill(
                    com.example.skillsync.data.api.BulkAssignRequest(
                        course_id = courseId,
                        trainers = rows.map { (email, level) ->
                            com.example.skillsync.data.api.BulkAssignRow(email, level)
                        },
                    )
                )
            }.getOrNull()

            bulkResults.value = response?.results?.map {
                com.example.skillsync.ui.main.SkillWriteResult(
                    email = it.trainer_email, ok = it.ok, message = it.message,
                )
            } ?: rows.map { (email, _) ->
                // A transport failure is not a refusal, and must not be shown
                // as one: nothing is known about whether the write landed.
                com.example.skillsync.ui.main.SkillWriteResult(
                    email = email, ok = false,
                    message = "No response from the server. Check the RMS skill register before retrying.",
                )
            }
            bulkWorking.value = false
        }
    }

    fun clearBulkResults() {
        bulkResults.value = null
    }
}
