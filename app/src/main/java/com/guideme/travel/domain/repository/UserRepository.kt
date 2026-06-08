package com.guideme.travel.domain.repository

import com.guideme.travel.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun observeProfile(uid: String): Flow<UserProfile?>
    suspend fun upsertProfile(profile: UserProfile)
    suspend fun syncTripsFromCloud(uid: String)
}
