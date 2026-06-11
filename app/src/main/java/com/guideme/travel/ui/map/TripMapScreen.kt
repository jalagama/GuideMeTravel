package com.guideme.travel.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.guideme.travel.R
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.guideme.travel.domain.model.Attraction
import com.guideme.travel.domain.model.AttractionStatus
import com.guideme.travel.domain.model.TripPlan
import com.guideme.travel.domain.model.toMapPoi
import com.guideme.travel.ui.components.GuideMeCard
import com.guideme.travel.ui.components.PoiCategoryIcon
import com.guideme.travel.ui.components.PoiMapStyle
import com.guideme.travel.ui.components.addPoiMapLayers
import com.guideme.travel.ui.components.fitMapToPois
import com.guideme.travel.ui.components.registerPoiMapIcons
import com.guideme.travel.util.BatteryOptimizationHelper
import com.guideme.travel.util.LocationPermissionHelper
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point

@Composable
fun TripMapScreen(
    uiState: TripMapUiState,
    onStartGuideService: (TripPlan) -> Unit,
    onPlayGuide: (String) -> Unit,
    onRecenter: () -> Unit,
    onCompleteTrip: () -> Unit
) {
    val trip = uiState.trip ?: return
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember { MapView(context) }
    val styleUrl = uiState.mapMetadata?.styleUrl
        ?: uiState.mapStyleUrl
        ?: PoiMapStyle.defaultStyleUrl()

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

    LaunchedEffect(trip.id, styleUrl, uiState.nextAttractionId) {
        mapView.getMapAsync { map ->
            map.setStyle(styleUrl) { style ->
                val mapPois = trip.attractions.map { it.toMapPoi() }
                registerPoiMapIcons(context, style)
                addPoiMapLayers(style, mapPois, uiState.nextAttractionId)
                fitMapToPois(map, mapPois)
            }
        }
    }

    LaunchedEffect(trip.id, uiState.userLocation, uiState.followUser) {
        val location = uiState.userLocation ?: return@LaunchedEffect
        mapView.getMapAsync { map ->
            map.style?.let { style ->
                updateUserLocationLayer(style, location.latitude, location.longitude)
            }
            if (uiState.followUser) {
                map.animateCamera(
                    CameraUpdateFactory.newLatLng(
                        LatLng(location.latitude, location.longitude)
                    )
                )
            }
        }
    }

    LaunchedEffect(trip.id, uiState.guideServiceRunning) {
        if (!uiState.guideServiceRunning &&
            LocationPermissionHelper.canStartLocationForegroundService(context)
        ) {
            onStartGuideService(trip)
        }
    }

    LaunchedEffect(uiState.allSpotsVisited) {
        if (uiState.allSpotsVisited) {
            onCompleteTrip()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.58f)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { mapView }
            )
            FloatingActionButton(
                onClick = onRecenter,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Recenter map")
            }
        }

        LazyColumn(
            modifier = Modifier.weight(0.42f),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Live trip map", style = MaterialTheme.typography.headlineMedium)
                if (uiState.mapMetadata == null) {
                    Text(
                        text = stringResource(R.string.online_mode_map_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "Guides play automatically when you approach each marker. You can also play manually.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                uiState.nextAttractionId?.let { nextId ->
                    val next = trip.attractions.firstOrNull { it.id == nextId }
                    if (next != null) {
                        Text(
                            text = "Next stop: ${next.name}",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)) {
                    OutlinedButton(
                        onClick = { BatteryOptimizationHelper.openBatteryOptimizationSettings(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Allow background guide (battery setting)")
                    }
                }
            }

            items(trip.attractions.sortedBy { it.orderIndex }, key = { it.id }) { attraction ->
                AttractionMapCard(
                    attraction = attraction,
                    isNext = attraction.id == uiState.nextAttractionId,
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

private fun updateUserLocationLayer(style: org.maplibre.android.maps.Style, lat: Double, lng: Double) {
    val sourceId = "user-location"
    val layerId = "user-location-layer"
    val point = Feature.fromGeometry(Point.fromLngLat(lng, lat))
    val existing = style.getSourceAs<GeoJsonSource>(sourceId)
    if (existing != null) {
        existing.setGeoJson(point)
    } else {
        style.addSource(GeoJsonSource(sourceId, point))
        style.addLayer(
            CircleLayer(layerId, sourceId).withProperties(
                PropertyFactory.circleRadius(7f),
                PropertyFactory.circleColor("#1A73E8"),
                PropertyFactory.circleStrokeWidth(3f),
                PropertyFactory.circleStrokeColor("#FFFFFF")
            )
        )
    }
}

@Composable
private fun AttractionMapCard(
    attraction: Attraction,
    isNext: Boolean,
    onPlayGuide: () -> Unit
) {
    val poi = attraction.toMapPoi()
    GuideMeCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            PoiCategoryIcon(category = poi.category, contentDescription = poi.category.name)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attraction.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when (attraction.status) {
                        AttractionStatus.VISITED -> "Visited"
                        AttractionStatus.SKIPPED -> "Skipped"
                        AttractionStatus.PENDING -> if (isNext) "Up next" else "Upcoming"
                    }
                )
                OutlinedButton(onClick = onPlayGuide, modifier = Modifier.fillMaxWidth()) {
                    Text("Play guide now")
                }
            }
        }
    }
}
