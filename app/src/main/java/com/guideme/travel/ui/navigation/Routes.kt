package com.guideme.travel.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object OnboardingRoute

@Serializable
object ConsentRoute

@Serializable
object AuthRoute

@Serializable
object HomeRoute

@Serializable
data class ItineraryRoute(val tripId: String)

@Serializable
data class DownloadRoute(val tripId: String)

@Serializable
data class TripMapRoute(val tripId: String)

@Serializable
data class GuidePlayerRoute(val tripId: String, val attractionId: String)

@Serializable
data class TripSummaryRoute(val tripId: String)
