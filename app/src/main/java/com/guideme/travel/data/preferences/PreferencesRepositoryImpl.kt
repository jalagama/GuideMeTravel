package com.guideme.travel.data.preferences

import com.guideme.travel.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : PreferencesRepository {

    override val onboardingComplete: Flow<Boolean> = userPreferencesRepository.onboardingComplete
    override val locationConsentGranted: Flow<Boolean> = userPreferencesRepository.locationConsentGranted
    override val privacyConsentGranted: Flow<Boolean> = userPreferencesRepository.privacyConsentGranted
    override val useFirebaseBackend: Flow<Boolean> = userPreferencesRepository.useFirebaseBackend
    override val defaultLanguageCode: Flow<String> = userPreferencesRepository.defaultLanguageCode
    override val wifiOnlyDownloads: Flow<Boolean> = userPreferencesRepository.wifiOnlyDownloads

    override suspend fun setOnboardingComplete(value: Boolean) {
        userPreferencesRepository.setOnboardingComplete(value)
    }

    override suspend fun setLocationConsent(value: Boolean) {
        userPreferencesRepository.setLocationConsent(value)
    }

    override suspend fun setPrivacyConsent(value: Boolean) {
        userPreferencesRepository.setPrivacyConsent(value)
    }

    override suspend fun setUseFirebaseBackend(value: Boolean) {
        userPreferencesRepository.setUseFirebaseBackend(value)
    }

    override suspend fun setDefaultLanguageCode(value: String) {
        userPreferencesRepository.setDefaultLanguageCode(value)
    }

    override suspend fun setWifiOnlyDownloads(value: Boolean) {
        userPreferencesRepository.setWifiOnlyDownloads(value)
    }
}
