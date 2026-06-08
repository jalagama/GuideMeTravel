package com.guideme.travel.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guideme.travel.domain.model.TripPlan
import com.guideme.travel.domain.usecase.CreateTripUseCase
import com.guideme.travel.domain.usecase.ObserveDefaultLanguageUseCase
import com.guideme.travel.domain.usecase.ObserveTripsUseCase
import com.guideme.travel.domain.usecase.SetDefaultLanguageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val origin: String = "Bangalore",
    val destination: String = "Hampi",
    val languageCode: String = "en",
    val isLoading: Boolean = false,
    val recentTrips: List<TripPlan> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val createTripUseCase: CreateTripUseCase,
    private val observeTripsUseCase: ObserveTripsUseCase,
    private val observeDefaultLanguageUseCase: ObserveDefaultLanguageUseCase,
    private val setDefaultLanguageUseCase: SetDefaultLanguageUseCase
) : ViewModel() {

    private val formState = MutableStateFlow(HomeUiState())

    val uiState: StateFlow<HomeUiState> = combine(
        formState,
        observeTripsUseCase(),
        observeDefaultLanguageUseCase()
    ) { form, trips, defaultLanguage ->
        form.copy(
            recentTrips = trips,
            languageCode = if (form.languageCode == "en" && defaultLanguage != "en") {
                defaultLanguage
            } else {
                form.languageCode
            }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    fun updateOrigin(value: String) {
        formState.update { it.copy(origin = value) }
    }

    fun updateDestination(value: String) {
        formState.update { it.copy(destination = value) }
    }

    fun updateLanguage(value: String) {
        formState.update { it.copy(languageCode = value) }
        viewModelScope.launch {
            setDefaultLanguageUseCase(value)
        }
    }

    fun createPlan(onSuccess: (String) -> Unit) {
        val current = formState.value
        if (current.origin.isBlank() || current.destination.isBlank()) {
            formState.update { it.copy(errorMessage = "Enter origin and destination") }
            return
        }

        viewModelScope.launch {
            formState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                createTripUseCase(
                    origin = current.origin.trim(),
                    destination = current.destination.trim(),
                    languageCode = current.languageCode
                )
            }.onSuccess { trip ->
                formState.update { it.copy(isLoading = false) }
                onSuccess(trip.id)
            }.onFailure { error ->
                formState.update {
                    it.copy(isLoading = false, errorMessage = error.message ?: "Failed to create plan")
                }
            }
        }
    }
}
