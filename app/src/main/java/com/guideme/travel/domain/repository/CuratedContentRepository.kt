package com.guideme.travel.domain.repository

import com.guideme.travel.domain.model.CountryGenres
import com.guideme.travel.domain.model.GenrePackages
import com.guideme.travel.domain.model.TourPackageDetail
import com.guideme.travel.domain.model.TripPlan
import kotlinx.coroutines.flow.Flow

interface CuratedContentRepository {
    fun observeCountryGenres(countryCode: String): Flow<CountryGenres?>
    fun observeGenrePackages(countryCode: String, genreId: String): Flow<GenrePackages?>
    fun observeTourPackageDetail(packageId: String): Flow<TourPackageDetail?>

    suspend fun getCountryGenres(countryCode: String): CountryGenres
    suspend fun getGenrePackages(countryCode: String, genreId: String): GenrePackages
    suspend fun getTourPackageDetail(
        packageId: String,
        countryCode: String,
        genreId: String
    ): TourPackageDetail

    suspend fun refreshCountryGenres(countryCode: String): CountryGenres
    suspend fun refreshGenrePackages(countryCode: String, genreId: String): GenrePackages
    suspend fun refreshTourPackageDetail(
        packageId: String,
        countryCode: String,
        genreId: String
    ): TourPackageDetail

    suspend fun createTripFromPackage(
        packageId: String,
        countryCode: String,
        genreId: String,
        origin: String,
        languageCode: String
    ): TripPlan

    suspend fun createTripFromLocalPackage(
        packageId: String,
        countryCode: String,
        genreId: String,
        origin: String,
        languageCode: String
    ): TripPlan

    suspend fun getCachedGenreIds(countryCode: String): List<String>
}
