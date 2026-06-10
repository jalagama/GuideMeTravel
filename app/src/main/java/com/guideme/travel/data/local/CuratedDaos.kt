package com.guideme.travel.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CuratedContentDao {

    @Query("SELECT * FROM country_genres_cache WHERE countryCode = :countryCode LIMIT 1")
    fun observeCountryGenresCache(countryCode: String): Flow<CountryGenresCacheEntity?>

    @Query("SELECT * FROM curated_genres WHERE countryCode = :countryCode ORDER BY rank ASC")
    fun observeGenresForCountry(countryCode: String): Flow<List<CuratedGenreEntity>>

    @Query("SELECT * FROM genre_packages_cache WHERE countryCode = :countryCode AND genreId = :genreId LIMIT 1")
    fun observeGenrePackagesCache(countryCode: String, genreId: String): Flow<GenrePackagesCacheEntity?>

    @Query(
        "SELECT * FROM tour_package_summaries WHERE countryCode = :countryCode AND genreId = :genreId ORDER BY rank ASC"
    )
    fun observePackageSummaries(countryCode: String, genreId: String): Flow<List<TourPackageSummaryEntity>>

    @Query("SELECT * FROM tour_package_details WHERE id = :packageId LIMIT 1")
    fun observePackageDetailEntity(packageId: String): Flow<TourPackageDetailEntity?>

    @Query("SELECT * FROM curated_spots WHERE packageId = :packageId ORDER BY orderIndex ASC")
    fun observeSpotsForPackage(packageId: String): Flow<List<CuratedSpotEntity>>

    @Query("SELECT * FROM nearby_places WHERE packageId = :packageId ORDER BY localId ASC")
    fun observeNearbyPlaces(packageId: String): Flow<List<NearbyPlaceEntity>>

    @Query("SELECT genreId FROM genre_packages_cache WHERE countryCode = :countryCode")
    suspend fun getCachedGenreIds(countryCode: String): List<String>

    @Transaction
    suspend fun upsertCountryGenres(
        cache: CountryGenresCacheEntity,
        genres: List<CuratedGenreEntity>
    ) {
        deleteGenresForCountry(cache.countryCode)
        insertCountryGenresCache(cache)
        insertGenres(genres)
    }

    @Transaction
    suspend fun upsertGenrePackages(
        cache: GenrePackagesCacheEntity,
        packages: List<TourPackageSummaryEntity>
    ) {
        deletePackageSummaries(cache.countryCode, cache.genreId)
        insertGenrePackagesCache(cache)
        insertPackageSummaries(packages)
    }

    @Transaction
    suspend fun upsertTourPackageDetail(
        detail: TourPackageDetailEntity,
        spots: List<CuratedSpotEntity>,
        nearbyPlaces: List<NearbyPlaceEntity>
    ) {
        deleteSpotsForPackage(detail.id)
        deleteNearbyPlacesForPackage(detail.id)
        insertPackageDetail(detail)
        insertSpots(spots)
        insertNearbyPlaces(nearbyPlaces)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCountryGenresCache(cache: CountryGenresCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGenres(genres: List<CuratedGenreEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGenrePackagesCache(cache: GenrePackagesCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPackageSummaries(packages: List<TourPackageSummaryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPackageDetail(detail: TourPackageDetailEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpots(spots: List<CuratedSpotEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNearbyPlaces(places: List<NearbyPlaceEntity>)

    @Query("DELETE FROM curated_genres WHERE countryCode = :countryCode")
    suspend fun deleteGenresForCountry(countryCode: String)

    @Query(
        "DELETE FROM tour_package_summaries WHERE countryCode = :countryCode AND genreId = :genreId"
    )
    suspend fun deletePackageSummaries(countryCode: String, genreId: String)

    @Query("DELETE FROM curated_spots WHERE packageId = :packageId")
    suspend fun deleteSpotsForPackage(packageId: String)

    @Query("DELETE FROM nearby_places WHERE packageId = :packageId")
    suspend fun deleteNearbyPlacesForPackage(packageId: String)
}
