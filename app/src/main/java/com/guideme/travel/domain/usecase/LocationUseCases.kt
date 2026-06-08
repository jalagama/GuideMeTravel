package com.guideme.travel.domain.usecase

import com.guideme.travel.domain.model.UserLocation
import com.guideme.travel.domain.repository.LocationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveLocationUseCase @Inject constructor(
    private val locationRepository: LocationRepository
) {
    operator fun invoke(): Flow<UserLocation?> = locationRepository.observeLocation()
}
