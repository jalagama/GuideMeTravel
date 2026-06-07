package com.guideme.travel.ui.itinerary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import com.guideme.travel.domain.model.Attraction
import com.guideme.travel.ui.components.GuideMeCard
import com.guideme.travel.util.LocationPermissionHelper

@Composable
fun ItineraryScreen(
    uiState: ItineraryUiState,
    onDownload: () -> Unit,
    onStartTrip: () -> Unit,
    onBack: () -> Unit
) {
    val trip = uiState.trip
    val context = LocalContext.current
    var permissionMessage by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val locationGranted = LocationPermissionHelper.hasFineOrCoarseLocation(context)
        if (locationGranted) {
            permissionMessage = null
            onStartTrip()
        } else {
            permissionMessage = "Location permission is required to start the trip guide."
        }
    }

    fun handleStartTrip() {
        if (LocationPermissionHelper.canStartLocationForegroundService(context)) {
            permissionMessage = null
            onStartTrip()
        } else {
            permissionLauncher.launch(LocationPermissionHelper.startTripPermissions())
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Your itinerary", style = MaterialTheme.typography.headlineMedium)
        }

        if (uiState.isLoading || trip == null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
            }
            return
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                GuideMeCard {
                    Text(
                        text = "${trip.origin} → ${trip.destination}",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text("${trip.attractions.size} curated spots around ${trip.destination}")
                    Text("Estimated offline pack: ${trip.offlinePackSizeMb} MB")
                }
            }

            items(trip.attractions, key = { it.id }) { attraction ->
                AttractionTimelineCard(attraction = attraction)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                Text(if (trip.offlinePackDownloaded) "Re-download offline pack" else "Download offline pack")
            }
            if (permissionMessage != null) {
                Text(
                    text = permissionMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Button(
                onClick = { handleStartTrip() },
                modifier = Modifier.fillMaxWidth(),
                enabled = trip.offlinePackDownloaded
            ) {
                Text("Start trip")
            }
        }
    }
}

@Composable
private fun AttractionTimelineCard(attraction: Attraction) {
    GuideMeCard {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AsyncImage(
                model = attraction.imageUrl,
                contentDescription = attraction.name,
                modifier = Modifier.size(88.dp),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("Stop ${attraction.orderIndex + 1}", style = MaterialTheme.typography.labelLarge)
                Text(attraction.name, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = attraction.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("${attraction.estimatedMinutes} min")
            }
        }
    }
}
