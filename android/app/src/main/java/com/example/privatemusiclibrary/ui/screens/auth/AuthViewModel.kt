package com.example.privatemusiclibrary.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.privatemusiclibrary.data.auth.AuthTokenStore
import com.example.privatemusiclibrary.data.network.LoginRequest
import com.example.privatemusiclibrary.data.network.MixtapeApi
import com.example.privatemusiclibrary.data.network.RegisterRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthState {
    object Checking : AuthState()
    object Unauthenticated : AuthState()
    object Authenticated : AuthState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val api: MixtapeApi,
    private val tokenStore: AuthTokenStore
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Checking)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _uiError = MutableStateFlow<String?>(null)
    val uiError: StateFlow<String?> = _uiError.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _requireInvitation = MutableStateFlow(false)
    val requireInvitation: StateFlow<Boolean> = _requireInvitation.asStateFlow()

    init {
        checkSession()
    }

    fun checkSession() {
        val token = tokenStore.getAccessToken()
        if (token != null) {
            _authState.value = AuthState.Authenticated
        } else {
            _authState.value = AuthState.Unauthenticated
        }
    }

    private suspend fun performLogin(email: String, pass: String): Boolean {
        return try {
            val req = LoginRequest(email, pass, "Android", "Android")
            val res = api.login(req)
            if (res.isSuccessful && res.body() != null) {
                val tokens = res.body()!!
                tokenStore.save(tokens.accessToken, tokens.refreshToken)
                _authState.value = AuthState.Authenticated
                true
            } else if (res.code() >= 500) {
                _uiError.value = "Mixtape server is temporarily unavailable. Your local library still works."
                false
            } else {
                _uiError.value = "Invalid email or password"
                false
            }
        } catch (e: Exception) {
            _uiError.value = networkErrorMessage(e)
            false
        }
    }

    fun login(email: String, pass: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _uiError.value = null
            try {
                performLogin(email, pass)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun register(email: String, pass: String, name: String, inviteToken: String) {
        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiError.value = "Please enter a valid email address."
            return
        }
        if (pass.length < 6) {
            _uiError.value = "Password must be at least 6 characters."
            return
        }
        if (name.isBlank()) {
            _uiError.value = "Display name cannot be empty."
            return
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            _uiError.value = null
            try {
                val req = RegisterRequest(email, pass, name, inviteToken)
                val res = api.register(req)
                if (res.isSuccessful) {
                    val loginSuccess = performLogin(email, pass)
                    if (!loginSuccess && _uiError.value == null) {
                        _uiError.value = "Account created successfully, but automatic sign-in failed. Please sign in with your password."
                    }
                } else {
                    val errStr = res.errorBody()?.string() ?: ""
                    if (res.code() == 403) {
                        if (errStr.contains("invitation required")) {
                            _requireInvitation.value = true
                            _uiError.value = "An invitation code is required."
                        } else if (errStr.contains("invalid invitation")) {
                            _uiError.value = "The invitation code is invalid."
                        } else if (errStr.contains("invitation already used")) {
                            _uiError.value = "This invitation has already been used."
                        } else if (errStr.contains("invitation expired")) {
                            _uiError.value = "This invitation has expired."
                        } else {
                            _uiError.value = "Registration failed. Check your invite code."
                        }
                    } else if (res.code() == 409) {
                        _uiError.value = "Email is already registered."
                    } else if (res.code() >= 500) {
                        _uiError.value = "Mixtape server is temporarily unavailable. Please try again later."
                    } else {
                        _uiError.value = "Registration failed. Please check your details."
                    }
                }
            } catch (e: Exception) {
                _uiError.value = networkErrorMessage(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                api.logout(com.example.privatemusiclibrary.data.network.RefreshRequest(tokenStore.getRefreshToken() ?: ""))
            } catch (e: Exception) {
                // ignore
            }
            tokenStore.clear()
            _authState.value = AuthState.Unauthenticated
        }
    }

    fun clearError() {
        _uiError.value = null
    }

    fun setError(msg: String) {
        _uiError.value = msg
    }

    private fun networkErrorMessage(e: Exception): String = when (e) {
        is java.net.UnknownHostException -> "No internet connection. Sign-in and registration need internet."
        is java.net.SocketTimeoutException -> "The server took too long to respond. Please try again."
        is java.net.ConnectException -> "Couldn't reach the Mixtape server. Please try again."
        is javax.net.ssl.SSLException -> "Secure connection failed. Check your network and try again."
        is java.io.IOException -> "Network error. Please check your connection and try again."
        else -> "Something went wrong. Please try again."
    }
}
