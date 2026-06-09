package com.guideme.travel.ui.components

import com.guideme.travel.domain.model.CuratedSpot
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

fun addCuratedSpotsMapLayers(style: Style, spots: List<CuratedSpot>) {
    val sorted = spots.sortedBy { it.orderIndex }
    if (sorted.size >= 2) {
        val routeSourceId = "curated-route"
        val routePoints = sorted.map { Point.fromLngLat(it.longitude, it.latitude) }
        style.addSource(
            GeoJsonSource(routeSourceId, Feature.fromGeometry(LineString.fromLngLats(routePoints)))
        )
        style.addLayer(
            LineLayer("$routeSourceId-layer", routeSourceId).withProperties(
                PropertyFactory.lineColor("#4285F4"),
                PropertyFactory.lineWidth(4f),
                PropertyFactory.lineOpacity(0.85f)
            )
        )
    }

    val markerFeatures = sorted.map { spot ->
        Feature.fromGeometry(Point.fromLngLat(spot.longitude, spot.latitude)).apply {
            addStringProperty("label", (spot.orderIndex + 1).toString())
            addStringProperty("name", spot.name)
        }
    }

    val markerSourceId = "curated-markers"
    style.addSource(GeoJsonSource(markerSourceId, FeatureCollection.fromFeatures(markerFeatures)))
    style.addLayer(
        SymbolLayer("$markerSourceId-layer", markerSourceId).withProperties(
            PropertyFactory.iconImage("marker-15"),
            PropertyFactory.textField(Expression.get("label")),
            PropertyFactory.textSize(12f),
            PropertyFactory.textColor("#FFFFFF"),
            PropertyFactory.textHaloColor("#FF5A5F"),
            PropertyFactory.textHaloWidth(2f),
            PropertyFactory.textOffset(arrayOf(0f, -1.2f)),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.iconAllowOverlap(true)
        )
    )
}

fun curatedMapCenter(spots: List<CuratedSpot>): LatLng? {
    if (spots.isEmpty()) return null
    val lat = spots.map { it.latitude }.average()
    val lng = spots.map { it.longitude }.average()
    return LatLng(lat, lng)
}
