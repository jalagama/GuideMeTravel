package com.guideme.travel.domain.repository

import com.guideme.travel.domain.model.AuthUser
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val authState: Flow<AuthUser?>
    suspend fun ensureSignedIn(): AuthUser
    suspend fun signInAnonymously(): AuthUser
    suspend fun signInWithGoogle(idToken: String): AuthUser
    suspend fun sendSignInLink(email: String)
    suspend fun completeSignInFromLink(email: String, link: String): AuthUser
    suspend fun linkAnonymousWithGoogle(idToken: String): AuthUser
    suspend fun signOut()
    suspend fun getIdToken(forceRefresh: Boolean = false): String
    fun isSignInWithEmailLink(link: String): Boolean
}
