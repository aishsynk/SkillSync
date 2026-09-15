package com.example.skillsync.feature.auth.ui

import com.example.skillsync.core.data.AuthRepository
import com.example.skillsync.core.network.AuthCheckResponse
import com.example.skillsync.core.network.LoginRequest
import com.example.skillsync.core.network.LoginResponse
import com.example.skillsync.core.network.SetPasswordRequest
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
import org.junit.Test

/**
 * Confirms LoginViewModel goes through AuthRepository (not a direct Retrofit
 * call) for every sign-in step.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private class FakeRepository(
        var authCheckResult: AuthCheckResponse = AuthCheckResponse(ok = true, email = null, role = "manager", name = "Aishwar", needs_password = true, first_login = false, error = null),
        var loginResult: LoginResponse = LoginResponse(success = true, session_id = "sid", email = "aishwar.c@koenig-solutions.com", role = "manager", code = null, manager_email = null, must_change = false, error = null, message = null),
    ) : AuthRepository() {
        var authCheckCalls = 0
        var loginCalls = 0
        var setPasswordCalls = 0

        override suspend fun authCheck(request: LoginRequest): AuthCheckResponse {
            authCheckCalls++
            return authCheckResult
        }

        override suspend fun login(request: LoginRequest): LoginResponse {
            loginCalls++
            return loginResult
        }

        override suspend fun setPassword(request: SetPasswordRequest): Map<String, Any> {
            setPasswordCalls++
            return emptyMap()
        }
    }

    private fun runVmTest(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            block()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun idStep_needsPassword_goesThroughTheRepositoryAndAdvancesToPasswordStep() = runVmTest {
        val repo = FakeRepository()
        val vm = LoginViewModel(repo)
        vm.submit("aishwar.c", "")
        advanceUntilIdle()

        assertEquals(1, repo.authCheckCalls)
        assertEquals(LoginStep.PASSWORD, vm.step.value)
    }

    @Test
    fun passwordStep_success_goesThroughTheRepositoryAndReachesSuccessState() = runVmTest {
        val repo = FakeRepository()
        val vm = LoginViewModel(repo)
        vm.submit("aishwar.c", "")
        advanceUntilIdle()
        vm.submit("aishwar.c", "correct-password")
        advanceUntilIdle()

        assertEquals(1, repo.loginCalls)
        assertTrue(vm.loginState.value is LoginState.Success)
    }

    @Test
    fun mustChangePassword_setPasswordGoesThroughTheRepository() = runVmTest {
        val repo = FakeRepository(
            loginResult = LoginResponse(success = true, session_id = "sid", email = "aishwar.c@koenig-solutions.com", role = "manager", code = null, manager_email = null, must_change = true, error = null, message = null),
        )
        val vm = LoginViewModel(repo)
        vm.submit("aishwar.c", "")
        advanceUntilIdle()
        vm.submit("aishwar.c", "employee-code")
        advanceUntilIdle()
        assertEquals(LoginStep.SET_PASSWORD, vm.step.value)

        vm.submit("aishwar.c", "new-password-123")
        advanceUntilIdle()

        assertEquals(1, repo.setPasswordCalls)
    }
}
