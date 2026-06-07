package com.guideme.travel.ui.map

import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.guideme.travel.domain.model.TripPlan
import com.guideme.travel.domain.repository.TripRepository
import com.guideme.travel.service.TripGuideForegroundService
import com.guideme.travel.ui.navigation.TripMapRoute
import com.guideme.travel.util.LocationPermissionHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TripMapUiState(
    val trip: TripPlan? = null,
    val guideServiceRunning: Boolean = false
)

@HiltViewModel
class TripMapViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository
) : ViewModel() {

    private val tripId: String = savedStateHandle.toRoute<TripMapRoute>().tripId
    private val guideServiceStarted = MutableStateFlow(false)

    val uiState: StateFlow<TripMapUiState> = combine(
        tripRepository.observeTrip(tripId),
        guideServiceStarted
    ) { trip, serviceRunning ->
        TripMapUiState(trip = trip, guideServiceRunning = serviceRunning)
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TripMapUiState()
        )

    fun startGuideService(trip: TripPlan) {
        if (guideServiceStarted.value) return
        if (!LocationPermissionHelper.canStartLocationForegroundService(context)) return

        guideServiceStarted.value = true
        val intent = Intent(context, TripGuideForegroundService::class.java).apply {
            putExtra(TripGuideForegroundService.EXTRA_TRIP_ID, trip.id)
            putExtra(
                TripGuideForegroundService.EXTRA_ATTRACTION_IDS,
                trip.attractions.map { it.id }.toTypedArray()
            )
            putExtra(
                TripGuideForegroundService.EXTRA_ATTRACTION_NAMES,
                trip.attractions.map { it.name }.toTypedArray()
            )
            putExtra(
                TripGuideForegroundService.EXTRA_LATITUDES,
                trip.attractions.map { it.latitude }.toDoubleArray()
            )
            putExtra(
                TripGuideForegroundService.EXTRA_LONGITUDES,
                trip.attractions.map { it.longitude }.toDoubleArray()
            )
        }
        context.startForegroundService(intent)
    }

    fun completeTrip() {
        viewModelScope.launch {
            tripRepository.completeTrip(tripId)
            context.stopService(Intent(context, TripGuideForegroundService::class.java))
            guideServiceStarted.value = false
        }
    }
}
