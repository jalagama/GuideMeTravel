package com.guideme.travel.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.guideme.travel.data.local.AttractionDao
import com.guideme.travel.data.local.TripDao
import com.guideme.travel.data.local.toDomain
import com.guideme.travel.data.local.toEntity
import com.guideme.travel.data.offline.OfflinePackManager
import com.guideme.travel.data.remote.FirebaseTripRemoteDataSource
import com.guideme.travel.domain.model.AttractionStatus
import com.guideme.travel.domain.model.LanguageOptions
import com.guideme.travel.domain.model.OfflinePackProgress
import com.guideme.travel.domain.model.TripPlan
import com.guideme.travel.domain.model.TripStatus
import com.guideme.travel.domain.repository.AuthRepository
import com.guideme.travel.domain.repository.PreferencesRepository
import com.guideme.travel.domain.repository.TripRepository
import com.guideme.travel.domain.usecase.GetUserCountryUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap
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
    private val preferencesRepository: PreferencesRepository,
    private val getUserCountryUseCase: GetUserCountryUseCase,
    private val firestore: FirebaseFirestore
) : TripRepository {

    private val triggeredAttractions = ConcurrentHashMap<String, MutableSet<String>>()

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
        require(LanguageOptions.isSupported(languageCode)) {
            "Unsupported language code: $languageCode"
        }

        val countryCode = preferencesRepository.countryCode.first()
            ?: getUserCountryUseCase().also { preferencesRepository.setCountryCode(it) }

        val useFirebase = preferencesRepository.useFirebaseBackend.first()
        val trip = if (useFirebase) {
            authRepository.ensureSignedIn()
            remoteDataSource.generateItinerary(origin, destination, languageCode, countryCode)
        } else {
            error("Firebase backend is required for global trip planning.")
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
        val useFirebase = preferencesRepository.useFirebaseBackend.first()

        tripDao.upsertTrip(trip.copy(status = TripStatus.DOWNLOADING).toEntity())

        return try {
            val updated = offlinePackManager.downloadPack(
                trip = trip,
                useFirebase = useFirebase,
                onProgress = onProgress
            )
            tripDao.upsertTrip(updated.copy(status = TripStatus.READY).toEntity())
            attractionDao.upsertAttractions(updated.attractions.map { it.toEntity(tripId) })
            updated.copy(status = TripStatus.READY)
        } catch (error: Exception) {
            tripDao.upsertTrip(trip.copy(status = TripStatus.READY).toEntity())
            throw error
        }
    }

    override suspend fun startTrip(tripId: String) {
        val trip = observeTrip(tripId).first() ?: return
        triggeredAttractions.remove(tripId)
        tripDao.upsertTrip(trip.copy(status = TripStatus.ACTIVE).toEntity())
    }

    override suspend fun completeTrip(tripId: String) {
        val trip = observeTrip(tripId).first() ?: return
        tripDao.upsertTrip(trip.copy(status = TripStatus.COMPLETED).toEntity())
        triggeredAttractions.remove(tripId)
    }

    override suspend fun deleteOfflineData(tripId: String): Long {
        val sizeBefore = offlinePackManager.packSizeBytes(tripId)
        offlinePackManager.deletePack(tripId)
        val trip = observeTrip(tripId).first() ?: return sizeBefore

        val clearedAttractions = trip.attractions.map {
            it.copy(audioLocalPath = null)
        }
        attractionDao.upsertAttractions(clearedAttractions.map { it.toEntity(tripId) })
        tripDao.upsertTrip(
            trip.copy(
                offlinePackDownloaded = false,
                offlinePackSizeMb = 0,
                status = TripStatus.COMPLETED,
                attractions = clearedAttractions
            ).toEntity()
        )
        return sizeBefore
    }

    override suspend fun deleteTrip(tripId: String): Long {
        val sizeBefore = offlinePackManager.packSizeBytes(tripId)
        offlinePackManager.deletePack(tripId)
        attractionDao.deleteByTrip(tripId)
        tripDao.deleteTrip(tripId)
        triggeredAttractions.remove(tripId)
        runCatching {
            firestore.collection("trips").document(tripId).delete().await()
        }
        return sizeBefore
    }

    override suspend fun markAttractionVisited(tripId: String, attractionId: String) {
        attractionDao.updateStatus(attractionId, AttractionStatus.VISITED.name)
    }

    override suspend fun getOfflinePackSizeBytes(tripId: String): Long {
        return offlinePackManager.packSizeBytes(tripId)
    }

    override fun isAttractionAlreadyTriggered(tripId: String, attractionId: String): Boolean {
        return triggeredAttractions[tripId]?.contains(attractionId) == true
    }

    override fun markAttractionTriggered(tripId: String, attractionId: String) {
        triggeredAttractions.computeIfAbsent(tripId) { mutableSetOf() }.add(attractionId)
    }
}
