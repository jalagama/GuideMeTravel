package com.guideme.travel.data.offline

import android.content.Context
import com.guideme.travel.data.local.AttractionDao
import com.guideme.travel.data.remote.FirebaseTripRemoteDataSource
import com.guideme.travel.data.remote.GuidePackFile
import com.guideme.travel.domain.model.Attraction
import com.guideme.travel.domain.model.OfflinePackProgress
import com.guideme.travel.domain.model.TripPlan
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflinePackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val remoteDataSource: FirebaseTripRemoteDataSource,
    private val attractionDao: AttractionDao,
    private val mapOfflineManager: MapLibreOfflineMapManager
) {
    fun tripPackDir(tripId: String): File {
        return File(context.filesDir, "offline_packs/$tripId").apply { mkdirs() }
    }

    fun estimatePackSizeMb(trip: TripPlan): Int {
        return trip.offlinePackSizeMb.takeIf { it > 0 }
            ?: (45 + (trip.attractions.size * 3))
    }

    suspend fun downloadPack(
        trip: TripPlan,
        useFirebase: Boolean,
        onProgress: (OfflinePackProgress) -> Unit
    ): TripPlan = withContext(Dispatchers.IO) {
        val tripId = trip.id
        val totalSteps = 4
        val packDir = tripPackDir(tripId)

        onProgress(step(tripId, 0, totalSteps, "Fetching guide scripts", trip))

        val guideFiles = if (useFirebase) {
            remoteDataSource.generateGuidePack(tripId)
        } else {
            buildLocalGuideFiles(trip)
        }

        onProgress(step(tripId, 1, totalSteps, "Downloading audio guides", trip))

        val updatedAttractions = trip.attractions.map { attraction ->
            val guide = guideFiles.firstOrNull {
                it.attractionId == attraction.id ||
                    it.attractionId.endsWith(attraction.id.substringAfterLast("_")) ||
                    attraction.id.endsWith(it.attractionId)
            } ?: return@map attraction

            val audioFile = File(packDir, "${attraction.id}.mp3")
            if (guide.url.startsWith("http")) {
                downloadFile(guide.url, audioFile)
            }

            val transcriptFile = File(packDir, "${attraction.id}.txt")
            transcriptFile.writeText(guide.transcript)

            attractionDao.updateAudioPath(attraction.id, audioFile.absolutePath)

            attraction.copy(
                audioLocalPath = if (audioFile.exists() && audioFile.length() > 0) {
                    audioFile.absolutePath
                } else {
                    null
                },
                transcript = guide.transcript
            )
        }

        onProgress(step(tripId, 2, totalSteps, "Downloading offline map tiles", trip))
        mapOfflineManager.downloadRegion(tripId, updatedAttractions) { mapPercent ->
            onProgress(
                step(
                    tripId,
                    2,
                    totalSteps,
                    "Downloading offline map tiles ($mapPercent%)",
                    trip
                )
            )
        }

        onProgress(step(tripId, 3, totalSteps, "Saving offline pack", trip))
        onProgress(step(tripId, totalSteps, totalSteps, "Complete", trip))

        val actualSizeMb = (packSizeBytes(tripId) / (1024 * 1024)).toInt().coerceAtLeast(1)

        trip.copy(
            attractions = updatedAttractions,
            offlinePackDownloaded = true,
            offlinePackSizeMb = actualSizeMb
        )
    }

    suspend fun deletePack(tripId: String) = withContext(Dispatchers.IO) {
        mapOfflineManager.deleteRegionsForTrip(tripId)
        tripPackDir(tripId).deleteRecursively()
    }

    fun packSizeBytes(tripId: String): Long {
        val packDir = tripPackDir(tripId)
        if (!packDir.exists()) return 0L
        return packDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun buildLocalGuideFiles(trip: TripPlan): List<GuidePackFile> {
        return trip.attractions.map { attraction ->
            GuidePackFile(
                attractionId = attraction.id,
                url = "",
                storagePath = null,
                transcript = attraction.transcript
                    ?: "Welcome to ${attraction.name}. ${attraction.description}"
            )
        }
    }

    private fun downloadFile(url: String, destination: File) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 30_000
        connection.readTimeout = 120_000
        connection.inputStream.use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        connection.disconnect()
    }

    private fun step(
        tripId: String,
        completed: Int,
        total: Int,
        task: String,
        trip: TripPlan
    ): OfflinePackProgress {
        return OfflinePackProgress(
            tripId = tripId,
            totalSteps = total,
            completedSteps = completed,
            currentTask = task,
            estimatedSizeMb = estimatePackSizeMb(trip)
        )
    }
}
