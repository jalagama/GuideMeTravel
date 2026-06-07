package com.guideme.travel.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.guideme.travel.ui.auth.AuthScreen
import com.guideme.travel.ui.auth.AuthViewModel
import com.guideme.travel.ui.consent.ConsentScreen
import com.guideme.travel.ui.consent.ConsentViewModel
import com.guideme.travel.ui.home.HomeScreen
import com.guideme.travel.ui.home.HomeViewModel
import com.guideme.travel.ui.itinerary.ItineraryScreen
import com.guideme.travel.ui.itinerary.ItineraryViewModel
import com.guideme.travel.ui.map.TripMapScreen
import com.guideme.travel.ui.map.TripMapViewModel
import com.guideme.travel.ui.navigation.AuthRoute
import com.guideme.travel.ui.navigation.ConsentRoute
import com.guideme.travel.ui.navigation.DownloadRoute
import com.guideme.travel.ui.navigation.GuidePlayerRoute
import com.guideme.travel.ui.navigation.HomeRoute
import com.guideme.travel.ui.navigation.ItineraryRoute
import com.guideme.travel.ui.navigation.OnboardingRoute
import com.guideme.travel.ui.navigation.TripMapRoute
import com.guideme.travel.ui.navigation.TripSummaryRoute
import com.guideme.travel.ui.offline.DownloadPackScreen
import com.guideme.travel.ui.offline.DownloadPackViewModel
import com.guideme.travel.ui.onboarding.OnboardingScreen
import com.guideme.travel.ui.player.GuidePlayerScreen
import com.guideme.travel.ui.player.GuidePlayerViewModel
import com.guideme.travel.ui.summary.TripSummaryScreen
import com.guideme.travel.ui.summary.TripSummaryViewModel

@Composable
fun GuideMeApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = OnboardingRoute
    ) {
        composable<OnboardingRoute> {
            OnboardingScreen(
                onContinue = {
                    navController.navigate(ConsentRoute) {
                        popUpTo(OnboardingRoute) { inclusive = true }
                    }
                }
            )
        }

        composable<ConsentRoute> {
            val viewModel: ConsentViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()
            ConsentScreen(
                uiState = uiState,
                onPrivacyChanged = viewModel::togglePrivacy,
                onLocationChanged = viewModel::toggleLocation,
                onContinue = {
                    viewModel.saveConsent {
                        navController.navigate(AuthRoute)
                    }
                }
            )
        }

        composable<AuthRoute> {
            val viewModel: AuthViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()
            AuthScreen(
                uiState = uiState,
                onSignIn = {
                    viewModel.signIn {
                        navController.navigate(HomeRoute) {
                            popUpTo(AuthRoute) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable<HomeRoute> {
            val viewModel: HomeViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()
            HomeScreen(
                uiState = uiState,
                onOriginChange = viewModel::updateOrigin,
                onDestinationChange = viewModel::updateDestination,
                onLanguageChange = viewModel::updateLanguage,
                onCreatePlan = {
                    viewModel.createPlan { tripId ->
                        navController.navigate(ItineraryRoute(tripId))
                    }
                },
                onOpenTrip = { tripId ->
                    navController.navigate(ItineraryRoute(tripId))
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
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable<DownloadRoute> { entry ->
            val route = entry.toRoute<DownloadRoute>()
            val viewModel: DownloadPackViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()
            DownloadPackScreen(
                uiState = uiState,
                onStartDownload = { viewModel.download(route.tripId) },
                onDone = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
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
                onCompleteTrip = {
                    viewModel.completeTrip()
                    navController.navigate(TripSummaryRoute(route.tripId)) {
                        popUpTo(HomeRoute)
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
                onStop = viewModel::stop,
                onBack = { navController.popBackStack() }
            )
        }

        composable<TripSummaryRoute> { entry ->
            val route = entry.toRoute<TripSummaryRoute>()
            val viewModel: TripSummaryViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()
            TripSummaryScreen(
                uiState = uiState,
                onDeleteOfflineData = { viewModel.deleteOfflineData(route.tripId) },
                onDone = {
                    navController.navigate(HomeRoute) {
                        popUpTo(HomeRoute) { inclusive = false }
                    }
                }
            )
        }
    }
}
