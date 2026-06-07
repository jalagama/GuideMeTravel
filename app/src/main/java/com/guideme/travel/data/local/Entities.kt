package com.guideme.travel.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val id: String,
    val origin: String,
    val destination: String,
    val languageCode: String,
    val status: String,
    val createdAtMillis: Long,
    val offlinePackSizeMb: Int,
    val offlinePackDownloaded: Boolean
)

@Entity(tableName = "attractions")
data class AttractionEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val name: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val imageUrl: String?,
    val orderIndex: Int,
    val status: String,
    val audioLocalPath: String?,
    val transcript: String?,
    val estimatedMinutes: Int
)
