package com.guideme.travel.domain.repository

interface CatalogPrefetchRepository {
    suspend fun prefetchGenrePackages(countryCode: String, genreIds: List<String>)
    fun enqueuePrefetch(countryCode: String, genreIds: List<String>, wifiOnly: Boolean)
}
