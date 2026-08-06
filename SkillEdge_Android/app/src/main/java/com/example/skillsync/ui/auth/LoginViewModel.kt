package com.example.skillsync.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skillsync.data.api.LoginRequest
import com.example.skillsync.data.api.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class Success(val token: String) : LoginState()
    data class Error(val message: String) : LoginState()
}

class LoginViewModel : ViewModel() {
    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState

    fun login(email: String) {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            try {
                val request = LoginRequest(email = email)
                val response = RetrofitClient.instance.login(request)
                if (response.success == true) {
                    _loginState.value = LoginState.Success(response.session_id ?: "dummy-token")
                } else {
                    _loginState.value = LoginState.Error(response.error ?: response.message ?: "Login failed")
                }
            } catch (e: Exception) {
                _loginState.value = LoginState.Error(e.localizedMessage ?: "Unknown network error")
            }
        }
    }
}
