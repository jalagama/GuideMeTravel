package com.guideme.travel.ui.offline

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.guideme.travel.domain.model.OfflinePackProgress
import com.guideme.travel.domain.repository.TripRepository
import com.guideme.travel.ui.navigation.DownloadRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadPackUiState(
    val progress: OfflinePackProgress? = null,
    val isDownloading: Boolean = false,
    val isComplete: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class DownloadPackViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository
) : ViewModel() {

    private val tripId: String = savedStateHandle.toRoute<DownloadRoute>().tripId
    private val _uiState = MutableStateFlow(DownloadPackUiState())
    val uiState: StateFlow<DownloadPackUiState> = _uiState.asStateFlow()

    fun download(tripId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDownloading = true, errorMessage = null) }
            runCatching {
                tripRepository.downloadOfflinePack(tripId) { progress ->
                    _uiState.update {
                        it.copy(progress = progress, isComplete = progress.completedSteps == progress.totalSteps)
                    }
                }
            }.onSuccess {
                _uiState.update { it.copy(isDownloading = false, isComplete = true) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        errorMessage = error.message ?: "Download failed"
                    )
                }
            }
        }
    }
}
