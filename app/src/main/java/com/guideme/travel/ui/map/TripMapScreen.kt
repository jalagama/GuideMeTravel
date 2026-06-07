package com.guideme.travel.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.guideme.travel.domain.model.Attraction
import com.guideme.travel.domain.model.TripPlan
import com.guideme.travel.ui.components.GuideMeCard
import com.guideme.travel.util.BatteryOptimizationHelper
import com.guideme.travel.util.LocationPermissionHelper
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

@Composable
fun TripMapScreen(
    uiState: TripMapUiState,
    onStartGuideService: (TripPlan) -> Unit,
    onPlayGuide: (String) -> Unit,
    onCompleteTrip: () -> Unit
) {
    val trip = uiState.trip ?: return
    val context = LocalContext.current

    LaunchedEffect(trip.id, uiState.guideServiceRunning) {
        if (!uiState.guideServiceRunning &&
            LocationPermissionHelper.canStartLocationForegroundService(context)
        ) {
            onStartGuideService(trip)
        }
    }

    val center = trip.attractions.firstOrNull()?.let { LatLng(it.latitude, it.longitude) }
        ?: LatLng(15.3350, 76.4600)

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.45f)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { mapContext ->
                    MapView(mapContext).apply {
                        getMapAsync { map ->
                            map.setStyle("https://demotiles.maplibre.org/style.json") { style ->
                                addAttractionMarkers(style, trip)
                                map.cameraPosition = CameraPosition.Builder()
                                    .target(center)
                                    .zoom(12.0)
                                    .build()
                            }
                        }
                    }
                }
            )
        }

        LazyColumn(
            modifier = Modifier.weight(0.55f),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Live trip map", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "Guides play automatically when you approach each marker. You can also play manually.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)) {
                    OutlinedButton(
                        onClick = { BatteryOptimizationHelper.openBatteryOptimizationSettings(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Allow background guide (battery setting)")
                    }
                }
            }

            items(trip.attractions, key = { it.id }) { attraction ->
                AttractionMapCard(
                    attraction = attraction,
                    onPlayGuide = { onPlayGuide(attraction.id) }
                )
            }

            item {
                Button(
                    onClick = onCompleteTrip,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Complete trip")
                }
            }
        }
    }
}

private fun addAttractionMarkers(
    style: org.maplibre.android.maps.Style,
    trip: TripPlan
) {
    val features = trip.attractions.map { attraction ->
        Feature.fromGeometry(
            Point.fromLngLat(attraction.longitude, attraction.latitude)
        )
    }
    val sourceId = "trip-attractions"
    val layerId = "trip-attractions-layer"
    val collection = FeatureCollection.fromFeatures(features)
    style.addSource(GeoJsonSource(sourceId, collection))
    style.addLayer(
        CircleLayer(layerId, sourceId).withProperties(
            PropertyFactory.circleRadius(8f),
            PropertyFactory.circleColor("#FF5A5F"),
            PropertyFactory.circleStrokeWidth(2f),
            PropertyFactory.circleStrokeColor("#FFFFFF")
        )
    )
}

@Composable
private fun AttractionMapCard(
    attraction: Attraction,
    onPlayGuide: () -> Unit
) {
    GuideMeCard {
        Text("Stop ${attraction.orderIndex + 1}: ${attraction.name}", style = MaterialTheme.typography.titleLarge)
        Text("${attraction.latitude}, ${attraction.longitude}")
        OutlinedButton(onClick = onPlayGuide, modifier = Modifier.fillMaxWidth()) {
            Text("Play guide now")
        }
    }
}
