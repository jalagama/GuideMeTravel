package com.guideme.travel.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.appcheck.FirebaseAppCheck
import com.guideme.travel.BuildConfig
import com.guideme.travel.domain.model.Attraction
import com.guideme.travel.domain.model.AttractionStatus
import com.guideme.travel.domain.model.TripPlan
import com.guideme.travel.domain.model.TripStatus
import com.guideme.travel.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
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
    private val authRepository: AuthRepository,
    private val firebaseAuth: FirebaseAuth
) {
    suspend fun generateItinerary(
        origin: String,
        destination: String,
        languageCode: String,
        countryCode: String
    ): TripPlan {
        authRepository.ensureSignedIn()
        val uid = firebaseAuth.currentUser?.uid
            ?: error("Not signed in. Enable Anonymous Auth in Firebase Console.")
        authRepository.getIdToken(forceRefresh = true)

        if (!BuildConfig.DEBUG) {
            FirebaseAppCheck.getInstance().getAppCheckToken(true).await()
        }

        val result = try {
            functions
                .getHttpsCallable("generateItinerary")
                .call(
                    mapOf(
                        "origin" to origin,
                        "destination" to destination,
                        "languageCode" to languageCode,
                        "countryCode" to countryCode
                    )
                )
                .await()
        } catch (error: Exception) {
            throw mapRemoteError(error, uid)
        }

        @Suppress("UNCHECKED_CAST")
        val data = result.getData() as Map<String, Any?>
        return parseTripPlan(data)
    }

    suspend fun generateGuidePack(tripId: String): List<GuidePackFile> = withContext(Dispatchers.IO) {
        authRepository.ensureSignedIn()
        val uid = firebaseAuth.currentUser?.uid
            ?: error("Not signed in. Enable Anonymous Auth in Firebase Console.")
        authRepository.getIdToken(forceRefresh = true)

        if (!BuildConfig.DEBUG) {
            FirebaseAppCheck.getInstance().getAppCheckToken(true).await()
        }

        val result = try {
            functions
                .getHttpsCallable("getGuidePackForTripCallable")
                .call(mapOf("tripId" to tripId))
                .await()
        } catch (readOnlyError: Exception) {
            try {
                functions
                    .getHttpsCallable("generateGuidePack")
                    .call(mapOf("tripId" to tripId))
                    .await()
            } catch (error: Exception) {
                throw mapRemoteError(error, uid)
            }
        }

        @Suppress("UNCHECKED_CAST")
        val data = result.getData() as Map<String, Any?>
        parseGuidePackFiles(data)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseGuidePackFiles(data: Map<String, Any?>): List<GuidePackFile> {
        val audioFiles = data["audioFiles"] as? List<Map<String, Any?>> ?: emptyList()
        return audioFiles.map { file ->
            GuidePackFile(
                attractionId = file["attractionId"] as String,
                url = file["url"] as String,
                storagePath = (file["storagePath"] as? String)?.ifBlank { null },
                transcript = file["transcript"] as String
            )
        }
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

    suspend fun deleteTripFromCloud(tripId: String) {
        // Firestore client delete is handled via direct SDK in TripRepositoryImpl
    }

    private fun mapRemoteError(error: Exception, uid: String): Exception {
        val rawMessage = error.message.orEmpty()

        if (rawMessage.contains("not authorized to invoke this service", ignoreCase = true) ||
            rawMessage.contains("access token could not be verified", ignoreCase = true)
        ) {
            return IllegalStateException(
                "Cloud Run blocked the function call (IAM). Redeploy with invoker: public, then run: " +
                    "gcloud run services add-iam-policy-binding generateitinerary " +
                    "--region=asia-south1 --member=allUsers --role=roles/run.invoker " +
                    "--project=travelguide-47f80",
                error
            )
        }

        if (error is FirebaseFunctionsException) {
            val details = error.details?.toString()?.takeIf { it.isNotBlank() }
            val baseMessage = listOfNotNull(error.message, details).joinToString(" — ")

            if (error.code == FirebaseFunctionsException.Code.UNAUTHENTICATED) {
                val hint = if (BuildConfig.DEBUG) {
                    "Debug checklist: (1) Firebase Console > App Check > APIs > set Cloud Functions " +
                        "and Authentication to Unenforced, (2) Authentication > Anonymous enabled, " +
                        "(3) deploy generateItinerary to asia-south1 in project travelguide-47f80, " +
                        "(4) signed-in uid was $uid."
                } else {
                    "Confirm you are signed in and App Check (Play Integrity) is configured for release."
                }
                return IllegalStateException("$baseMessage. $hint", error)
            }

            if (error.code == FirebaseFunctionsException.Code.NOT_FOUND) {
                return IllegalStateException(
                    "$baseMessage. Deploy backend/functions to region asia-south1.",
                    error
                )
            }

            if (error.code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED) {
                return IllegalStateException(
                    "$baseMessage. You hit the itinerary rate limit (30/hour). " +
                        "In Firestore delete rateLimits/itinerary:$uid or wait up to 1 hour.",
                    error
                )
            }
        }

        return error
    }
}
