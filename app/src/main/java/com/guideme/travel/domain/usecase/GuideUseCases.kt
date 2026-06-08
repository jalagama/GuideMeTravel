package com.guideme.travel.domain.usecase

import com.guideme.travel.domain.model.Attraction
import com.guideme.travel.domain.model.GuidePlaybackState
import com.guideme.travel.domain.repository.GuideRepository
import com.guideme.travel.domain.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class PlayGuideUseCase @Inject constructor(
    private val guideRepository: GuideRepository
) {
    suspend operator fun invoke(attraction: Attraction, languageCode: String) {
        guideRepository.playGuideForAttraction(attraction, languageCode)
    }
}

class StopGuideUseCase @Inject constructor(
    private val guideRepository: GuideRepository
) {
    suspend operator fun invoke() = guideRepository.stopGuide()
}

class ObservePlaybackStateUseCase @Inject constructor(
    private val guideRepository: GuideRepository
) {
    operator fun invoke(): Flow<GuidePlaybackState> = guideRepository.observePlaybackState()
}

class GetGuideContentUseCase @Inject constructor(
    private val tripRepository: TripRepository
) {
    suspend operator fun invoke(tripId: String, attractionId: String): Attraction? {
        val trip = tripRepository.observeTrip(tripId).first() ?: return null
        return trip.attractions.firstOrNull { it.id == attractionId }
    }
}
