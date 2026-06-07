package com.guideme.travel.data.sample

import com.guideme.travel.domain.model.Attraction
import com.guideme.travel.domain.model.AttractionStatus
import com.guideme.travel.domain.model.TripPlan
import com.guideme.travel.domain.model.TripStatus
import java.util.UUID

object SampleTripData {
    fun createHampiTrip(origin: String, destination: String, languageCode: String): TripPlan {
        val tripId = UUID.randomUUID().toString()
        val attractions = listOf(
            Attraction(
                id = "${tripId}_virupaksha",
                name = "Virupaksha Temple",
                description = "A 7th-century temple dedicated to Lord Shiva and the spiritual heart of Hampi.",
                latitude = 15.3350,
                longitude = 76.4600,
                imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/8/8e/Virupaksha_Temple_Hampi.jpg/640px-Virupaksha_Temple_Hampi.jpg",
                orderIndex = 0,
                transcript = "Welcome to Virupaksha Temple, one of the oldest functioning temples in India. This sacred site has been active since the 7th century and remains the spiritual center of Hampi.",
                estimatedMinutes = 60
            ),
            Attraction(
                id = "${tripId}_vittala",
                name = "Vittala Temple",
                description = "Famous for its iconic stone chariot and musical pillars.",
                latitude = 15.3420,
                longitude = 76.4780,
                imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4a/Stone_Chariot_Hampi.jpg/640px-Stone_Chariot_Hampi.jpg",
                orderIndex = 1,
                transcript = "You are approaching Vittala Temple, home to the legendary stone chariot and extraordinary musical pillars that produce melodic tones when tapped.",
                estimatedMinutes = 75
            ),
            Attraction(
                id = "${tripId}_lotus",
                name = "Lotus Mahal",
                description = "An elegant Indo-Islamic pavilion in the Zenana enclosure.",
                latitude = 15.3185,
                longitude = 76.4715,
                imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1f/Lotus_Mahal_Hampi.jpg/640px-Lotus_Mahal_Hampi.jpg",
                orderIndex = 2,
                transcript = "Lotus Mahal blends Hindu and Islamic architecture. Its lotus-shaped arches and airy design made it a retreat for royal women.",
                estimatedMinutes = 40
            ),
            Attraction(
                id = "${tripId}_matanga",
                name = "Matanga Hill",
                description = "Panoramic sunrise and sunset views over the Hampi boulder landscape.",
                latitude = 15.3375,
                longitude = 76.4675,
                imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/5/5e/Hampi_sunrise.jpg/640px-Hampi_sunrise.jpg",
                orderIndex = 3,
                transcript = "Matanga Hill offers the finest panoramic views in Hampi. From here you can see the Tungabhadra River winding through ancient ruins.",
                estimatedMinutes = 50
            ),
            Attraction(
                id = "${tripId}_hemakuta",
                name = "Hemakuta Hill Temples",
                description = "Cluster of early temples with sweeping views near Virupaksha.",
                latitude = 15.3338,
                longitude = 76.4588,
                imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/9/9a/Hemakuta_Hill_Hampi.jpg/640px-Hemakuta_Hill_Hampi.jpg",
                orderIndex = 4,
                transcript = "Hemakuta Hill holds a cluster of early Vijayanagara temples. The hill is especially beautiful at sunset when the stone glows golden.",
                estimatedMinutes = 45
            )
        )

        return TripPlan(
            id = tripId,
            origin = origin,
            destination = destination,
            languageCode = languageCode,
            status = TripStatus.DRAFT,
            attractions = attractions,
            createdAtMillis = System.currentTimeMillis(),
            offlinePackSizeMb = 120
        )
    }
}
