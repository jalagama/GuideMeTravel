package com.guideme.travel.domain.repository

import kotlinx.coroutines.flow.Flow

data class DownloadWorkState(
    val tripId: String,
    val progress: Float,
    val task: String,
    val completedSteps: Int,
    val totalSteps: Int,
    val estimatedSizeMb: Int,
    val isRunning: Boolean,
    val isComplete: Boolean,
    val isFailed: Boolean,
    val errorMessage: String?
)

interface DownloadWorkRepository {
    fun observeDownload(tripId: String): Flow<DownloadWorkState?>
    suspend fun enqueueDownload(tripId: String, wifiOnly: Boolean)
    suspend fun cancelDownload(tripId: String)
}
