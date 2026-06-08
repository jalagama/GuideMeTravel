package com.guideme.travel.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guideme.travel.domain.model.AppLaunchState
import com.guideme.travel.domain.usecase.ObserveAppLaunchStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AppLaunchViewModel @Inject constructor(
    observeAppLaunchStateUseCase: ObserveAppLaunchStateUseCase
) : ViewModel() {

    val launchState: StateFlow<AppLaunchState> = observeAppLaunchStateUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppLaunchState(onboardingComplete = false, isAuthenticated = false)
        )
}
