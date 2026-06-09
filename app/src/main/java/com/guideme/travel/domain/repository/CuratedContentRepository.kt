package com.guideme.travel.domain.repository

import com.guideme.travel.domain.model.CountryGenres
import com.guideme.travel.domain.model.GenrePackages
import com.guideme.travel.domain.model.TourPackageDetail
import com.guideme.travel.domain.model.TripPlan

interface CuratedContentRepository {
    suspend fun getCountryGenres(countryCode: String): CountryGenres
    suspend fun getGenrePackages(countryCode: String, genreId: String): GenrePackages
    suspend fun getTourPackageDetail(
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
}
