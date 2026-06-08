package com.guideme.travel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.hilt.navigation.compose.hiltViewModel
import com.guideme.travel.ui.GuideMeApp
import com.guideme.travel.ui.auth.AuthViewModel
import com.guideme.travel.ui.theme.GuideMeTheme
import com.guideme.travel.util.GoogleSignInHelper
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private lateinit var googleSignInHelper: GoogleSignInHelper
    private var onGoogleToken: (String) -> Unit = {}
    private var onGoogleError: (String) -> Unit = {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        googleSignInHelper = GoogleSignInHelper(
            activity = this,
            webClientId = GoogleSignInHelper.webClientId(this),
            onToken = { token -> onGoogleToken(token) },
            onError = { message -> onGoogleError(message) }
        )

        setContent {
            val authViewModel: AuthViewModel = hiltViewModel()
            onGoogleToken = { token -> authViewModel.signInWithGoogle(token) { } }
            onGoogleError = { /* surfaced via AuthViewModel on next attempt */ }

            GuideMeTheme {
                GuideMeApp(
                    onGoogleSignIn = { googleSignInHelper.launch() }
                )
            }
        }
    }
}
