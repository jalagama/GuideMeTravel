package com.guideme.travel.util

import com.guideme.travel.domain.model.MapPoi
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val toRad = { value: Double -> value * Math.PI / 180.0 }
    val dLat = toRad(lat2 - lat1)
    val dLon = toRad(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(toRad(lat1)) * cos(toRad(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return 6371.0 * 2 * atan2(sqrt(a), sqrt(1 - a))
}

fun routeDistanceKm(pois: List<MapPoi>): Double {
    val sorted = pois.sortedBy { it.orderIndex }
    if (sorted.size < 2) return 0.0
    return sorted.zipWithNext().sumOf { (from, to) ->
        haversineKm(from.latitude, from.longitude, to.latitude, to.longitude)
    }
}
