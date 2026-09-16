package com.medianest.ui.auth

import android.content.Context
import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.medianest.MediaNestApp
import com.medianest.data.auth.*
import com.medianest.data.settings.SettingsManager
import com.medianest.data.sync.CloudSyncManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AuthMode {
    LOGIN,
    REGISTER,
    FORGOT_PASSWORD
}

class AuthViewModel(
    val authRepository: AuthRepository,
    val cloudSyncManager: CloudSyncManager,
    val settingsManager: SettingsManager
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState
    val syncState: StateFlow<SyncState> = cloudSyncManager.syncState

    var authMode by mutableStateOf(AuthMode.LOGIN)
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var displayName by mutableStateOf("")
    var isPasswordVisible by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var successMessage by mutableStateOf<String?>(null)
    var isLoading by mutableStateOf(false)

    fun clearMessages() {
        errorMessage = null
        successMessage = null
    }

    fun submitEmailAuth() {
        clearMessages()
        val emailPattern = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$")
        if (email.isBlank() || !email.trim().matches(emailPattern)) {
            errorMessage = "Please enter a valid email address"
            return
        }

        when (authMode) {
            AuthMode.LOGIN -> {
                if (password.isBlank()) {
                    errorMessage = "Password cannot be empty"
                    return
                }
                viewModelScope.launch {
                    isLoading = true
                    val result = authRepository.loginWithEmail(email, password)
                    isLoading = false
                    when (result) {
                        is AuthResult.Success -> {
                            successMessage = "Logged in as ${result.user.email ?: result.user.displayName}"
                            cloudSyncManager.syncUserData(result.user)
                        }
                        is AuthResult.Error -> errorMessage = result.message
                    }
                }
            }
            AuthMode.REGISTER -> {
                if (password.length < 6) {
                    errorMessage = "Password must be at least 6 characters"
                    return
                }
                viewModelScope.launch {
                    isLoading = true
                    val result = authRepository.registerWithEmail(email, password, displayName)
                    isLoading = false
                    when (result) {
                        is AuthResult.Success -> {
                            successMessage = "Account created successfully!"
                            cloudSyncManager.syncUserData(result.user)
                            authMode = AuthMode.LOGIN
                        }
                        is AuthResult.Error -> errorMessage = result.message
                    }
                }
            }
            AuthMode.FORGOT_PASSWORD -> {
                viewModelScope.launch {
                    isLoading = true
                    val result = authRepository.sendPasswordReset(email)
                    isLoading = false
                    when (result) {
                        is AuthResult.Success -> successMessage = "Password reset link sent to your email"
                        is AuthResult.Error -> errorMessage = result.message
                    }
                }
            }
        }
    }

    fun handleGoogleSignIn(idToken: String) {
        clearMessages()
        viewModelScope.launch {
            isLoading = true
            val result = authRepository.signInWithGoogleCredential(idToken)
            isLoading = false
            when (result) {
                is AuthResult.Success -> {
                    successMessage = "Signed in with Google"
                    cloudSyncManager.syncUserData(result.user)
                }
                is AuthResult.Error -> errorMessage = result.message
            }
        }
    }

    fun continueAsGuest() {
        viewModelScope.launch {
            authRepository.continueAsGuest()
            settingsManager.setGuestMode(true)
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            settingsManager.setGuestMode(false)
            authMode = AuthMode.LOGIN
            email = ""
            password = ""
            displayName = ""
            clearMessages()
        }
    }

    fun triggerSync() {
        val user = authRepository.getCurrentUser()
        if (user != null) {
            viewModelScope.launch {
                cloudSyncManager.syncUserData(user)
            }
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val app = MediaNestApp.instance
            val settings = app.settingsManager
            val authRepo = FirebaseAuthRepositoryImpl(context, settings)
            val syncMgr = CloudSyncManager(context, settings)
            return AuthViewModel(authRepo, syncMgr, settings) as T
        }
    }
}
