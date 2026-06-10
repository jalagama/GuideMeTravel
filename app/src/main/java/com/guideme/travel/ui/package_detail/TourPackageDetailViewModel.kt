package com.guideme.travel.ui.package_detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.guideme.travel.domain.model.TourPackageDetail
import com.guideme.travel.domain.usecase.GetTourPackageDetailUseCase
import com.guideme.travel.domain.usecase.ObserveTourPackageDetailUseCase
import com.guideme.travel.ui.navigation.TourPackageDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TourPackageDetailUiState(
    val detail: TourPackageDetail? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val playingSpotId: String? = null
)

@HiltViewModel
class TourPackageDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeTourPackageDetailUseCase: ObserveTourPackageDetailUseCase,
    private val getTourPackageDetailUseCase: GetTourPackageDetailUseCase
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

            launch {
                observeTourPackageDetailUseCase(route.packageId).collect { cached ->
                    if (cached != null) {
                        _uiState.update { it.copy(isLoading = false, detail = cached) }
                    }
                }
            }

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
                    val hasCache = _uiState.value.detail != null
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = if (hasCache) null else error.message ?: "Failed to load tour details"
                        )
                    }
                }
        }
    }

    fun setPlayingSpot(spotId: String?) {
        _uiState.update { it.copy(playingSpotId = spotId) }
    }
}
