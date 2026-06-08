package com.guideme.travel.data.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.guideme.travel.domain.repository.DownloadWorkRepository
import com.guideme.travel.domain.repository.DownloadWorkState
import com.guideme.travel.worker.OfflinePackDownloadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadWorkRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : DownloadWorkRepository {

    private val workManager = WorkManager.getInstance(context)

    override fun observeDownload(tripId: String): Flow<DownloadWorkState?> {
        return workManager.getWorkInfosForUniqueWorkFlow(workName(tripId)).map { infos ->
            val info = infos.firstOrNull() ?: return@map null
            val progress = info.progress
            DownloadWorkState(
                tripId = tripId,
                progress = progress.getFloat(OfflinePackDownloadWorker.KEY_PROGRESS, 0f),
                task = progress.getString(OfflinePackDownloadWorker.KEY_TASK) ?: "",
                completedSteps = progress.getInt(OfflinePackDownloadWorker.KEY_COMPLETED_STEPS, 0),
                totalSteps = progress.getInt(OfflinePackDownloadWorker.KEY_TOTAL_STEPS, 0),
                estimatedSizeMb = progress.getInt(OfflinePackDownloadWorker.KEY_ESTIMATED_SIZE_MB, 0),
                isRunning = info.state == WorkInfo.State.RUNNING || info.state == WorkInfo.State.ENQUEUED,
                isComplete = info.state == WorkInfo.State.SUCCEEDED,
                isFailed = info.state == WorkInfo.State.FAILED,
                errorMessage = if (info.state == WorkInfo.State.FAILED) {
                    info.outputData.getString(OfflinePackDownloadWorker.KEY_ERROR)
                } else {
                    null
                }
            )
        }
    }

    override suspend fun enqueueDownload(tripId: String, wifiOnly: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<OfflinePackDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(OfflinePackDownloadWorker.KEY_TRIP_ID to tripId))
            .addTag(workName(tripId))
            .build()

        workManager.enqueueUniqueWork(
            workName(tripId),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    override suspend fun cancelDownload(tripId: String) {
        workManager.cancelUniqueWork(workName(tripId))
    }

    private fun workName(tripId: String): String =
        OfflinePackDownloadWorker.WORK_NAME_PREFIX + tripId
}
