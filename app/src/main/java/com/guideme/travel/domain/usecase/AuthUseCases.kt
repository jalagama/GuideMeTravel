package com.guideme.travel.domain.usecase

import com.guideme.travel.domain.model.AuthUser
import com.guideme.travel.domain.model.UserProfile
import com.guideme.travel.domain.repository.AuthRepository
import com.guideme.travel.domain.repository.PreferencesRepository
import com.guideme.travel.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
    private val userRepository: UserRepository,
    private val preferencesRepository: PreferencesRepository,
    private val getUserCountryUseCase: GetUserCountryUseCase
) {
    suspend operator fun invoke(languageCode: String): AuthUser {
        val user = authRepository.signInAnonymously()
        val countryCode = getUserCountryUseCase()
        preferencesRepository.setCountryCode(countryCode)
        userRepository.upsertProfile(
            UserProfile(
                uid = user.uid,
                displayName = user.displayName,
                email = user.email,
                languageCode = languageCode,
                createdAtMillis = System.currentTimeMillis(),
                countryCode = countryCode
            )
        )
        return user
    }
}

class SignInWithGoogleUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val preferencesRepository: PreferencesRepository,
    private val getUserCountryUseCase: GetUserCountryUseCase
) {
    suspend operator fun invoke(idToken: String, languageCode: String): AuthUser {
        val user = runCatching { authRepository.linkAnonymousWithGoogle(idToken) }
            .getOrElse { authRepository.signInWithGoogle(idToken) }
        val countryCode = preferencesRepository.countryCode.first()
            ?: getUserCountryUseCase().also { preferencesRepository.setCountryCode(it) }
        userRepository.upsertProfile(
            UserProfile(
                uid = user.uid,
                displayName = user.displayName,
                email = user.email,
                languageCode = languageCode,
                createdAtMillis = System.currentTimeMillis(),
                countryCode = countryCode
            )
        )
        userRepository.syncTripsFromCloud(user.uid)
        return user
    }
}

class SendSignInLinkUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val preferencesRepository: PreferencesRepository
) {
    suspend operator fun invoke(email: String) {
        val trimmed = email.trim()
        require(trimmed.contains("@")) { "Enter a valid email address" }
        authRepository.sendSignInLink(trimmed)
        preferencesRepository.setPendingSignInEmail(trimmed)
    }
}

class CompleteSignInFromLinkUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val preferencesRepository: PreferencesRepository,
    private val getUserCountryUseCase: GetUserCountryUseCase
) {
    suspend operator fun invoke(email: String, link: String, languageCode: String): AuthUser {
        val user = authRepository.completeSignInFromLink(email.trim(), link)
        preferencesRepository.setPendingSignInEmail(null)
        val countryCode = preferencesRepository.countryCode.first()
            ?: getUserCountryUseCase().also { preferencesRepository.setCountryCode(it) }
        userRepository.upsertProfile(
            UserProfile(
                uid = user.uid,
                displayName = user.displayName,
                email = user.email ?: email.trim(),
                languageCode = languageCode,
                createdAtMillis = System.currentTimeMillis(),
                countryCode = countryCode
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
