package com.guideme.travel.ui.package_detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.guideme.travel.domain.model.TourPackageDetail
import com.guideme.travel.domain.usecase.GetTourPackageDetailUseCase
import com.guideme.travel.domain.usecase.ObserveDefaultLanguageUseCase
import com.guideme.travel.domain.usecase.StartTripFromPackageUseCase
import com.guideme.travel.ui.navigation.TourPackageDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TourPackageDetailUiState(
    val detail: TourPackageDetail? = null,
    val isLoading: Boolean = true,
    val isStartingTrip: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class TourPackageDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getTourPackageDetailUseCase: GetTourPackageDetailUseCase,
    private val startTripFromPackageUseCase: StartTripFromPackageUseCase,
    private val observeDefaultLanguageUseCase: ObserveDefaultLanguageUseCase
) : ViewModel() {

    private val route = savedStateHandle.toRoute<TourPackageDetailRoute>()
    private val _uiState = MutableStateFlow(TourPackageDetailUiState())
    val uiState: StateFlow<TourPackageDetailUiState> = _uiState.asStateFlow()

    init {
        loadDetail()
    }

    private fun loadDetail() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                getTourPackageDetailUseCase(
                    packageId = route.packageId,
                    countryCode = route.countryCode,
                    genreId = route.genreId
                )
            }
                .onSuccess { detail ->
                    _uiState.update { it.copy(isLoading = false, detail = detail) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Failed to load tour details"
                        )
                    }
                }
        }
    }

    fun startTrip(onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isStartingTrip = true, errorMessage = null) }
            val language = observeDefaultLanguageUseCase().first()
            runCatching {
                startTripFromPackageUseCase(
                    packageId = route.packageId,
                    countryCode = route.countryCode,
                    genreId = route.genreId,
                    origin = "Current location",
                    languageCode = language
                )
            }.onSuccess { trip ->
                _uiState.update { it.copy(isStartingTrip = false) }
                onSuccess(trip.id)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isStartingTrip = false,
                        errorMessage = error.message ?: "Failed to start trip"
                    )
                }
            }
        }
    }
}
