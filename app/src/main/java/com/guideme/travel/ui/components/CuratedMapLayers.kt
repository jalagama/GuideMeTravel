package com.guideme.travel.ui.components

import com.guideme.travel.domain.model.CuratedSpot
import com.guideme.travel.domain.model.toMapPoi
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style

@Deprecated("Use registerPoiMapIcons + addPoiMapLayers", ReplaceWith("addPoiMapLayers(style, spots.map { it.toMapPoi() })"))
fun addCuratedSpotsMapLayers(style: Style, spots: List<CuratedSpot>) {
    addPoiMapLayers(style, spots.map { it.toMapPoi() })
}

fun curatedMapCenter(spots: List<CuratedSpot>): LatLng? {
    return centerOfPois(spots.map { it.toMapPoi() })
}
