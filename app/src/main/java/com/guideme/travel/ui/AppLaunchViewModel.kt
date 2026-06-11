package com.guideme.travel.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavBackStackEntry
import com.guideme.travel.domain.analytics.AnalyticsEvents
import com.guideme.travel.domain.analytics.AnalyticsParams
import com.guideme.travel.domain.analytics.GuideMeAnalytics
import com.guideme.travel.domain.model.AppLaunchState
import com.guideme.travel.domain.usecase.ObserveAppLaunchStateUseCase
import com.guideme.travel.domain.usecase.ObserveAuthStateUseCase
import com.guideme.travel.ui.navigation.resolveAnalyticsScreenName
import com.guideme.travel.ui.navigation.resolveAnalyticsScreenParams
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppLaunchViewModel @Inject constructor(
    observeAppLaunchStateUseCase: ObserveAppLaunchStateUseCase,
    private val observeAuthStateUseCase: ObserveAuthStateUseCase,
    private val analytics: GuideMeAnalytics
) : ViewModel() {

    val launchState: StateFlow<AppLaunchState> = observeAppLaunchStateUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppLaunchState(onboardingComplete = false, isAuthenticated = false)
        )

    private var lastTrackedScreen: String? = null

    init {
        analytics.logEvent(
            AnalyticsEvents.SESSION_START,
            mapOf(
                AnalyticsParams.ONBOARDING_COMPLETE to true,
                AnalyticsParams.AUTHENTICATED to false
            )
        )

        viewModelScope.launch {
            observeAuthStateUseCase()
                .distinctUntilChanged { old, new -> old?.uid == new?.uid }
                .collect { user ->
                    analytics.setUserId(user?.uid)
                    analytics.setUserProperty("is_anonymous", user?.isAnonymous?.toString())
                    analytics.setUserProperty(
                        AnalyticsParams.AUTHENTICATED,
                        (user != null).toString()
                    )
                }
        }

        viewModelScope.launch {
            launchState.collect { state ->
                analytics.setUserProperty(
                    AnalyticsParams.ONBOARDING_COMPLETE,
                    state.onboardingComplete.toString()
                )
            }
        }
    }

    fun trackScreen(entry: NavBackStackEntry?) {
        val screenName = resolveAnalyticsScreenName(entry)
        if (screenName == lastTrackedScreen) return
        lastTrackedScreen = screenName
        analytics.logScreen(screenName)
        val params = resolveAnalyticsScreenParams(entry)
        if (params.isNotEmpty()) {
            analytics.logEvent(
                AnalyticsEvents.SCREEN_VIEW,
                mapOf(AnalyticsParams.SCREEN_NAME to screenName) + params
            )
        }
    }
}
