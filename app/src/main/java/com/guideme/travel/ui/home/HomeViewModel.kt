package com.guideme.travel.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guideme.travel.domain.model.CuratedGenre
import com.guideme.travel.domain.model.TripPlan
import com.guideme.travel.domain.usecase.CreateTripUseCase
import com.guideme.travel.domain.usecase.GetCountryGenresUseCase
import com.guideme.travel.domain.usecase.ObserveDefaultLanguageUseCase
import com.guideme.travel.domain.usecase.ObserveTripsUseCase
import com.guideme.travel.domain.usecase.SetDefaultLanguageUseCase
import com.guideme.travel.util.PlaceSuggestion
import com.guideme.travel.util.PlacesAutocompleteHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val searchQuery: String = "",
    val languageCode: String = "en",
    val countryCode: String = "",
    val countryName: String = "",
    val genres: List<CuratedGenre> = emptyList(),
    val isLoadingGenres: Boolean = true,
    val isCreatingTrip: Boolean = false,
    val recentTrips: List<TripPlan> = emptyList(),
    val searchSuggestions: List<PlaceSuggestion> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getCountryGenresUseCase: GetCountryGenresUseCase,
    private val createTripUseCase: CreateTripUseCase,
    private val observeTripsUseCase: ObserveTripsUseCase,
    private val observeDefaultLanguageUseCase: ObserveDefaultLanguageUseCase,
    private val setDefaultLanguageUseCase: SetDefaultLanguageUseCase,
    private val placesAutocompleteHelper: PlacesAutocompleteHelper
) : ViewModel() {

    private val formState = MutableStateFlow(HomeUiState())
    private var searchJob: Job? = null

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

    init {
        loadGenres()
    }

    fun reloadAfterLocationGranted() {
        loadGenres()
    }

    fun loadGenres() {
        viewModelScope.launch {
            formState.update { it.copy(isLoadingGenres = true, errorMessage = null) }
            runCatching { getCountryGenresUseCase() }
                .onSuccess { result ->
                    formState.update {
                        it.copy(
                            isLoadingGenres = false,
                            countryCode = result.countryCode,
                            countryName = result.countryName,
                            genres = result.genres
                        )
                    }
                }
                .onFailure { error ->
                    formState.update {
                        it.copy(
                            isLoadingGenres = false,
                            errorMessage = error.message ?: "Failed to load destinations"
                        )
                    }
                }
        }
    }

    fun updateSearchQuery(value: String) {
        formState.update { it.copy(searchQuery = value, errorMessage = null) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            runCatching {
                placesAutocompleteHelper.fetchPredictions(
                    query = value,
                    countryCode = formState.value.countryCode
                )
            }
                .onSuccess { suggestions ->
                    formState.update { it.copy(searchSuggestions = suggestions) }
                }
        }
    }

    fun clearSuggestions() {
        formState.update { it.copy(searchSuggestions = emptyList()) }
    }

    fun selectDestinationSuggestion(suggestion: PlaceSuggestion, onSuccess: (String) -> Unit) {
        formState.update {
            it.copy(
                searchQuery = suggestion.fullText,
                searchSuggestions = emptyList()
            )
        }
        createPlanForDestination(suggestion.fullText, onSuccess)
    }

    fun createPlanFromSearch(onSuccess: (String) -> Unit) {
        val destination = formState.value.searchQuery.trim()
        if (destination.isBlank()) {
            formState.update { it.copy(errorMessage = "Search for a place to plan a trip") }
            return
        }
        createPlanForDestination(destination, onSuccess)
    }

    private fun createPlanForDestination(destination: String, onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            formState.update { it.copy(isCreatingTrip = true, errorMessage = null) }
            runCatching {
                createTripUseCase(
                    origin = "Current location",
                    destination = destination,
                    languageCode = formState.value.languageCode
                )
            }.onSuccess { trip ->
                formState.update { it.copy(isCreatingTrip = false, searchQuery = "") }
                onSuccess(trip.id)
            }.onFailure { error ->
                formState.update {
                    it.copy(
                        isCreatingTrip = false,
                        errorMessage = error.message ?: "Failed to create plan"
                    )
                }
            }
        }
    }

    fun updateLanguage(value: String) {
        formState.update { it.copy(languageCode = value) }
        viewModelScope.launch {
            setDefaultLanguageUseCase(value)
        }
    }
}
