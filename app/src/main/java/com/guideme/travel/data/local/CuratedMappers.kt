package com.guideme.travel.data.local

import com.guideme.travel.domain.model.CountryGenres
import com.guideme.travel.domain.model.CuratedGenre
import com.guideme.travel.domain.model.CuratedSpot
import com.guideme.travel.domain.model.GenrePackages
import com.guideme.travel.domain.model.NearbyPlace
import com.guideme.travel.domain.model.TourPackageDetail
import com.guideme.travel.domain.model.TourPackageSummary
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val curatedJson = Json { ignoreUnknownKeys = true }

const val CURATED_SCHEMA_VERSION = 7

fun CountryGenres.toCacheEntity(schemaVersion: Int = CURATED_SCHEMA_VERSION): CountryGenresCacheEntity {
    return CountryGenresCacheEntity(
        countryCode = countryCode,
        countryName = countryName,
        schemaVersion = schemaVersion,
        updatedAtMillis = System.currentTimeMillis()
    )
}

fun CuratedGenre.toEntity(countryCode: String): CuratedGenreEntity {
    return CuratedGenreEntity(
        id = id,
        countryCode = countryCode,
        name = name,
        type = type,
        imageUrl = imageUrl,
        blurb = blurb,
        rank = rank
    )
}

fun CuratedGenreEntity.toDomain(): CuratedGenre {
    return CuratedGenre(
        id = id,
        name = name,
        type = type,
        imageUrl = imageUrl,
        blurb = blurb,
        rank = rank
    )
}

fun GenrePackages.toCacheEntity(schemaVersion: Int = CURATED_SCHEMA_VERSION): GenrePackagesCacheEntity {
    return GenrePackagesCacheEntity(
        cacheKey = "${countryCode}_${genreId}",
        countryCode = countryCode,
        genreId = genreId,
        genreName = genreName,
        schemaVersion = schemaVersion,
        updatedAtMillis = System.currentTimeMillis()
    )
}

fun TourPackageSummary.toEntity(countryCode: String, genreId: String): TourPackageSummaryEntity {
    return TourPackageSummaryEntity(
        id = id,
        countryCode = countryCode,
        genreId = genreId,
        title = title,
        region = region,
        days = days,
        heroImageUrl = heroImageUrl,
        shortInfo = shortInfo,
        rank = rank,
        bestFor = bestFor,
        seasonality = seasonality
    )
}

fun TourPackageSummaryEntity.toDomain(): TourPackageSummary {
    return TourPackageSummary(
        id = id,
        title = title,
        region = region,
        days = days,
        heroImageUrl = heroImageUrl,
        shortInfo = shortInfo,
        rank = rank,
        bestFor = bestFor,
        seasonality = seasonality
    )
}

fun TourPackageDetail.toEntity(schemaVersion: Int = CURATED_SCHEMA_VERSION): TourPackageDetailEntity {
    return TourPackageDetailEntity(
        id = id,
        countryCode = countryCode,
        genreId = genreId,
        title = title,
        region = region,
        days = days,
        heroImageUrl = heroImageUrl,
        overview = overview,
        daySummariesJson = curatedJson.encodeToString(daySummaries),
        tipsJson = curatedJson.encodeToString(tips),
        essentialsJson = curatedJson.encodeToString(essentials),
        highlightsJson = curatedJson.encodeToString(highlights),
        schemaVersion = schemaVersion,
        updatedAtMillis = System.currentTimeMillis()
    )
}

fun CuratedSpot.toEntity(packageId: String): CuratedSpotEntity {
    return CuratedSpotEntity(
        id = id,
        packageId = packageId,
        name = name,
        description = description,
        latitude = latitude,
        longitude = longitude,
        imageUrl = imageUrl,
        orderIndex = orderIndex,
        day = day,
        whyChosen = whyChosen,
        previewSnippet = previewSnippet,
        transcript = transcript,
        estimatedMinutes = estimatedMinutes
    )
}

fun NearbyPlace.toEntity(packageId: String, placeType: String): NearbyPlaceEntity {
    return NearbyPlaceEntity(
        packageId = packageId,
        placeType = placeType,
        name = name,
        description = description,
        latitude = latitude,
        longitude = longitude,
        rating = rating
    )
}

fun CuratedSpotEntity.toDomain(): CuratedSpot {
    return CuratedSpot(
        id = id,
        name = name,
        description = description,
        latitude = latitude,
        longitude = longitude,
        imageUrl = imageUrl,
        orderIndex = orderIndex,
        day = day,
        whyChosen = whyChosen,
        previewSnippet = previewSnippet,
        transcript = transcript,
        estimatedMinutes = estimatedMinutes
    )
}

fun NearbyPlaceEntity.toDomain(): NearbyPlace {
    return NearbyPlace(
        name = name,
        description = description,
        latitude = latitude,
        longitude = longitude,
        rating = rating
    )
}

fun TourPackageDetailEntity.toDomain(
    spots: List<CuratedSpot>,
    hotels: List<NearbyPlace>,
    restaurants: List<NearbyPlace>
): TourPackageDetail {
    return TourPackageDetail(
        id = id,
        countryCode = countryCode,
        genreId = genreId,
        title = title,
        region = region,
        days = days,
        heroImageUrl = heroImageUrl,
        overview = overview,
        daySummaries = curatedJson.decodeFromString(daySummariesJson),
        spots = spots,
        tips = curatedJson.decodeFromString(tipsJson),
        essentials = curatedJson.decodeFromString(essentialsJson),
        highlights = curatedJson.decodeFromString(highlightsJson),
        hotels = hotels,
        restaurants = restaurants,
        updatedAtMillis = updatedAtMillis
    )
}
