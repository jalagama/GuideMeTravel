package com.guideme.travel.domain.usecase

import com.guideme.travel.domain.model.CountryGenres
import com.guideme.travel.domain.model.GenrePackages
import com.guideme.travel.domain.model.TourPackageDetail
import com.guideme.travel.domain.model.TripPlan
import com.guideme.travel.domain.repository.CuratedContentRepository
import com.guideme.travel.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class GetCountryGenresUseCase @Inject constructor(
    private val curatedContentRepository: CuratedContentRepository,
    private val preferencesRepository: PreferencesRepository,
    private val getUserCountryUseCase: GetUserCountryUseCase
) {
    suspend operator fun invoke(): CountryGenres {
        val countryCode = preferencesRepository.countryCode.first()
            ?: getUserCountryUseCase().also { preferencesRepository.setCountryCode(it) }
        return curatedContentRepository.getCountryGenres(countryCode)
    }
}

class GetGenrePackagesUseCase @Inject constructor(
    private val curatedContentRepository: CuratedContentRepository
) {
    suspend operator fun invoke(countryCode: String, genreId: String): GenrePackages {
        return curatedContentRepository.getGenrePackages(countryCode, genreId)
    }
}

class GetTourPackageDetailUseCase @Inject constructor(
    private val curatedContentRepository: CuratedContentRepository
) {
    suspend operator fun invoke(packageId: String): TourPackageDetail {
        return curatedContentRepository.getTourPackageDetail(packageId)
    }
}

class StartTripFromPackageUseCase @Inject constructor(
    private val curatedContentRepository: CuratedContentRepository
) {
    suspend operator fun invoke(
        packageId: String,
        origin: String,
        languageCode: String
    ): TripPlan {
        return curatedContentRepository.createTripFromPackage(packageId, origin, languageCode)
    }
}
