package com.guideme.travel.ui.components

import com.guideme.travel.BuildConfig

object PoiMapStyle {
    fun defaultStyleUrl(): String {
        val key = BuildConfig.MAPTILER_API_KEY
        return if (key.isBlank()) {
            "https://demotiles.maplibre.org/style.json"
        } else {
            "https://api.maptiler.com/maps/streets-v2/style.json?key=$key"
        }
    }
}
