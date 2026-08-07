package com.example.skillsync.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skillsync.data.api.LoginRequest
import com.example.skillsync.data.api.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class Success(val sessionId: String, val email: String) : LoginState()
    data class Error(val message: String) : LoginState()
}

class LoginViewModel : ViewModel() {
    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState

    fun login(email: String) {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            try {
                val response = RetrofitClient.instance.login(LoginRequest(email = email.trim().lowercase()))
                if (response.success == true) {
                    val finalSessionId = response.session_id ?: ""
                    val finalEmail = response.email ?: email.trim().lowercase()
                    com.example.skillsync.data.SessionManager.saveSession(finalEmail, finalSessionId)
                    _loginState.value = LoginState.Success(
                        sessionId = finalSessionId,
                        email     = finalEmail,
                    )
                } else {
                    _loginState.value = LoginState.Error(
                        response.error ?: response.message ?: "Login failed"
                    )
                }
            } catch (e: HttpException) {
                val msg = when (e.code()) {
                    401  -> "Access denied — must be a @koenig-solutions.com manager or Trainer Plus"
                    503  -> "RMS service unavailable — please retry in a moment"
                    400  -> "Invalid email address"
                    else -> "Login failed (${e.code()})"
                }
                _loginState.value = LoginState.Error(msg)
            } catch (e: Exception) {
                _loginState.value = LoginState.Error(e.localizedMessage ?: "Network error — check your connection")
            }
        }
    }
}
