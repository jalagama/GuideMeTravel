package com.guideme.travel.data.repository

import com.guideme.travel.data.local.AttractionDao
import com.guideme.travel.data.local.CURATED_SCHEMA_VERSION
import com.guideme.travel.data.local.CuratedContentLocalDataSource
import com.guideme.travel.data.local.TripDao
import com.guideme.travel.data.local.toEntity
import com.guideme.travel.data.remote.FirebaseCuratedContentDataSource
import com.guideme.travel.domain.logging.GuideMeLogger
import com.guideme.travel.domain.logging.LogTags
import com.guideme.travel.domain.model.CountryGenres
import com.guideme.travel.domain.model.GenrePackages
import com.guideme.travel.domain.model.TourPackageDetail
import com.guideme.travel.domain.model.TripPlan
import com.guideme.travel.domain.model.TripStatus
import com.guideme.travel.domain.repository.CuratedContentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CuratedContentRepositoryImpl @Inject constructor(
    private val remoteDataSource: FirebaseCuratedContentDataSource,
    private val localDataSource: CuratedContentLocalDataSource,
    private val tripDao: TripDao,
    private val attractionDao: AttractionDao,
    private val logger: GuideMeLogger
) : CuratedContentRepository {

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun observeCountryGenres(countryCode: String): Flow<CountryGenres?> {
        return localDataSource.observeCountryGenres(countryCode)
    }

    override fun observeGenrePackages(countryCode: String, genreId: String): Flow<GenrePackages?> {
        return localDataSource.observeGenrePackages(countryCode, genreId)
    }

    override fun observeTourPackageDetail(packageId: String): Flow<TourPackageDetail?> {
        return localDataSource.observeTourPackageDetail(packageId)
    }

    override suspend fun getCountryGenres(countryCode: String): CountryGenres {
        val cached = localDataSource.observeCountryGenres(countryCode).firstOrNull()
        if (cached != null && cached.schemaVersion >= CURATED_SCHEMA_VERSION) {
            logger.logCacheHit("countryGenres", countryCode, mapOf("genreCount" to cached.genres.size))
            return cached
        }
        logger.logCacheMiss("countryGenres", countryCode)
        return refreshCountryGenres(countryCode)
    }

    override suspend fun getGenrePackages(countryCode: String, genreId: String): GenrePackages {
        val cacheKey = "${countryCode}_$genreId"
        val cached = localDataSource.observeGenrePackages(countryCode, genreId).firstOrNull()
        if (cached != null && cached.schemaVersion >= CURATED_SCHEMA_VERSION) {
            logger.logCacheHit("genrePackages", cacheKey, mapOf("packageCount" to cached.packages.size))
            return cached
        }
        logger.logCacheMiss("genrePackages", cacheKey)
        return refreshGenrePackages(countryCode, genreId)
    }

    override suspend fun getTourPackageDetail(
        packageId: String,
        countryCode: String,
        genreId: String
    ): TourPackageDetail {
        val cached = localDataSource.observeTourPackageDetail(packageId).firstOrNull()
        if (cached != null) {
            logger.logCacheHit("tourPackageDetail", packageId, mapOf("spotCount" to cached.spots.size))
            return cached
        }
        logger.logCacheMiss("tourPackageDetail", packageId, mapOf("countryCode" to countryCode, "genreId" to genreId))
        return refreshTourPackageDetail(packageId, countryCode, genreId)
    }

    override suspend fun refreshCountryGenres(countryCode: String): CountryGenres {
        logger.info(LogTags.CURATED_REPO, "refresh_country_genres", mapOf("countryCode" to countryCode))
        val remote = remoteDataSource.getCountryGenres(countryCode)
        localDataSource.upsertCountryGenres(remote)
        logger.info(
            LogTags.CURATED_REPO,
            "refresh_country_genres_complete",
            mapOf("countryCode" to countryCode, "genreCount" to remote.genres.size)
        )
        return remote
    }

    override suspend fun refreshGenrePackages(countryCode: String, genreId: String): GenrePackages {
        logger.info(
            LogTags.CURATED_REPO,
            "refresh_genre_packages",
            mapOf("countryCode" to countryCode, "genreId" to genreId)
        )
        val remote = remoteDataSource.getGenrePackages(countryCode, genreId)
        localDataSource.upsertGenrePackages(remote)
        logger.info(
            LogTags.CURATED_REPO,
            "refresh_genre_packages_complete",
            mapOf("countryCode" to countryCode, "genreId" to genreId, "packageCount" to remote.packages.size)
        )
        return remote
    }

    override suspend fun refreshTourPackageDetail(
        packageId: String,
        countryCode: String,
        genreId: String
    ): TourPackageDetail {
        logger.info(
            LogTags.CURATED_REPO,
            "refresh_package_detail",
            mapOf("packageId" to packageId, "countryCode" to countryCode, "genreId" to genreId)
        )
        val remote = remoteDataSource.getTourPackageDetail(packageId, countryCode, genreId)
        localDataSource.upsertTourPackageDetail(remote)
        logger.info(
            LogTags.CURATED_REPO,
            "refresh_package_detail_complete",
            mapOf("packageId" to packageId, "spotCount" to remote.spots.size)
        )
        return remote
    }

    override suspend fun createTripFromPackage(
        packageId: String,
        countryCode: String,
        genreId: String,
        origin: String,
        languageCode: String
    ): TripPlan {
        logger.info(
            LogTags.CURATED_REPO,
            "create_trip_remote",
            mapOf("packageId" to packageId, "languageCode" to languageCode)
        )
        val trip = remoteDataSource.createTripFromPackage(
            packageId,
            countryCode,
            genreId,
            origin,
            languageCode
        )
        persistTrip(trip)
        logger.info(LogTags.CURATED_REPO, "create_trip_remote_complete", mapOf("tripId" to trip.id))
        return trip
    }

    override suspend fun createTripFromLocalPackage(
        packageId: String,
        countryCode: String,
        genreId: String,
        origin: String,
        languageCode: String
    ): TripPlan {
        logger.info(
            LogTags.CURATED_REPO,
            "create_trip_local",
            mapOf("packageId" to packageId, "languageCode" to languageCode)
        )
        val detail = localDataSource.observeTourPackageDetail(packageId).firstOrNull()
            ?: getTourPackageDetail(packageId, countryCode, genreId)

        val tripId = UUID.randomUUID().toString()
        val attractions = detail.spots.mapIndexed { index, spot ->
            com.guideme.travel.domain.model.Attraction(
                id = "${tripId}_${spot.id}",
                name = spot.name,
                description = spot.description,
                latitude = spot.latitude,
                longitude = spot.longitude,
                imageUrl = spot.imageUrl,
                orderIndex = spot.orderIndex,
                estimatedMinutes = spot.estimatedMinutes,
                transcript = spot.previewSnippet ?: "Welcome to ${spot.name}. ${spot.description}"
            )
        }
        val offlinePackSizeMb = (attractions.size * 2.5 + 45).toInt()
        val trip = TripPlan(
            id = tripId,
            origin = origin,
            destination = detail.title,
            languageCode = languageCode,
            status = TripStatus.READY,
            attractions = attractions,
            createdAtMillis = System.currentTimeMillis(),
            offlinePackSizeMb = offlinePackSizeMb,
            offlinePackDownloaded = false
        )
        persistTrip(trip)
        logger.info(
            LogTags.CURATED_REPO,
            "create_trip_local_complete",
            mapOf("tripId" to tripId, "attractionCount" to attractions.size)
        )

        syncScope.launch {
            runCatching {
                remoteDataSource.createTripFromPackage(
                    packageId,
                    countryCode,
                    genreId,
                    origin,
                    languageCode
                )
            }.onSuccess {
                logger.info(LogTags.CURATED_REPO, "create_trip_background_sync_success", mapOf("packageId" to packageId))
            }.onFailure { error ->
                logger.warn(
                    LogTags.CURATED_REPO,
                    "create_trip_background_sync_failed",
                    mapOf("packageId" to packageId),
                    error
                )
            }
        }
        return trip
    }

    override suspend fun getCachedGenreIds(countryCode: String): List<String> {
        return localDataSource.getCachedGenreIds(countryCode)
    }

    private suspend fun persistTrip(trip: TripPlan) {
        val readyTrip = trip.copy(status = TripStatus.READY)
        tripDao.upsertTrip(readyTrip.toEntity())
        attractionDao.upsertAttractions(readyTrip.attractions.map { it.toEntity(readyTrip.id) })
    }
}
