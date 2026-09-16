package com.example.skillsync.feature.home

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.skillsync.core.data.DataSource
import com.example.skillsync.core.data.RepositoryResult
import com.example.skillsync.core.storage.LocalCache
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Release-blocker regression: the People tab ("No reportees returned") was
 * caused by [MainScreenViewModel] committing the backend's cold-cache
 * placeholder — HTTP 200, `loading: true`, an empty `trainer_operations_df`
 * — as final [DashboardState.Success] data, instead of recognising it as
 * still-building and waiting for the real payload (see
 * `services`/`_serve_or_warm` in backend.py, which returns exactly this
 * shape while a fresh build runs in the background).
 *
 * These tests exercise the real ViewModel with a fake `fetchDashboardData`
 * seam — no network, no Compose, deterministic fixtures only.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MainScreenViewModelDashboardTest {

    private val email = "manager.test@koenig-solutions.com"
    private val context: android.content.Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        LocalCache.init(context)
    }

    /** Always "online" — the same seam MainScreenViewModel uses in production,
     *  injected here so the test never depends on Robolectric's real
     *  ConnectivityManager shadow. */
    private val online: (android.content.Context) -> Boolean = { true }

    private fun runVmTest(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            block()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun rosterPayload(vararg names: String) = mapOf<String, Any>(
        "loading" to false,
        "trainer_operations_df" to names.map { mapOf("official_email" to "$it@koenig-solutions.com", "name" to it) },
        "trainer_current_state_df" to emptyList<Any>(),
        "delivery_intelligence_df" to emptyList<Any>(),
    )

    private fun loadingPlaceholder() = mapOf<String, Any>(
        "loading" to true,
        "trainer_operations_df" to emptyList<Any>(),
    )

    @Test
    fun realRosterFromBackend_reachesDashboardStateUnchanged() = runVmTest {
        var calls = 0
        val vm = MainScreenViewModel(
            isNetworkAvailable = online,
            fetchDashboardData = { _, _ ->
                calls++
                RepositoryResult(rosterPayload("Niharika Rao", "Priya Sharma"), DataSource.LIVE)
            },
        )
        vm.loadData(email, context)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("expected Success, got $state", state is DashboardState.Success)
        val ops = (state as DashboardState.Success).intelligenceData["trainer_operations_df"] as List<*>
        assertEquals(2, ops.size)
        assertEquals(1, calls)
    }

    @Test
    fun genuinelyEmptyRoster_isCommittedAsSuccessNotAnError() = runVmTest {
        val vm = MainScreenViewModel(
            isNetworkAvailable = online,
            fetchDashboardData = { _, _ -> RepositoryResult(rosterPayload(), DataSource.LIVE) },
        )
        vm.loadData(email, context)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("a real, complete zero-reportee response must still be Success", state is DashboardState.Success)
        val ops = (state as DashboardState.Success).intelligenceData["trainer_operations_df"] as List<*>
        assertTrue(ops.isEmpty())
    }

    @Test
    fun coldCacheLoadingPlaceholder_isNeverCommittedAsTheFinalRoster() = runVmTest {
        // Every poll returns the still-building placeholder — this reproduces
        // the exact regression: a manager who genuinely has reportees must
        // never see an empty roster just because the backend was still warm.
        var calls = 0
        val vm = MainScreenViewModel(
            isNetworkAvailable = online,
            fetchDashboardData = { _, _ -> calls++; RepositoryResult(loadingPlaceholder(), DataSource.LIVE) },
        )
        vm.loadData(email, context)
        advanceUntilIdle()

        val state = vm.uiState.value
        if (state is DashboardState.Success) {
            // Never true for this fixture (no cache seeded), but if a cached
            // snapshot were held it must never be the empty placeholder.
            val ops = state.intelligenceData["trainer_operations_df"] as? List<*>
            assertTrue(ops == null || ops.isNotEmpty())
        } else {
            assertTrue("expected Loading or Error while still warming, got $state", state is DashboardState.Error)
        }
        // Polled more than once rather than accepting the first loading response.
        assertTrue("expected more than one poll attempt, got $calls", calls > 1)
    }

    @Test
    fun coldCacheThenRealData_resolvesToTheRealRosterOnceReady() = runVmTest {
        var calls = 0
        val vm = MainScreenViewModel(
            isNetworkAvailable = online,
            fetchDashboardData = { _, _ ->
                calls++
                if (calls <= 2) RepositoryResult(loadingPlaceholder(), DataSource.LIVE)
                else RepositoryResult(rosterPayload("Rahul Verma"), DataSource.LIVE)
            },
        )
        vm.loadData(email, context)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("expected Success once the real payload arrived, got $state", state is DashboardState.Success)
        val ops = (state as DashboardState.Success).intelligenceData["trainer_operations_df"] as List<*>
        assertEquals(1, ops.size)
    }

    @Test
    fun requestFailure_surfacesAnErrorNeverAnEmptyRoster() = runVmTest {
        val vm = MainScreenViewModel(
            isNetworkAvailable = online,
            fetchDashboardData = { _, _ -> throw java.io.IOException("network down") },
        )
        vm.loadData(email, context)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("a request failure must be an Error state, got $state", state is DashboardState.Error)
    }

    @Test
    fun requestFailure_afterAGoodLoad_keepsTheLastKnownRosterVisible() = runVmTest {
        var fail = false
        val vm = MainScreenViewModel(
            isNetworkAvailable = online,
            fetchDashboardData = { _, _ ->
                if (fail) throw java.io.IOException("network down")
                RepositoryResult(rosterPayload("Niharika Rao"), DataSource.LIVE)
            },
        )
        vm.loadData(email, context)
        advanceUntilIdle()
        assertTrue(vm.uiState.value is DashboardState.Success)

        fail = true
        vm.refresh(email, context)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("a failed refresh must not blank a good roster", state is DashboardState.Success)
        val ops = (state as DashboardState.Success).intelligenceData["trainer_operations_df"] as List<*>
        assertEquals(1, ops.size)
    }
}
