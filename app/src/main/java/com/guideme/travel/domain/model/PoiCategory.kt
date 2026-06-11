package com.guideme.travel.domain.model

import androidx.annotation.DrawableRes
import com.guideme.travel.R

enum class PoiCategory(
    val mapIconId: String,
    @DrawableRes val iconRes: Int,
    val mapTintColor: Int
) {
    PARK("poi-park", R.drawable.ic_poi_park, 0xFF2E7D32.toInt()),
    MONUMENT("poi-monument", R.drawable.ic_poi_monument, 0xFF8D6E63.toInt()),
    VIEWPOINT("poi-viewpoint", R.drawable.ic_poi_viewpoint, 0xFF5C6BC0.toInt()),
    BEACH("poi-beach", R.drawable.ic_poi_beach, 0xFF00ACC1.toInt()),
    LANDMARK("poi-landmark", R.drawable.ic_poi_landmark, 0xFFFF5A5F.toInt());

    companion object {
        fun infer(name: String, description: String = ""): PoiCategory {
            val text = "$name $description".lowercase()
            return when {
                text.contains(Regex("national park|state park|wildlife|sanctuary|reserve|garden|forest|zoo")) ->
                    PARK
                text.contains(Regex("viewpoint|overlook|scenic|vista|lookout|observation")) ->
                    VIEWPOINT
                text.contains(Regex("beach|coast|shore|bay|island")) ->
                    BEACH
                text.contains(
                    Regex(
                        "monument|memorial|fort|palace|temple|mosque|church|cathedral|" +
                            "museum|heritage|unesco|mausoleum|ruins|basilica|shrine|fortress"
                    )
                ) -> MONUMENT
                else -> LANDMARK
            }
        }
    }
}

data class MapPoi(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val orderIndex: Int,
    val category: PoiCategory
)

fun Attraction.toMapPoi(): MapPoi {
    return MapPoi(
        id = id,
        name = name,
        latitude = latitude,
        longitude = longitude,
        orderIndex = orderIndex,
        category = PoiCategory.infer(name, description)
    )
}

fun CuratedSpot.toMapPoi(): MapPoi {
    return MapPoi(
        id = id,
        name = name,
        latitude = latitude,
        longitude = longitude,
        orderIndex = orderIndex,
        category = PoiCategory.infer(name, description)
    )
}
