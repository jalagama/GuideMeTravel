package com.guideme.travel.util

import android.content.Context
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class PlaceSuggestion(
    val placeId: String,
    val primaryText: String,
    val fullText: String
)

@Singleton
class PlacesAutocompleteHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sessionToken = AutocompleteSessionToken.newInstance()

    private val placesClient: PlacesClient? by lazy {
        if (Places.isInitialized()) {
            Places.createClient(context)
        } else {
            null
        }
    }

    suspend fun fetchPredictions(query: String): List<PlaceSuggestion> {
        if (query.length < 2) return emptyList()
        val client = placesClient ?: return emptyList()

        val request = FindAutocompletePredictionsRequest.builder()
            .setSessionToken(sessionToken)
            .setQuery(query)
            .build()

        return suspendCancellableCoroutine { continuation ->
            client.findAutocompletePredictions(request)
                .addOnSuccessListener { response ->
                    continuation.resume(
                        response.autocompletePredictions.map { it.toSuggestion() }
                    )
                }
                .addOnFailureListener { error ->
                    continuation.resumeWithException(error)
                }
        }
    }

    private fun AutocompletePrediction.toSuggestion(): PlaceSuggestion {
        return PlaceSuggestion(
            placeId = placeId,
            primaryText = getPrimaryText(null).toString(),
            fullText = getFullText(null).toString()
        )
    }
}
