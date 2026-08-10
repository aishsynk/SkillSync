package com.example.skillsync.data

import com.example.skillsync.data.api.RetrofitClient
import com.example.skillsync.data.api.SkillEdgeApi
import com.example.skillsync.data.api.MarkSkillRequest
import com.example.skillsync.data.api.MarkSkillResponse
import com.example.skillsync.data.api.CapacityPlanResponse
import com.example.skillsync.data.cache.LocalCache
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
    val actions: List<Map<String, Any>>,
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

    suspend fun utilizationHistory(email: String) =
        cachedMap("utilization_${email.lowercase()}", false) { api.getTrainerUtilizationHistory(email) }.data.orEmpty()

    suspend fun syllabus(courseName: String) =
        cachedMap("syllabus_${courseName.lowercase()}", false) { api.getCourseSyllabus(courseName) }.data.orEmpty()

    suspend fun searchCourses(query: String) =
        cachedMap("course_search_${query.lowercase()}", false) { api.searchCourses(query) }.data.orEmpty()

    suspend fun courseIntelligence(courseName: String) =
        cachedMap("course_intelligence_${courseName.lowercase()}", false) { api.getCourseIntelligence(courseName) }.data.orEmpty()

    /** Production skill writes share the repository boundary with all reads. */
    suspend fun markSkill(request: MarkSkillRequest): retrofit2.Response<MarkSkillResponse> =
        api.markSkill(request)

    suspend fun actions(email: String, fresh: Boolean = false): RepositoryResult<List<Map<String, Any>>> = try {
        val result = cachedMap("actions_$email", fresh) { api.getActions(email) }
        val body = result.data ?: return RepositoryResult(null, result.source, result.cachedAt, result.error)
        @Suppress("UNCHECKED_CAST")
        val rows = (body["actions"] as? List<Map<String, Any>>).orEmpty()
        RepositoryResult(rows, result.source, result.cachedAt, result.error)
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
