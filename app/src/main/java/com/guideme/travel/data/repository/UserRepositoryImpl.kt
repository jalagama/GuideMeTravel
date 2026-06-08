package com.guideme.travel.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.guideme.travel.data.local.AttractionDao
import com.guideme.travel.data.local.TripDao
import com.guideme.travel.data.local.toDomain
import com.guideme.travel.data.local.toEntity
import com.guideme.travel.domain.model.Attraction
import com.guideme.travel.domain.model.AttractionStatus
import com.guideme.travel.domain.model.TripPlan
import com.guideme.travel.domain.model.TripStatus
import com.guideme.travel.domain.model.UserProfile
import com.guideme.travel.domain.repository.UserRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val tripDao: TripDao,
    private val attractionDao: AttractionDao
) : UserRepository {

    override fun observeProfile(uid: String): Flow<UserProfile?> = callbackFlow {
        val listener = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null || !snapshot.exists()) {
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(
                    UserProfile(
                        uid = uid,
                        displayName = snapshot.getString("displayName"),
                        email = snapshot.getString("email"),
                        languageCode = snapshot.getString("languageCode") ?: "en",
                        createdAtMillis = snapshot.getLong("createdAtMillis") ?: 0L
                    )
                )
            }
        awaitClose { listener.remove() }
    }

    override suspend fun upsertProfile(profile: UserProfile) {
        firestore.collection("users").document(profile.uid).set(
            mapOf(
                "displayName" to profile.displayName,
                "email" to profile.email,
                "languageCode" to profile.languageCode,
                "createdAtMillis" to profile.createdAtMillis
            )
        ).await()
    }

    override suspend fun syncTripsFromCloud(uid: String) {
        val snapshot = firestore.collection("trips")
            .whereEqualTo("userId", uid)
            .get()
            .await()

        snapshot.documents.forEach { doc ->
            val data = doc.data ?: return@forEach
            val trip = parseTripPlan(doc.id, data)
            tripDao.upsertTrip(trip.toEntity())
            attractionDao.upsertAttractions(trip.attractions.map { it.toEntity(trip.id) })
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseTripPlan(tripId: String, data: Map<String, Any?>): TripPlan {
        val attractionsRaw = data["attractions"] as? List<Map<String, Any?>> ?: emptyList()
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
