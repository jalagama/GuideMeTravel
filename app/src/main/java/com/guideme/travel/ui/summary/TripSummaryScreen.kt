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
import com.guideme.travel.domain.model.AttractionStatus
import com.guideme.travel.ui.components.GuideMeCard

@Composable
fun TripSummaryScreen(
    uiState: TripSummaryUiState,
    onDeleteOfflineData: () -> Unit,
    onDeleteTrip: () -> Unit,
    onDone: () -> Unit
) {
    val trip = uiState.trip
    val visitedCount = trip?.attractions?.count { it.status == AttractionStatus.VISITED } ?: 0
    val freedMb = (uiState.freedBytes / (1024 * 1024)).coerceAtLeast(0)

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
            Text("You visited $visitedCount of ${trip?.attractions?.size ?: 0} spots.")
            if (uiState.offlineDeleted) {
                Text("Freed approximately $freedMb MB of offline map and audio data.")
            } else {
                Text("Offline pack size: ${trip?.offlinePackSizeMb ?: 0} MB")
            }
        }

        if (!uiState.offlineDeleted) {
            OutlinedButton(onClick = onDeleteOfflineData, modifier = Modifier.fillMaxWidth()) {
                Text("Delete offline map and audio")
            }
        } else {
            Text("Offline data deleted.", color = MaterialTheme.colorScheme.secondary)
        }

        if (!uiState.tripDeleted) {
            OutlinedButton(onClick = onDeleteTrip, modifier = Modifier.fillMaxWidth()) {
                Text("Delete trip record")
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Back to home")
        }
    }
}
