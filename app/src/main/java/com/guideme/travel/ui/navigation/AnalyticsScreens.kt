package com.guideme.travel.ui.navigation

import androidx.navigation.NavBackStackEntry
import androidx.navigation.toRoute

fun resolveAnalyticsScreenName(entry: NavBackStackEntry?): String {
    if (entry == null) return "unknown"

    return runCatching { entry.toRoute<AuthRoute>() }
        .map { "auth" }
        .getOrElse {
            runCatching { entry.toRoute<HomeRoute>() }
                .map { "home" }
                .getOrElse {
                    runCatching { entry.toRoute<TripsRoute>() }
                        .map { "trips" }
                        .getOrElse {
                            runCatching { entry.toRoute<GenreDetailRoute>() }
                                .map { "genre_detail" }
                                .getOrElse {
                                    runCatching { entry.toRoute<TourPackageDetailRoute>() }
                                        .map { "package_detail" }
                                        .getOrElse {
                                            runCatching { entry.toRoute<BookTripRoute>() }
                                                .map { "book_trip" }
                                                .getOrElse {
                                                    runCatching { entry.toRoute<ItineraryRoute>() }
                                                        .map { "itinerary" }
                                                        .getOrElse {
                                                            runCatching { entry.toRoute<DownloadRoute>() }
                                                                .map { "offline_download" }
                                                                .getOrElse {
                                                                    runCatching { entry.toRoute<TripMapRoute>() }
                                                                        .map { "trip_map" }
                                                                        .getOrElse {
                                                                            runCatching { entry.toRoute<GuidePlayerRoute>() }
                                                                                .map { "guide_player" }
                                                                                .getOrElse {
                                                                                    runCatching { entry.toRoute<TripSummaryRoute>() }
                                                                                        .map { "trip_summary" }
                                                                                        .getOrElse { "unknown" }
                                                                                }
                                                                        }
                                                                }
                                                        }
                                                }
                                        }
                                }
                        }
                }
        }
}

fun resolveAnalyticsScreenParams(entry: NavBackStackEntry?): Map<String, Any?> {
    if (entry == null) return emptyMap()

    return runCatching { entry.toRoute<GenreDetailRoute>() }
        .map { mapOf("country_code" to it.countryCode, "genre_id" to it.genreId) }
        .getOrElse {
            runCatching { entry.toRoute<TourPackageDetailRoute>() }
                .map {
                    mapOf(
                        "country_code" to it.countryCode,
                        "genre_id" to it.genreId,
                        "package_id" to it.packageId
                    )
                }
                .getOrElse {
                    runCatching { entry.toRoute<BookTripRoute>() }
                        .map {
                            mapOf(
                                "country_code" to it.countryCode,
                                "genre_id" to it.genreId,
                                "package_id" to it.packageId
                            )
                        }
                        .getOrElse {
                            runCatching { entry.toRoute<ItineraryRoute>() }
                                .map { mapOf("trip_id" to it.tripId) }
                                .getOrElse {
                                    runCatching { entry.toRoute<DownloadRoute>() }
                                        .map { mapOf("trip_id" to it.tripId) }
                                        .getOrElse {
                                            runCatching { entry.toRoute<TripMapRoute>() }
                                                .map { mapOf("trip_id" to it.tripId) }
                                                .getOrElse {
                                                    runCatching { entry.toRoute<GuidePlayerRoute>() }
                                                        .map {
                                                            mapOf(
                                                                "trip_id" to it.tripId,
                                                                "attraction_id" to it.attractionId
                                                            )
                                                        }
                                                        .getOrElse {
                                                            runCatching { entry.toRoute<TripSummaryRoute>() }
                                                                .map { mapOf("trip_id" to it.tripId) }
                                                                .getOrElse { emptyMap() }
                                                        }
                                                }
                                        }
                                }
                        }
                }
        }
}
