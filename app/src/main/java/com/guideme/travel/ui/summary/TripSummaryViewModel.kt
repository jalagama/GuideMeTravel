package com.guideme.travel.ui.summary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.guideme.travel.domain.model.TripPlan
import com.guideme.travel.domain.usecase.DeleteOfflineDataUseCase
import com.guideme.travel.domain.usecase.DeleteTripUseCase
import com.guideme.travel.domain.usecase.ObserveTripUseCase
import com.guideme.travel.ui.navigation.TripSummaryRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TripSummaryUiState(
    val trip: TripPlan? = null,
    val offlineDeleted: Boolean = false,
    val freedBytes: Long = 0L,
    val tripDeleted: Boolean = false
)

@HiltViewModel
class TripSummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeTripUseCase: ObserveTripUseCase,
    private val deleteOfflineDataUseCase: DeleteOfflineDataUseCase,
    private val deleteTripUseCase: DeleteTripUseCase
) : ViewModel() {

    private val tripId: String = savedStateHandle.toRoute<TripSummaryRoute>().tripId
    private val cleanupState = MutableStateFlow(CleanupState())

    val uiState: StateFlow<TripSummaryUiState> = combine(
        observeTripUseCase(tripId),
        cleanupState
    ) { trip, cleanup ->
        TripSummaryUiState(
            trip = trip,
            offlineDeleted = cleanup.offlineDeleted,
            freedBytes = cleanup.freedBytes,
            tripDeleted = cleanup.tripDeleted
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TripSummaryUiState()
    )

    fun deleteOfflineData(tripId: String) {
        viewModelScope.launch {
            val freed = deleteOfflineDataUseCase(tripId)
            cleanupState.value = cleanupState.value.copy(
                offlineDeleted = true,
                freedBytes = freed
            )
        }
    }

    fun deleteTrip(tripId: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            val freed = deleteTripUseCase(tripId)
            cleanupState.value = cleanupState.value.copy(
                tripDeleted = true,
                freedBytes = freed,
                offlineDeleted = true
            )
            onDeleted()
        }
    }

    private data class CleanupState(
        val offlineDeleted: Boolean = false,
        val freedBytes: Long = 0L,
        val tripDeleted: Boolean = false
    )
}
