package com.guideme.travel.service

import com.guideme.travel.domain.repository.GuideRepository
import com.guideme.travel.domain.repository.TripRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeofenceHandler @Inject constructor(
    private val tripRepository: TripRepository,
    private val guideRepository: GuideRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var activeTripId: String? = null

    fun setActiveTrip(tripId: String) {
        activeTripId = tripId
    }

    fun onEnterGeofence(attractionId: String) {
        val tripId = activeTripId ?: return
        scope.launch {
            val trip = tripRepository.observeTrip(tripId).first() ?: return@launch
            val attraction = trip.attractions.firstOrNull { it.id == attractionId } ?: return@launch
            guideRepository.playGuideForAttraction(attraction)
            tripRepository.markAttractionVisited(tripId, attractionId)
        }
    }
}
