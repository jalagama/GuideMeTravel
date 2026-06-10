package com.guideme.travel.domain.usecase

import com.guideme.travel.domain.repository.CatalogPrefetchRepository
import com.guideme.travel.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class EnqueueCatalogPrefetchUseCase @Inject constructor(
    private val catalogPrefetchRepository: CatalogPrefetchRepository,
    private val preferencesRepository: PreferencesRepository
) {
    suspend operator fun invoke(countryCode: String, genreIds: List<String>) {
        val wifiOnly = preferencesRepository.wifiOnlyDownloads.first()
        catalogPrefetchRepository.enqueuePrefetch(countryCode, genreIds, wifiOnly)
    }
}
