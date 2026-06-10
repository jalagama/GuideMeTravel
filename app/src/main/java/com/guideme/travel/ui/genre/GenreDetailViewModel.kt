package com.guideme.travel.ui.genre

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.guideme.travel.domain.model.TourPackageSummary
import com.guideme.travel.domain.usecase.GetGenrePackagesUseCase
import com.guideme.travel.domain.usecase.ObserveGenrePackagesUseCase
import com.guideme.travel.ui.navigation.GenreDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GenreDetailUiState(
    val genreName: String = "",
    val packages: List<TourPackageSummary> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isOfflineCached: Boolean = false
)

@HiltViewModel
class GenreDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeGenrePackagesUseCase: ObserveGenrePackagesUseCase,
    private val getGenrePackagesUseCase: GetGenrePackagesUseCase
) : ViewModel() {

    private val route = savedStateHandle.toRoute<GenreDetailRoute>()
    private val _uiState = MutableStateFlow(GenreDetailUiState())
    val uiState: StateFlow<GenreDetailUiState> = _uiState.asStateFlow()

    init {
        loadPackages()
    }

    private fun loadPackages() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            launch {
                observeGenrePackagesUseCase(route.countryCode, route.genreId).collect { cached ->
                    if (cached != null) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                genreName = cached.genreName,
                                packages = cached.packages.sortedBy { p -> p.rank },
                                isOfflineCached = true
                            )
                        }
                    }
                }
            }

            runCatching {
                getGenrePackagesUseCase(route.countryCode, route.genreId)
            }.onSuccess { result ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        genreName = result.genreName,
                        packages = result.packages.sortedBy { p -> p.rank },
                        isOfflineCached = true
                    )
                }
            }.onFailure { error ->
                val hasCache = _uiState.value.packages.isNotEmpty()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = if (hasCache) {
                            null
                        } else {
                            error.message ?: "Connect once to download this collection."
                        }
                    )
                }
            }
        }
    }
}
