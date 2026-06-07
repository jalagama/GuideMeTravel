package com.guideme.travel.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.guideme.travel.domain.repository.TripRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class OfflinePackDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val tripRepository: TripRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val tripId = inputData.getString(KEY_TRIP_ID) ?: return Result.failure()
        return runCatching {
            tripRepository.downloadOfflinePack(tripId) {}
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        const val KEY_TRIP_ID = "trip_id"
        const val WORK_NAME_PREFIX = "offline_pack_"
    }
}
