package com.guideme.travel.domain.model

data class CuratedGenre(
    val id: String,
    val name: String,
    val type: String,
    val imageUrl: String,
    val blurb: String,
    val rank: Int = 0
)

data class CountryGenres(
    val countryCode: String,
    val countryName: String,
    val genres: List<CuratedGenre>,
    val schemaVersion: Int = 3,
    val updatedAtMillis: Long = 0L
)

data class TourPackageSummary(
    val id: String,
    val title: String,
    val region: String,
    val days: Int,
    val heroImageUrl: String,
    val shortInfo: String,
    val rank: Int = 0,
    val bestFor: String = "",
    val seasonality: String? = null
)

data class GenrePackages(
    val countryCode: String,
    val genreId: String,
    val genreName: String,
    val packages: List<TourPackageSummary>,
    val schemaVersion: Int = 3,
    val updatedAtMillis: Long = 0L
)

data class CuratedSpot(
    val id: String,
    val name: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val imageUrl: String?,
    val orderIndex: Int,
    val day: Int,
    val whyChosen: String?,
    val previewSnippet: String? = null,
    val estimatedMinutes: Int = 45
)

data class NearbyPlace(
    val name: String,
    val description: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val rating: Double? = null
)

data class TourPackageDetail(
    val id: String,
    val countryCode: String,
    val genreId: String,
    val title: String,
    val region: String,
    val days: Int,
    val heroImageUrl: String,
    val overview: String,
    val daySummaries: Map<String, String> = emptyMap(),
    val spots: List<CuratedSpot>,
    val tips: List<String>,
    val essentials: List<String>,
    val highlights: List<String>,
    val hotels: List<NearbyPlace>,
    val restaurants: List<NearbyPlace>,
    val updatedAtMillis: Long = 0L
)

data class TripOffering(
    val packageId: String,
    val priceCents: Int = 0,
    val currency: String = "USD",
    val isFree: Boolean = true
)
