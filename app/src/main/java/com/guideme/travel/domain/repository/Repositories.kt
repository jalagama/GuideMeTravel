package com.guideme.travel.domain.repository

import com.guideme.travel.domain.model.Attraction
import com.guideme.travel.domain.model.OfflinePackProgress
import com.guideme.travel.domain.model.TripPlan
import kotlinx.coroutines.flow.Flow

interface TripRepository {
    fun observeTrips(): Flow<List<TripPlan>>
    fun observeTrip(tripId: String): Flow<TripPlan?>
    suspend fun createTrip(origin: String, destination: String, languageCode: String): TripPlan
    suspend fun downloadOfflinePack(tripId: String, onProgress: (OfflinePackProgress) -> Unit): TripPlan
    suspend fun startTrip(tripId: String)
    suspend fun completeTrip(tripId: String)
    suspend fun deleteOfflineData(tripId: String): Long
    suspend fun deleteTrip(tripId: String): Long
    suspend fun markAttractionVisited(tripId: String, attractionId: String)
    suspend fun getOfflinePackSizeBytes(tripId: String): Long
    fun isAttractionAlreadyTriggered(tripId: String, attractionId: String): Boolean
    fun markAttractionTriggered(tripId: String, attractionId: String)
}

interface GuideRepository {
    suspend fun playGuideForAttraction(attraction: Attraction, languageCode: String)
    suspend fun stopGuide()
    fun observePlaybackState(): Flow<com.guideme.travel.domain.model.GuidePlaybackState>
}
