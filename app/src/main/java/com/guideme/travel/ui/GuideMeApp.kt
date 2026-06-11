package com.guideme.travel.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.guideme.travel.ui.auth.AuthScreen
import com.guideme.travel.ui.booking.BookTripScreen
import com.guideme.travel.ui.booking.BookTripViewModel
import com.guideme.travel.ui.auth.AuthViewModel
import com.guideme.travel.ui.components.GuideMeScaffold
import com.guideme.travel.ui.components.MainTab
import com.guideme.travel.ui.genre.GenreDetailScreen
import com.guideme.travel.ui.genre.GenreDetailViewModel
import com.guideme.travel.ui.home.HomeScreen
import com.guideme.travel.ui.home.HomeViewModel
import com.guideme.travel.ui.itinerary.ItineraryScreen
import com.guideme.travel.ui.itinerary.ItineraryViewModel
import com.guideme.travel.ui.map.TripMapScreen
import com.guideme.travel.ui.map.TripMapViewModel
import com.guideme.travel.ui.navigation.AuthRoute
import com.guideme.travel.ui.navigation.BookTripRoute
import com.guideme.travel.ui.navigation.DownloadRoute
import com.guideme.travel.ui.navigation.GenreDetailRoute
import com.guideme.travel.ui.navigation.GuidePlayerRoute
import com.guideme.travel.ui.navigation.HomeRoute
import com.guideme.travel.ui.navigation.ItineraryRoute
import com.guideme.travel.ui.navigation.TourPackageDetailRoute
import com.guideme.travel.ui.navigation.TripMapRoute
import com.guideme.travel.ui.navigation.TripSummaryRoute
import com.guideme.travel.ui.navigation.TripsRoute
import com.guideme.travel.ui.navigation.resolveShellState
import com.guideme.travel.ui.offline.DownloadPackScreen
import com.guideme.travel.ui.offline.DownloadPackViewModel
import com.guideme.travel.ui.package_detail.TourPackageDetailScreen
import com.guideme.travel.ui.package_detail.TourPackageDetailViewModel
import com.guideme.travel.ui.player.GuidePlayerScreen
import com.guideme.travel.ui.player.GuidePlayerViewModel
import com.guideme.travel.ui.summary.TripSummaryScreen
import com.guideme.travel.ui.summary.TripSummaryViewModel
import com.guideme.travel.ui.trips.TripsScreen
import com.guideme.travel.ui.trips.TripsViewModel

@Composable
fun GuideMeApp(
    onGoogleSignIn: () -> Unit = {}
) {
    val navController = rememberNavController()
    val launchViewModel: AppLaunchViewModel = hiltViewModel()
    val launchState by launchViewModel.launchState.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(navBackStackEntry) {
        launchViewModel.trackScreen(navBackStackEntry)
    }
    val onAuthScreen = navBackStackEntry?.destination?.route?.contains("AuthRoute") == true
    val showShell = launchState.isAuthenticated && !onAuthScreen
    val shellState = resolveShellState(navBackStackEntry)

    LaunchedEffect(launchState.isAuthenticated) {
        if (!launchState.isAuthenticated) return@LaunchedEffect
        val currentIsAuth = navController.currentDestination?.route?.contains("AuthRoute") == true
        if (currentIsAuth) {
            navController.navigate(HomeRoute) {
                popUpTo(AuthRoute) { inclusive = true }
            }
        }
    }

    val navigateTab: (MainTab) -> Unit = { tab ->
        when (tab) {
            MainTab.Home -> navController.navigate(HomeRoute) {
                popUpTo(HomeRoute) { inclusive = false }
                launchSingleTop = true
            }
            MainTab.Trips -> navController.navigate(TripsRoute) {
                popUpTo(HomeRoute) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    val content: @Composable (Modifier) -> Unit = { modifier ->
        NavHost(
            navController = navController,
            startDestination = AuthRoute,
            modifier = modifier
        ) {
            composable<AuthRoute> {
                val viewModel: AuthViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsState()
                AuthScreen(
                    uiState = uiState,
                    onSignInAnonymously = { viewModel.signInAnonymously { } },
                    onSignInWithGoogle = onGoogleSignIn,
                    onSendSignInLink = viewModel::sendSignInLink,
                    onEmailChange = viewModel::updateEmail
                )
            }

            composable<HomeRoute> {
                val viewModel: HomeViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsState()
                HomeScreen(
                    uiState = uiState,
                    onSearchQueryChange = viewModel::updateSearchQuery,
                    onSelectSuggestion = { suggestion ->
                        viewModel.selectDestinationSuggestion(suggestion) { tripId ->
                            navController.navigate(ItineraryRoute(tripId))
                        }
                    },
                    onClearSuggestions = viewModel::clearSuggestions,
                    onOpenGenre = { countryCode, genreId ->
                        navController.navigate(GenreDetailRoute(countryCode, genreId))
                    },
                    onOpenTrip = { tripId ->
                        navController.navigate(ItineraryRoute(tripId))
                    },
                    onLocationPermissionResult = viewModel::reloadAfterLocationGranted
                )
            }

            composable<TripsRoute> {
                val viewModel: TripsViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsState()
                TripsScreen(
                    uiState = uiState,
                    onOpenTrip = { tripId ->
                        navController.navigate(ItineraryRoute(tripId))
                    }
                )
            }

            composable<GenreDetailRoute> { entry ->
                val genreRoute = entry.toRoute<GenreDetailRoute>()
                val viewModel: GenreDetailViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsState()
                GenreDetailScreen(
                    uiState = uiState,
                    onOpenPackage = { packageId ->
                        navController.navigate(
                            TourPackageDetailRoute(
                                packageId = packageId,
                                countryCode = genreRoute.countryCode,
                                genreId = genreRoute.genreId
                            )
                        )
                    }
                )
            }

            composable<TourPackageDetailRoute> { entry ->
                val detailRoute = entry.toRoute<TourPackageDetailRoute>()
                val viewModel: TourPackageDetailViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsState()
                TourPackageDetailScreen(
                    uiState = uiState,
                    onBookTrip = {
                        navController.navigate(
                            BookTripRoute(
                                packageId = detailRoute.packageId,
                                countryCode = detailRoute.countryCode,
                                genreId = detailRoute.genreId
                            )
                        )
                    },
                    onPlayPreview = { spotId, _ -> viewModel.setPlayingSpot(spotId) },
                    onStopPreview = { viewModel.setPlayingSpot(null) }
                )
            }

            composable<BookTripRoute> { entry ->
                val bookRoute = entry.toRoute<BookTripRoute>()
                val viewModel: BookTripViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsState()
                BookTripScreen(
                    uiState = uiState,
                    onDownloadOffline = {
                        viewModel.bookForDownload { tripId ->
                            navController.navigate(DownloadRoute(tripId))
                        }
                    },
                    onStartOnline = {
                        viewModel.bookForOnline { tripId ->
                            navController.navigate(ItineraryRoute(tripId))
                        }
                    }
                )
            }

            composable<ItineraryRoute> { entry ->
                val route = entry.toRoute<ItineraryRoute>()
                val viewModel: ItineraryViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsState()
                ItineraryScreen(
                    uiState = uiState,
                    onDownload = { navController.navigate(DownloadRoute(route.tripId)) },
                    onStartTrip = {
                        viewModel.startTrip(route.tripId)
                        navController.navigate(TripMapRoute(route.tripId))
                    }
                )
            }

            composable<DownloadRoute> { entry ->
                val route = entry.toRoute<DownloadRoute>()
                val viewModel: DownloadPackViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsState()
                DownloadPackScreen(
                    uiState = uiState,
                    onStartDownload = { viewModel.download(route.tripId) },
                    onCancelDownload = { viewModel.cancelDownload() },
                    onStartOnline = {
                        viewModel.startTripOnline()
                        navController.navigate(TripMapRoute(route.tripId)) {
                            popUpTo(ItineraryRoute(route.tripId)) { inclusive = false }
                        }
                    },
                    onDone = { navController.popBackStack() }
                )
            }

            composable<TripMapRoute> { entry ->
                val route = entry.toRoute<TripMapRoute>()
                val viewModel: TripMapViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsState()
                TripMapScreen(
                    uiState = uiState,
                    onStartGuideService = viewModel::startGuideService,
                    onPlayGuide = { attractionId ->
                        navController.navigate(GuidePlayerRoute(route.tripId, attractionId))
                    },
                    onRecenter = viewModel::toggleFollowUser,
                    onCompleteTrip = {
                        viewModel.completeTrip {
                            navController.navigate(TripSummaryRoute(route.tripId)) {
                                popUpTo(HomeRoute)
                            }
                        }
                    }
                )
            }

            composable<GuidePlayerRoute> { entry ->
                val route = entry.toRoute<GuidePlayerRoute>()
                val viewModel: GuidePlayerViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsState()
                GuidePlayerScreen(
                    uiState = uiState,
                    onPlay = { viewModel.play(route.tripId, route.attractionId) },
                    onStop = viewModel::stop
                )
            }

            composable<TripSummaryRoute> { entry ->
                val route = entry.toRoute<TripSummaryRoute>()
                val viewModel: TripSummaryViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsState()
                TripSummaryScreen(
                    uiState = uiState,
                    onDeleteOfflineData = { viewModel.deleteOfflineData(route.tripId) },
                    onDeleteTrip = {
                        viewModel.deleteTrip(route.tripId) {
                            navController.navigate(HomeRoute) {
                                popUpTo(HomeRoute) { inclusive = false }
                            }
                        }
                    },
                    onDone = {
                        navController.navigate(HomeRoute) {
                            popUpTo(HomeRoute) { inclusive = false }
                        }
                    }
                )
            }
        }
    }

    if (showShell) {
        GuideMeScaffold(
            title = shellState.title,
            showBack = shellState.showBack,
            selectedTab = shellState.selectedTab,
            onBack = { navController.popBackStack() },
            onTabSelected = navigateTab,
            modifier = Modifier.fillMaxSize()
        ) { padding ->
            content(Modifier.fillMaxSize().padding(padding))
        }
    } else {
        content(Modifier.fillMaxSize())
    }
}
