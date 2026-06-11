package com.guideme.travel.ui.map

import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.guideme.travel.data.offline.MapLibreOfflineMapManager
import com.guideme.travel.data.offline.OfflineMapMetadata
import com.guideme.travel.domain.analytics.AnalyticsEvents
import com.guideme.travel.domain.analytics.AnalyticsParams
import com.guideme.travel.domain.analytics.GuideMeAnalytics
import com.guideme.travel.domain.model.AttractionStatus
import com.guideme.travel.domain.model.TripPlan
import com.guideme.travel.domain.model.UserLocation
import com.guideme.travel.domain.usecase.CompleteTripUseCase
import com.guideme.travel.domain.usecase.IsTripCompleteUseCase
import com.guideme.travel.domain.usecase.ObserveLocationUseCase
import com.guideme.travel.domain.usecase.ObserveTripUseCase
import com.guideme.travel.service.TripGuideForegroundService
import com.guideme.travel.ui.navigation.TripMapRoute
import com.guideme.travel.util.LocationPermissionHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TripMapUiState(
    val trip: TripPlan? = null,
    val guideServiceRunning: Boolean = false,
    val userLocation: UserLocation? = null,
    val followUser: Boolean = true,
    val mapStyleUrl: String? = null,
    val mapMetadata: OfflineMapMetadata? = null,
    val nextAttractionId: String? = null,
    val allSpotsVisited: Boolean = false
)

@HiltViewModel
class TripMapViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val observeTripUseCase: ObserveTripUseCase,
    private val observeLocationUseCase: ObserveLocationUseCase,
    private val completeTripUseCase: CompleteTripUseCase,
    private val isTripCompleteUseCase: IsTripCompleteUseCase,
    private val mapLibreOfflineMapManager: MapLibreOfflineMapManager,
    private val analytics: GuideMeAnalytics
) : ViewModel() {

    private val tripId: String = savedStateHandle.toRoute<TripMapRoute>().tripId
    private val guideServiceStarted = MutableStateFlow(false)
    private val followUser = MutableStateFlow(true)

    val uiState: StateFlow<TripMapUiState> = combine(
        observeTripUseCase(tripId),
        guideServiceStarted,
        observeLocationUseCase(),
        followUser
    ) { trip, serviceRunning, location, follow ->
        val nextAttraction = trip?.attractions
            ?.sortedBy { it.orderIndex }
            ?.firstOrNull { it.status != AttractionStatus.VISITED }
        TripMapUiState(
            trip = trip,
            guideServiceRunning = serviceRunning,
            userLocation = location,
            followUser = follow,
            mapStyleUrl = mapLibreOfflineMapManager.styleUrl(),
            mapMetadata = mapLibreOfflineMapManager.loadRegionMetadata(tripId),
            nextAttractionId = nextAttraction?.id,
            allSpotsVisited = trip?.attractions?.isNotEmpty() == true &&
                trip.attractions.all { it.status == AttractionStatus.VISITED }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TripMapUiState()
    )

    init {
        analytics.logEvent(AnalyticsEvents.TRIP_MAP_STARTED, mapOf(AnalyticsParams.TRIP_ID to tripId))
        viewModelScope.launch {
            observeTripUseCase(tripId).collect {
                if (isTripCompleteUseCase(tripId)) {
                    // UI can react via allSpotsVisited
                }
            }
        }
    }

    fun toggleFollowUser() {
        followUser.value = !followUser.value
        analytics.logEvent(
            "map_recenter_toggled",
            mapOf(AnalyticsParams.TRIP_ID to tripId, "follow_user" to followUser.value)
        )
    }

    fun startGuideService(trip: TripPlan) {
        if (guideServiceStarted.value) return
        if (!LocationPermissionHelper.canStartLocationForegroundService(context)) return

        guideServiceStarted.value = true
        analytics.logEvent(
            AnalyticsEvents.GUIDE_SERVICE_STARTED,
            mapOf(
                AnalyticsParams.TRIP_ID to trip.id,
                AnalyticsParams.COUNT to trip.attractions.size
            )
        )
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

    fun completeTrip(onComplete: () -> Unit) {
        viewModelScope.launch {
            completeTripUseCase(tripId)
            analytics.logEvent(AnalyticsEvents.TRIP_COMPLETED, mapOf(AnalyticsParams.TRIP_ID to tripId))
            context.stopService(Intent(context, TripGuideForegroundService::class.java))
            guideServiceStarted.value = false
            onComplete()
        }
    }
}
