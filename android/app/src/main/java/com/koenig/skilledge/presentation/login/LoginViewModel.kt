package com.koenig.skilledge.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.koenig.skilledge.data.api.SkillEdgeApiService
import com.koenig.skilledge.data.api.LoginRequest
import com.koenig.skilledge.domain.models.SessionData
import com.koenig.skilledge.domain.models.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val apiService: SkillEdgeApiService
) : ViewModel() {

    private val _loginState = MutableStateFlow<UiState<SessionData>>(UiState.Empty)
    val loginState: StateFlow<UiState<SessionData>> = _loginState

    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    fun setEmail(email: String) {
        _email.value = email
        _errorMessage.value = null
    }

    fun login() {
        val emailValue = _email.value.trim()

        // Validate email
        if (emailValue.isEmpty()) {
            _errorMessage.value = "Please enter your email address"
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(emailValue).matches()) {
            _errorMessage.value = "Please enter a valid email address"
            return
        }

        viewModelScope.launch {
            try {
                _isLoading.value = true
                _loginState.value = UiState.Loading

                val response = apiService.login(LoginRequest(email = emailValue))

                if (response.isSuccessful && response.body() != null) {
                    val loginResponse = response.body()!!
                    val session = SessionData(
                        email = loginResponse.email,
                        name = null,
                        issuedAt = System.currentTimeMillis(),
                        expiresAt = System.currentTimeMillis() + (24 * 60 * 60 * 1000) // 24 hours
                    )

                    _loginState.value = UiState.Success(session)
                    Timber.d("Login successful for ${session.email}")
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    val errorMsg = "Login failed: ${response.code()} - $errorBody"
                    _loginState.value = UiState.Error(errorMsg)
                    _errorMessage.value = "Unable to login. Please check your email and try again."
                    Timber.e("Login error: $errorMsg")
                }
            } catch (e: Exception) {
                Timber.e(e, "Login exception")
                _loginState.value = UiState.Error(e.message ?: "Network error")
                _errorMessage.value = e.message ?: "Network error. Please try again."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
