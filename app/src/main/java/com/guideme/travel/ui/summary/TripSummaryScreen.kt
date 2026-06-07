package com.guideme.travel.ui.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
fun TripSummaryScreen(
    uiState: TripSummaryUiState,
    onDeleteOfflineData: () -> Unit,
    onDone: () -> Unit
) {
    val trip = uiState.trip

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Trip complete", style = MaterialTheme.typography.displaySmall)

        GuideMeCard {
            Text(
                text = "${trip?.origin.orEmpty()} → ${trip?.destination.orEmpty()}",
                style = MaterialTheme.typography.titleLarge
            )
            Text("You visited ${trip?.attractions?.count { it.status.name == "VISITED" } ?: 0} of ${trip?.attractions?.size ?: 0} spots.")
            Text("Offline pack size freed: ${trip?.offlinePackSizeMb ?: 0} MB after cleanup.")
        }

        if (uiState.offlineDeleted) {
            Text("Offline data deleted.", color = MaterialTheme.colorScheme.secondary)
        } else {
            OutlinedButton(onClick = onDeleteOfflineData, modifier = Modifier.fillMaxWidth()) {
                Text("Delete offline map and audio")
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Back to home")
        }
    }
}
