package com.guideme.travel.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guideme.travel.domain.usecase.ObserveAuthStateUseCase
import com.guideme.travel.domain.usecase.ObserveDefaultLanguageUseCase
import com.guideme.travel.domain.usecase.SignInAnonymouslyUseCase
import com.guideme.travel.domain.usecase.SignInWithEmailUseCase
import com.guideme.travel.domain.usecase.SignInWithGoogleUseCase
import com.guideme.travel.domain.usecase.SignUpWithEmailUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val isSignedIn: Boolean = false,
    val email: String = "",
    val password: String = "",
    val isSignUpMode: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val observeAuthStateUseCase: ObserveAuthStateUseCase,
    private val signInAnonymouslyUseCase: SignInAnonymouslyUseCase,
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase,
    private val signInWithEmailUseCase: SignInWithEmailUseCase,
    private val signUpWithEmailUseCase: SignUpWithEmailUseCase,
    private val observeDefaultLanguageUseCase: ObserveDefaultLanguageUseCase
) : ViewModel() {

    private val formState = MutableStateFlow(AuthUiState())

    val uiState: StateFlow<AuthUiState> = combine(
        formState,
        observeAuthStateUseCase()
    ) { form, user ->
        form.copy(isSignedIn = user != null)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AuthUiState()
    )

    fun updateEmail(value: String) {
        formState.update { it.copy(email = value) }
    }

    fun updatePassword(value: String) {
        formState.update { it.copy(password = value) }
    }

    fun toggleSignUpMode() {
        formState.update { it.copy(isSignUpMode = !it.isSignUpMode, errorMessage = null) }
    }

    fun signInAnonymously(onSuccess: () -> Unit) {
        viewModelScope.launch {
            formState.update { it.copy(isLoading = true, errorMessage = null) }
            val language = observeDefaultLanguageUseCase().first()
            runCatching { signInAnonymouslyUseCase(language) }
                .onSuccess {
                    formState.update { it.copy(isLoading = false) }
                    onSuccess()
                }
                .onFailure { error ->
                    formState.update {
                        it.copy(isLoading = false, errorMessage = error.message ?: "Sign in failed")
                    }
                }
        }
    }

    fun signInWithGoogle(idToken: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            formState.update { it.copy(isLoading = true, errorMessage = null) }
            val language = observeDefaultLanguageUseCase().first()
            runCatching { signInWithGoogleUseCase(idToken, language) }
                .onSuccess {
                    formState.update { it.copy(isLoading = false) }
                    onSuccess()
                }
                .onFailure { error ->
                    formState.update {
                        it.copy(isLoading = false, errorMessage = error.message ?: "Google sign-in failed")
                    }
                }
        }
    }

    fun signInWithEmail(onSuccess: () -> Unit) {
        val current = formState.value
        if (current.email.isBlank() || current.password.length < 6) {
            formState.update { it.copy(errorMessage = "Enter a valid email and password (6+ chars)") }
            return
        }

        viewModelScope.launch {
            formState.update { it.copy(isLoading = true, errorMessage = null) }
            val language = observeDefaultLanguageUseCase().first()
            runCatching {
                if (current.isSignUpMode) {
                    signUpWithEmailUseCase(current.email.trim(), current.password, language)
                } else {
                    signInWithEmailUseCase(current.email.trim(), current.password, language)
                }
            }
                .onSuccess {
                    formState.update { it.copy(isLoading = false) }
                    onSuccess()
                }
                .onFailure { error ->
                    formState.update {
                        it.copy(isLoading = false, errorMessage = error.message ?: "Email sign-in failed")
                    }
                }
        }
    }
}
