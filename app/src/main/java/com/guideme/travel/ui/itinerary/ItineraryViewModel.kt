package com.guideme.travel.ui.itinerary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.guideme.travel.domain.model.TripPlan
import com.guideme.travel.domain.usecase.ObserveTripUseCase
import com.guideme.travel.domain.usecase.StartTripUseCase
import com.guideme.travel.ui.navigation.ItineraryRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ItineraryUiState(
    val trip: TripPlan? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class ItineraryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeTripUseCase: ObserveTripUseCase,
    private val startTripUseCase: StartTripUseCase
) : ViewModel() {

    private val tripId: String = savedStateHandle.toRoute<ItineraryRoute>().tripId

    val uiState: StateFlow<ItineraryUiState> = observeTripUseCase(tripId)
        .map { trip ->
            ItineraryUiState(trip = trip, isLoading = trip == null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ItineraryUiState()
        )

    fun startTrip(tripId: String) {
        viewModelScope.launch {
            startTripUseCase(tripId)
        }
    }
}
