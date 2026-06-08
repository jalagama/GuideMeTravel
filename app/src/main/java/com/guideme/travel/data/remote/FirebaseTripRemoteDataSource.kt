package com.guideme.travel.data.remote

import com.google.firebase.functions.FirebaseFunctions
import com.guideme.travel.BuildConfig
import com.guideme.travel.domain.model.Attraction
import com.guideme.travel.domain.model.AttractionStatus
import com.guideme.travel.domain.model.TripPlan
import com.guideme.travel.domain.model.TripStatus
import com.guideme.travel.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class GuidePackFile(
    val attractionId: String,
    val url: String,
    val storagePath: String?,
    val transcript: String
)

@Singleton
class FirebaseTripRemoteDataSource @Inject constructor(
    private val functions: FirebaseFunctions,
    private val authRepository: AuthRepository
) {
    suspend fun generateItinerary(
        origin: String,
        destination: String,
        languageCode: String
    ): TripPlan {
        val result = functions
            .getHttpsCallable("generateItinerary")
            .call(
                mapOf(
                    "origin" to origin,
                    "destination" to destination,
                    "languageCode" to languageCode
                )
            )
            .await()

        @Suppress("UNCHECKED_CAST")
        val data = result.getData() as Map<String, Any?>
        return parseTripPlan(data)
    }

    suspend fun generateGuidePack(tripId: String): List<GuidePackFile> = withContext(Dispatchers.IO) {
        val token = authRepository.getIdToken()
        val url = URL("${BuildConfig.GUIDE_PACK_BASE_URL}/generateGuidePack")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 900_000
        }

        connection.outputStream.bufferedWriter().use {
            it.write(JSONObject(mapOf("tripId" to tripId)).toString())
        }

        val responseCode = connection.responseCode
        val body = if (responseCode in 200..299) {
            connection.inputStream.bufferedReader().readText()
        } else {
            connection.errorStream?.bufferedReader()?.readText()
                ?: "Guide pack request failed with code $responseCode"
        }
        connection.disconnect()

        if (responseCode !in 200..299) {
            error(body)
        }

        val json = JSONObject(body)
        val audioFiles = json.getJSONArray("audioFiles")
        buildList {
            for (index in 0 until audioFiles.length()) {
                val file = audioFiles.getJSONObject(index)
                add(
                    GuidePackFile(
                        attractionId = file.getString("attractionId"),
                        url = file.getString("url"),
                        storagePath = file.optString("storagePath").ifBlank { null },
                        transcript = file.getString("transcript")
                    )
                )
            }
        }
    }

    suspend fun deleteTripFromCloud(tripId: String) {
        // Firestore client delete is handled via direct SDK in TripRepositoryImpl
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
            offlinePackSizeMb = (data["offlinePackSizeMb"] as? Number)?.toInt() ?: 0,
            offlinePackDownloaded = data["offlinePackDownloaded"] as? Boolean ?: false
        )
    }
}
