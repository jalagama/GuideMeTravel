package com.guideme.travel.ui.offline

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
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
    private val startTripUseCase: StartTripUseCase
) : ViewModel() {

    private val tripId: String = savedStateHandle.toRoute<DownloadRoute>().tripId
    private val localState = MutableStateFlow(DownloadPackUiState())

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
    }

    fun download(tripId: String) {
        viewModelScope.launch {
            val wifiOnly = observeWifiOnlyDownloadsUseCase().first()
            localState.update { it.copy(isDownloading = true, errorMessage = null) }
            downloadWorkRepository.enqueueDownload(tripId, wifiOnly)
        }
    }

    fun cancelDownload() {
        viewModelScope.launch {
            downloadWorkRepository.cancelDownload(tripId)
            localState.update { it.copy(isDownloading = false) }
        }
    }

    suspend fun currentPackSizeBytes(): Long = getOfflinePackSizeUseCase(tripId)

    fun startTripOnline() {
        viewModelScope.launch {
            startTripUseCase(tripId)
        }
    }
}
