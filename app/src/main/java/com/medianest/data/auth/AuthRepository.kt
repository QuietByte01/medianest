package com.medianest.data.auth

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.medianest.data.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

interface AuthRepository {
    val authState: StateFlow<AuthState>
    
    suspend fun loginWithEmail(email: String, password: String): AuthResult
    suspend fun registerWithEmail(email: String, password: String, displayName: String): AuthResult
    suspend fun sendPasswordReset(email: String): AuthResult
    suspend fun signInWithGoogleCredential(idToken: String): AuthResult
    suspend fun continueAsGuest()
    suspend fun logout()
    fun getCurrentUser(): User?
}

class FirebaseAuthRepositoryImpl(
    private val context: Context,
    private val settingsManager: SettingsManager
) : AuthRepository {

    private val TAG = "AuthRepository"
    private val scope = CoroutineScope(Dispatchers.IO)
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private var firebaseAuth: FirebaseAuth? = null

    init {
        try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                firebaseAuth = FirebaseAuth.getInstance()
                firebaseAuth?.addAuthStateListener { auth ->
                    val firebaseUser = auth.currentUser
                    if (firebaseUser != null) {
                        val user = User(
                            uid = firebaseUser.uid,
                            email = firebaseUser.email,
                            displayName = firebaseUser.displayName ?: firebaseUser.email?.substringBefore("@"),
                            photoUrl = firebaseUser.photoUrl?.toString(),
                            providerId = firebaseUser.providerId,
                            isEmailVerified = firebaseUser.isEmailVerified
                        )
                        _authState.value = AuthState.Authenticated(user)
                        scope.launch {
                            settingsManager.setOfflineMode(false)
                        }
                    } else {
                        if (_authState.value !is AuthState.Guest) {
                            _authState.value = AuthState.Unauthenticated
                        }
                    }
                }
            } else {
                Log.w(TAG, "FirebaseApp is not initialized yet (missing google-services.json?). Defaulting to Guest mode.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "FirebaseAuth not initialized: ${e.message}")
            _authState.value = AuthState.Unauthenticated
        }
    }

    override fun getCurrentUser(): User? {
        val state = _authState.value
        return if (state is AuthState.Authenticated) state.user else null
    }

    override suspend fun loginWithEmail(email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        val auth = firebaseAuth ?: return@withContext mockFallbackLogin(email)
        try {
            _authState.value = AuthState.Loading
            val authResult = auth.signInWithEmailAndPassword(email.trim(), password).await()
            val firebaseUser = authResult.user
            if (firebaseUser != null) {
                val user = User(
                    uid = firebaseUser.uid,
                    email = firebaseUser.email,
                    displayName = firebaseUser.displayName ?: email.substringBefore("@"),
                    photoUrl = firebaseUser.photoUrl?.toString(),
                    isEmailVerified = firebaseUser.isEmailVerified
                )
                _authState.value = AuthState.Authenticated(user)
                settingsManager.setOfflineMode(false)
                AuthResult.Success(user)
            } else {
                _authState.value = AuthState.Error("Sign in failed")
                AuthResult.Error("Sign in failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Email login error", e)
            val msg = e.localizedMessage ?: "Authentication failed"
            _authState.value = AuthState.Error(msg)
            AuthResult.Error(msg)
        }
    }

    override suspend fun registerWithEmail(
        email: String,
        password: String,
        displayName: String
    ): AuthResult = withContext(Dispatchers.IO) {
        val auth = firebaseAuth ?: return@withContext mockFallbackLogin(email, displayName)
        try {
            _authState.value = AuthState.Loading
            val authResult = auth.createUserWithEmailAndPassword(email.trim(), password).await()
            val firebaseUser = authResult.user
            if (firebaseUser != null) {
                if (displayName.isNotBlank()) {
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(displayName.trim())
                        .build()
                    firebaseUser.updateProfile(profileUpdates).await()
                }
                val user = User(
                    uid = firebaseUser.uid,
                    email = firebaseUser.email,
                    displayName = displayName.ifBlank { email.substringBefore("@") },
                    photoUrl = firebaseUser.photoUrl?.toString(),
                    isEmailVerified = firebaseUser.isEmailVerified
                )
                _authState.value = AuthState.Authenticated(user)
                settingsManager.setOfflineMode(false)
                AuthResult.Success(user)
            } else {
                _authState.value = AuthState.Error("Registration failed")
                AuthResult.Error("Registration failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Email register error", e)
            val msg = e.localizedMessage ?: "Registration failed"
            _authState.value = AuthState.Error(msg)
            AuthResult.Error(msg)
        }
    }

    override suspend fun sendPasswordReset(email: String): AuthResult = withContext(Dispatchers.IO) {
        val auth = firebaseAuth ?: return@withContext AuthResult.Success(User(uid = "guest", email = email))
        try {
            auth.sendPasswordResetEmail(email.trim()).await()
            AuthResult.Success(User(uid = "reset", email = email))
        } catch (e: Exception) {
            Log.e(TAG, "Password reset error", e)
            AuthResult.Error(e.localizedMessage ?: "Failed to send reset email")
        }
    }

    override suspend fun signInWithGoogleCredential(idToken: String): AuthResult = withContext(Dispatchers.IO) {
        val auth = firebaseAuth ?: return@withContext mockFallbackLogin("google_user@medianest.app", "Google User")
        try {
            _authState.value = AuthState.Loading
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            val firebaseUser = authResult.user
            if (firebaseUser != null) {
                val user = User(
                    uid = firebaseUser.uid,
                    email = firebaseUser.email,
                    displayName = firebaseUser.displayName ?: "Google User",
                    photoUrl = firebaseUser.photoUrl?.toString(),
                    providerId = "google.com",
                    isEmailVerified = firebaseUser.isEmailVerified
                )
                _authState.value = AuthState.Authenticated(user)
                settingsManager.setOfflineMode(false)
                AuthResult.Success(user)
            } else {
                _authState.value = AuthState.Error("Google sign-in failed")
                AuthResult.Error("Google sign-in failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google sign-in error", e)
            val msg = e.localizedMessage ?: "Google sign-in failed"
            _authState.value = AuthState.Error(msg)
            AuthResult.Error(msg)
        }
    }

    override suspend fun continueAsGuest() {
        _authState.value = AuthState.Guest
    }

    override suspend fun logout() {
        try {
            firebaseAuth?.signOut()
        } catch (e: Exception) {
            Log.w(TAG, "Error signing out: ${e.message}")
        }
        _authState.value = AuthState.Unauthenticated
    }

    private fun mockFallbackLogin(email: String, displayName: String? = null): AuthResult {
        val mockUser = User(
            uid = "local_user_${System.currentTimeMillis()}",
            email = email,
            displayName = displayName ?: email.substringBefore("@"),
            isEmailVerified = true
        )
        _authState.value = AuthState.Authenticated(mockUser)
        return AuthResult.Success(mockUser)
    }
}
