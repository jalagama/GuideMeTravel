package com.guideme.travel.domain.usecase

import com.guideme.travel.domain.model.AppLaunchState
import com.guideme.travel.domain.repository.AuthRepository
import com.guideme.travel.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ObserveOnboardingCompleteUseCase @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) {
    operator fun invoke(): Flow<Boolean> = preferencesRepository.onboardingComplete
}

class ObserveLocationConsentUseCase @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) {
    operator fun invoke(): Flow<Boolean> = preferencesRepository.locationConsentGranted
}

class ObservePrivacyConsentUseCase @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) {
    operator fun invoke(): Flow<Boolean> = preferencesRepository.privacyConsentGranted
}

class ObserveDefaultLanguageUseCase @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) {
    operator fun invoke(): Flow<String> = preferencesRepository.defaultLanguageCode
}

class ObserveWifiOnlyDownloadsUseCase @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) {
    operator fun invoke(): Flow<Boolean> = preferencesRepository.wifiOnlyDownloads
}

class SaveConsentUseCase @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) {
    suspend operator fun invoke(privacyGranted: Boolean, locationGranted: Boolean) {
        preferencesRepository.setPrivacyConsent(privacyGranted)
        preferencesRepository.setLocationConsent(locationGranted)
        preferencesRepository.setOnboardingComplete(true)
    }
}

class SetDefaultLanguageUseCase @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) {
    suspend operator fun invoke(languageCode: String) {
        preferencesRepository.setDefaultLanguageCode(languageCode)
    }
}

class SetWifiOnlyDownloadsUseCase @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) {
    suspend operator fun invoke(enabled: Boolean) {
        preferencesRepository.setWifiOnlyDownloads(enabled)
    }
}

class GetAppLaunchStateUseCase @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): AppLaunchState {
        val onboardingComplete = preferencesRepository.onboardingComplete.first()
        val user = authRepository.authState.first()
        return AppLaunchState(
            onboardingComplete = onboardingComplete,
            isAuthenticated = user != null
        )
    }
}

class ObserveAppLaunchStateUseCase @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val authRepository: AuthRepository
) {
    operator fun invoke(): Flow<AppLaunchState> = combine(
        preferencesRepository.onboardingComplete,
        authRepository.authState
    ) { onboardingComplete, user ->
        AppLaunchState(
            onboardingComplete = onboardingComplete,
            isAuthenticated = user != null
        )
    }
}
