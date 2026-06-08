package com.guideme.travel.domain.model

data class AuthUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val isAnonymous: Boolean
)

data class UserProfile(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val languageCode: String,
    val createdAtMillis: Long
)

data class SupportedLanguage(
    val code: String,
    val displayName: String
)

data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val bearing: Float = 0f
)

data class AppLaunchState(
    val onboardingComplete: Boolean,
    val isAuthenticated: Boolean
)

data class OfflineCleanupResult(
    val freedBytes: Long,
    val tripId: String
)
