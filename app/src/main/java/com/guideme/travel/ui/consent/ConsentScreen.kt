package com.guideme.travel.ui.consent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.guideme.travel.ui.components.GuideMeCard

@Composable
fun ConsentScreen(
    uiState: ConsentUiState,
    onPrivacyChanged: (Boolean) -> Unit,
    onLocationChanged: (Boolean) -> Unit,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Privacy & permissions", style = MaterialTheme.typography.displaySmall)
        Text(
            text = "GuideMe uses background location to play audio guides automatically as you approach each attraction.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        GuideMeCard {
            ConsentRow(
                checked = uiState.privacyAccepted,
                onCheckedChange = onPrivacyChanged,
                title = "Privacy policy",
                description = "I agree to GuideMe processing trip and location data to provide offline travel guides (GDPR/CCPA/DPDP compliant)."
            )
            ConsentRow(
                checked = uiState.locationAccepted,
                onCheckedChange = onLocationChanged,
                title = "Background location",
                description = "I allow GuideMe to access location in the background during an active trip."
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.canContinue
        ) {
            Text("Continue")
        }
    }
}

@Composable
private fun ConsentRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    description: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}
