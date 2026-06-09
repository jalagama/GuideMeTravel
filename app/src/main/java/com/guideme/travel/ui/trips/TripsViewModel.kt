package com.guideme.travel.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guideme.travel.domain.model.TripPlan
import com.guideme.travel.domain.usecase.ObserveTripsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class TripsUiState(
    val trips: List<TripPlan> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class TripsViewModel @Inject constructor(
    observeTripsUseCase: ObserveTripsUseCase
) : ViewModel() {

    val uiState: StateFlow<TripsUiState> = observeTripsUseCase()
        .map { trips ->
            TripsUiState(trips = trips, isLoading = false)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TripsUiState()
        )
}
