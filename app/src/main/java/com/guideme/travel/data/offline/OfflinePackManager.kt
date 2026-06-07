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
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflinePackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val remoteDataSource: FirebaseTripRemoteDataSource,
    private val attractionDao: AttractionDao
) {
    fun tripPackDir(tripId: String): File {
        return File(context.filesDir, "offline_packs/$tripId").apply { mkdirs() }
    }

    fun estimatePackSizeMb(trip: TripPlan): Int {
        return 40 + (trip.attractions.size * 15)
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
            runCatching { remoteDataSource.generateGuidePack(tripId) }
                .getOrElse { buildLocalGuideFiles(trip) }
        } else {
            buildLocalGuideFiles(trip)
        }

        onProgress(step(tripId, 1, totalSteps, "Downloading audio guides", trip))

        val updatedAttractions = trip.attractions.map { attraction ->
            val guide = guideFiles.firstOrNull {
                it.attractionId == attraction.id ||
                    it.attractionId.endsWith(attraction.id.substringAfterLast("_"))
            }
            if (guide == null) return@map attraction

            val audioFile = File(packDir, "${attraction.id}.mp3")
            if (guide.url.startsWith("http")) {
                runCatching { downloadFile(guide.url, audioFile) }
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

        onProgress(step(tripId, 2, totalSteps, "Preparing offline map region", trip))
        File(packDir, "map_region.json").writeText(
            buildMapRegionMetadata(updatedAttractions)
        )

        onProgress(step(tripId, 3, totalSteps, "Saving offline pack", trip))
        onProgress(step(tripId, totalSteps, totalSteps, "Complete", trip))

        trip.copy(
            attractions = updatedAttractions,
            offlinePackDownloaded = true,
            offlinePackSizeMb = estimatePackSizeMb(trip)
        )
    }

    suspend fun deletePack(tripId: String) = withContext(Dispatchers.IO) {
        tripPackDir(tripId).deleteRecursively()
    }

    fun packSizeBytes(tripId: String): Long {
        return tripPackDir(tripId).walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun buildLocalGuideFiles(trip: TripPlan): List<GuidePackFile> {
        return trip.attractions.map { attraction ->
            GuidePackFile(
                attractionId = attraction.id,
                url = "",
                transcript = attraction.transcript
                    ?: "Welcome to ${attraction.name}. ${attraction.description}"
            )
        }
    }

    private fun downloadFile(url: String, destination: File) {
        URL(url).openStream().use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun buildMapRegionMetadata(attractions: List<Attraction>): String {
        val lats = attractions.map { it.latitude }
        val lngs = attractions.map { it.longitude }
        return """
            {
              "minLat": ${lats.minOrNull() ?: 0.0},
              "maxLat": ${lats.maxOrNull() ?: 0.0},
              "minLng": ${lngs.minOrNull() ?: 0.0},
              "maxLng": ${lngs.maxOrNull() ?: 0.0},
              "spotCount": ${attractions.size}
            }
        """.trimIndent()
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
