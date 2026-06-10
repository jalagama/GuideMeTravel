package com.guideme.travel.ui.package_detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.guideme.travel.R
import com.guideme.travel.domain.model.CuratedSpot
import com.guideme.travel.domain.model.NearbyPlace
import com.guideme.travel.domain.model.TourPackageDetail
import com.guideme.travel.ui.components.GuideMeCard
import com.guideme.travel.ui.components.addCuratedSpotsMapLayers
import com.guideme.travel.ui.components.curatedMapCenter
import com.guideme.travel.util.PreviewTtsHelper
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.maps.MapView

@Composable
fun TourPackageDetailScreen(
    uiState: TourPackageDetailUiState,
    onBookTrip: () -> Unit,
    onPlayPreview: (String, String) -> Unit,
    onStopPreview: () -> Unit
) {
    when {
        uiState.isLoading && uiState.detail == null -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        }
        uiState.errorMessage != null && uiState.detail == null -> {
            Text(
                text = uiState.errorMessage,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(20.dp)
            )
        }
        uiState.detail != null -> {
            TourPackageDetailContent(
                detail = uiState.detail,
                playingSpotId = uiState.playingSpotId,
                onBookTrip = onBookTrip,
                onPlayPreview = onPlayPreview,
                onStopPreview = onStopPreview
            )
        }
    }
}

@Composable
private fun TourPackageDetailContent(
    detail: TourPackageDetail,
    playingSpotId: String?,
    onBookTrip: () -> Unit,
    onPlayPreview: (String, String) -> Unit,
    onStopPreview: () -> Unit
) {
    val context = LocalContext.current
    val ttsHelper = remember { PreviewTtsHelper(context) }

    DisposableEffect(Unit) {
        onDispose { ttsHelper.shutdown() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                AsyncImage(
                    model = detail.heroImageUrl,
                    contentDescription = detail.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(detail.title, style = MaterialTheme.typography.headlineMedium)
                Text(
                    "${detail.region} • ${detail.days} days • ${detail.spots.size} spots",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(detail.overview, style = MaterialTheme.typography.bodyLarge)
            }

            item {
                Text(stringResource(R.string.route_map), style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                CuratedSpotsMap(spots = detail.spots)
            }

            if (detail.highlights.isNotEmpty()) {
                item {
                    GuideMeCard {
                        Text(
                            stringResource(R.string.curator_highlights),
                            style = MaterialTheme.typography.titleLarge
                        )
                        detail.highlights.forEach { highlight ->
                            Text("• $highlight", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            item {
                Text(stringResource(R.string.itinerary_by_day), style = MaterialTheme.typography.titleLarge)
            }

            val spotsByDay = detail.spots.groupBy { it.day }.toSortedMap()
            spotsByDay.forEach { (day, spots) ->
                item {
                    Text(stringResource(R.string.day_label, day), style = MaterialTheme.typography.titleMedium)
                    detail.daySummaries[day.toString()]?.let { summary ->
                        Text(
                            summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
                items(spots.sortedBy { it.orderIndex }, key = { it.id }) { spot ->
                    CuratedSpotCard(spot = spot)
                }
            }

            if (detail.tips.isNotEmpty()) {
                item { SectionList(title = stringResource(R.string.tips), items = detail.tips) }
            }
            if (detail.essentials.isNotEmpty()) {
                item { SectionList(title = stringResource(R.string.essentials), items = detail.essentials) }
            }
            if (detail.hotels.isNotEmpty()) {
                item { NearbySection(title = stringResource(R.string.hotels_nearby), places = detail.hotels) }
            }
            if (detail.restaurants.isNotEmpty()) {
                item { NearbySection(title = stringResource(R.string.local_restaurants), places = detail.restaurants) }
            }

            val previewSpots = detail.spots.sortedBy { it.orderIndex }.take(3)
            if (previewSpots.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.listen_before_you_go), style = MaterialTheme.typography.titleLarge)
                }
                items(previewSpots, key = { "preview-${it.id}" }) { spot ->
                    AudioPreviewCard(
                        spot = spot,
                        isPlaying = playingSpotId == spot.id,
                        onPlay = {
                            val script = spot.previewSnippet ?: spot.description
                            onPlayPreview(spot.id, script)
                            ttsHelper.speak(script)
                        },
                        onStop = {
                            ttsHelper.stop()
                            onStopPreview()
                        }
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            shadowElevation = 8.dp,
            tonalElevation = 3.dp
        ) {
            Button(
                onClick = onBookTrip,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(stringResource(R.string.book_trip_free))
            }
        }
    }
}

@Composable
private fun AudioPreviewCard(
    spot: CuratedSpot,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit
) {
    GuideMeCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(spot.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    spot.previewSnippet ?: spot.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3
                )
            }
            IconButton(onClick = if (isPlaying) onStop else onPlay) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Stop preview" else "Play preview"
                )
            }
        }
    }
}

@Composable
private fun CuratedSpotsMap(spots: List<CuratedSpot>) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember { MapView(context) }
    val styleUrl = "https://demotiles.maplibre.org/style.json"

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    LaunchedEffect(spots) {
        mapView.getMapAsync { map ->
            map.setStyle(styleUrl) { style ->
                addCuratedSpotsMapLayers(style, spots)
                curatedMapCenter(spots)?.let { center ->
                    map.cameraPosition = CameraPosition.Builder()
                        .target(center)
                        .zoom(10.0)
                        .build()
                }
            }
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
    )
}

@Composable
private fun CuratedSpotCard(spot: CuratedSpot) {
    GuideMeCard {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!spot.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = spot.imageUrl,
                    contentDescription = spot.name,
                    modifier = Modifier.size(72.dp),
                    contentScale = ContentScale.Crop
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Stop ${spot.orderIndex + 1}", style = MaterialTheme.typography.labelLarge)
                Text(spot.name, style = MaterialTheme.typography.titleMedium)
                if (!spot.whyChosen.isNullOrBlank()) {
                    Text(
                        spot.whyChosen,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("${spot.estimatedMinutes} min", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SectionList(title: String, items: List<String>) {
    GuideMeCard {
        Text(title, style = MaterialTheme.typography.titleLarge)
        items.forEach { item ->
            Text("• $item", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun NearbySection(title: String, places: List<NearbyPlace>) {
    GuideMeCard {
        Text(title, style = MaterialTheme.typography.titleLarge)
        places.forEach { place ->
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(place.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    place.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                place.rating?.let {
                    Text("Rating: $it", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
