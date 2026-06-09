package com.guideme.travel.data.repository

import com.guideme.travel.data.local.AttractionDao
import com.guideme.travel.data.local.TripDao
import com.guideme.travel.data.local.toEntity
import com.guideme.travel.data.remote.FirebaseCuratedContentDataSource
import com.guideme.travel.domain.model.CountryGenres
import com.guideme.travel.domain.model.GenrePackages
import com.guideme.travel.domain.model.TourPackageDetail
import com.guideme.travel.domain.model.TripPlan
import com.guideme.travel.domain.model.TripStatus
import com.guideme.travel.domain.repository.CuratedContentRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CuratedContentRepositoryImpl @Inject constructor(
    private val remoteDataSource: FirebaseCuratedContentDataSource,
    private val tripDao: TripDao,
    private val attractionDao: AttractionDao
) : CuratedContentRepository {

    override suspend fun getCountryGenres(countryCode: String): CountryGenres {
        return remoteDataSource.getCountryGenres(countryCode)
    }

    override suspend fun getGenrePackages(countryCode: String, genreId: String): GenrePackages {
        return remoteDataSource.getGenrePackages(countryCode, genreId)
    }

    override suspend fun getTourPackageDetail(
        packageId: String,
        countryCode: String,
        genreId: String
    ): TourPackageDetail {
        return remoteDataSource.getTourPackageDetail(packageId, countryCode, genreId)
    }

    override suspend fun createTripFromPackage(
        packageId: String,
        countryCode: String,
        genreId: String,
        origin: String,
        languageCode: String
    ): TripPlan {
        val trip = remoteDataSource.createTripFromPackage(
            packageId,
            countryCode,
            genreId,
            origin,
            languageCode
        )
        val readyTrip = trip.copy(status = TripStatus.READY)
        tripDao.upsertTrip(readyTrip.toEntity())
        attractionDao.upsertAttractions(readyTrip.attractions.map { it.toEntity(readyTrip.id) })
        return readyTrip
    }
}
