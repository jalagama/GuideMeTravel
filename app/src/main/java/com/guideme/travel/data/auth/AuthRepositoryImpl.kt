package com.guideme.travel.data.auth

import com.google.firebase.auth.ActionCodeSettings
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

    companion object {
        private const val EMAIL_LINK_CONTINUE_URL =
            "https://travelguide-47f80.firebaseapp.com/finishSignIn"
        private const val ANDROID_PACKAGE_NAME = "com.guideme.travel"
    }

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

    override suspend fun sendSignInLink(email: String) {
        val actionCodeSettings = ActionCodeSettings.newBuilder()
            .setUrl(EMAIL_LINK_CONTINUE_URL)
            .setHandleCodeInApp(true)
            .setAndroidPackageName(ANDROID_PACKAGE_NAME, true, null)
            .build()
        firebaseAuth.sendSignInLinkToEmail(email, actionCodeSettings).await()
    }

    override fun isSignInWithEmailLink(link: String): Boolean {
        return firebaseAuth.isSignInWithEmailLink(link)
    }

    override suspend fun completeSignInFromLink(email: String, link: String): AuthUser {
        if (!firebaseAuth.isSignInWithEmailLink(link)) {
            error("Invalid sign-in link")
        }

        val credential = EmailAuthProvider.getCredentialWithLink(email, link)
        val current = firebaseAuth.currentUser

        val user = if (current != null && current.isAnonymous) {
            runCatching {
                current.linkWithCredential(credential).await().user
            }.getOrElse {
                firebaseAuth.signInWithEmailLink(email, link).await().user
            }
        } else {
            firebaseAuth.signInWithEmailLink(email, link).await().user
        } ?: error("Email link sign-in failed")

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

    override suspend fun signOut() {
        firebaseAuth.signOut()
    }

    override suspend fun getIdToken(forceRefresh: Boolean): String {
        val user = firebaseAuth.currentUser ?: error("Not signed in")
        return user.getIdToken(forceRefresh).await().token ?: error("Unable to fetch ID token")
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
