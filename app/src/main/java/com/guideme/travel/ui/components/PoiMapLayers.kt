package com.guideme.travel.ui.components

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.guideme.travel.domain.model.MapPoi
import com.guideme.travel.domain.model.PoiCategory
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.utils.BitmapUtils
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private const val ROUTE_SOURCE_ID = "poi-route"
private const val ROUTE_CASE_SOURCE_ID = "poi-route-case"
private const val MARKER_SOURCE_ID = "poi-markers"
private const val LABEL_SOURCE_ID = "poi-labels"

fun registerPoiMapIcons(context: Context, style: Style) {
    PoiCategory.entries.forEach { category ->
        if (style.getImage(category.mapIconId) != null) return@forEach
        val drawable = ContextCompat.getDrawable(context, category.iconRes)?.mutate() ?: return@forEach
        DrawableCompat.setTint(drawable, category.mapTintColor)
        val bitmap = BitmapUtils.getBitmapFromDrawable(drawable) ?: return@forEach
        style.addImage(category.mapIconId, bitmap)
    }
}

fun addPoiMapLayers(
    style: Style,
    pois: List<MapPoi>,
    highlightPoiId: String? = null
) {
    val sorted = pois.sortedBy { it.orderIndex }
    if (sorted.isEmpty()) return

    removePoiMapLayers(style)

    if (sorted.size >= 2) {
        val routePoints = sorted.map { Point.fromLngLat(it.longitude, it.latitude) }
        val line = LineString.fromLngLats(routePoints)

        style.addSource(GeoJsonSource(ROUTE_CASE_SOURCE_ID, Feature.fromGeometry(line)))
        style.addLayer(
            LineLayer("$ROUTE_CASE_SOURCE_ID-layer", ROUTE_CASE_SOURCE_ID).withProperties(
                PropertyFactory.lineColor("#FFFFFF"),
                PropertyFactory.lineWidth(7f),
                PropertyFactory.lineOpacity(0.9f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        )

        style.addSource(GeoJsonSource(ROUTE_SOURCE_ID, Feature.fromGeometry(line)))
        style.addLayer(
            LineLayer("$ROUTE_SOURCE_ID-layer", ROUTE_SOURCE_ID).withProperties(
                PropertyFactory.lineColor("#1A73E8"),
                PropertyFactory.lineWidth(4.5f),
                PropertyFactory.lineOpacity(0.95f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
    }

    val lastIndex = sorted.lastIndex
    val markerFeatures = sorted.mapIndexed { index, poi ->
        Feature.fromGeometry(Point.fromLngLat(poi.longitude, poi.latitude)).apply {
            addStringProperty("iconId", poi.category.mapIconId)
            addStringProperty("name", poi.name)
            addStringProperty(
                "markerLabel",
                when (index) {
                    0 -> "START"
                    lastIndex -> if (lastIndex == 0) "START" else "END"
                    else -> (index + 1).toString()
                }
            )
            addStringProperty(
                "labelColor",
                when (index) {
                    0 -> "#2E7D32"
                    lastIndex -> if (lastIndex == 0) "#2E7D32" else "#D32F2F"
                    else -> "#1A73E8"
                }
            )
            addNumberProperty(
                "iconSize",
                when {
                    poi.id == highlightPoiId -> 1.45
                    index == 0 || index == lastIndex -> 1.2
                    else -> 1.0
                }
            )
        }
    }

    style.addSource(GeoJsonSource(MARKER_SOURCE_ID, FeatureCollection.fromFeatures(markerFeatures)))
    style.addLayer(
        SymbolLayer("$MARKER_SOURCE_ID-layer", MARKER_SOURCE_ID).withProperties(
            PropertyFactory.iconImage(Expression.get("iconId")),
            PropertyFactory.iconSize(Expression.get("iconSize")),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM)
        )
    )

    style.addSource(GeoJsonSource(LABEL_SOURCE_ID, FeatureCollection.fromFeatures(markerFeatures)))
    style.addLayer(
        SymbolLayer("$LABEL_SOURCE_ID-layer", LABEL_SOURCE_ID).withProperties(
            PropertyFactory.textField(Expression.get("name")),
            PropertyFactory.textSize(11f),
            PropertyFactory.textColor("#212121"),
            PropertyFactory.textHaloColor("#FFFFFF"),
            PropertyFactory.textHaloWidth(1.5f),
            PropertyFactory.textOffset(arrayOf(0f, 1.6f)),
            PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
            PropertyFactory.textAllowOverlap(false),
            PropertyFactory.textOptional(true),
            PropertyFactory.textMaxWidth(8f)
        )
    )

    style.addLayer(
        SymbolLayer("$LABEL_SOURCE_ID-order-layer", LABEL_SOURCE_ID).withProperties(
            PropertyFactory.textField(Expression.get("markerLabel")),
            PropertyFactory.textSize(10f),
            PropertyFactory.textColor(Expression.get("labelColor")),
            PropertyFactory.textHaloColor("#FFFFFF"),
            PropertyFactory.textHaloWidth(2f),
            PropertyFactory.textOffset(arrayOf(0f, -2.4f)),
            PropertyFactory.textAnchor(Property.TEXT_ANCHOR_BOTTOM),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textFont(arrayOf("Open Sans Bold", "Arial Unicode MS Bold"))
        )
    )
}

fun removePoiMapLayers(style: Style) {
    listOf(
        "$LABEL_SOURCE_ID-order-layer",
        "$LABEL_SOURCE_ID-layer",
        "$MARKER_SOURCE_ID-layer",
        "$ROUTE_SOURCE_ID-layer",
        "$ROUTE_CASE_SOURCE_ID-layer"
    ).forEach { layerId ->
        style.getLayer(layerId)?.let { style.removeLayer(it) }
    }
    listOf(
        LABEL_SOURCE_ID,
        MARKER_SOURCE_ID,
        ROUTE_SOURCE_ID,
        ROUTE_CASE_SOURCE_ID
    ).forEach { sourceId ->
        style.getSource(sourceId)?.let { style.removeSource(it) }
    }
}

fun fitMapToPois(map: MapLibreMap, pois: List<MapPoi>, paddingPx: Int = 96) {
    if (pois.isEmpty()) return

    if (pois.size == 1) {
        val poi = pois.first()
        map.cameraPosition = CameraPosition.Builder()
            .target(LatLng(poi.latitude, poi.longitude))
            .zoom(14.0)
            .build()
        return
    }

    val builder = LatLngBounds.Builder()
    pois.forEach { poi ->
        builder.include(LatLng(poi.latitude, poi.longitude))
    }
    map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), paddingPx))
}

fun centerOfPois(pois: List<MapPoi>): LatLng? {
    if (pois.isEmpty()) return null
    val lat = pois.map { it.latitude }.average()
    val lng = pois.map { it.longitude }.average()
    return LatLng(lat, lng)
}
