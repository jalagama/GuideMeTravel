package com.guideme.travel.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.guideme.travel.domain.logging.GuideMeLogger
import com.guideme.travel.domain.logging.LogTags
import com.guideme.travel.domain.repository.CatalogPrefetchRepository
import com.guideme.travel.domain.repository.CuratedContentRepository
import com.guideme.travel.worker.CuratedCatalogPrefetchWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogPrefetchRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val curatedContentRepository: CuratedContentRepository,
    private val logger: GuideMeLogger
) : CatalogPrefetchRepository {

    private val workManager = WorkManager.getInstance(context)

    override suspend fun prefetchGenrePackages(countryCode: String, genreIds: List<String>) {
        val cached = curatedContentRepository.getCachedGenreIds(countryCode).toSet()
        val pending = genreIds.filterNot { it in cached }
        logger.logBackgroundWork(
            CuratedCatalogPrefetchWorker.WORK_NAME,
            "prefetch_started",
            mapOf("countryCode" to countryCode, "pendingGenreCount" to pending.size)
        )
        for (genreId in pending) {
            runCatching {
                curatedContentRepository.getGenrePackages(countryCode, genreId)
            }.onSuccess {
                logger.logBackgroundWork(
                    CuratedCatalogPrefetchWorker.WORK_NAME,
                    "prefetch_genre_complete",
                    mapOf("countryCode" to countryCode, "genreId" to genreId)
                )
            }.onFailure { error ->
                logger.warn(
                    LogTags.CATALOG_PREFETCH,
                    "prefetch_genre_failed",
                    mapOf("countryCode" to countryCode, "genreId" to genreId),
                    error
                )
            }
        }
        logger.logBackgroundWork(
            CuratedCatalogPrefetchWorker.WORK_NAME,
            "prefetch_finished",
            mapOf("countryCode" to countryCode)
        )
    }

    override fun enqueuePrefetch(countryCode: String, genreIds: List<String>, wifiOnly: Boolean) {
        logger.logBackgroundWork(
            CuratedCatalogPrefetchWorker.WORK_NAME,
            "prefetch_enqueued",
            mapOf("countryCode" to countryCode, "genreCount" to genreIds.size, "wifiOnly" to wifiOnly)
        )
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = OneTimeWorkRequestBuilder<CuratedCatalogPrefetchWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    CuratedCatalogPrefetchWorker.KEY_COUNTRY_CODE to countryCode,
                    CuratedCatalogPrefetchWorker.KEY_GENRE_IDS to genreIds.toTypedArray()
                )
            )
            .build()

        workManager.enqueueUniqueWork(
            CuratedCatalogPrefetchWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
