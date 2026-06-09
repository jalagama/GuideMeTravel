package com.guideme.travel.domain.repository

import com.guideme.travel.domain.model.UserLocation
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    fun observeLocation(): Flow<UserLocation?>
    suspend fun getLastLocation(): UserLocation?
}
