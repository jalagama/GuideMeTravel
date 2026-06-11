package com.guideme.travel.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guideme.travel.domain.model.CuratedGenre
import com.guideme.travel.domain.model.TripPlan
import com.guideme.travel.domain.usecase.CreateTripUseCase
import com.guideme.travel.domain.usecase.EnqueueCatalogPrefetchUseCase
import com.guideme.travel.domain.usecase.GetCountryGenresUseCase
import com.guideme.travel.domain.usecase.ObserveCountryGenresUseCase
import com.guideme.travel.domain.usecase.ObserveDefaultLanguageUseCase
import com.guideme.travel.domain.usecase.ObserveTripsUseCase
import com.guideme.travel.domain.usecase.RefreshCountryGenresUseCase
import com.guideme.travel.domain.analytics.AnalyticsEvents
import com.guideme.travel.domain.analytics.AnalyticsParams
import com.guideme.travel.domain.analytics.GuideMeAnalytics
import com.guideme.travel.domain.logging.GuideMeLogger
import com.guideme.travel.domain.logging.LogTags
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
    val isRefreshing: Boolean = false,
    val isCreatingTrip: Boolean = false,
    val recentTrips: List<TripPlan> = emptyList(),
    val searchSuggestions: List<PlaceSuggestion> = emptyList(),
    val errorMessage: String? = null,
    val lastUpdatedMillis: Long = 0L
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val observeCountryGenresUseCase: ObserveCountryGenresUseCase,
    private val getCountryGenresUseCase: GetCountryGenresUseCase,
    private val refreshCountryGenresUseCase: RefreshCountryGenresUseCase,
    private val enqueueCatalogPrefetchUseCase: EnqueueCatalogPrefetchUseCase,
    private val createTripUseCase: CreateTripUseCase,
    private val observeTripsUseCase: ObserveTripsUseCase,
    private val observeDefaultLanguageUseCase: ObserveDefaultLanguageUseCase,
    private val setDefaultLanguageUseCase: SetDefaultLanguageUseCase,
    private val placesAutocompleteHelper: PlacesAutocompleteHelper,
    private val logger: GuideMeLogger,
    private val analytics: GuideMeAnalytics
) : ViewModel() {

    private val formState = MutableStateFlow(HomeUiState())
    private var searchJob: Job? = null
    private var countryCode: String = ""

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
            countryCode = observeCountryGenresUseCase.countryCode()
            formState.update { it.copy(isLoadingGenres = true, errorMessage = null) }

            launch {
                observeCountryGenresUseCase(countryCode).collect { cached ->
                    if (cached != null) {
                        formState.update {
                            it.copy(
                                isLoadingGenres = false,
                                countryCode = cached.countryCode,
                                countryName = cached.countryName,
                                genres = cached.genres.sortedBy { g -> g.rank },
                                lastUpdatedMillis = cached.updatedAtMillis
                            )
                        }
                    }
                }
            }

            runCatching { getCountryGenresUseCase() }
                .onSuccess { result ->
                    logger.info(
                        LogTags.HOME_VM,
                        AnalyticsEvents.GENRES_LOADED,
                        mapOf(
                            AnalyticsParams.COUNTRY_CODE to result.countryCode,
                            AnalyticsParams.COUNT to result.genres.size
                        )
                    )
                    formState.update {
                        it.copy(
                            isLoadingGenres = false,
                            countryCode = result.countryCode,
                            countryName = result.countryName,
                            genres = result.genres.sortedBy { g -> g.rank },
                            lastUpdatedMillis = result.updatedAtMillis
                        )
                    }
                    enqueueCatalogPrefetchUseCase(result.countryCode, result.genres.map { it.id })
                }
                .onFailure { error ->
                    val hasCache = formState.value.genres.isNotEmpty()
                    logger.warn(
                        LogTags.HOME_VM,
                        "genres_load_failed",
                        mapOf("hasCache" to hasCache),
                        error
                    )
                    formState.update {
                        it.copy(
                            isLoadingGenres = false,
                            errorMessage = if (hasCache) null else error.message ?: "Failed to load destinations"
                        )
                    }
                }
        }
    }

    fun refreshGenres() {
        viewModelScope.launch {
            formState.update { it.copy(isRefreshing = true, errorMessage = null) }
            runCatching { refreshCountryGenresUseCase() }
                .onSuccess { result ->
                    formState.update {
                        it.copy(
                            isRefreshing = false,
                            countryCode = result.countryCode,
                            countryName = result.countryName,
                            genres = result.genres.sortedBy { g -> g.rank },
                            lastUpdatedMillis = result.updatedAtMillis
                        )
                    }
                    enqueueCatalogPrefetchUseCase(result.countryCode, result.genres.map { it.id })
                }
                .onFailure { error ->
                    formState.update {
                        it.copy(
                            isRefreshing = false,
                            errorMessage = error.message ?: "Failed to refresh destinations"
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
                analytics.logEvent(
                    AnalyticsEvents.CUSTOM_TRIP_CREATED,
                    mapOf(AnalyticsParams.TRIP_ID to trip.id, "destination" to destination)
                )
                formState.update { it.copy(isCreatingTrip = false, searchQuery = "") }
                onSuccess(trip.id)
            }.onFailure { error ->
                analytics.recordNonFatal(error, mapOf("destination" to destination))
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
