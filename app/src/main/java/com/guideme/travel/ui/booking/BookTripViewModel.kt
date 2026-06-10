package com.guideme.travel.ui.booking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.guideme.travel.domain.model.TourPackageDetail
import com.guideme.travel.domain.model.TripOffering
import com.guideme.travel.domain.usecase.ObserveDefaultLanguageUseCase
import com.guideme.travel.domain.usecase.ObserveTourPackageDetailUseCase
import com.guideme.travel.domain.usecase.StartTripFromPackageUseCase
import com.guideme.travel.ui.navigation.BookTripRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookTripUiState(
    val detail: TourPackageDetail? = null,
    val offering: TripOffering? = null,
    val isLoading: Boolean = true,
    val isBooking: Boolean = false,
    val errorMessage: String? = null,
    val estimatedPackSizeMb: Int = 0
)

@HiltViewModel
class BookTripViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeTourPackageDetailUseCase: ObserveTourPackageDetailUseCase,
    private val startTripFromPackageUseCase: StartTripFromPackageUseCase,
    private val observeDefaultLanguageUseCase: ObserveDefaultLanguageUseCase
) : ViewModel() {

    private val route = savedStateHandle.toRoute<BookTripRoute>()
    private val _uiState = MutableStateFlow(BookTripUiState())
    val uiState: StateFlow<BookTripUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeTourPackageDetailUseCase(route.packageId).collect { detail ->
                if (detail != null) {
                    val packSize = (detail.spots.size * 2.5 + 45).toInt()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            detail = detail,
                            offering = TripOffering(packageId = detail.id),
                            estimatedPackSizeMb = packSize
                        )
                    }
                }
            }
        }
    }

    fun bookForDownload(onSuccess: (String) -> Unit) {
        bookTrip(preferLocal = true, onSuccess)
    }

    fun bookForOnline(onSuccess: (String) -> Unit) {
        bookTrip(preferLocal = true, onSuccess)
    }

    private fun bookTrip(preferLocal: Boolean, onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBooking = true, errorMessage = null) }
            val language = observeDefaultLanguageUseCase().first()
            runCatching {
                startTripFromPackageUseCase(
                    packageId = route.packageId,
                    countryCode = route.countryCode,
                    genreId = route.genreId,
                    origin = "Current location",
                    languageCode = language,
                    preferLocal = preferLocal
                )
            }.onSuccess { trip ->
                _uiState.update { it.copy(isBooking = false) }
                onSuccess(trip.id)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isBooking = false,
                        errorMessage = error.message ?: "Failed to book trip"
                    )
                }
            }
        }
    }
}
