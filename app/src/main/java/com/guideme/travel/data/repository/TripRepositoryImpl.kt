package com.guideme.travel.data.repository

import com.guideme.travel.data.auth.AuthRepository
import com.guideme.travel.data.local.AttractionDao
import com.guideme.travel.data.local.TripDao
import com.guideme.travel.data.local.toDomain
import com.guideme.travel.data.local.toEntity
import com.guideme.travel.data.offline.OfflinePackManager
import com.guideme.travel.data.preferences.UserPreferencesRepository
import com.guideme.travel.data.remote.FirebaseTripRemoteDataSource
import com.guideme.travel.data.sample.SampleTripData
import com.guideme.travel.domain.model.AttractionStatus
import com.guideme.travel.domain.model.OfflinePackProgress
import com.guideme.travel.domain.model.TripPlan
import com.guideme.travel.domain.model.TripStatus
import com.guideme.travel.domain.repository.TripRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class TripRepositoryImpl @Inject constructor(
    private val tripDao: TripDao,
    private val attractionDao: AttractionDao,
    private val authRepository: AuthRepository,
    private val remoteDataSource: FirebaseTripRemoteDataSource,
    private val offlinePackManager: OfflinePackManager,
    private val userPreferencesRepository: UserPreferencesRepository
) : TripRepository {

    override fun observeTrips(): Flow<List<TripPlan>> {
        return tripDao.observeTrips().flatMapLatest { trips ->
            if (trips.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(
                    trips.map { trip ->
                        attractionDao.observeAttractions(trip.id).map { attractions ->
                            trip.toDomain(attractions.map { it.toDomain() })
                        }
                    }
                ) { it.toList() }
            }
        }
    }

    override fun observeTrip(tripId: String): Flow<TripPlan?> {
        return combine(
            tripDao.observeTrip(tripId),
            attractionDao.observeAttractions(tripId)
        ) { trip, attractions ->
            trip?.toDomain(attractions.map { it.toDomain() })
        }
    }

    override suspend fun createTrip(
        origin: String,
        destination: String,
        languageCode: String
    ): TripPlan {
        val useFirebase = userPreferencesRepository.useFirebaseBackend.first()
        val trip = if (useFirebase) {
            runCatching {
                authRepository.ensureSignedIn()
                remoteDataSource.generateItinerary(origin, destination, languageCode)
            }.getOrElse {
                SampleTripData.createHampiTrip(origin, destination, languageCode)
            }
        } else {
            SampleTripData.createHampiTrip(origin, destination, languageCode)
        }

        val readyTrip = trip.copy(status = TripStatus.READY)
        tripDao.upsertTrip(readyTrip.toEntity())
        attractionDao.upsertAttractions(readyTrip.attractions.map { it.toEntity(readyTrip.id) })
        return readyTrip
    }

    override suspend fun downloadOfflinePack(
        tripId: String,
        onProgress: (OfflinePackProgress) -> Unit
    ): TripPlan {
        val trip = observeTrip(tripId).first() ?: error("Trip not found")
        val useFirebase = userPreferencesRepository.useFirebaseBackend.first()

        val updated = offlinePackManager.downloadPack(
            trip = trip,
            useFirebase = useFirebase,
            onProgress = onProgress
        )

        tripDao.upsertTrip(updated.toEntity())
        attractionDao.upsertAttractions(updated.attractions.map { it.toEntity(tripId) })
        return updated
    }

    override suspend fun startTrip(tripId: String) {
        val trip = observeTrip(tripId).first() ?: return
        tripDao.upsertTrip(trip.copy(status = TripStatus.ACTIVE).toEntity())
    }

    override suspend fun completeTrip(tripId: String) {
        val trip = observeTrip(tripId).first() ?: return
        tripDao.upsertTrip(trip.copy(status = TripStatus.COMPLETED).toEntity())
    }

    override suspend fun deleteOfflineData(tripId: String) {
        offlinePackManager.deletePack(tripId)
        val trip = observeTrip(tripId).first() ?: return
        tripDao.upsertTrip(
            trip.copy(
                offlinePackDownloaded = false,
                offlinePackSizeMb = 0,
                status = TripStatus.COMPLETED
            ).toEntity()
        )
    }

    override suspend fun markAttractionVisited(tripId: String, attractionId: String) {
        attractionDao.updateStatus(attractionId, AttractionStatus.VISITED.name)
    }
}
