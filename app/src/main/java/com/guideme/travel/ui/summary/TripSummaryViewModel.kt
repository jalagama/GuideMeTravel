package com.guideme.travel.ui.summary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.guideme.travel.domain.model.TripPlan
import com.guideme.travel.domain.repository.TripRepository
import com.guideme.travel.ui.navigation.TripSummaryRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TripSummaryUiState(
    val trip: TripPlan? = null,
    val offlineDeleted: Boolean = false
)

@HiltViewModel
class TripSummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository
) : ViewModel() {

    private val tripId: String = savedStateHandle.toRoute<TripSummaryRoute>().tripId
    private val offlineDeleted = kotlinx.coroutines.flow.MutableStateFlow(false)

    val uiState: StateFlow<TripSummaryUiState> = kotlinx.coroutines.flow.combine(
        tripRepository.observeTrip(tripId),
        offlineDeleted
    ) { trip, deleted ->
        TripSummaryUiState(trip = trip, offlineDeleted = deleted)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TripSummaryUiState()
    )

    fun deleteOfflineData(tripId: String) {
        viewModelScope.launch {
            tripRepository.deleteOfflineData(tripId)
            offlineDeleted.value = true
        }
    }
}
