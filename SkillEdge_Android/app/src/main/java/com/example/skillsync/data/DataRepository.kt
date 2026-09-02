package com.example.skillsync.data

import com.example.skillsync.data.api.RetrofitClient
import com.example.skillsync.data.api.SkillEdgeApi
import com.example.skillsync.data.api.MarkSkillRequest
import com.example.skillsync.data.api.MarkSkillResponse
import com.example.skillsync.data.api.CapacityPlanResponse
import com.example.skillsync.data.cache.LocalCache
import com.example.skillsync.data.models.ActionRow
import com.example.skillsync.data.models.parseActions
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

enum class DataSource { LIVE, CACHE }

data class RepositoryResult<T>(
    val data: T?,
    val source: DataSource,
    val cachedAt: Long = 0L,
    val error: String? = null,
) {
    val hasData: Boolean get() = data != null
}

/** Typed aggregate needed by the Team command page. */
data class TeamIntelligence(
    val capability: Map<String, Any>?,
    val actions: List<ActionRow>,
    val capabilityError: String? = null,
    val actionsError: String? = null,
)

data class SyncResult(
    val successfulDatasets: Int,
    val changedDatasets: Int,
    val errors: List<String>,
) {
    val succeeded: Boolean get() = successfulDatasets > 0
}

/**
 * Single data boundary for manager intelligence.
 *
 * ViewModels own presentation state; this repository owns API calls, disk
 * caching and partial-success behavior. A slow or failed capability request no
 * longer makes Actions disappear, and vice versa.
 */
class ManagerRepository(
    private val apiProvider: () -> SkillEdgeApi = { RetrofitClient.instance },
) {
    private val api: SkillEdgeApi by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { apiProvider() }

    suspend fun dashboard(email: String, fresh: Boolean): RepositoryResult<Map<String, Any>> =
        cachedMap("dashboard_$email", fresh) { api.getTrainerIntelligence(email, fresh.flag()) }

    suspend fun managerProfile(email: String, fresh: Boolean): RepositoryResult<Map<String, Any>> =
        cachedMap("profile_$email", fresh) { api.getManagerProfile(email, fresh.flag()) }

    suspend fun trainer360(email: String, manager: String, fresh: Boolean): RepositoryResult<Map<String, Any>> =
        cachedMap("trainer360_$email", fresh) {
            api.getTrainer360(email, manager.takeIf { it.isNotBlank() }, fresh.flag())
        }

    suspend fun allocation(email: String, fresh: Boolean): RepositoryResult<Map<String, Any>> =
        cachedMap("allocation_$email", fresh) { api.getAllocationDesk(email, fresh.flag()) }

    suspend fun capacityPlan(email: String): CapacityPlanResponse? = try {
        val live = api.getCapacityPlan(email)
        if (live.ready) {
            LocalCache.saveObject("capacity_plan_$email", live)
            live
        } else LocalCache.loadObject("capacity_plan_$email", CapacityPlanResponse::class.java)
    } catch (_: Exception) {
        LocalCache.loadObject("capacity_plan_$email", CapacityPlanResponse::class.java)
    }

    /**
     * Monthly HR report. Heavy on the backend (one full RMS fetch per reportee),
     * so it is served partial-first and warmed in the background there; here it
     * is cached per (manager, month) so the screen renders the last snapshot
     * instantly instead of sitting on a spinner while the rebuild runs.
     */
    suspend fun hrMonthlyReport(email: String, month: String, fresh: Boolean = false): RepositoryResult<Map<String, Any>> =
        cachedMap("hr_report_${email}_$month", fresh) { api.getHrMonthlyReport(email, month) }

    /** Weekly delivery report — same partial-first backend, cached per (manager, week). */
    suspend fun weeklyReport(email: String, week: String, fresh: Boolean = false): RepositoryResult<Map<String, Any>> =
        cachedMap("weekly_report_${email}_$week", fresh) { api.getWeeklyReport(email, week) }

    /** "This Week" priority board — ranked, actionable items for the manager. */
    suspend fun priorities(email: String, fresh: Boolean = false): RepositoryResult<Map<String, Any>> =
        cachedMap("priorities_$email", fresh) { api.getManagerPriorities(email) }

    /** "Capacity Runway" — forward 8-week demand vs capacity. Partial-first on the
     *  backend, cached per manager so the screen renders the last snapshot first. */
    suspend fun capacityRunway(email: String, fresh: Boolean = false): RepositoryResult<Map<String, Any>> =
        cachedMap("runway_$email", fresh) { api.getCapacityRunway(email) }

    /** "New trainer ramp" — onboarding tracking for reportees who joined <12mo ago.
     *  Partial-first on the backend, cached per manager. */
    suspend fun rampReport(email: String, fresh: Boolean = false): RepositoryResult<Map<String, Any>> =
        cachedMap("ramp_$email", fresh) { api.getRamp(email) }

    /** "Accounts" — the team's customer book. Partial-first on the backend,
     *  cached per manager so the screen renders the last snapshot first. */
    suspend fun accountsReport(email: String, fresh: Boolean = false): RepositoryResult<Map<String, Any>> =
        cachedMap("accounts_$email", fresh) { api.getAccounts(email) }

    /** "How your team compares" — team health vs a documented baseline. Partial-first
     *  on the backend, cached per manager so the screen renders the last snapshot first. */
    suspend fun benchmarkReport(email: String, fresh: Boolean = false): RepositoryResult<Map<String, Any>> =
        cachedMap("benchmark_$email", fresh) { api.getBenchmark(email) }

    suspend fun utilizationHistory(email: String) =
        cachedMap("utilization_${email.lowercase()}", false) { api.getTrainerUtilizationHistory(email) }.data.orEmpty()

    suspend fun syllabus(courseName: String) =
        cachedMap("syllabus_${courseName.lowercase()}", false) { api.getCourseSyllabus(courseName) }.data.orEmpty()

    suspend fun searchCourses(query: String) =
        cachedMap("course_search_${query.lowercase()}", false) { api.searchCourses(query) }.data.orEmpty()

    /**
     * Returns the parsed result rather than an empty map so the caller can
     * distinguish "RMS has no schedule" from "the request failed". Turning a
     * failure into an empty dataset is what let a screen lie about its source.
     */
    suspend fun courseIntelligence(courseName: String): RepositoryResult<Map<String, Any>> =
        cachedMap("course_intelligence_${courseName.lowercase()}", false) { api.getCourseIntelligence(courseName) }

    /**
     * Development plan for one reportee. Cache key `devplan_<manager>_<trainer>`;
     * offline-first like every other read here. `suggested` items are recomputed
     * server-side on every call and are never persisted until adopted.
     */
    suspend fun devPlan(manager: String, trainer: String, fresh: Boolean = false): RepositoryResult<Map<String, Any>> =
        cachedMap("devplan_${manager}_$trainer", fresh) { api.getDevPlan(manager, trainer) }

    /** Adopt a suggestion / add a manual goal, then refresh the cached plan. */
    suspend fun addDevPlanItem(
        manager: String,
        trainer: String,
        title: String,
        kind: String,
        targetDate: String = "",
        note: String = "",
    ): RepositoryResult<Map<String, Any>> {
        val body = mutableMapOf(
            "manager" to manager, "trainer" to trainer, "title" to title, "kind" to kind,
        )
        if (targetDate.isNotBlank()) body["target_date"] = targetDate
        if (note.isNotBlank()) body["note"] = note
        return try {
            api.createDevPlanItem(body)
            devPlan(manager, trainer, fresh = true)
        } catch (e: Exception) {
            RepositoryResult(null, DataSource.LIVE, error = e.localizedMessage ?: "Could not add goal")
        }
    }

    /** Change a plan item's status / note / target date, then refresh the cached plan. */
    suspend fun updateDevPlanItem(
        manager: String,
        trainer: String,
        id: String,
        status: String = "",
        note: String = "",
        targetDate: String = "",
    ): RepositoryResult<Map<String, Any>> {
        val body = mutableMapOf("manager" to manager, "id" to id)
        if (status.isNotBlank()) body["status"] = status
        if (note.isNotBlank()) body["note"] = note
        if (targetDate.isNotBlank()) body["target_date"] = targetDate
        return try {
            api.updateDevPlanItem(body)
            devPlan(manager, trainer, fresh = true)
        } catch (e: Exception) {
            RepositoryResult(null, DataSource.LIVE, error = e.localizedMessage ?: "Could not update goal")
        }
    }

    suspend fun preDemandPipeline(manager: String, fresh: Boolean = false): RepositoryResult<Map<String, Any>> =
        cachedMap("pipeline_$manager", fresh) { api.getPreDemandPipeline(manager) }

    suspend fun deliveryCompliance(manager: String, fresh: Boolean = false): RepositoryResult<Map<String, Any>> =
        cachedMap("compliance_$manager", fresh) { api.getDeliveryCompliance(manager) }

    suspend fun endorseSkill(
        managerEmail: String,
        trainerEmail: String,
        courseId: String,
        courseName: String,
        skillLevel: Int,
        fromDate: String = "",
        devPlanId: String = "",
    ): Map<String, Any> = api.endorseSkill(
        mapOf(
            "manager_email" to managerEmail,
            "trainer_email" to trainerEmail,
            "course_id" to courseId,
            "course_name" to courseName,
            "skill_level" to skillLevel,
            "from_date" to fromDate,
            "dev_plan_id" to devPlanId,
        )
    )

    suspend fun trainerSentiment(trainerEmail: String, fresh: Boolean = false): RepositoryResult<Map<String, Any>> =
        cachedMap("sentiment_$trainerEmail", fresh) { api.getTrainerSentiment(trainerEmail) }

    suspend fun viberQueue(manager: String, fresh: Boolean = false): RepositoryResult<Map<String, Any>> =
        cachedMap("viber_queue_$manager", fresh) { api.getViberQueue(manager) }

    suspend fun dispatchViber(body: Map<String, Any>): Map<String, Any> =
        api.dispatchViber(body)

    suspend fun viberConfig(manager: String, fresh: Boolean = false): RepositoryResult<Map<String, Any>> =
        cachedMap("viber_config_$manager", fresh) { api.getViberConfig(manager) }

    suspend fun updateViberConfig(body: Map<String, Any>): Map<String, Any> =
        api.updateViberConfig(body)

    /** Production skill writes share the repository boundary with all reads. */
    suspend fun markSkill(request: MarkSkillRequest): retrofit2.Response<MarkSkillResponse> =
        api.markSkill(request)

    suspend fun actions(email: String, fresh: Boolean = false): RepositoryResult<List<ActionRow>> = try {
        val result = cachedMap("actions_$email", fresh) { api.getActions(email) }
        RepositoryResult(parseActions(result.data), result.source, result.cachedAt, result.error)
    } catch (e: Exception) {
        RepositoryResult(null, DataSource.LIVE, error = e.localizedMessage ?: "Could not load actions")
    }

    suspend fun teamIntelligence(email: String, fresh: Boolean): TeamIntelligence = coroutineScope {
        val capability = async {
            cachedMap("capability_$email", fresh) { api.getTeamCapability(email, fresh.flag()) }
        }
        val actions = async {
            runCatching { actions(email, fresh) }
                .fold(
                    onSuccess = { result -> Pair(result.data.orEmpty(), result.error) },
                    onFailure = { Pair(emptyList(), it.localizedMessage ?: "Could not load actions") },
                )
        }
        val cap = capability.await()
        val act = actions.await()
        TeamIntelligence(cap.data, act.first, cap.error, act.second)
    }

    /** Refresh every persistent manager snapshot in parallel for WorkManager. */
    suspend fun syncAll(email: String): SyncResult = coroutineScope {
        val before = listOf("dashboard_$email", "profile_$email", "allocation_$email", "capability_$email", "actions_$email")
            .associateWith { LocalCache.savedAt(it) }
        val dashboard = async { dashboard(email, fresh = false) }
        val profile = async { managerProfile(email, fresh = false) }
        val allocation = async { allocation(email, fresh = false) }
        val team = async { teamIntelligence(email, fresh = false) }
        val results = listOf(dashboard.await(), profile.await(), allocation.await())
        // Capacity depends on the completed allocation snapshot, so refresh it
        // only after allocation has returned rather than racing the two calls.
        capacityPlan(email)
        val teamResult = team.await()
        val success = results.count { it.data != null } +
            (if (teamResult.capability != null) 1 else 0) +
            (if (teamResult.actions.isNotEmpty() || teamResult.actionsError == null) 1 else 0)
        val errors = results.mapNotNull { it.error } +
            listOfNotNull(teamResult.capabilityError, teamResult.actionsError)
        val changed = before.count { (key, saved) -> LocalCache.savedAt(key) > saved }
        SyncResult(success, changed, errors.distinct())
    }

    private suspend fun cachedMap(
        key: String,
        fresh: Boolean,
        network: suspend () -> Map<String, Any>,
    ): RepositoryResult<Map<String, Any>> {
        return try {
            val live = network()
            // A 202/preparing response is control state, not a dataset. Never
            // overwrite the last complete board with it; the UI remains
            // populated until the completed incremental snapshot arrives.
            if (live["loading"] != true) LocalCache.saveMap(key, live)
            RepositoryResult(live, DataSource.LIVE)
        } catch (e: Exception) {
            // A forced network refresh must never remove the last usable local
            // snapshot. `fresh` controls the request, not offline resilience.
            val cached = LocalCache.loadMap(key)
            if (cached != null) {
                RepositoryResult(
                    cached, DataSource.CACHE, LocalCache.savedAt(key),
                    e.localizedMessage ?: "Live refresh failed",
                )
            } else {
                RepositoryResult(null, DataSource.LIVE, error = e.localizedMessage ?: "Request failed")
            }
        }
    }

    private fun Boolean.flag() = if (this) 1 else null
}
