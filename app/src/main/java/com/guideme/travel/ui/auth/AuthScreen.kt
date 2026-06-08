package com.guideme.travel.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.guideme.travel.ui.components.GuideMeCard

@Composable
fun AuthScreen(
    uiState: AuthUiState,
    onSignInAnonymously: () -> Unit,
    onSignInWithGoogle: () -> Unit,
    onSignInWithEmail: () -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onToggleSignUpMode: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Sign in to GuideMe", style = MaterialTheme.typography.displaySmall)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Sign in to sync trips across devices and download AI-generated offline guides.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        GuideMeCard {
            Text("Google account", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(
                onClick = onSignInWithGoogle,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading
            ) {
                Text("Continue with Google")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        GuideMeCard {
            Text(
                if (uiState.isSignUpMode) "Create email account" else "Email sign-in",
                style = MaterialTheme.typography.titleLarge
            )
            OutlinedTextField(
                value = uiState.email,
                onValueChange = onEmailChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Email") },
                singleLine = true
            )
            OutlinedTextField(
                value = uiState.password,
                onValueChange = onPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") },
                singleLine = true
            )
            TextButton(onClick = onToggleSignUpMode) {
                Text(
                    if (uiState.isSignUpMode) "Already have an account? Sign in"
                    else "New here? Create account"
                )
            }
            Button(
                onClick = onSignInWithEmail,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading
            ) {
                Text(if (uiState.isSignUpMode) "Create account" else "Sign in with email")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        GuideMeCard {
            Text("Quick start", style = MaterialTheme.typography.titleLarge)
            Text("Anonymous sign-in for trying the app without an account.")
            if (uiState.errorMessage != null) {
                Text(uiState.errorMessage, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = onSignInAnonymously,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator()
                } else {
                    Text("Continue as guest")
                }
            }
        }
    }
}
