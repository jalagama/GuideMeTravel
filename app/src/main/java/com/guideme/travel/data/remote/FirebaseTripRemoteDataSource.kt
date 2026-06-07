package com.guideme.travel.data.remote

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableResult
import com.guideme.travel.domain.model.Attraction
import com.guideme.travel.domain.model.AttractionStatus
import com.guideme.travel.domain.model.TripPlan
import com.guideme.travel.domain.model.TripStatus
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class GuidePackFile(
    val attractionId: String,
    val url: String,
    val transcript: String
)

@Singleton
class FirebaseTripRemoteDataSource @Inject constructor(
    private val functions: FirebaseFunctions
) {
    suspend fun generateItinerary(
        origin: String,
        destination: String,
        languageCode: String
    ): TripPlan {
        val result = callFunction(
            "generateItinerary",
            mapOf(
                "origin" to origin,
                "destination" to destination,
                "languageCode" to languageCode
            )
        )

        @Suppress("UNCHECKED_CAST")
        val data = result.getData() as Map<String, Any?>
        return parseTripPlan(data)
    }

    suspend fun generateGuidePack(tripId: String): List<GuidePackFile> {
        val result = callFunction(
            "generateGuidePack",
            mapOf("tripId" to tripId)
        )

        @Suppress("UNCHECKED_CAST")
        val data = result.getData() as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val audioFiles = data["audioFiles"] as? List<Map<String, Any?>> ?: emptyList()

        return audioFiles.map { file ->
            GuidePackFile(
                attractionId = file["attractionId"] as String,
                url = file["url"] as String,
                transcript = file["transcript"] as String
            )
        }
    }

    private suspend fun callFunction(name: String, payload: Map<String, Any>): HttpsCallableResult {
        return functions
            .getHttpsCallable(name)
            .call(payload)
            .await()
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseTripPlan(data: Map<String, Any?>): TripPlan {
        val tripId = data["tripId"] as String
        val attractionsRaw = data["attractions"] as List<Map<String, Any?>>

        val attractions = attractionsRaw.mapIndexed { index, item ->
            Attraction(
                id = "${tripId}_${item["id"] ?: index}",
                name = item["name"] as String,
                description = item["description"] as String,
                latitude = (item["latitude"] as Number).toDouble(),
                longitude = (item["longitude"] as Number).toDouble(),
                imageUrl = item["imageUrl"] as? String,
                orderIndex = (item["orderIndex"] as? Number)?.toInt() ?: index,
                estimatedMinutes = (item["estimatedMinutes"] as? Number)?.toInt() ?: 45,
                transcript = item["transcript"] as? String
            )
        }

        return TripPlan(
            id = tripId,
            origin = data["origin"] as String,
            destination = data["destination"] as String,
            languageCode = data["languageCode"] as String,
            status = TripStatus.valueOf((data["status"] as? String) ?: TripStatus.READY.name),
            attractions = attractions,
            createdAtMillis = (data["createdAtMillis"] as Number).toLong(),
            offlinePackSizeMb = (data["offlinePackSizeMb"] as? Number)?.toInt() ?: 120,
            offlinePackDownloaded = data["offlinePackDownloaded"] as? Boolean ?: false
        )
    }
}
