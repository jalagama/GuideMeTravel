package com.guideme.travel.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY createdAtMillis DESC")
    fun observeTrips(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :tripId LIMIT 1")
    fun observeTrip(tripId: String): Flow<TripEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrip(trip: TripEntity)

    @Query("DELETE FROM trips WHERE id = :tripId")
    suspend fun deleteTrip(tripId: String)
}

@Dao
interface AttractionDao {
    @Query("SELECT * FROM attractions WHERE tripId = :tripId ORDER BY orderIndex ASC")
    fun observeAttractions(tripId: String): Flow<List<AttractionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttractions(attractions: List<AttractionEntity>)

    @Query("UPDATE attractions SET status = :status WHERE id = :attractionId")
    suspend fun updateStatus(attractionId: String, status: String)

    @Query("UPDATE attractions SET audioLocalPath = :path WHERE id = :attractionId")
    suspend fun updateAudioPath(attractionId: String, path: String)

    @Query("DELETE FROM attractions WHERE tripId = :tripId")
    suspend fun deleteByTrip(tripId: String)
}
