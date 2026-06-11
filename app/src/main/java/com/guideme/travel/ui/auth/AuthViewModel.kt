package com.guideme.travel.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guideme.travel.data.auth.PendingEmailLinkStore
import com.guideme.travel.domain.analytics.AnalyticsEvents
import com.guideme.travel.domain.analytics.AnalyticsParams
import com.guideme.travel.domain.analytics.GuideMeAnalytics
import com.guideme.travel.domain.repository.PreferencesRepository
import com.guideme.travel.domain.usecase.CompleteSignInFromLinkUseCase
import com.guideme.travel.domain.usecase.ObserveAuthStateUseCase
import com.guideme.travel.domain.usecase.ObserveDefaultLanguageUseCase
import com.guideme.travel.domain.usecase.SendSignInLinkUseCase
import com.guideme.travel.domain.usecase.SignInAnonymouslyUseCase
import com.guideme.travel.domain.usecase.SignInWithGoogleUseCase
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
    val linkSent: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val observeAuthStateUseCase: ObserveAuthStateUseCase,
    private val signInAnonymouslyUseCase: SignInAnonymouslyUseCase,
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase,
    private val sendSignInLinkUseCase: SendSignInLinkUseCase,
    private val completeSignInFromLinkUseCase: CompleteSignInFromLinkUseCase,
    private val observeDefaultLanguageUseCase: ObserveDefaultLanguageUseCase,
    private val preferencesRepository: PreferencesRepository,
    private val pendingEmailLinkStore: PendingEmailLinkStore,
    private val analytics: GuideMeAnalytics
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

    init {
        viewModelScope.launch {
            pendingEmailLinkStore.pendingLink.collect { link ->
                if (!link.isNullOrBlank()) {
                    completePendingEmailLink(link)
                }
            }
        }
    }

    fun updateEmail(value: String) {
        formState.update { it.copy(email = value, linkSent = false, errorMessage = null) }
    }

    fun signInAnonymously(onSuccess: () -> Unit) {
        viewModelScope.launch {
            formState.update { it.copy(isLoading = true, errorMessage = null) }
            val language = observeDefaultLanguageUseCase().first()
            runCatching { signInAnonymouslyUseCase(language) }
                .onSuccess {
                    analytics.logEvent(AnalyticsEvents.SIGN_IN_ANONYMOUS)
                    formState.update { it.copy(isLoading = false) }
                    onSuccess()
                }
                .onFailure { error ->
                    analytics.logEvent(
                        AnalyticsEvents.SIGN_IN_FAILED,
                        mapOf(AnalyticsParams.SOURCE to "anonymous", AnalyticsParams.ERROR_MESSAGE to error.message)
                    )
                    analytics.recordNonFatal(error, mapOf(AnalyticsParams.SOURCE to "anonymous"))
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
                    analytics.logEvent(AnalyticsEvents.SIGN_IN_GOOGLE)
                    formState.update { it.copy(isLoading = false) }
                    onSuccess()
                }
                .onFailure { error ->
                    analytics.logEvent(
                        AnalyticsEvents.SIGN_IN_FAILED,
                        mapOf(AnalyticsParams.SOURCE to "google", AnalyticsParams.ERROR_MESSAGE to error.message)
                    )
                    analytics.recordNonFatal(error, mapOf(AnalyticsParams.SOURCE to "google"))
                    formState.update {
                        it.copy(isLoading = false, errorMessage = error.message ?: "Google sign-in failed")
                    }
                }
        }
    }

    fun sendSignInLink() {
        val current = formState.value
        if (current.email.isBlank() || !current.email.contains("@")) {
            formState.update { it.copy(errorMessage = "Enter a valid email address") }
            return
        }

        viewModelScope.launch {
            formState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { sendSignInLinkUseCase(current.email.trim()) }
                .onSuccess {
                    analytics.logEvent(AnalyticsEvents.SIGN_IN_EMAIL_LINK_SENT)
                    formState.update {
                        it.copy(isLoading = false, linkSent = true, errorMessage = null)
                    }
                }
                .onFailure { error ->
                    analytics.logEvent(
                        AnalyticsEvents.SIGN_IN_FAILED,
                        mapOf(AnalyticsParams.SOURCE to "email_link", AnalyticsParams.ERROR_MESSAGE to error.message)
                    )
                    formState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Failed to send sign-in link"
                        )
                    }
                }
        }
    }

    fun handleEmailLink(link: String, onSuccess: () -> Unit) {
        pendingEmailLinkStore.setLink(link)
        viewModelScope.launch {
            completePendingEmailLink(link, onSuccess)
        }
    }

    private suspend fun completePendingEmailLink(link: String, onSuccess: (() -> Unit)? = null) {
        val email = preferencesRepository.pendingSignInEmail.first()
            ?: formState.value.email.takeIf { it.isNotBlank() }
        if (email.isNullOrBlank()) {
            formState.update {
                it.copy(errorMessage = "Enter the same email address you used to request the link")
            }
            return
        }

        formState.update { it.copy(isLoading = true, errorMessage = null) }
        val language = observeDefaultLanguageUseCase().first()
        runCatching { completeSignInFromLinkUseCase(email, link, language) }
            .onSuccess {
                analytics.logEvent(AnalyticsEvents.SIGN_IN_EMAIL_COMPLETE)
                pendingEmailLinkStore.consumeLink()
                formState.update { it.copy(isLoading = false) }
                onSuccess?.invoke()
            }
            .onFailure { error ->
                analytics.logEvent(
                    AnalyticsEvents.SIGN_IN_FAILED,
                    mapOf(AnalyticsParams.SOURCE to "email_complete", AnalyticsParams.ERROR_MESSAGE to error.message)
                )
                analytics.recordNonFatal(error, mapOf(AnalyticsParams.SOURCE to "email_complete"))
                formState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Email link sign-in failed"
                    )
                }
            }
    }
}
