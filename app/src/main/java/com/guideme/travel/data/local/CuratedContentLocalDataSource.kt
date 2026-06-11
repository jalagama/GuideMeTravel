package com.guideme.travel.data.local

import com.guideme.travel.domain.logging.GuideMeLogger
import com.guideme.travel.domain.model.CountryGenres
import com.guideme.travel.domain.model.GenrePackages
import com.guideme.travel.domain.model.TourPackageDetail
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CuratedContentLocalDataSource @Inject constructor(
    private val dao: CuratedContentDao,
    private val logger: GuideMeLogger
) {
    fun observeCountryGenres(countryCode: String): Flow<CountryGenres?> {
        return combine(
            dao.observeCountryGenresCache(countryCode),
            dao.observeGenresForCountry(countryCode)
        ) { cache, genres ->
            if (cache == null || genres.isEmpty()) return@combine null
            CountryGenres(
                countryCode = cache.countryCode,
                countryName = cache.countryName,
                genres = genres.map { it.toDomain() },
                schemaVersion = cache.schemaVersion,
                updatedAtMillis = cache.updatedAtMillis
            )
        }
    }

    fun observeGenrePackages(countryCode: String, genreId: String): Flow<GenrePackages?> {
        return combine(
            dao.observeGenrePackagesCache(countryCode, genreId),
            dao.observePackageSummaries(countryCode, genreId)
        ) { cache, packages ->
            if (cache == null || packages.isEmpty()) return@combine null
            GenrePackages(
                countryCode = cache.countryCode,
                genreId = cache.genreId,
                genreName = cache.genreName,
                packages = packages.map { it.toDomain() },
                schemaVersion = cache.schemaVersion,
                updatedAtMillis = cache.updatedAtMillis
            )
        }
    }

    suspend fun getTourPackageDetailIfFresh(packageId: String): TourPackageDetail? {
        val detail = dao.observePackageDetailEntity(packageId).first() ?: return null
        if (detail.schemaVersion < CURATED_SCHEMA_VERSION) {
            return null
        }
        val spots = dao.observeSpotsForPackage(packageId).first()
        if (spots.isEmpty()) {
            return null
        }
        val nearby = dao.observeNearbyPlaces(packageId).first()
        val hotels = nearby.filter { it.placeType == "hotel" }.map { it.toDomain() }
        val restaurants = nearby.filter { it.placeType == "restaurant" }.map { it.toDomain() }
        return detail.toDomain(spots.map { it.toDomain() }, hotels, restaurants)
    }

    fun observeTourPackageDetail(packageId: String): Flow<TourPackageDetail?> {
        return combine(
            dao.observePackageDetailEntity(packageId),
            dao.observeSpotsForPackage(packageId),
            dao.observeNearbyPlaces(packageId)
        ) { detail, spots, nearby ->
            if (detail == null) return@combine null
            val hotels = nearby.filter { it.placeType == "hotel" }.map { it.toDomain() }
            val restaurants = nearby.filter { it.placeType == "restaurant" }.map { it.toDomain() }
            detail.toDomain(spots.map { it.toDomain() }, hotels, restaurants)
        }
    }

    suspend fun upsertCountryGenres(data: CountryGenres, schemaVersion: Int = CURATED_SCHEMA_VERSION) {
        dao.upsertCountryGenres(
            cache = data.toCacheEntity(schemaVersion),
            genres = data.genres.map { it.toEntity(data.countryCode) }
        )
        logger.logLocalUpsert(
            "CountryGenres",
            data.countryCode,
            mapOf("genreCount" to data.genres.size, "schemaVersion" to schemaVersion)
        )
    }

    suspend fun upsertGenrePackages(data: GenrePackages, schemaVersion: Int = CURATED_SCHEMA_VERSION) {
        dao.upsertGenrePackages(
            cache = data.toCacheEntity(schemaVersion),
            packages = data.packages.map { it.toEntity(data.countryCode, data.genreId) }
        )
        logger.logLocalUpsert(
            "GenrePackages",
            "${data.countryCode}_${data.genreId}",
            mapOf("packageCount" to data.packages.size, "schemaVersion" to schemaVersion)
        )
    }

    suspend fun upsertTourPackageDetail(detail: TourPackageDetail, schemaVersion: Int = CURATED_SCHEMA_VERSION) {
        val nearbyPlaces = detail.hotels.map { it.toEntity(detail.id, "hotel") } +
            detail.restaurants.map { it.toEntity(detail.id, "restaurant") }
        dao.upsertTourPackageDetail(
            detail = detail.toEntity(schemaVersion),
            spots = detail.spots.map { it.toEntity(detail.id) },
            nearbyPlaces = nearbyPlaces
        )
        logger.logLocalUpsert(
            "TourPackageDetail",
            detail.id,
            mapOf(
                "spotCount" to detail.spots.size,
                "hotelCount" to detail.hotels.size,
                "restaurantCount" to detail.restaurants.size,
                "schemaVersion" to schemaVersion
            )
        )
    }

    suspend fun getCachedGenreIds(countryCode: String): List<String> {
        return dao.getCachedGenreIds(countryCode)
    }
}
