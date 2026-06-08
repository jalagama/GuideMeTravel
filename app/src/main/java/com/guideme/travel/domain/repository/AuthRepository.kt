package com.guideme.travel.domain.repository

import com.guideme.travel.domain.model.AuthUser
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val authState: Flow<AuthUser?>
    suspend fun ensureSignedIn(): AuthUser
    suspend fun signInAnonymously(): AuthUser
    suspend fun signInWithGoogle(idToken: String): AuthUser
    suspend fun signInWithEmail(email: String, password: String): AuthUser
    suspend fun signUpWithEmail(email: String, password: String): AuthUser
    suspend fun linkAnonymousWithGoogle(idToken: String): AuthUser
    suspend fun linkAnonymousWithEmail(email: String, password: String): AuthUser
    suspend fun signOut()
    suspend fun getIdToken(): String
}
