package com.guideme.travel.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.guideme.travel.domain.analytics.AnalyticsEvents
import com.guideme.travel.domain.analytics.AnalyticsParams
import com.guideme.travel.domain.analytics.GuideMeAnalytics
import com.guideme.travel.domain.model.GuidePlaybackState
import com.guideme.travel.domain.usecase.GetGuideContentUseCase
import com.guideme.travel.domain.usecase.ObservePlaybackStateUseCase
import com.guideme.travel.domain.usecase.ObserveTripUseCase
import com.guideme.travel.domain.usecase.PlayGuideUseCase
import com.guideme.travel.domain.usecase.StopGuideUseCase
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
    private val observeTripUseCase: ObserveTripUseCase,
    private val observePlaybackStateUseCase: ObservePlaybackStateUseCase,
    private val playGuideUseCase: PlayGuideUseCase,
    private val stopGuideUseCase: StopGuideUseCase,
    private val getGuideContentUseCase: GetGuideContentUseCase,
    private val analytics: GuideMeAnalytics
) : ViewModel() {

    private val route = savedStateHandle.toRoute<GuidePlayerRoute>()

    val uiState: StateFlow<GuidePlayerUiState> = combine(
        observeTripUseCase(route.tripId),
        observePlaybackStateUseCase()
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
            val trip = observeTripUseCase(tripId).first() ?: return@launch
            val attraction = getGuideContentUseCase(tripId, attractionId) ?: return@launch
            analytics.logEvent(
                AnalyticsEvents.GUIDE_PLAYED,
                mapOf(
                    AnalyticsParams.TRIP_ID to tripId,
                    "attraction_id" to attractionId,
                    "attraction_name" to attraction.name
                )
            )
            playGuideUseCase(attraction, trip.languageCode)
        }
    }

    fun stop() {
        viewModelScope.launch {
            analytics.logEvent(
                "guide_stopped",
                mapOf(
                    AnalyticsParams.TRIP_ID to route.tripId,
                    "attraction_id" to route.attractionId
                )
            )
            stopGuideUseCase()
        }
    }
}
