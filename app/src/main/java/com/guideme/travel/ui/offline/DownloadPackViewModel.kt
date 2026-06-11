package com.guideme.travel.ui.offline

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.guideme.travel.domain.analytics.AnalyticsEvents
import com.guideme.travel.domain.analytics.AnalyticsParams
import com.guideme.travel.domain.analytics.GuideMeAnalytics
import com.guideme.travel.domain.model.OfflinePackProgress
import com.guideme.travel.domain.repository.DownloadWorkRepository
import com.guideme.travel.domain.usecase.GetOfflinePackSizeUseCase
import com.guideme.travel.domain.usecase.ObserveTripUseCase
import com.guideme.travel.domain.usecase.ObserveWifiOnlyDownloadsUseCase
import com.guideme.travel.domain.usecase.StartTripUseCase
import com.guideme.travel.ui.navigation.DownloadRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadPackUiState(
    val progress: OfflinePackProgress? = null,
    val isDownloading: Boolean = false,
    val isComplete: Boolean = false,
    val estimatedSizeMb: Int = 0,
    val errorMessage: String? = null
)

@HiltViewModel
class DownloadPackViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val downloadWorkRepository: DownloadWorkRepository,
    private val observeTripUseCase: ObserveTripUseCase,
    private val observeWifiOnlyDownloadsUseCase: ObserveWifiOnlyDownloadsUseCase,
    private val getOfflinePackSizeUseCase: GetOfflinePackSizeUseCase,
    private val startTripUseCase: StartTripUseCase,
    private val analytics: GuideMeAnalytics
) : ViewModel() {

    private val tripId: String = savedStateHandle.toRoute<DownloadRoute>().tripId
    private val localState = MutableStateFlow(DownloadPackUiState())
    private var downloadCompleteLogged = false
    private var downloadFailedLogged = false

    val uiState: StateFlow<DownloadPackUiState> = combine(
        localState,
        downloadWorkRepository.observeDownload(tripId),
        observeTripUseCase(tripId)
    ) { local, work, trip ->
        val estimated = trip?.offlinePackSizeMb ?: local.estimatedSizeMb
        if (work == null) {
            local.copy(estimatedSizeMb = estimated)
        } else {
            local.copy(
                isDownloading = work.isRunning,
                isComplete = work.isComplete,
                errorMessage = work.errorMessage,
                estimatedSizeMb = work.estimatedSizeMb.takeIf { it > 0 } ?: estimated,
                progress = OfflinePackProgress(
                    tripId = tripId,
                    totalSteps = work.totalSteps.takeIf { it > 0 } ?: 4,
                    completedSteps = work.completedSteps,
                    currentTask = work.task.ifBlank { "Downloading offline pack" },
                    estimatedSizeMb = work.estimatedSizeMb.takeIf { it > 0 } ?: estimated
                )
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DownloadPackUiState()
    )

    init {
        viewModelScope.launch {
            val trip = observeTripUseCase(tripId).first()
            localState.update {
                it.copy(estimatedSizeMb = trip?.offlinePackSizeMb ?: 0)
            }
        }

        viewModelScope.launch {
            downloadWorkRepository.observeDownload(tripId)
                .distinctUntilChanged { old, new ->
                    old?.isComplete == new?.isComplete && old?.errorMessage == new?.errorMessage
                }
                .collect { work ->
                    if (work?.isComplete == true && !downloadCompleteLogged) {
                        downloadCompleteLogged = true
                        analytics.logEvent(
                            AnalyticsEvents.OFFLINE_DOWNLOAD_COMPLETE,
                            mapOf(
                                AnalyticsParams.TRIP_ID to tripId,
                                AnalyticsParams.COUNT to work.completedSteps
                            )
                        )
                    }
                    val error = work?.errorMessage
                    if (!error.isNullOrBlank() && !downloadFailedLogged) {
                        downloadFailedLogged = true
                        analytics.logEvent(
                            AnalyticsEvents.OFFLINE_DOWNLOAD_FAILED,
                            mapOf(
                                AnalyticsParams.TRIP_ID to tripId,
                                AnalyticsParams.ERROR_MESSAGE to error
                            )
                        )
                    }
                }
        }
    }

    fun download(tripId: String) {
        viewModelScope.launch {
            val wifiOnly = observeWifiOnlyDownloadsUseCase().first()
            analytics.logEvent(
                AnalyticsEvents.OFFLINE_DOWNLOAD_STARTED,
                mapOf(AnalyticsParams.TRIP_ID to tripId, "wifi_only" to wifiOnly)
            )
            localState.update { it.copy(isDownloading = true, errorMessage = null) }
            downloadWorkRepository.enqueueDownload(tripId, wifiOnly)
        }
    }

    fun cancelDownload() {
        viewModelScope.launch {
            downloadWorkRepository.cancelDownload(tripId)
            localState.update { it.copy(isDownloading = false) }
            analytics.logEvent(
                "offline_download_cancelled",
                mapOf(AnalyticsParams.TRIP_ID to tripId)
            )
        }
    }

    suspend fun currentPackSizeBytes(): Long = getOfflinePackSizeUseCase(tripId)

    fun startTripOnline() {
        viewModelScope.launch {
            analytics.logEvent(AnalyticsEvents.START_TRIP_ONLINE, mapOf(AnalyticsParams.TRIP_ID to tripId))
            startTripUseCase(tripId)
        }
    }
}
