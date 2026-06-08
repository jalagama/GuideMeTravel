package com.guideme.travel.domain.usecase

import com.guideme.travel.domain.model.AuthUser
import com.guideme.travel.domain.model.UserProfile
import com.guideme.travel.domain.repository.AuthRepository
import com.guideme.travel.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveAuthStateUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    operator fun invoke(): Flow<AuthUser?> = authRepository.authState
}

class EnsureSignedInUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): AuthUser = authRepository.ensureSignedIn()
}

class SignInAnonymouslyUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(languageCode: String): AuthUser {
        val user = authRepository.signInAnonymously()
        userRepository.upsertProfile(
            UserProfile(
                uid = user.uid,
                displayName = user.displayName,
                email = user.email,
                languageCode = languageCode,
                createdAtMillis = System.currentTimeMillis()
            )
        )
        return user
    }
}

class SignInWithGoogleUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(idToken: String, languageCode: String): AuthUser {
        val user = runCatching { authRepository.linkAnonymousWithGoogle(idToken) }
            .getOrElse { authRepository.signInWithGoogle(idToken) }
        userRepository.upsertProfile(
            UserProfile(
                uid = user.uid,
                displayName = user.displayName,
                email = user.email,
                languageCode = languageCode,
                createdAtMillis = System.currentTimeMillis()
            )
        )
        userRepository.syncTripsFromCloud(user.uid)
        return user
    }
}

class SignInWithEmailUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(email: String, password: String, languageCode: String): AuthUser {
        val user = runCatching { authRepository.linkAnonymousWithEmail(email, password) }
            .getOrElse { authRepository.signInWithEmail(email, password) }
        userRepository.upsertProfile(
            UserProfile(
                uid = user.uid,
                displayName = user.displayName,
                email = user.email,
                languageCode = languageCode,
                createdAtMillis = System.currentTimeMillis()
            )
        )
        userRepository.syncTripsFromCloud(user.uid)
        return user
    }
}

class SignUpWithEmailUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(email: String, password: String, languageCode: String): AuthUser {
        val user = runCatching { authRepository.linkAnonymousWithEmail(email, password) }
            .getOrElse { authRepository.signUpWithEmail(email, password) }
        userRepository.upsertProfile(
            UserProfile(
                uid = user.uid,
                displayName = user.displayName,
                email = user.email,
                languageCode = languageCode,
                createdAtMillis = System.currentTimeMillis()
            )
        )
        userRepository.syncTripsFromCloud(user.uid)
        return user
    }
}

class SignOutUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke() = authRepository.signOut()
}
