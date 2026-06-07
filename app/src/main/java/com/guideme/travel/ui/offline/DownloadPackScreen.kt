package com.guideme.travel.ui.offline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.guideme.travel.ui.components.GuideMeCard

@Composable
fun DownloadPackScreen(
    uiState: DownloadPackUiState,
    onStartDownload: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }

        Text("Offline pack", style = MaterialTheme.typography.displaySmall)
        Text(
            text = "Download maps, audio guides, and transcripts so GuideMe works without internet during your trip.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        GuideMeCard {
            Text("Includes", style = MaterialTheme.typography.titleLarge)
            Text("• Curated route and attraction markers")
            Text("• Offline map tiles for the destination region")
            Text("• Pre-generated audio guides in your selected language")
            Text("Estimated size: ${uiState.progress?.estimatedSizeMb ?: 120} MB")
        }

        if (uiState.progress != null) {
            GuideMeCard {
                Text(uiState.progress.currentTask, style = MaterialTheme.typography.titleLarge)
                LinearProgressIndicator(
                    progress = { uiState.progress.progressFraction },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (uiState.errorMessage != null) {
            Text(uiState.errorMessage, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.weight(1f))

        if (uiState.isComplete) {
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text("Continue")
            }
        } else {
            Button(
                onClick = onStartDownload,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isDownloading
            ) {
                Text(if (uiState.isDownloading) "Downloading..." else "Start download")
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
        }
    }
}
