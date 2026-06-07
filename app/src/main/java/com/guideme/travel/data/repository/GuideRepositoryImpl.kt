package com.guideme.travel.data.repository

import com.guideme.travel.domain.model.Attraction
import com.guideme.travel.domain.model.GuidePlaybackState
import com.guideme.travel.domain.repository.GuideRepository
import com.guideme.travel.service.AudioGuidePlayer
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GuideRepositoryImpl @Inject constructor(
    private val audioGuidePlayer: AudioGuidePlayer
) : GuideRepository {

    override suspend fun playGuideForAttraction(attraction: Attraction) {
        audioGuidePlayer.playGuide(
            attractionId = attraction.id,
            attractionName = attraction.name,
            transcript = attraction.transcript.orEmpty(),
            audioLocalPath = attraction.audioLocalPath
        )
    }

    override suspend fun stopGuide() {
        audioGuidePlayer.stop()
    }

    override fun observePlaybackState(): Flow<GuidePlaybackState> {
        return audioGuidePlayer.playbackState
    }
}
