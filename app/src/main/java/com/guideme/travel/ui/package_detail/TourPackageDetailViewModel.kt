package com.guideme.travel.ui.package_detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.guideme.travel.domain.model.TourPackageDetail
import com.guideme.travel.domain.analytics.AnalyticsEvents
import com.guideme.travel.domain.analytics.AnalyticsParams
import com.guideme.travel.domain.logging.GuideMeLogger
import com.guideme.travel.domain.logging.LogTags
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
    private val getTourPackageDetailUseCase: GetTourPackageDetailUseCase,
    private val logger: GuideMeLogger
) : ViewModel() {

    private val route = savedStateHandle.toRoute<TourPackageDetailRoute>()
    private val _uiState = MutableStateFlow(TourPackageDetailUiState())
    val uiState: StateFlow<TourPackageDetailUiState> = _uiState.asStateFlow()

    init {
        logger.info(
            LogTags.PACKAGE_DETAIL_VM,
            AnalyticsEvents.PACKAGE_OPENED,
            mapOf(
                AnalyticsParams.PACKAGE_ID to route.packageId,
                AnalyticsParams.COUNTRY_CODE to route.countryCode,
                AnalyticsParams.GENRE_ID to route.genreId
            )
        )
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
                    logger.info(
                        LogTags.PACKAGE_DETAIL_VM,
                        AnalyticsEvents.PACKAGE_DETAIL_LOADED,
                        mapOf(
                            AnalyticsParams.PACKAGE_ID to route.packageId,
                            AnalyticsParams.COUNT to detail.spots.size
                        )
                    )
                    logger.info(
                        LogTags.PACKAGE_DETAIL_VM,
                        AnalyticsEvents.DETAIL_EXPLORED,
                        mapOf(AnalyticsParams.PACKAGE_ID to route.packageId)
                    )
                    _uiState.update { it.copy(isLoading = false, detail = detail) }
                }
                .onFailure { error ->
                    val hasCache = _uiState.value.detail != null
                    logger.warn(
                        LogTags.PACKAGE_DETAIL_VM,
                        "detail_load_failed",
                        mapOf("packageId" to route.packageId, "hasCache" to hasCache),
                        error
                    )
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
        if (spotId != null) {
            logger.info(
                LogTags.PACKAGE_DETAIL_VM,
                AnalyticsEvents.AUDIO_PREVIEW_PLAYED,
                mapOf(AnalyticsParams.PACKAGE_ID to route.packageId, AnalyticsParams.SPOT_ID to spotId)
            )
        }
        _uiState.update { it.copy(playingSpotId = spotId) }
    }
}
