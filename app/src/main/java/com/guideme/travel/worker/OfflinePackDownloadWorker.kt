package com.guideme.travel.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.guideme.travel.domain.usecase.DownloadOfflinePackUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class OfflinePackDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val downloadOfflinePackUseCase: DownloadOfflinePackUseCase
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val tripId = inputData.getString(KEY_TRIP_ID) ?: return Result.failure()
        if (runAttemptCount >= MAX_ATTEMPTS) {
            return Result.failure(
                workDataOf(KEY_ERROR to "Download failed after $MAX_ATTEMPTS attempts")
            )
        }

        return runCatching {
            downloadOfflinePackUseCase(tripId) { progress ->
                setProgressAsync(
                    workDataOf(
                        KEY_PROGRESS to progress.progressFraction,
                        KEY_TASK to progress.currentTask,
                        KEY_COMPLETED_STEPS to progress.completedSteps,
                        KEY_TOTAL_STEPS to progress.totalSteps,
                        KEY_ESTIMATED_SIZE_MB to progress.estimatedSizeMb
                    )
                )
            }
            Result.success()
        }.getOrElse { error ->
            Log.e(TAG, "Offline pack download failed for $tripId (attempt ${runAttemptCount + 1})", error)
            val message = error.message ?: "Offline pack download failed"
            if (runAttemptCount >= MAX_ATTEMPTS - 1) {
                Result.failure(workDataOf(KEY_ERROR to message))
            } else {
                Result.retry()
            }
        }
    }

    companion object {
        private const val TAG = "OfflinePackDownloadWorker"
        const val KEY_TRIP_ID = "trip_id"
        const val KEY_PROGRESS = "progress"
        const val KEY_TASK = "task"
        const val KEY_COMPLETED_STEPS = "completed_steps"
        const val KEY_TOTAL_STEPS = "total_steps"
        const val KEY_ESTIMATED_SIZE_MB = "estimated_size_mb"
        const val KEY_ERROR = "error"
        const val WORK_NAME_PREFIX = "offline_pack_"
        private const val MAX_ATTEMPTS = 3
    }
}
