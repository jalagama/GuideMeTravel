package com.guideme.travel.domain.repository

import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
    val onboardingComplete: Flow<Boolean>
    val locationConsentGranted: Flow<Boolean>
    val privacyConsentGranted: Flow<Boolean>
    val useFirebaseBackend: Flow<Boolean>
    val defaultLanguageCode: Flow<String>
    val wifiOnlyDownloads: Flow<Boolean>

    suspend fun setOnboardingComplete(value: Boolean)
    suspend fun setLocationConsent(value: Boolean)
    suspend fun setPrivacyConsent(value: Boolean)
    suspend fun setUseFirebaseBackend(value: Boolean)
    suspend fun setDefaultLanguageCode(value: String)
    suspend fun setWifiOnlyDownloads(value: Boolean)
}
