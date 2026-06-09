package com.guideme.travel.data.offline

import android.content.Context
import com.guideme.travel.BuildConfig
import com.guideme.travel.domain.model.Attraction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OfflineMapMetadata(
    val regionId: Long,
    val styleUrl: String,
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double
)

@Singleton
class MapLibreOfflineMapManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val offlineManager: OfflineManager by lazy { OfflineManager.getInstance(context) }

    fun styleUrl(): String {
        val key = BuildConfig.MAPTILER_API_KEY
        return if (key.isBlank()) {
            "https://demotiles.maplibre.org/style.json"
        } else {
            "https://api.maptiler.com/maps/streets-v2/style.json?key=$key"
        }
    }

    suspend fun downloadRegion(
        tripId: String,
        attractions: List<Attraction>,
        onProgress: (Int) -> Unit
    ) = suspendCancellableCoroutine { continuation ->
        if (attractions.isEmpty()) {
            continuation.resume(Unit)
            return@suspendCancellableCoroutine
        }

        val style = styleUrl()
        val padding = 0.05
        val latSouth = attractions.minOf { it.latitude } - padding
        val latNorth = attractions.maxOf { it.latitude } + padding
        val lonWest = attractions.minOf { it.longitude } - padding
        val lonEast = attractions.maxOf { it.longitude } + padding
        val bounds = LatLngBounds.from(latNorth, lonEast, latSouth, lonWest)

        val definition = OfflineTilePyramidRegionDefinition(
            style,
            bounds,
            MIN_ZOOM,
            MAX_ZOOM,
            context.resources.displayMetrics.density
        )

        val metadata = JSONObject()
            .put("tripId", tripId)
            .put("styleUrl", style)
            .toString()
            .toByteArray()

        offlineManager.createOfflineRegion(
            definition,
            metadata,
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(region: OfflineRegion) {
                    region.setObserver(object : OfflineRegion.OfflineRegionObserver {
                        override fun onStatusChanged(status: OfflineRegionStatus) {
                            val required = status.requiredResourceCount
                            val completed = status.completedResourceCount
                            if (required > 0) {
                                val percent = ((completed * 100) / required).toInt().coerceIn(0, 100)
                                onProgress(percent)
                            }
                            if (status.isComplete) {
                                saveRegionMetadata(tripId, region.id, bounds, style)
                                if (continuation.isActive) continuation.resume(Unit)
                            }
                        }

                        override fun onError(error: OfflineRegionError) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    IllegalStateException("Offline map download failed: ${error.message}")
                                )
                            }
                        }

                        override fun mapboxTileCountLimitExceeded(tileCount: Long) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    IllegalStateException(
                                        "Offline tile limit exceeded ($tileCount tiles). Reduce zoom range or area."
                                    )
                                )
                            }
                        }
                    })
                    region.setDownloadState(OfflineRegion.STATE_ACTIVE)
                }

                override fun onError(error: String) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            IllegalStateException("Failed to create offline region: $error")
                        )
                    }
                }
            }
        )
    }

    fun loadRegionMetadata(tripId: String): OfflineMapMetadata? {
        val metadataFile = File(context.filesDir, "offline_packs/$tripId/map_region.json")
        if (!metadataFile.exists()) return null
        return runCatching {
            val json = JSONObject(metadataFile.readText())
            OfflineMapMetadata(
                regionId = json.getLong("regionId"),
                styleUrl = json.getString("styleUrl"),
                minLat = json.getDouble("minLat"),
                maxLat = json.getDouble("maxLat"),
                minLng = json.getDouble("minLng"),
                maxLng = json.getDouble("maxLng")
            )
        }.getOrNull()
    }

    suspend fun deleteRegionsForTrip(tripId: String) = suspendCancellableCoroutine { continuation ->
        val metadata = loadRegionMetadata(tripId)
        if (metadata == null) {
            continuation.resume(Unit)
            return@suspendCancellableCoroutine
        }

        offlineManager.getOfflineRegion(
            metadata.regionId,
            object : OfflineManager.GetOfflineRegionCallback {
                override fun onRegion(region: OfflineRegion) {
                    region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                        override fun onDelete() {
                            if (continuation.isActive) continuation.resume(Unit)
                        }

                        override fun onError(error: String) {
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                    })
                }

                override fun onRegionNotFound() {
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onError(error: String) {
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
        )
    }

    private fun saveRegionMetadata(
        tripId: String,
        regionId: Long,
        bounds: LatLngBounds,
        styleUrl: String
    ) {
        val packDir = File(context.filesDir, "offline_packs/$tripId")
        packDir.mkdirs()
        File(packDir, "map_region.json").writeText(
            """
            {
              "regionId": $regionId,
              "styleUrl": "$styleUrl",
              "minLat": ${bounds.latitudeSouth},
              "maxLat": ${bounds.latitudeNorth},
              "minLng": ${bounds.longitudeWest},
              "maxLng": ${bounds.longitudeEast}
            }
            """.trimIndent()
        )
    }

    companion object {
        private const val MIN_ZOOM = 10.0
        private const val MAX_ZOOM = 16.0
    }
}
