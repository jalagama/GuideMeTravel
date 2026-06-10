package com.guideme.travel.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.guideme.travel.domain.repository.CatalogPrefetchRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class CuratedCatalogPrefetchWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val catalogPrefetchRepository: CatalogPrefetchRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val countryCode = inputData.getString(KEY_COUNTRY_CODE) ?: return Result.failure()
        val genreIds = inputData.getStringArray(KEY_GENRE_IDS)?.toList() ?: emptyList()
        return runCatching {
            catalogPrefetchRepository.prefetchGenrePackages(countryCode, genreIds)
            Result.success()
        }.getOrElse { error ->
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
