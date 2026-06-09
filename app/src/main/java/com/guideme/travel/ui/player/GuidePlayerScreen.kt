package com.guideme.travel.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.guideme.travel.ui.components.GuideMeCard

@Composable
fun GuidePlayerScreen(
    uiState: GuidePlayerUiState,
    onPlay: () -> Unit,
    onStop: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(uiState.attractionName, style = MaterialTheme.typography.headlineMedium)

        GuideMeCard {
            Text("Transcript", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = uiState.transcript,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        if (uiState.playbackState?.isPlaying == true) {
            OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                Text("Stop")
            }
        } else {
            Button(onClick = onPlay, modifier = Modifier.fillMaxWidth()) {
                Text("Play guide")
            }
        }
    }
}
