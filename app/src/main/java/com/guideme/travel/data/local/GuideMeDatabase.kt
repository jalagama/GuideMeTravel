package com.guideme.travel.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TripEntity::class, AttractionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class GuideMeDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun attractionDao(): AttractionDao
}
