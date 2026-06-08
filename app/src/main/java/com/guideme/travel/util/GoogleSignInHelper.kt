package com.guideme.travel.util

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

class GoogleSignInHelper(
    private val activity: ComponentActivity,
    private val webClientId: String,
    private val onToken: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private val launcher: ActivityResultLauncher<android.content.Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            runCatching {
                val account = task.getResult(ApiException::class.java)
                val token = account.idToken
                if (token.isNullOrBlank()) {
                    onError("Google sign-in returned no ID token")
                } else {
                    onToken(token)
                }
            }.onFailure { error ->
                onError(error.message ?: "Google sign-in failed")
            }
        }

    fun launch() {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        val client = GoogleSignIn.getClient(activity, options)
        launcher.launch(client.signInIntent)
    }

    companion object {
        fun webClientId(context: Context): String {
            return context.getString(
                context.resources.getIdentifier(
                    "default_web_client_id",
                    "string",
                    context.packageName
                )
            )
        }
    }
}
