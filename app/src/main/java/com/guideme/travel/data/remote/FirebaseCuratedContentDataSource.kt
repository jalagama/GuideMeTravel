package com.guideme.travel.data.remote

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.guideme.travel.BuildConfig
import com.guideme.travel.domain.model.Attraction
import com.guideme.travel.domain.model.AttractionStatus
import com.guideme.travel.domain.model.CountryGenres
import com.guideme.travel.domain.model.CuratedGenre
import com.guideme.travel.domain.model.CuratedSpot
import com.guideme.travel.domain.model.GenrePackages
import com.guideme.travel.domain.model.NearbyPlace
import com.guideme.travel.domain.model.TourPackageDetail
import com.guideme.travel.domain.model.TourPackageSummary
import com.guideme.travel.domain.model.TripPlan
import com.guideme.travel.domain.model.TripStatus
import com.guideme.travel.domain.logging.GuideMeLogger
import com.guideme.travel.domain.repository.AuthRepository
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseCuratedContentDataSource @Inject constructor(
    private val functions: FirebaseFunctions,
    private val authRepository: AuthRepository,
    private val firebaseAuth: FirebaseAuth,
    private val logger: GuideMeLogger
) {
    suspend fun getCountryGenres(countryCode: String): CountryGenres {
        val data = callFunction(
            "getCountryGenres",
            mapOf("countryCode" to countryCode)
        )
        @Suppress("UNCHECKED_CAST")
        val genresRaw = data["genres"] as? List<Map<String, Any?>> ?: emptyList()
        return CountryGenres(
            countryCode = data["countryCode"] as? String ?: countryCode,
            countryName = data["countryName"] as? String ?: countryCode,
            genres = genresRaw.map { parseGenre(it) },
            schemaVersion = (data["schemaVersion"] as? Number)?.toInt() ?: 3,
            updatedAtMillis = (data["updatedAtMillis"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }

    suspend fun getGenrePackages(countryCode: String, genreId: String): GenrePackages {
        val data = callFunction(
            "getGenrePackages",
            mapOf(
                "countryCode" to countryCode,
                "genreId" to genreId
            )
        )
        @Suppress("UNCHECKED_CAST")
        val packagesRaw = data["packages"] as? List<Map<String, Any?>> ?: emptyList()
        return GenrePackages(
            countryCode = data["countryCode"] as? String ?: countryCode,
            genreId = data["genreId"] as? String ?: genreId,
            genreName = data["genreName"] as? String ?: genreId,
            packages = packagesRaw.map { parsePackageSummary(it) },
            schemaVersion = (data["schemaVersion"] as? Number)?.toInt() ?: 3,
            updatedAtMillis = (data["updatedAtMillis"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }

    suspend fun getTourPackageDetail(
        packageId: String,
        countryCode: String,
        genreId: String
    ): TourPackageDetail {
        val data = callFunction(
            "getTourPackageDetail",
            mapOf(
                "packageId" to packageId,
                "countryCode" to countryCode,
                "genreId" to genreId
            )
        )
        return parseTourPackageDetail(data)
    }

    suspend fun createTripFromPackage(
        packageId: String,
        countryCode: String,
        genreId: String,
        origin: String,
        languageCode: String
    ): TripPlan {
        val data = callFunction(
            "createTripFromPackage",
            mapOf(
                "packageId" to packageId,
                "countryCode" to countryCode,
                "genreId" to genreId,
                "origin" to origin,
                "languageCode" to languageCode
            )
        )
        return parseTripPlan(data)
    }

    private suspend fun callFunction(
        name: String,
        payload: Map<String, Any>
    ): Map<String, Any?> {
        logger.logRemoteRequest(name, payload.mapValues { it.value })
        authRepository.ensureSignedIn()
        authRepository.getIdToken(forceRefresh = true)
        if (!BuildConfig.DEBUG) {
            FirebaseAppCheck.getInstance().getAppCheckToken(true).await()
        }

        val result = try {
            functions.getHttpsCallable(name).call(payload).await()
        } catch (error: Exception) {
            logger.logRemoteError(name, error, payload.mapValues { it.value })
            throw mapRemoteError(error)
        }
        @Suppress("UNCHECKED_CAST")
        val data = result.getData() as Map<String, Any?>
        logger.logRemoteResponse(name, summarizeResponse(name, data))
        return data
    }

    private fun summarizeResponse(functionName: String, data: Map<String, Any?>): Map<String, Any?> {
        return when (functionName) {
            "getCountryGenres" -> mapOf(
                "countryCode" to data["countryCode"],
                "genreCount" to (data["genres"] as? List<*>)?.size,
                "schemaVersion" to data["schemaVersion"]
            )
            "getGenrePackages" -> mapOf(
                "countryCode" to data["countryCode"],
                "genreId" to data["genreId"],
                "packageCount" to (data["packages"] as? List<*>)?.size,
                "schemaVersion" to data["schemaVersion"]
            )
            "getTourPackageDetail" -> mapOf(
                "packageId" to data["id"],
                "spotCount" to (data["spots"] as? List<*>)?.size,
                "days" to data["days"]
            )
            "createTripFromPackage" -> mapOf(
                "tripId" to data["tripId"],
                "attractionCount" to (data["attractions"] as? List<*>)?.size
            )
            else -> mapOf("keys" to data.keys.joinToString(","))
        }
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

    private fun mapRemoteError(error: Exception): Exception {
        if (error is FirebaseFunctionsException) {
            val details = error.details?.toString()?.takeIf { it.isNotBlank() }
            val baseMessage = listOfNotNull(error.message, details).joinToString(" — ")
            return IllegalStateException(baseMessage, error)
        }
        return error
    }
}
