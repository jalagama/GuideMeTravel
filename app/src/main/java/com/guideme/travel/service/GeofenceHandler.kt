package com.guideme.travel.service

import com.guideme.travel.domain.usecase.HandleGeofenceEnterUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.guideme.travel.domain.usecase.ObserveTripUseCase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeofenceHandler @Inject constructor(
    private val handleGeofenceEnterUseCase: HandleGeofenceEnterUseCase,
    private val observeTripUseCase: ObserveTripUseCase
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var activeTripId: String? = null

    fun setActiveTrip(tripId: String) {
        activeTripId = tripId
    }

    fun onEnterGeofence(attractionId: String) {
        val tripId = activeTripId ?: return
        scope.launch {
            val trip = observeTripUseCase(tripId).first() ?: return@launch
            handleGeofenceEnterUseCase(tripId, attractionId, trip.languageCode)
        }
    }
}
