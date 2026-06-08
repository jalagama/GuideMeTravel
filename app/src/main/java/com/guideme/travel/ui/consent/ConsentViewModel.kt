package com.guideme.travel.ui.consent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guideme.travel.domain.usecase.SaveConsentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConsentUiState(
    val privacyAccepted: Boolean = false,
    val locationAccepted: Boolean = false,
    val canContinue: Boolean = false
)

@HiltViewModel
class ConsentViewModel @Inject constructor(
    private val saveConsentUseCase: SaveConsentUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConsentUiState())
    val uiState: StateFlow<ConsentUiState> = _uiState.asStateFlow()

    fun togglePrivacy(value: Boolean) {
        _uiState.update {
            it.copy(
                privacyAccepted = value,
                canContinue = value && it.locationAccepted
            )
        }
    }

    fun toggleLocation(value: Boolean) {
        _uiState.update {
            it.copy(
                locationAccepted = value,
                canContinue = it.privacyAccepted && value
            )
        }
    }

    fun saveConsent(onSuccess: () -> Unit) {
        viewModelScope.launch {
            saveConsentUseCase(
                privacyGranted = _uiState.value.privacyAccepted,
                locationGranted = _uiState.value.locationAccepted
            )
            onSuccess()
        }
    }
}
