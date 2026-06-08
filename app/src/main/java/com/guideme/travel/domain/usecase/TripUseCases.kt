package com.guideme.travel.domain.usecase

import com.guideme.travel.domain.model.Attraction
import com.guideme.travel.domain.model.AttractionStatus
import com.guideme.travel.domain.model.OfflinePackProgress
import com.guideme.travel.domain.model.TripPlan
import com.guideme.travel.domain.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ObserveTripsUseCase @Inject constructor(
    private val tripRepository: TripRepository
) {
    operator fun invoke(): Flow<List<TripPlan>> = tripRepository.observeTrips()
}

class ObserveTripUseCase @Inject constructor(
    private val tripRepository: TripRepository
) {
    operator fun invoke(tripId: String): Flow<TripPlan?> = tripRepository.observeTrip(tripId)
}

class CreateTripUseCase @Inject constructor(
    private val tripRepository: TripRepository
) {
    suspend operator fun invoke(origin: String, destination: String, languageCode: String): TripPlan {
        return tripRepository.createTrip(origin, destination, languageCode)
    }
}

class DownloadOfflinePackUseCase @Inject constructor(
    private val tripRepository: TripRepository
) {
    suspend operator fun invoke(
        tripId: String,
        onProgress: (OfflinePackProgress) -> Unit
    ): TripPlan = tripRepository.downloadOfflinePack(tripId, onProgress)
}

class StartTripUseCase @Inject constructor(
    private val tripRepository: TripRepository
) {
    suspend operator fun invoke(tripId: String) = tripRepository.startTrip(tripId)
}

class CompleteTripUseCase @Inject constructor(
    private val tripRepository: TripRepository
) {
    suspend operator fun invoke(tripId: String) = tripRepository.completeTrip(tripId)
}

class DeleteOfflineDataUseCase @Inject constructor(
    private val tripRepository: TripRepository
) {
    suspend operator fun invoke(tripId: String): Long = tripRepository.deleteOfflineData(tripId)
}

class DeleteTripUseCase @Inject constructor(
    private val tripRepository: TripRepository
) {
    suspend operator fun invoke(tripId: String): Long = tripRepository.deleteTrip(tripId)
}

class MarkAttractionVisitedUseCase @Inject constructor(
    private val tripRepository: TripRepository
) {
    suspend operator fun invoke(tripId: String, attractionId: String) {
        tripRepository.markAttractionVisited(tripId, attractionId)
    }
}

class GetNextAttractionUseCase @Inject constructor(
    private val tripRepository: TripRepository
) {
    suspend operator fun invoke(tripId: String): Attraction? {
        val trip = tripRepository.observeTrip(tripId).first()
        return trip?.attractions
            ?.sortedBy { it.orderIndex }
            ?.firstOrNull { it.status != AttractionStatus.VISITED }
    }
}

class IsTripCompleteUseCase @Inject constructor(
    private val tripRepository: TripRepository
) {
    suspend operator fun invoke(tripId: String): Boolean {
        val trip = tripRepository.observeTrip(tripId).first() ?: return false
        return trip.attractions.isNotEmpty() &&
            trip.attractions.all { it.status == AttractionStatus.VISITED }
    }
}

class GetOfflinePackSizeUseCase @Inject constructor(
    private val tripRepository: TripRepository
) {
    suspend operator fun invoke(tripId: String): Long = tripRepository.getOfflinePackSizeBytes(tripId)
}

class HandleGeofenceEnterUseCase @Inject constructor(
    private val tripRepository: TripRepository,
    private val playGuideUseCase: PlayGuideUseCase,
    private val markAttractionVisitedUseCase: MarkAttractionVisitedUseCase
) {
    suspend operator fun invoke(tripId: String, attractionId: String, languageCode: String) {
        if (tripRepository.isAttractionAlreadyTriggered(tripId, attractionId)) return
        val trip = tripRepository.observeTrip(tripId).first() ?: return
        val attraction = trip.attractions.firstOrNull { it.id == attractionId } ?: return
        if (attraction.status == AttractionStatus.VISITED) return

        tripRepository.markAttractionTriggered(tripId, attractionId)
        playGuideUseCase(attraction, languageCode)
        markAttractionVisitedUseCase(tripId, attractionId)
    }
}
