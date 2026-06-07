package com.guideme.travel.domain.model

enum class TripStatus {
    DRAFT,
    READY,
    DOWNLOADING,
    ACTIVE,
    COMPLETED
}

enum class AttractionStatus {
    PENDING,
    VISITED,
    SKIPPED
}

data class Attraction(
    val id: String,
    val name: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val imageUrl: String? = null,
    val orderIndex: Int,
    val status: AttractionStatus = AttractionStatus.PENDING,
    val audioLocalPath: String? = null,
    val transcript: String? = null,
    val estimatedMinutes: Int = 45
)

data class TripPlan(
    val id: String,
    val origin: String,
    val destination: String,
    val languageCode: String,
    val status: TripStatus,
    val attractions: List<Attraction>,
    val createdAtMillis: Long,
    val offlinePackSizeMb: Int = 0,
    val offlinePackDownloaded: Boolean = false
)

data class OfflinePackProgress(
    val tripId: String,
    val totalSteps: Int,
    val completedSteps: Int,
    val currentTask: String,
    val estimatedSizeMb: Int
) {
    val progressFraction: Float
        get() = if (totalSteps == 0) 0f else completedSteps.toFloat() / totalSteps
}

data class GuidePlaybackState(
    val attractionId: String?,
    val attractionName: String?,
    val isPlaying: Boolean,
    val transcript: String?
)
