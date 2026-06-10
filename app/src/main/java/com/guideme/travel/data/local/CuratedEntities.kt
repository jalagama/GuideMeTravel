package com.guideme.travel.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "country_genres_cache")
data class CountryGenresCacheEntity(
    @PrimaryKey val countryCode: String,
    val countryName: String,
    val schemaVersion: Int,
    val updatedAtMillis: Long
)

@Entity(
    tableName = "curated_genres",
    primaryKeys = ["id", "countryCode"],
    indices = [Index("countryCode")]
)
data class CuratedGenreEntity(
    val id: String,
    val countryCode: String,
    val name: String,
    val type: String,
    val imageUrl: String,
    val blurb: String,
    val rank: Int
)

@Entity(tableName = "genre_packages_cache")
data class GenrePackagesCacheEntity(
    @PrimaryKey val cacheKey: String,
    val countryCode: String,
    val genreId: String,
    val genreName: String,
    val schemaVersion: Int,
    val updatedAtMillis: Long
)

@Entity(
    tableName = "tour_package_summaries",
    indices = [Index("countryCode"), Index("genreId")]
)
data class TourPackageSummaryEntity(
    @PrimaryKey val id: String,
    val countryCode: String,
    val genreId: String,
    val title: String,
    val region: String,
    val days: Int,
    val heroImageUrl: String,
    val shortInfo: String,
    val rank: Int,
    val bestFor: String,
    val seasonality: String?
)

@Entity(tableName = "tour_package_details")
data class TourPackageDetailEntity(
    @PrimaryKey val id: String,
    val countryCode: String,
    val genreId: String,
    val title: String,
    val region: String,
    val days: Int,
    val heroImageUrl: String,
    val overview: String,
    val daySummariesJson: String,
    val tipsJson: String,
    val essentialsJson: String,
    val highlightsJson: String,
    val schemaVersion: Int,
    val updatedAtMillis: Long
)

@Entity(
    tableName = "curated_spots",
    indices = [Index("packageId")]
)
data class CuratedSpotEntity(
    @PrimaryKey val id: String,
    val packageId: String,
    val name: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val imageUrl: String?,
    val orderIndex: Int,
    val day: Int,
    val whyChosen: String?,
    val previewSnippet: String?,
    val estimatedMinutes: Int
)

@Entity(
    tableName = "nearby_places",
    indices = [Index("packageId")]
)
data class NearbyPlaceEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val packageId: String,
    val placeType: String,
    val name: String,
    val description: String,
    val latitude: Double?,
    val longitude: Double?,
    val rating: Double?
)
