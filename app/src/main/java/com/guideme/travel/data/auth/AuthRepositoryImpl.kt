package com.guideme.travel.data.auth

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.guideme.travel.domain.model.AuthUser
import com.guideme.travel.domain.repository.AuthRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) : AuthRepository {

    override val authState: Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.toAuthUser())
        }
        firebaseAuth.addAuthStateListener(listener)
        trySend(firebaseAuth.currentUser?.toAuthUser())
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    override suspend fun ensureSignedIn(): AuthUser {
        firebaseAuth.currentUser?.let { return it.toAuthUser() }
        return signInAnonymously()
    }

    override suspend fun signInAnonymously(): AuthUser {
        val user = firebaseAuth.signInAnonymously().await().user
            ?: error("Unable to sign in anonymously")
        return user.toAuthUser()
    }

    override suspend fun signInWithGoogle(idToken: String): AuthUser {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val user = firebaseAuth.signInWithCredential(credential).await().user
            ?: error("Google sign-in failed")
        return user.toAuthUser()
    }

    override suspend fun signInWithEmail(email: String, password: String): AuthUser {
        val user = firebaseAuth.signInWithEmailAndPassword(email, password).await().user
            ?: error("Email sign-in failed")
        return user.toAuthUser()
    }

    override suspend fun signUpWithEmail(email: String, password: String): AuthUser {
        val user = firebaseAuth.createUserWithEmailAndPassword(email, password).await().user
            ?: error("Email sign-up failed")
        return user.toAuthUser()
    }

    override suspend fun linkAnonymousWithGoogle(idToken: String): AuthUser {
        val current = firebaseAuth.currentUser ?: return signInWithGoogle(idToken)
        if (!current.isAnonymous) return current.toAuthUser()
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val user = current.linkWithCredential(credential).await().user
            ?: error("Failed to link Google account")
        return user.toAuthUser()
    }

    override suspend fun linkAnonymousWithEmail(email: String, password: String): AuthUser {
        val current = firebaseAuth.currentUser
        if (current == null || !current.isAnonymous) {
            return runCatching { signInWithEmail(email, password) }
                .getOrElse { signUpWithEmail(email, password) }
        }
        val credential = EmailAuthProvider.getCredential(email, password)
        val user = runCatching {
            current.linkWithCredential(credential).await().user
        }.getOrElse {
            signUpWithEmail(email, password).let { firebaseAuth.currentUser }
        } ?: error("Failed to link email account")
        return user.toAuthUser()
    }

    override suspend fun signOut() {
        firebaseAuth.signOut()
    }

    override suspend fun getIdToken(): String {
        val user = firebaseAuth.currentUser ?: error("Not signed in")
        return user.getIdToken(false).await().token ?: error("Unable to fetch ID token")
    }

    private fun com.google.firebase.auth.FirebaseUser.toAuthUser(): AuthUser {
        return AuthUser(
            uid = uid,
            email = email,
            displayName = displayName,
            isAnonymous = isAnonymous
        )
    }
}
