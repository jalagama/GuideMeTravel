package com.guideme.travel.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.guideme.travel.data.local.CURATED_SCHEMA_VERSION
import com.guideme.travel.domain.model.CountryGenres
import com.guideme.travel.domain.model.CuratedGenre
import com.guideme.travel.domain.model.CuratedSpot
import com.guideme.travel.domain.model.GenrePackages
import com.guideme.travel.domain.model.NearbyPlace
import com.guideme.travel.domain.model.TourPackageDetail
import com.guideme.travel.domain.model.TourPackageSummary
import com.guideme.travel.domain.logging.GuideMeLogger
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreCuratedContentDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val logger: GuideMeLogger
) {
    suspend fun getCountryGenres(countryCode: String): CountryGenres {
        val normalized = countryCode.trim().uppercase()
        logger.logRemoteRequest("firestore.getCountryGenres", mapOf("countryCode" to normalized))
        val snap = firestore.collection("curatedGenres").document(normalized).get().await()
        if (!snap.exists()) {
            error("Curated genres for $normalized are not available yet.")
        }
        val data = snap.data ?: error("Empty genres document for $normalized")
        val schemaVersion = (data["schemaVersion"] as? Number)?.toInt() ?: 0
        if (schemaVersion < CURATED_SCHEMA_VERSION) {
            error("Curated genres for $normalized are outdated (schema $schemaVersion).")
        }
        @Suppress("UNCHECKED_CAST")
        val genresRaw = data["genres"] as? List<Map<String, Any?>> ?: emptyList()
        return CountryGenres(
            countryCode = data["countryCode"] as? String ?: normalized,
            countryName = data["countryName"] as? String ?: normalized,
            genres = genresRaw.map { parseGenre(it) },
            schemaVersion = schemaVersion,
            updatedAtMillis = (data["updatedAtMillis"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }

    suspend fun getGenrePackages(countryCode: String, genreId: String): GenrePackages {
        val normalized = countryCode.trim().uppercase()
        val docId = "${normalized}_$genreId"
        logger.logRemoteRequest("firestore.getGenrePackages", mapOf("docId" to docId))
        val snap = firestore.collection("curatedPackages").document(docId).get().await()
        if (!snap.exists()) {
            error("Curated packages for $docId are not available yet.")
        }
        val data = snap.data ?: error("Empty packages document for $docId")
        val schemaVersion = (data["schemaVersion"] as? Number)?.toInt() ?: 0
        if (schemaVersion < CURATED_SCHEMA_VERSION) {
            error("Curated packages for $docId are outdated.")
        }
        @Suppress("UNCHECKED_CAST")
        val packagesRaw = data["packages"] as? List<Map<String, Any?>> ?: emptyList()
        return GenrePackages(
            countryCode = data["countryCode"] as? String ?: normalized,
            genreId = data["genreId"] as? String ?: genreId,
            genreName = data["genreName"] as? String ?: genreId,
            packages = packagesRaw.map { parsePackageSummary(it) },
            schemaVersion = schemaVersion,
            updatedAtMillis = (data["updatedAtMillis"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }

    suspend fun getTourPackageDetail(packageId: String): TourPackageDetail {
        logger.logRemoteRequest("firestore.getTourPackageDetail", mapOf("packageId" to packageId))
        val snap = firestore.collection("tourPackages").document(packageId).get().await()
        if (!snap.exists()) {
            error("Tour package $packageId is not available yet.")
        }
        val data = snap.data ?: error("Empty tour package $packageId")
        val schemaVersion = (data["schemaVersion"] as? Number)?.toInt() ?: 0
        if (schemaVersion < CURATED_SCHEMA_VERSION) {
            error("Tour package $packageId is outdated.")
        }
        return parseTourPackageDetail(data)
    }

    private fun parseGenre(data: Map<String, Any?>): CuratedGenre {
        return CuratedGenre(
            id = data["id"] as String,
            name = data["name"] as String,
            type = data["type"] as? String ?: "region",
            imageUrl = data["imageUrl"] as? String ?: "",
            blurb = data["blurb"] as? String ?: "",
            rank = (data["rank"] as? Number)?.toInt() ?: 0
        )
    }

    private fun parsePackageSummary(data: Map<String, Any?>): TourPackageSummary {
        return TourPackageSummary(
            id = data["id"] as String,
            title = data["title"] as String,
            region = data["region"] as String,
            days = (data["days"] as? Number)?.toInt() ?: 1,
            heroImageUrl = data["heroImageUrl"] as? String ?: "",
            shortInfo = data["shortInfo"] as? String ?: "",
            rank = (data["rank"] as? Number)?.toInt() ?: 0,
            bestFor = data["bestFor"] as? String ?: "",
            seasonality = data["seasonality"] as? String
        )
    }

    private fun parseTourPackageDetail(data: Map<String, Any?>): TourPackageDetail {
        @Suppress("UNCHECKED_CAST")
        val spotsRaw = data["spots"] as? List<Map<String, Any?>> ?: emptyList()
        return TourPackageDetail(
            id = data["id"] as String,
            countryCode = data["countryCode"] as? String ?: "",
            genreId = data["genreId"] as? String ?: "",
            title = data["title"] as String,
            region = data["region"] as String,
            days = (data["days"] as? Number)?.toInt() ?: 1,
            heroImageUrl = data["heroImageUrl"] as? String ?: "",
            overview = data["overview"] as? String ?: "",
            daySummaries = parseDaySummaries(data["daySummaries"]),
            spots = spotsRaw.mapIndexed { index, spot -> parseSpot(spot, index) },
            tips = parseStringList(data["tips"]),
            essentials = parseStringList(data["essentials"]),
            highlights = parseStringList(data["highlights"]),
            hotels = parseNearbyPlaces(data["hotels"]),
            restaurants = parseNearbyPlaces(data["restaurants"]),
            updatedAtMillis = (data["createdAtMillis"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }

    private fun parseSpot(data: Map<String, Any?>, fallbackIndex: Int): CuratedSpot {
        return CuratedSpot(
            id = data["id"] as? String ?: "spot-$fallbackIndex",
            name = data["name"] as String,
            description = data["description"] as? String ?: "",
            latitude = (data["latitude"] as Number).toDouble(),
            longitude = (data["longitude"] as Number).toDouble(),
            imageUrl = data["imageUrl"] as? String,
            orderIndex = (data["orderIndex"] as? Number)?.toInt() ?: fallbackIndex,
            day = (data["day"] as? Number)?.toInt() ?: 1,
            whyChosen = data["whyChosen"] as? String,
            previewSnippet = data["previewSnippet"] as? String,
            transcript = data["transcript"] as? String,
            estimatedMinutes = (data["estimatedMinutes"] as? Number)?.toInt() ?: 45
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseDaySummaries(raw: Any?): Map<String, String> {
        val map = raw as? Map<String, Any?> ?: return emptyMap()
        return map.mapNotNull { (key, value) ->
            value?.toString()?.let { key to it }
        }.toMap()
    }

    private fun parseStringList(raw: Any?): List<String> {
        val items = raw as? List<*> ?: return emptyList()
        return items.mapNotNull { it?.toString() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseNearbyPlaces(raw: Any?): List<NearbyPlace> {
        val items = raw as? List<Map<String, Any?>> ?: return emptyList()
        return items.map { item ->
            NearbyPlace(
                name = item["name"] as String,
                description = item["description"] as? String ?: "",
                latitude = (item["latitude"] as? Number)?.toDouble(),
                longitude = (item["longitude"] as? Number)?.toDouble(),
                rating = (item["rating"] as? Number)?.toDouble()
            )
        }
    }
}
