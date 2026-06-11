package com.guideme.travel.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.guideme.travel.domain.logging.GuideMeLogger
import com.guideme.travel.domain.logging.LogTags
import com.guideme.travel.domain.repository.CatalogPrefetchRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class CuratedCatalogPrefetchWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val catalogPrefetchRepository: CatalogPrefetchRepository,
    private val logger: GuideMeLogger
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val countryCode = inputData.getString(KEY_COUNTRY_CODE) ?: return Result.failure()
        val genreIds = inputData.getStringArray(KEY_GENRE_IDS)?.toList() ?: emptyList()
        logger.logBackgroundWork(
            WORK_NAME,
            "worker_started",
            mapOf("countryCode" to countryCode, "genreCount" to genreIds.size, "attempt" to runAttemptCount)
        )
        return runCatching {
            catalogPrefetchRepository.prefetchGenrePackages(countryCode, genreIds)
            logger.logBackgroundWork(WORK_NAME, "worker_success", mapOf("countryCode" to countryCode))
            Result.success()
        }.getOrElse { error ->
            logger.error(
                LogTags.CATALOG_PREFETCH,
                "worker_failed",
                mapOf("countryCode" to countryCode, "attempt" to runAttemptCount),
                error
            )
            Log.e(TAG, "Catalog prefetch failed for $countryCode", error)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "CuratedCatalogPrefetch"
        const val KEY_COUNTRY_CODE = "country_code"
        const val KEY_GENRE_IDS = "genre_ids"
        const val WORK_NAME = "curated_catalog_prefetch"
    }
}
