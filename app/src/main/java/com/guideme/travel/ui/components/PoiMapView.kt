package com.guideme.travel.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.guideme.travel.domain.model.MapPoi
import org.maplibre.android.maps.MapView

@Composable
fun PoiMapView(
    pois: List<MapPoi>,
    modifier: Modifier = Modifier,
    styleUrl: String = PoiMapStyle.defaultStyleUrl(),
    highlightPoiId: String? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember { MapView(context) }

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

    LaunchedEffect(pois, highlightPoiId) {
        mapView.getMapAsync { map ->
            map.setStyle(styleUrl) { style ->
                registerPoiMapIcons(context, style)
                addPoiMapLayers(style, pois, highlightPoiId)
                fitMapToPois(map, pois)
            }
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier
    )
}
