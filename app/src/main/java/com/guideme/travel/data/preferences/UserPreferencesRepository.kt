package com.guideme.travel.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "guideme_preferences"
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val onboardingCompleteKey = booleanPreferencesKey("onboarding_complete")
    private val locationConsentKey = booleanPreferencesKey("location_consent")
    private val privacyConsentKey = booleanPreferencesKey("privacy_consent")
    private val useFirebaseBackendKey = booleanPreferencesKey("use_firebase_backend")
    private val defaultLanguageCodeKey = androidx.datastore.preferences.core.stringPreferencesKey("default_language_code")
    private val wifiOnlyDownloadsKey = booleanPreferencesKey("wifi_only_downloads")
    private val pendingSignInEmailKey = androidx.datastore.preferences.core.stringPreferencesKey("pending_sign_in_email")
    private val countryCodeKey = androidx.datastore.preferences.core.stringPreferencesKey("country_code")

    val onboardingComplete: Flow<Boolean> = context.userPreferencesDataStore.data.map {
        it[onboardingCompleteKey] ?: false
    }

    val locationConsentGranted: Flow<Boolean> = context.userPreferencesDataStore.data.map {
        it[locationConsentKey] ?: false
    }

    val privacyConsentGranted: Flow<Boolean> = context.userPreferencesDataStore.data.map {
        it[privacyConsentKey] ?: false
    }

    val useFirebaseBackend: Flow<Boolean> = context.userPreferencesDataStore.data.map {
        it[useFirebaseBackendKey] ?: true
    }

    val defaultLanguageCode: Flow<String> = context.userPreferencesDataStore.data.map {
        it[defaultLanguageCodeKey] ?: "en"
    }

    val wifiOnlyDownloads: Flow<Boolean> = context.userPreferencesDataStore.data.map {
        it[wifiOnlyDownloadsKey] ?: false
    }

    val pendingSignInEmail: Flow<String?> = context.userPreferencesDataStore.data.map {
        it[pendingSignInEmailKey]
    }

    val countryCode: Flow<String?> = context.userPreferencesDataStore.data.map {
        it[countryCodeKey]
    }

    suspend fun setOnboardingComplete(value: Boolean) {
        context.userPreferencesDataStore.edit { it[onboardingCompleteKey] = value }
    }

    suspend fun setLocationConsent(value: Boolean) {
        context.userPreferencesDataStore.edit { it[locationConsentKey] = value }
    }

    suspend fun setPrivacyConsent(value: Boolean) {
        context.userPreferencesDataStore.edit { it[privacyConsentKey] = value }
    }

    suspend fun setUseFirebaseBackend(value: Boolean) {
        context.userPreferencesDataStore.edit { it[useFirebaseBackendKey] = value }
    }

    suspend fun setDefaultLanguageCode(value: String) {
        context.userPreferencesDataStore.edit { it[defaultLanguageCodeKey] = value }
    }

    suspend fun setWifiOnlyDownloads(value: Boolean) {
        context.userPreferencesDataStore.edit { it[wifiOnlyDownloadsKey] = value }
    }

    suspend fun setPendingSignInEmail(value: String?) {
        context.userPreferencesDataStore.edit {
            if (value == null) {
                it.remove(pendingSignInEmailKey)
            } else {
                it[pendingSignInEmailKey] = value
            }
        }
    }

    suspend fun setCountryCode(value: String?) {
        context.userPreferencesDataStore.edit {
            if (value == null) {
                it.remove(countryCodeKey)
            } else {
                it[countryCodeKey] = value
            }
        }
    }
}
