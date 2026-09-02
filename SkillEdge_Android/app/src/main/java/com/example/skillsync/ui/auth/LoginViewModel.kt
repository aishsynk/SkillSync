package com.example.skillsync.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skillsync.data.api.LoginRequest
import com.example.skillsync.data.api.RetrofitClient
import com.example.skillsync.data.api.SetPasswordRequest
import com.example.skillsync.ui.common.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

private const val DOMAIN = "@koenig-solutions.com"

/** Which field the sign-in form is currently asking for. */
enum class LoginStep { ID, PASSWORD, SET_PASSWORD }

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class Success(val sessionId: String, val email: String) : LoginState()
    data class Error(val message: String) : LoginState()
}

/** Turn "Aishwar.C " / "aishwar.c@koenig-solutions.com" into a bare local part. */
fun sanitiseWorkId(raw: String): String =
    raw.trim().lowercase().removeSuffix(DOMAIN).substringBefore("@").filterNot { it.isWhitespace() }

class LoginViewModel : ViewModel() {
    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState

    private val _step = MutableStateFlow(LoginStep.ID)
    val step: StateFlow<LoginStep> = _step

    private var pendingId: String = ""

    fun reset() {
        _loginState.value = LoginState.Idle
        _step.value = LoginStep.ID
        pendingId = ""
    }

    /** Submit whatever the current step needs. */
    fun submit(workId: String, password: String) {
        when (_step.value) {
            LoginStep.ID -> authenticate(sanitiseWorkId(workId), null)
            LoginStep.PASSWORD -> authenticate(pendingId, password)
            LoginStep.SET_PASSWORD -> setNewPassword(password)
        }
    }

    private fun authenticate(id: String, password: String?) {
        if (id.isBlank()) return
        pendingId = id
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            try {
                val response = RetrofitClient.instance.login(
                    LoginRequest(email = "$id$DOMAIN", password = password?.ifBlank { null }),
                )
                when {
                    response.code == "PASSWORD_REQUIRED" -> {
                        _step.value = LoginStep.PASSWORD
                        _loginState.value = LoginState.Idle
                    }
                    response.success == true -> {
                        val sid = response.session_id ?: ""
                        val email = response.email ?: "$id$DOMAIN"
                        val role = response.role ?: "manager"
                        val mustChange = response.must_change == true
                        com.example.skillsync.data.SessionManager.saveSession(email, sid, role, mustChange)
                        if (mustChange) {
                            _step.value = LoginStep.SET_PASSWORD
                            _loginState.value = LoginState.Idle
                        } else {
                            _loginState.value = LoginState.Success(sid, email)
                        }
                    }
                    else -> _loginState.value =
                        LoginState.Error(response.error ?: response.message ?: "Sign-in failed")
                }
            } catch (e: HttpException) {
                val msg = when (e.code()) {
                    401 -> if (_step.value == LoginStep.PASSWORD)
                        "Incorrect password — first-time sign-in uses your employee code"
                    else "Access denied — use your @koenig-solutions.com work ID"
                    503 -> "RMS service unavailable — please retry in a moment"
                    400 -> "Check your work ID and try again"
                    else -> e.userMessage("sign in")
                }
                _loginState.value = LoginState.Error(msg)
            } catch (e: Exception) {
                _loginState.value = LoginState.Error(e.userMessage("sign in"))
            }
        }
    }

    private fun setNewPassword(newPassword: String) {
        if (newPassword.length < 6) {
            _loginState.value = LoginState.Error("Choose a password of at least 6 characters")
            return
        }
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            try {
                RetrofitClient.instance.setPassword(SetPasswordRequest(newPassword))
                com.example.skillsync.data.SessionManager.clearMustChange()
                val email = com.example.skillsync.data.SessionManager.getEmail() ?: "$pendingId$DOMAIN"
                val sid = com.example.skillsync.data.SessionManager.getSessionId() ?: ""
                _loginState.value = LoginState.Success(sid, email)
            } catch (e: Exception) {
                _loginState.value = LoginState.Error(e.userMessage("set password"))
            }
        }
    }
}
