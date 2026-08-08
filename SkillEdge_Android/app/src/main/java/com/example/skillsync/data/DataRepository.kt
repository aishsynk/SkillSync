package com.example.skillsync.data

import com.example.skillsync.data.api.RetrofitClient
import com.example.skillsync.data.api.SkillEdgeApi
import com.example.skillsync.data.api.MarkSkillRequest
import com.example.skillsync.data.api.MarkSkillResponse
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

    suspend fun utilizationHistory(email: String) = api.getTrainerUtilizationHistory(email)

    suspend fun syllabus(courseName: String) = api.getCourseSyllabus(courseName)

    /** Production skill writes share the repository boundary with all reads. */
    suspend fun markSkill(request: MarkSkillRequest): retrofit2.Response<MarkSkillResponse> =
        api.markSkill(request)

    suspend fun actions(email: String): RepositoryResult<List<Map<String, Any>>> = try {
        val body = api.getActions(email)
        @Suppress("UNCHECKED_CAST")
        val rows = (body["actions"] as? List<Map<String, Any>>).orEmpty()
        RepositoryResult(rows, DataSource.LIVE)
    } catch (e: Exception) {
        RepositoryResult(null, DataSource.LIVE, error = e.localizedMessage ?: "Could not load actions")
    }

    suspend fun teamIntelligence(email: String, fresh: Boolean): TeamIntelligence = coroutineScope {
        val capability = async {
            cachedMap("capability_$email", fresh) { api.getTeamCapability(email, fresh.flag()) }
        }
        val actions = async {
            runCatching { api.getActions(email) }
                .fold(
                    onSuccess = { body ->
                        @Suppress("UNCHECKED_CAST")
                        Pair((body["actions"] as? List<Map<String, Any>>).orEmpty(), null)
                    },
                    onFailure = { Pair(emptyList(), it.localizedMessage ?: "Could not load actions") },
                )
        }
        val cap = capability.await()
        val act = actions.await()
        TeamIntelligence(cap.data, act.first, cap.error, act.second)
    }

    private suspend fun cachedMap(
        key: String,
        fresh: Boolean,
        network: suspend () -> Map<String, Any>,
    ): RepositoryResult<Map<String, Any>> {
        return try {
            val live = network()
            LocalCache.saveMap(key, live)
            RepositoryResult(live, DataSource.LIVE)
        } catch (e: Exception) {
            val cached = if (!fresh) LocalCache.loadMap(key) else null
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
