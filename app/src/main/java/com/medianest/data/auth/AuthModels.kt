package com.medianest.data.auth

data class User(
    val uid: String,
    val email: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val providerId: String = "email",
    val isEmailVerified: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

sealed class AuthState {
    data object Unauthenticated : AuthState()
    data class Authenticated(val user: User) : AuthState()
    data object Guest : AuthState()
    data object Loading : AuthState()
    data class Error(val message: String) : AuthState()
}

sealed class SyncState {
    data object Idle : SyncState()
    data object Syncing : SyncState()
    data class Success(val lastSyncedAt: Long) : SyncState()
    data class Error(val reason: String) : SyncState()
}

sealed class AuthResult {
    data class Success(val user: User) : AuthResult()
    data class Error(val message: String) : AuthResult()
}
