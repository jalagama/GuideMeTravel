package com.guideme.travel.domain.usecase

import com.guideme.travel.domain.model.CountryGenres
import com.guideme.travel.domain.model.GenrePackages
import com.guideme.travel.domain.model.TourPackageDetail
import com.guideme.travel.domain.model.TripPlan
import com.guideme.travel.domain.repository.CuratedContentRepository
import com.guideme.travel.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ObserveCountryGenresUseCase @Inject constructor(
    private val curatedContentRepository: CuratedContentRepository,
    private val preferencesRepository: PreferencesRepository,
    private val getUserCountryUseCase: GetUserCountryUseCase
) {
    suspend fun countryCode(): String {
        return preferencesRepository.countryCode.first()
            ?: getUserCountryUseCase().also { preferencesRepository.setCountryCode(it) }
    }

    operator fun invoke(countryCode: String): Flow<CountryGenres?> {
        return curatedContentRepository.observeCountryGenres(countryCode)
    }
}

class GetCountryGenresUseCase @Inject constructor(
    private val curatedContentRepository: CuratedContentRepository,
    private val observeCountryGenresUseCase: ObserveCountryGenresUseCase
) {
    suspend operator fun invoke(): CountryGenres {
        val countryCode = observeCountryGenresUseCase.countryCode()
        return curatedContentRepository.getCountryGenres(countryCode)
    }
}

class RefreshCountryGenresUseCase @Inject constructor(
    private val curatedContentRepository: CuratedContentRepository,
    private val observeCountryGenresUseCase: ObserveCountryGenresUseCase
) {
    suspend operator fun invoke(): CountryGenres {
        val countryCode = observeCountryGenresUseCase.countryCode()
        return curatedContentRepository.refreshCountryGenres(countryCode)
    }
}

class ObserveGenrePackagesUseCase @Inject constructor(
    private val curatedContentRepository: CuratedContentRepository
) {
    operator fun invoke(countryCode: String, genreId: String): Flow<GenrePackages?> {
        return curatedContentRepository.observeGenrePackages(countryCode, genreId)
    }
}

class GetGenrePackagesUseCase @Inject constructor(
    private val curatedContentRepository: CuratedContentRepository
) {
    suspend operator fun invoke(countryCode: String, genreId: String): GenrePackages {
        return curatedContentRepository.getGenrePackages(countryCode, genreId)
    }
}

class RefreshGenrePackagesUseCase @Inject constructor(
    private val curatedContentRepository: CuratedContentRepository
) {
    suspend operator fun invoke(countryCode: String, genreId: String): GenrePackages {
        return curatedContentRepository.refreshGenrePackages(countryCode, genreId)
    }
}

class ObserveTourPackageDetailUseCase @Inject constructor(
    private val curatedContentRepository: CuratedContentRepository
) {
    operator fun invoke(packageId: String): Flow<TourPackageDetail?> {
        return curatedContentRepository.observeTourPackageDetail(packageId)
    }
}

class GetTourPackageDetailUseCase @Inject constructor(
    private val curatedContentRepository: CuratedContentRepository
) {
    suspend operator fun invoke(
        packageId: String,
        countryCode: String,
        genreId: String
    ): TourPackageDetail {
        return curatedContentRepository.getTourPackageDetail(packageId, countryCode, genreId)
    }
}

class RefreshTourPackageDetailUseCase @Inject constructor(
    private val curatedContentRepository: CuratedContentRepository
) {
    suspend operator fun invoke(
        packageId: String,
        countryCode: String,
        genreId: String
    ): TourPackageDetail {
        return curatedContentRepository.refreshTourPackageDetail(packageId, countryCode, genreId)
    }
}

class StartTripFromPackageUseCase @Inject constructor(
    private val curatedContentRepository: CuratedContentRepository
) {
    suspend operator fun invoke(
        packageId: String,
        countryCode: String,
        genreId: String,
        origin: String,
        languageCode: String,
        preferLocal: Boolean = false
    ): TripPlan {
        return if (preferLocal) {
            curatedContentRepository.createTripFromLocalPackage(
                packageId,
                countryCode,
                genreId,
                origin,
                languageCode
            )
        } else {
            curatedContentRepository.createTripFromPackage(
                packageId,
                countryCode,
                genreId,
                origin,
                languageCode
            )
        }
    }
}

class GetCachedGenreIdsUseCase @Inject constructor(
    private val curatedContentRepository: CuratedContentRepository
) {
    suspend operator fun invoke(countryCode: String): List<String> {
        return curatedContentRepository.getCachedGenreIds(countryCode)
    }
}
