package com.guideme.travel.ui.navigation

import androidx.navigation.NavBackStackEntry
import androidx.navigation.toRoute
import com.guideme.travel.ui.components.MainTab

data class ShellState(
    val title: String,
    val showBack: Boolean,
    val selectedTab: MainTab?
)

fun resolveShellState(entry: NavBackStackEntry?): ShellState {
    if (entry == null) {
        return ShellState(title = "GuideMe", showBack = false, selectedTab = MainTab.Home)
    }

    return runCatching { entry.toRoute<HomeRoute>() }
        .map { ShellState(title = "GuideMe", showBack = false, selectedTab = MainTab.Home) }
        .getOrElse {
            runCatching { entry.toRoute<TripsRoute>() }
                .map { ShellState(title = "My trips", showBack = false, selectedTab = MainTab.Trips) }
                .getOrElse {
                    runCatching { entry.toRoute<GenreDetailRoute>() }
                        .map {
                            ShellState(
                                title = "Tour packages",
                                showBack = true,
                                selectedTab = MainTab.Home
                            )
                        }
                        .getOrElse {
                            runCatching { entry.toRoute<TourPackageDetailRoute>() }
                                .map {
                                    ShellState(
                                        title = "Tour details",
                                        showBack = true,
                                        selectedTab = MainTab.Home
                                    )
                                }
                                .getOrElse {
                                    runCatching { entry.toRoute<BookTripRoute>() }
                                        .map {
                                            ShellState(
                                                title = "Book trip",
                                                showBack = true,
                                                selectedTab = MainTab.Home
                                            )
                                        }
                                        .getOrElse {
                                            runCatching { entry.toRoute<ItineraryRoute>() }
                                                .map {
                                                    ShellState(
                                                        title = "Your itinerary",
                                                        showBack = true,
                                                        selectedTab = MainTab.Trips
                                                    )
                                                }
                                                .getOrElse {
                                                    runCatching { entry.toRoute<DownloadRoute>() }
                                                        .map {
                                                            ShellState(
                                                                title = "Offline pack",
                                                                showBack = true,
                                                                selectedTab = MainTab.Trips
                                                            )
                                                        }
                                                        .getOrElse {
                                                            runCatching { entry.toRoute<TripMapRoute>() }
                                                                .map {
                                                                    ShellState(
                                                                        title = "Trip map",
                                                                        showBack = true,
                                                                        selectedTab = MainTab.Trips
                                                                    )
                                                                }
                                                                .getOrElse {
                                                                    runCatching { entry.toRoute<GuidePlayerRoute>() }
                                                                        .map {
                                                                            ShellState(
                                                                                title = "Audio guide",
                                                                                showBack = true,
                                                                                selectedTab = MainTab.Trips
                                                                            )
                                                                        }
                                                                        .getOrElse {
                                                                            runCatching { entry.toRoute<TripSummaryRoute>() }
                                                                                .map {
                                                                                    ShellState(
                                                                                        title = "Trip summary",
                                                                                        showBack = true,
                                                                                        selectedTab = MainTab.Trips
                                                                                    )
                                                                                }
                                                                                .getOrElse {
                                                                                    ShellState(
                                                                                        title = "GuideMe",
                                                                                        showBack = false,
                                                                                        selectedTab = MainTab.Home
                                                                                    )
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
