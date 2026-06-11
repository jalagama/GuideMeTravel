package com.guideme.travel.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        TripEntity::class,
        AttractionEntity::class,
        CountryGenresCacheEntity::class,
        CuratedGenreEntity::class,
        GenrePackagesCacheEntity::class,
        TourPackageSummaryEntity::class,
        TourPackageDetailEntity::class,
        CuratedSpotEntity::class,
        NearbyPlaceEntity::class,
    ],
    version = 3,
    exportSchema = false
)
abstract class GuideMeDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun attractionDao(): AttractionDao
    abstract fun curatedContentDao(): CuratedContentDao
}
