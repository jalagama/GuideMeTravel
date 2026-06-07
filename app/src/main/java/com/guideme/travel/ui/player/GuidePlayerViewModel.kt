package com.guideme.travel.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.guideme.travel.domain.model.GuidePlaybackState
import com.guideme.travel.domain.repository.GuideRepository
import com.guideme.travel.domain.repository.TripRepository
import com.guideme.travel.ui.navigation.GuidePlayerRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GuidePlayerUiState(
    val attractionName: String = "",
    val transcript: String = "",
    val playbackState: GuidePlaybackState? = null
)

@HiltViewModel
class GuidePlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository,
    private val guideRepository: GuideRepository
) : ViewModel() {

    private val route = savedStateHandle.toRoute<GuidePlayerRoute>()

    val uiState: StateFlow<GuidePlayerUiState> = combine(
        tripRepository.observeTrip(route.tripId),
        guideRepository.observePlaybackState()
    ) { trip, playback ->
        val attraction = trip?.attractions?.firstOrNull { it.id == route.attractionId }
        GuidePlayerUiState(
            attractionName = attraction?.name.orEmpty(),
            transcript = attraction?.transcript.orEmpty(),
            playbackState = playback
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GuidePlayerUiState()
    )

    fun play(tripId: String, attractionId: String) {
        viewModelScope.launch {
            val trip = tripRepository.observeTrip(tripId).first()
            val attraction = trip?.attractions?.firstOrNull { it.id == attractionId } ?: return@launch
            guideRepository.playGuideForAttraction(attraction)
        }
    }

    fun stop() {
        viewModelScope.launch {
            guideRepository.stopGuide()
        }
    }
}
